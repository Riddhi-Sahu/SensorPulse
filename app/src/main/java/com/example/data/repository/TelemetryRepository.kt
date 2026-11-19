package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.export.CsvExportSummary
import com.example.data.export.SessionCsvExporter
import com.example.data.local.CameraCaptureEntity
import com.example.data.local.RecordingSessionEntity
import com.example.data.local.SensorPulseDatabase
import com.example.data.local.SensorReadingEntity
import com.example.data.local.TelemetryDao
import com.example.data.model.CameraCaptureRecord
import com.example.data.model.RecordingSession
import com.example.data.model.SensorReading
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TelemetryRepository(
    private val dao: TelemetryDao
) {
    constructor(context: Context) : this(
        SensorPulseDatabase.getInstance(context).telemetryDao()
    )

    fun observeSessions(): Flow<List<RecordingSession>> =
        dao.observeSessions().map { list -> list.map { it.toModel() } }

    fun observeSensorReadings(): Flow<List<SensorReading>> =
        dao.observeSensorReadings().map { list -> list.map { it.toModel() } }

    fun observeCameraCaptures(): Flow<List<CameraCaptureRecord>> =
        dao.observeCameraCaptures().map { list -> list.map { it.toModel() } }

    suspend fun startSession(
        title: String,
        userId: Long? = null,
        ownerUsername: String? = null
    ): Result<Long> {
        return try {
            val now = System.currentTimeMillis()
            val id = dao.insertSession(
                RecordingSessionEntity(
                    userId = userId,
                    ownerUsername = ownerUsername?.trim()?.takeIf { it.isNotEmpty() },
                    title = title.ifBlank { "Telemetry Session" },
                    startedAtMs = now,
                    endedAtMs = null,
                    isActive = true,
                    totalSteps = 0
                )
            )
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun stopSession(sessionId: Long, totalSteps: Int): Result<Unit> {
        return try {
            require(sessionId > 0L) { "Invalid session ID" }
            dao.finishSession(sessionId, System.currentTimeMillis(), totalSteps.coerceAtLeast(0))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSessionSteps(sessionId: Long, totalSteps: Int) {
        if (sessionId <= 0L) return
        runCatching { dao.updateSessionSteps(sessionId, totalSteps.coerceAtLeast(0)) }
    }

    suspend fun deleteSession(sessionId: Long): Result<Unit> {
        return try {
            require(sessionId > 0L) { "Invalid session ID" }
            dao.deleteReadingsForSession(sessionId)
            dao.clearSessionIdForCaptures(sessionId)
            dao.deleteSession(sessionId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveSensorReading(
        sessionId: Long? = null,
        sensorId: String,
        sensorName: String,
        sensorCategory: String,
        values: List<Double>,
        unit: String,
        accuracy: Int,
        stepCount: Int = 0,
        notes: String? = null
    ): Result<Unit> {
        return try {
            val safeValues = values.ifEmpty { listOf(0.0) }.take(16)
            require(sensorId.isNotBlank() && sensorId.length <= 128) { "Invalid sensorId" }
            require(sensorName.isNotBlank() && sensorName.length <= 120) { "Invalid sensorName" }

            val now = System.currentTimeMillis()
            val entity = SensorReadingEntity(
                sessionId = sessionId,
                sensorId = sensorId.take(128),
                sensorName = sensorName.take(120),
                sensorCategory = sensorCategory,
                values = safeValues,
                unit = unit.take(40),
                accuracy = accuracy.coerceIn(0, 5),
                stepCount = stepCount.coerceAtLeast(0),
                notes = notes?.trim()?.take(300)?.takeIf { it.isNotEmpty() },
                createdAtMs = now,
                updatedAtMs = now
            )
            dao.insertSensorReading(entity)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveCameraCapture(
        sessionId: Long? = null,
        fileName: String,
        localFilePath: String,
        lensFacing: String,
        widthPx: Int,
        heightPx: Int,
        fileSizeBytes: Long,
        flashUsed: Boolean,
        focalLengthMm: Double? = null,
        caption: String? = null
    ): Result<Unit> {
        return try {
            require(fileName.isNotBlank() && fileName.length <= 128) { "Invalid fileName" }
            require(localFilePath.isNotBlank() && localFilePath.length <= 512) { "Invalid localFilePath" }
            require(widthPx >= 1 && heightPx >= 1) { "Invalid image dimensions" }
            require(fileSizeBytes >= 1L) { "Invalid file size" }

            val now = System.currentTimeMillis()
            val entity = CameraCaptureEntity(
                sessionId = sessionId,
                fileName = fileName.take(128),
                localFilePath = localFilePath.take(512),
                lensFacing = if (lensFacing == "front") "front" else "back",
                widthPx = widthPx.coerceIn(1, 20000),
                heightPx = heightPx.coerceIn(1, 20000),
                fileSizeBytes = fileSizeBytes.coerceIn(1L, 100_000_000L),
                flashUsed = flashUsed,
                focalLengthMm = focalLengthMm?.takeIf { it in 0.0..500.0 },
                caption = caption?.trim()?.take(240)?.takeIf { it.isNotEmpty() },
                createdAtMs = now,
                updatedAtMs = now
            )
            dao.insertCameraCapture(entity)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateReadingNotes(readingId: Long, notes: String): Result<Unit> {
        return try {
            require(readingId > 0L) { "Invalid reading ID" }
            val trimmed = notes.trim().take(300).takeIf { it.isNotEmpty() }
            dao.updateReadingNotes(readingId, trimmed, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCaptureCaption(captureId: Long, caption: String): Result<Unit> {
        return try {
            require(captureId > 0L) { "Invalid capture ID" }
            val trimmed = caption.trim().take(240).takeIf { it.isNotEmpty() }
            dao.updateCaptureCaption(captureId, trimmed, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSensorReading(readingId: Long): Result<Unit> {
        return try {
            require(readingId > 0L) { "Invalid reading ID" }
            dao.deleteSensorReading(readingId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCameraCapture(captureId: Long, localFilePath: String?): Result<Unit> {
        return try {
            require(captureId > 0L) { "Invalid capture ID" }
            dao.deleteCameraCapture(captureId)
            if (!localFilePath.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val f = File(localFilePath)
                        if (f.exists()) f.delete()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportSessionsCsv(
        outputStream: OutputStream,
        sessionId: Long? = null
    ): Result<CsvExportSummary> {
        return try {
            val summary = withContext(Dispatchers.IO) {
                val bundles = if (sessionId != null) {
                    listOfNotNull(dao.getSessionWithTelemetryById(sessionId))
                } else {
                    dao.getAllSessionsWithTelemetry()
                }
                check(bundles.isNotEmpty()) {
                    "No recorded session data available to export."
                }
                SessionCsvExporter.writeToStream(outputStream, bundles)
            }
            Result.success(summary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportSessionsCsvToUri(
        context: Context,
        targetUri: Uri,
        sessionId: Long? = null
    ): Result<CsvExportSummary> {
        return try {
            withContext(Dispatchers.IO) {
                val stream = context.contentResolver.openOutputStream(targetUri)
                    ?: error("Unable to open destination file for writing.")
                stream.use { outputStream ->
                    exportSessionsCsv(outputStream, sessionId).getOrThrow()
                }
            }.let { Result.success(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
