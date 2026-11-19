package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CameraCaptureRecord
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class TelemetryVaultViewModel(
    private val repository: TelemetryRepository
) : ViewModel() {

    private val _logFilterCategory = MutableStateFlow<String?>(null)
    val logFilterCategory: StateFlow<String?> = _logFilterCategory.asStateFlow()

    private val _vaultFeedback = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val vaultFeedback: SharedFlow<String> = _vaultFeedback.asSharedFlow()

    val sensorReadings: StateFlow<UiState<List<SensorReading>>> =
        repository.observeSensorReadings()
            .map<List<SensorReading>, UiState<List<SensorReading>>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing sensor readings", error)
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

    private companion object {
        const val TAG = "TelemetryVaultVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
