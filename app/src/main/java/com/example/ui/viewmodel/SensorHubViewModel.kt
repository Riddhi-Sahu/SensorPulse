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
    private val cameraFileStore: CameraLocalFileStore,
    private val currentUserId: String
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

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _feedback = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val feedback: SharedFlow<String> = _feedback.asSharedFlow()

    private var streamJob: Job? = null

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

    fun openSensor(descriptor: SensorDescriptor) {
        stopStream()
        _streamError.value = null
        _liveSample.value = null
        _waveformHistory.value = emptyList()
        _activeSensor.value = descriptor
        // Always start in Permission Gate until user authorizes on tap;
        // if previously consented in this session and OS permission still holds, stream right away.
        if (hasRequiredPermissions(descriptor)) {
            startStreamFor(descriptor)
        }
    }

    fun onPermissionGranted(descriptor: SensorDescriptor) {
        _consentedSensorIds.value = _consentedSensorIds.value + descriptor.id
        _streamError.value = null
        if (descriptor.kind != SensorKind.CAMERA_OPTICAL) {
            startStreamFor(descriptor)
        }
    }

    fun closeActiveSensor() {
        stopStream()
        _activeSensor.value = null
        _liveSample.value = null
        _waveformHistory.value = emptyList()
        _streamError.value = null
    }

    private fun startStreamFor(descriptor: SensorDescriptor) {
        stopStream()
        if (descriptor.kind == SensorKind.CAMERA_OPTICAL) return

        val flow = when (descriptor.kind) {
            SensorKind.HARDWARE_SENSOR -> sensorManager.streamHardwareSensor(descriptor)
            SensorKind.GPS_LOCATION -> sensorManager.streamLocation(descriptor)
            SensorKind.MICROPHONE_AUDIO -> sensorManager.streamMicrophoneDb(descriptor)
            SensorKind.CAMERA_OPTICAL -> return
        }

        streamJob = viewModelScope.launch {
            flow.catch { err ->
                _streamError.value = err.localizedMessage ?: "Sensor stream interrupted"
            }.collect { sample ->
                _liveSample.value = sample
                val updated = (_waveformHistory.value + listOf(sample.values)).takeLast(MAX_WAVEFORM_POINTS)
                _waveformHistory.value = updated
            }
        }
    }

    private fun stopStream() {
        streamJob?.cancel()
        streamJob = null
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
            val result = repository.saveSensorReading(
                userId = currentUserId,
                sensorId = sensor.id,
                sensorName = sensor.name,
                sensorCategory = sensor.category.firestoreValue,
                values = sample.values,
                unit = sample.unit,
                accuracy = sample.accuracy,
                notes = notes
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _feedback.tryEmit("Snapshot synced to Cloud Firestore (${sensor.name})")
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
            val result = repository.saveCameraCapture(
                userId = currentUserId,
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
                    _feedback.tryEmit("Photo saved locally & optical metadata synced to Camera Vault")
                },
                onFailure = { err ->
                    _feedback.tryEmit(err.localizedMessage ?: "Failed to store camera capture metadata")
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopStream()
    }

    private companion object {
        const val MAX_WAVEFORM_POINTS = 60
    }
}
