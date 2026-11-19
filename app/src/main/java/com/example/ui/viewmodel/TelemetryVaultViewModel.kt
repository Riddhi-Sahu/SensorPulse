package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CameraCaptureRecord
import com.example.data.model.RecordingSession
import com.example.data.model.SensorReading
import com.example.data.repository.TelemetryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class SessionWithReadings(
    val session: RecordingSession,
    val readings: List<SensorReading>,
    val captures: List<CameraCaptureRecord> = emptyList()
)

data class TelemetryLogBundle(
    val sessions: List<SessionWithReadings>,
    val standaloneReadings: List<SensorReading>
)

class TelemetryVaultViewModel(
    private val repository: TelemetryRepository
) : ViewModel() {

    private val _logFilterCategory = MutableStateFlow<String?>(null)
    val logFilterCategory: StateFlow<String?> = _logFilterCategory.asStateFlow()

    private val _vaultFeedback = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val vaultFeedback: SharedFlow<String> = _vaultFeedback.asSharedFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    val logBundle: StateFlow<UiState<TelemetryLogBundle>> =
        combine(
            repository.observeSessions(),
            repository.observeSensorReadings(),
            repository.observeCameraCaptures()
        ) { sessions, readings, captures ->
            val bySession = readings.groupBy { it.sessionId }
            val capturesBySession = captures.groupBy { it.sessionId }
            val sessionBundles = sessions.map { session ->
                SessionWithReadings(
                    session = session,
                    readings = (bySession[session.id] ?: emptyList()).sortedByDescending { it.createdAtMs },
                    captures = (capturesBySession[session.id] ?: emptyList()).sortedByDescending { it.createdAtMs }
                )
            }
            val knownSessionIds = sessions.mapTo(mutableSetOf()) { it.id }
            val standalone = readings
                .filter { it.sessionId == null || it.sessionId !in knownSessionIds }
                .sortedByDescending { it.createdAtMs }
            UiState.Success(
                TelemetryLogBundle(
                    sessions = sessionBundles,
                    standaloneReadings = standalone
                )
            ) as UiState<TelemetryLogBundle>
        }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing telemetry sessions", error)
                emit(UiState.Error(error.message ?: "Failed to load telemetry logs"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val cameraCaptures: StateFlow<UiState<List<CameraCaptureRecord>>> =
        repository.observeCameraCaptures()
            .map<List<CameraCaptureRecord>, UiState<List<CameraCaptureRecord>>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing camera captures", error)
                emit(UiState.Error(error.message ?: "Failed to load camera captures"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    fun setLogFilterCategory(category: String?) {
        _logFilterCategory.value = category
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId).fold(
                onSuccess = { _vaultFeedback.tryEmit("Session deleted") },
                onFailure = { _vaultFeedback.tryEmit(it.localizedMessage ?: "Failed to delete session") }
            )
        }
    }

    fun updateReadingNotes(readingId: Long, notes: String) {
        viewModelScope.launch {
            repository.updateReadingNotes(readingId, notes).fold(
                onSuccess = { _vaultFeedback.tryEmit("Snapshot note updated") },
                onFailure = { _vaultFeedback.tryEmit(it.localizedMessage ?: "Failed to update note") }
            )
        }
    }

    fun deleteSensorReading(readingId: Long) {
        viewModelScope.launch {
            repository.deleteSensorReading(readingId).fold(
                onSuccess = { _vaultFeedback.tryEmit("Snapshot deleted") },
                onFailure = { _vaultFeedback.tryEmit(it.localizedMessage ?: "Failed to delete snapshot") }
            )
        }
    }

    fun updateCaptureCaption(captureId: Long, caption: String) {
        viewModelScope.launch {
            repository.updateCaptureCaption(captureId, caption).fold(
                onSuccess = { _vaultFeedback.tryEmit("Capture caption updated") },
                onFailure = { _vaultFeedback.tryEmit(it.localizedMessage ?: "Failed to update caption") }
            )
        }
    }

    fun deleteCameraCapture(captureId: Long, localFilePath: String?) {
        viewModelScope.launch {
            repository.deleteCameraCapture(captureId, localFilePath).fold(
                onSuccess = { _vaultFeedback.tryEmit("Camera capture removed") },
                onFailure = { _vaultFeedback.tryEmit(it.localizedMessage ?: "Failed to remove capture") }
            )
        }
    }

    fun exportSessionsCsv(context: Context, targetUri: Uri, sessionId: Long? = null) {
        if (_isExporting.value) return
        viewModelScope.launch {
            _isExporting.value = true
            try {
                repository.exportSessionsCsvToUri(context.applicationContext, targetUri, sessionId).fold(
                    onSuccess = { summary ->
                        _vaultFeedback.tryEmit(
                            "Exported ${summary.sessionCount} session(s) (${summary.rowCount} rows) to CSV"
                        )
                    },
                    onFailure = { err ->
                        _vaultFeedback.tryEmit(err.localizedMessage ?: "Failed to export CSV")
                    }
                )
            } finally {
                _isExporting.value = false
            }
        }
    }

    private companion object {
        const val TAG = "TelemetryVaultVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
