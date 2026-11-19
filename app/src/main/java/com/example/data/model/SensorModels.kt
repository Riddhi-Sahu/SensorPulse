package com.example.data.model

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
        fun fromCode(raw: String?): SensorCategory =
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

data class SensorReading(
    val id: Long = 0L,
    val sensorId: String = "",
    val sensorName: String = "",
    val sensorCategory: String = "OTHER",
    val values: List<Double> = emptyList(),
    val unit: String = "",
    val accuracy: Int = 0,
    val notes: String? = null,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L
)

data class CameraCaptureRecord(
    val id: Long = 0L,
    val fileName: String = "",
    val localFilePath: String = "",
    val lensFacing: String = "back",
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val fileSizeBytes: Long = 0L,
    val flashUsed: Boolean = false,
    val focalLengthMm: Double? = null,
    val caption: String? = null,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L
)
