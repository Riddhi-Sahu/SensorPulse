package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

enum class SensorCategory(val label: String, val firestoreValue: String) {
    MOTION("Motion", "MOTION"),
    ENVIRONMENT("Environment", "ENVIRONMENT"),
    POSITION("Position", "POSITION"),
    BIOMETRICS("Biometrics", "BIOMETRICS"),
    LOCATION("GPS", "LOCATION"),
    AUDIO("Audio", "AUDIO"),
    CAMERA("Camera", "CAMERA"),
    OTHER("Other", "OTHER");

    companion object {
        fun fromFirestore(raw: String?): SensorCategory =
            entries.firstOrNull { it.firestoreValue == raw } ?: OTHER
    }
}

enum class SensorKind {
    HARDWARE_SENSOR,
    GPS_LOCATION,
    MICROPHONE_AUDIO,
    CAMERA_OPTICAL
}

data class SensorDescriptor(
    val id: String,
    val name: String,
    val category: SensorCategory,
    val kind: SensorKind,
    val androidSensorType: Int? = null,
    val requiredPermissions: List<String> = emptyList(),
    val permissionRationale: String,
    val unit: String,
    val axisLabels: List<String>,
    val vendor: String,
    val maxRange: Float = 0f,
    val resolution: Float = 0f,
    val powerMa: Float = 0f,
    val isOneShotTrigger: Boolean = false
)

data class LiveTelemetrySample(
    val values: List<Double>,
    val axisLabels: List<String>,
    val unit: String,
    val accuracy: Int,
    val timestampMs: Long,
    val statusNote: String? = null
)

@Keep
@IgnoreExtraProperties
data class SensorReading(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sensorId: String = "",
    val sensorName: String = "",
    val sensorCategory: String = "OTHER",
    val values: List<Double> = emptyList(),
    val unit: String = "",
    val accuracy: Int = 0,
    val notes: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to userId,
            "sensorId" to sensorId.take(128),
            "sensorName" to sensorName.take(120),
            "sensorCategory" to sensorCategory,
            "values" to values.take(16),
            "unit" to unit.take(40),
            "accuracy" to accuracy.coerceIn(0, 5),
            "notes" to notes?.take(300)?.takeIf { it.isNotBlank() },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class CameraCaptureRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fileName: String = "",
    val localFilePath: String = "",
    val lensFacing: String = "back",
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val fileSizeBytes: Long = 0L,
    val flashUsed: Boolean = false,
    val focalLengthMm: Double? = null,
    val caption: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to userId,
            "fileName" to fileName.take(128),
            "localFilePath" to localFilePath.take(512),
            "lensFacing" to if (lensFacing == "front") "front" else "back",
            "widthPx" to widthPx.coerceIn(1, 20000),
            "heightPx" to heightPx.coerceIn(1, 20000),
            "fileSizeBytes" to fileSizeBytes.coerceIn(1L, 100_000_000L),
            "flashUsed" to flashUsed,
            "focalLengthMm" to focalLengthMm?.takeIf { it in 0.0..500.0 },
            "caption" to caption?.take(240)?.takeIf { it.isNotBlank() },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}
