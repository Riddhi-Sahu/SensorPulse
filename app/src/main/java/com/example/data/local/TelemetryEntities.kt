package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.data.model.CameraCaptureRecord
import com.example.data.model.SensorReading
import org.json.JSONArray

@Entity(tableName = "sensor_readings")
data class SensorReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sensorId: String,
    val sensorName: String,
    val sensorCategory: String,
    val values: List<Double>,
    val unit: String,
    val accuracy: Int,
    val notes: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    fun toModel(): SensorReading = SensorReading(
        id = id,
        sensorId = sensorId,
        sensorName = sensorName,
        sensorCategory = sensorCategory,
        values = values,
        unit = unit,
        accuracy = accuracy,
        notes = notes,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs
    )
}

@Entity(tableName = "camera_captures")
data class CameraCaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
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
