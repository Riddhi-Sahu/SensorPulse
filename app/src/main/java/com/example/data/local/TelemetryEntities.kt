package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.example.data.model.CameraCaptureRecord
import com.example.data.model.LocalUser
import com.example.data.model.RecordingSession
import com.example.data.model.SensorReading
import org.json.JSONArray

@Entity(
    tableName = "local_users",
    indices = [Index(value = ["usernameNormalized"], unique = true)]
)
data class LocalUserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val username: String,
    val usernameNormalized: String,
    val passwordHash: String,
    val saltHex: String,
    val createdAtMs: Long,
    val lastLoginAtMs: Long = createdAtMs,
    val isSignedIn: Boolean = false
) {
    fun toModel(): LocalUser = LocalUser(
        id = id,
        username = username,
        createdAtMs = createdAtMs
    )
}

@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Long? = null,
    val ownerUsername: String? = null,
    val title: String,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val isActive: Boolean = true,
    val totalSteps: Int = 0
) {
    fun toModel(): RecordingSession = RecordingSession(
        id = id,
        userId = userId,
        ownerUsername = ownerUsername,
        title = title,
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        isActive = isActive,
        totalSteps = totalSteps
    )
}

data class SessionWithTelemetry(
    @Embedded val session: RecordingSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val readings: List<SensorReadingEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val captures: List<CameraCaptureEntity>
)

@Entity(tableName = "sensor_readings")
data class SensorReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long? = null,
    val sensorId: String,
    val sensorName: String,
    val sensorCategory: String,
    val values: List<Double>,
    val unit: String,
    val accuracy: Int,
    val stepCount: Int = 0,
    val notes: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    fun toModel(): SensorReading = SensorReading(
        id = id,
        sessionId = sessionId,
        sensorId = sensorId,
        sensorName = sensorName,
        sensorCategory = sensorCategory,
        values = values,
        unit = unit,
        accuracy = accuracy,
        stepCount = stepCount,
        notes = notes,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs
    )
}

@Entity(tableName = "camera_captures")
data class CameraCaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long? = null,
    val fileName: String,
    val localFilePath: String,
    val lensFacing: String,
    val widthPx: Int,
    val heightPx: Int,
    val fileSizeBytes: Long,
    val flashUsed: Boolean,
    val focalLengthMm: Double?,
    val caption: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    fun toModel(): CameraCaptureRecord = CameraCaptureRecord(
        id = id,
        sessionId = sessionId,
        fileName = fileName,
        localFilePath = localFilePath,
        lensFacing = lensFacing,
        widthPx = widthPx,
        heightPx = heightPx,
        fileSizeBytes = fileSizeBytes,
        flashUsed = flashUsed,
        focalLengthMm = focalLengthMm,
        caption = caption,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs
    )
}

class TelemetryConverters {
    @TypeConverter
    fun fromDoubleList(values: List<Double>): String {
        val array = JSONArray()
        values.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toDoubleList(raw: String): List<Double> {
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { idx -> array.optDouble(idx, 0.0) }
        }.getOrDefault(emptyList())
    }
}
