package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.camera.CameraLocalFileStore
import com.example.data.model.LiveTelemetrySample
import com.example.data.model.SensorCategory
import com.example.data.model.SensorDescriptor
import com.example.data.model.SensorKind
import com.example.data.repository.TelemetryRepository
import com.example.data.sensors.DeviceSensorManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SensorHubViewModel(
    private val repository: TelemetryRepository,
    private val sensorManager: DeviceSensorManager,
    private val cameraFileStore: CameraLocalFileStore
) : ViewModel() {

    private val _allSensors = MutableStateFlow<List<SensorDescriptor>>(emptyList())
    val allSensors: StateFlow<List<SensorDescriptor>> = _allSensors.asStateFlow()

    init {
        refreshDiscoveredSensors()
    }

    private val _selectedCategory = MutableStateFlow<SensorCategory?>(null)
    val selectedCategory: StateFlow<SensorCategory?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeSensor = MutableStateFlow<SensorDescriptor?>(null)
    val activeSensor: StateFlow<SensorDescriptor?> = _activeSensor.asStateFlow()

    private val _consentedSensorIds = MutableStateFlow<Set<String>>(emptySet())
    val consentedSensorIds: StateFlow<Set<String>> = _consentedSensorIds.asStateFlow()

    private val _liveSample = MutableStateFlow<LiveTelemetrySample?>(null)
    val liveSample: StateFlow<LiveTelemetrySample?> = _liveSample.asStateFlow()

    private val _waveformHistory = MutableStateFlow<List<List<Double>>>(emptyList())
    val waveformHistory: StateFlow<List<List<Double>>> = _waveformHistory.asStateFlow()

    private val _streamError = MutableStateFlow<String?>(null)
    val streamError: StateFlow<String?> = _streamError.asStateFlow()

    private val _isSensorStreaming = MutableStateFlow(false)
    val isSensorStreaming: StateFlow<Boolean> = _isSensorStreaming.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _feedback = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val feedback: SharedFlow<String> = _feedback.asSharedFlow()

    // Recording Session & Motion Step State
    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    private val _sessionStartedAtMs = MutableStateFlow<Long?>(null)
    val sessionStartedAtMs: StateFlow<Long?> = _sessionStartedAtMs.asStateFlow()

    private val _sessionRecordingSensors = MutableStateFlow<List<SensorDescriptor>>(emptyList())
    val sessionRecordingSensors: StateFlow<List<SensorDescriptor>> = _sessionRecordingSensors.asStateFlow()

    private val _sessionRecordedCount = MutableStateFlow(0)
    val sessionRecordedCount: StateFlow<Int> = _sessionRecordedCount.asStateFlow()

    private val _sessionPhotoCount = MutableStateFlow(0)
    val sessionPhotoCount: StateFlow<Int> = _sessionPhotoCount.asStateFlow()

    private val _motionStepCount = MutableStateFlow(0)
    val motionStepCount: StateFlow<Int> = _motionStepCount.asStateFlow()

    private val _isUserInMotion = MutableStateFlow(false)
    val isUserInMotion: StateFlow<Boolean> = _isUserInMotion.asStateFlow()

    private var streamJob: Job? = null
    private var detailStepJob: Job? = null
    private var sessionStepJob: Job? = null
    private val sessionStreamJobs = mutableMapOf<String, Job>()
    private var stepBaselineOffset = 0
    private var lastRawSteps = 0

    fun refreshDiscoveredSensors() {
        viewModelScope.launch(Dispatchers.Default) {
            _allSensors.value = sensorManager.discoverAllSensors()
        }
    }

    fun selectCategory(category: SensorCategory?) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun hasRequiredPermissions(descriptor: SensorDescriptor): Boolean {
        return descriptor.id in _consentedSensorIds.value &&
            sensorManager.hasSystemPermissions(descriptor)
    }

    fun startRecordingSession() {
        if (_activeSessionId.value != null) return
        viewModelScope.launch {
            val timeLabel = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date())
            val title = "Session • $timeLabel"
            repository.startSession(title).fold(
                onSuccess = { sessionId ->
                    _activeSessionId.value = sessionId
                    _sessionStartedAtMs.value = System.currentTimeMillis()
                    _sessionRecordedCount.value = 0
                    _sessionPhotoCount.value = 0
                    _sessionRecordingSensors.value = emptyList()
                    stepBaselineOffset = 0
                    lastRawSteps = 0
                    _motionStepCount.value = 0
                    _isUserInMotion.value = false

                    detailStepJob?.cancel()
                    detailStepJob = null
                    startSessionStepTracker(sessionId)

                    val openSensor = _activeSensor.value
                    if (openSensor != null &&
                        openSensor.kind != SensorKind.CAMERA_OPTICAL &&
                        hasRequiredPermissions(openSensor) &&
                        _isSensorStreaming.value
                    ) {
                        enrollSensorInSession(openSensor, sessionId)
                    }
                    _feedback.tryEmit("Recording session started — sensors & photos record here")
                },
                onFailure = { err ->
                    _feedback.tryEmit(err.localizedMessage ?: "Failed to start recording session")
                }
            )
        }
    }

    fun stopRecordingSession() {
        val sessionId = _activeSessionId.value ?: return
        val count = _sessionRecordedCount.value
        val photos = _sessionPhotoCount.value
        val steps = _motionStepCount.value

        sessionStreamJobs.values.forEach { it.cancel() }
        sessionStreamJobs.clear()
        _sessionRecordingSensors.value = emptyList()

        sessionStepJob?.cancel()
        sessionStepJob = null

        // Stop all currently streaming/granted sensors immediately when the session ends
        stopDetailStreamOnly()
        _liveSample.value = null
        _waveformHistory.value = emptyList()
        _streamError.value = null
        _consentedSensorIds.value = emptySet()

        _activeSessionId.value = null
        _sessionStartedAtMs.value = null
        _sessionPhotoCount.value = 0

        viewModelScope.launch {
            repository.stopSession(sessionId, steps)
            val photoPart = if (photos > 0) ", $photos photo(s)" else ""
            _feedback.tryEmit("Session stopped & sensors stopped ($count readings$photoPart, $steps steps)")
        }
    }

    fun openSensor(descriptor: SensorDescriptor) {
        stopDetailStreamOnly()
        _streamError.value = null
        _liveSample.value = null
        _waveformHistory.value = emptyList()
        _activeSensor.value = descriptor
        if (hasRequiredPermissions(descriptor)) {
            startStreamFor(descriptor)
            val sid = _activeSessionId.value
            if (sid != null && descriptor.kind != SensorKind.CAMERA_OPTICAL) {
                enrollSensorInSession(descriptor, sid)
            }
        }
    }

    fun onPermissionGranted(descriptor: SensorDescriptor) {
        _consentedSensorIds.value = _consentedSensorIds.value + descriptor.id
        _streamError.value = null
        if (descriptor.kind != SensorKind.CAMERA_OPTICAL) {
            startStreamFor(descriptor)
            val sid = _activeSessionId.value
            if (sid != null) {
                enrollSensorInSession(descriptor, sid)
            }
        }
    }

    fun stopSensor(sensorId: String) {
        val removedJob = sessionStreamJobs.remove(sensorId)
        val wasInSession = removedJob != null
        removedJob?.cancel()
        if (wasInSession) {
            _sessionRecordingSensors.value = _sessionRecordingSensors.value.filterNot { it.id == sensorId }
        }

        if (_activeSensor.value?.id == sensorId) {
            stopDetailStreamOnly()
        }

        val name = _allSensors.value.firstOrNull { it.id == sensorId }?.name ?: "Sensor"
        _feedback.tryEmit("Stopped $name")
    }

    fun resumeActiveSensor() {
        val sensor = _activeSensor.value ?: return
        if (!hasRequiredPermissions(sensor) || sensor.kind == SensorKind.CAMERA_OPTICAL) return
        startStreamFor(sensor)
        val sid = _activeSessionId.value
        if (sid != null) {
            enrollSensorInSession(sensor, sid)
        }
    }

    fun resetMotionSteps() {
        stepBaselineOffset = lastRawSteps
        _motionStepCount.value = 0
        val sid = _activeSessionId.value
        if (sid != null) {
            viewModelScope.launch {
                repository.updateSessionSteps(sid, 0)
            }
        }
    }

    fun closeActiveSensor() {
        stopDetailStreamOnly()
        _activeSensor.value = null
        _liveSample.value = null
        _waveformHistory.value = emptyList()
        _streamError.value = null
    }

    private fun startStreamFor(descriptor: SensorDescriptor) {
        stopDetailStreamOnly()
        if (descriptor.kind == SensorKind.CAMERA_OPTICAL) return

        val flow = createSensorFlow(descriptor) ?: return
        _isSensorStreaming.value = true

        if (descriptor.category == SensorCategory.MOTION && _activeSessionId.value == null) {
            startDetailStepTracker()
        }

        streamJob = viewModelScope.launch {
            flow.catch { err ->
                _isSensorStreaming.value = false
                _streamError.value = err.localizedMessage ?: "Sensor stream interrupted"
            }.collect { sample ->
                _liveSample.value = sample
                val updated = (_waveformHistory.value + listOf(sample.values)).takeLast(MAX_WAVEFORM_POINTS)
                _waveformHistory.value = updated

                // If active session step tracker isn't running, also fold direct hardware step/motion signals
                if (_activeSessionId.value == null && descriptor.category == SensorCategory.MOTION) {
                    sample.stepCount?.let { s ->
                        if (s > _motionStepCount.value && detailStepJob == null) {
                            _motionStepCount.value = s
                        }
                    }
                    if (sample.isInMotion) {
                        _isUserInMotion.value = true
                    }
                }
            }
        }
    }

    private fun enrollSensorInSession(descriptor: SensorDescriptor, sessionId: Long) {
        if (descriptor.kind == SensorKind.CAMERA_OPTICAL) return
        if (sessionStreamJobs.containsKey(descriptor.id)) return

        val flow = createSensorFlow(descriptor) ?: return
        if (_sessionRecordingSensors.value.none { it.id == descriptor.id }) {
            _sessionRecordingSensors.value = _sessionRecordingSensors.value + descriptor
        }

        sessionStreamJobs[descriptor.id] = viewModelScope.launch {
            var lastSavedMs = 0L
            flow.catch { err ->
                _feedback.tryEmit("${descriptor.name} stopped: ${err.localizedMessage ?: "stream error"}")
            }.collect { sample ->
                if (_activeSessionId.value != sessionId) return@collect
                val now = System.currentTimeMillis()
                if (lastSavedMs == 0L || (now - lastSavedMs) >= SESSION_SAMPLE_INTERVAL_MS) {
                    lastSavedMs = now
                    val steps = if (descriptor.category == SensorCategory.MOTION) {
                        _motionStepCount.value
                    } else {
                        0
                    }
                    repository.saveSensorReading(
                        sessionId = sessionId,
                        sensorId = descriptor.id,
                        sensorName = descriptor.name,
                        sensorCategory = descriptor.category.firestoreValue,
                        values = sample.values,
                        unit = sample.unit,
                        accuracy = sample.accuracy,
                        stepCount = steps,
                        notes = if (descriptor.category == SensorCategory.MOTION) {
                            if (_isUserInMotion.value) "In motion • $steps steps" else "Stationary • $steps steps"
                        } else {
                            null
                        }
                    ).onSuccess {
                        _sessionRecordedCount.value = _sessionRecordedCount.value + 1
                    }
                }
            }
        }
    }

    private fun startSessionStepTracker(sessionId: Long) {
        sessionStepJob?.cancel()
        sessionStepJob = viewModelScope.launch {
            sensorManager.streamMotionSteps().collect { state ->
                lastRawSteps = state.stepCount
                val adjusted = (state.stepCount - stepBaselineOffset).coerceAtLeast(0)
                _motionStepCount.value = adjusted
                _isUserInMotion.value = state.isInMotion
                repository.updateSessionSteps(sessionId, adjusted)
            }
        }
    }

    private fun startDetailStepTracker() {
        detailStepJob?.cancel()
        stepBaselineOffset = 0
        lastRawSteps = 0
        _motionStepCount.value = 0
        _isUserInMotion.value = false
        detailStepJob = viewModelScope.launch {
            sensorManager.streamMotionSteps().collect { state ->
                lastRawSteps = state.stepCount
                val adjusted = (state.stepCount - stepBaselineOffset).coerceAtLeast(0)
                _motionStepCount.value = adjusted
                _isUserInMotion.value = state.isInMotion
            }
        }
    }

    private fun createSensorFlow(descriptor: SensorDescriptor) = when (descriptor.kind) {
        SensorKind.HARDWARE_SENSOR -> sensorManager.streamHardwareSensor(descriptor)
        SensorKind.GPS_LOCATION -> sensorManager.streamLocation(descriptor)
        SensorKind.MICROPHONE_AUDIO -> sensorManager.streamMicrophoneDb(descriptor)
        SensorKind.CAMERA_OPTICAL -> null
    }

    private fun stopDetailStreamOnly() {
        streamJob?.cancel()
        streamJob = null
        detailStepJob?.cancel()
        detailStepJob = null
        _isSensorStreaming.value = false
        if (_activeSessionId.value == null) {
            _isUserInMotion.value = false
        }
    }

    fun saveCurrentSnapshot(notes: String? = null) {
        val sensor = _activeSensor.value ?: return
        val sample = _liveSample.value ?: run {
            _feedback.tryEmit("Waiting for live sensor reading before saving…")
            return
        }
        if (_isSaving.value) return

        viewModelScope.launch {
            _isSaving.value = true
            val steps = if (sensor.category == SensorCategory.MOTION) _motionStepCount.value else 0
            val sid = _activeSessionId.value
            val result = repository.saveSensorReading(
                sessionId = sid,
                sensorId = sensor.id,
                sensorName = sensor.name,
                sensorCategory = sensor.category.firestoreValue,
                values = sample.values,
                unit = sample.unit,
                accuracy = sample.accuracy,
                stepCount = steps,
                notes = notes
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    if (sid != null) {
                        _sessionRecordedCount.value = _sessionRecordedCount.value + 1
                    }
                    _feedback.tryEmit(
                        if (sid != null) "Snapshot recorded in active session (${sensor.name})"
                        else "Snapshot saved locally (${sensor.name})"
                    )
                },
                onFailure = { err ->
                    _feedback.tryEmit(err.localizedMessage ?: "Failed to save snapshot")
                }
            )
        }
    }

    fun createCaptureOutputFile(): File = cameraFileStore.createCaptureDestinationFile()

    fun persistCameraCapture(
        savedFile: File,
        lensFacing: String,
        flashUsed: Boolean,
        focalLengthMm: Double?,
        caption: String?
    ) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            val info = withContext(Dispatchers.IO) {
                cameraFileStore.inspectSavedFile(savedFile)
            }
            val activeSid = _activeSessionId.value
            val result = repository.saveCameraCapture(
                sessionId = activeSid,
                fileName = info.fileName,
                localFilePath = info.absolutePath,
                lensFacing = lensFacing,
                widthPx = info.widthPx,
                heightPx = info.heightPx,
                fileSizeBytes = info.fileSizeBytes,
                flashUsed = flashUsed,
                focalLengthMm = focalLengthMm,
                caption = caption
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    if (activeSid != null && _activeSessionId.value == activeSid) {
                        _sessionPhotoCount.value = _sessionPhotoCount.value + 1
                        _feedback.tryEmit("Photo captured & stored with active session")
                    } else {
                        _feedback.tryEmit("Photo & optical metadata saved to local Camera Vault")
                    }
                },
                onFailure = { err ->
                    _feedback.tryEmit(err.localizedMessage ?: "Failed to store camera capture metadata")
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopDetailStreamOnly()
        sessionStreamJobs.values.forEach { it.cancel() }
        sessionStreamJobs.clear()
        sessionStepJob?.cancel()
    }

    private companion object {
        const val MAX_WAVEFORM_POINTS = 60
        const val SESSION_SAMPLE_INTERVAL_MS = 1500L
    }
}
