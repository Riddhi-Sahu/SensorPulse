package com.example.data.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.data.model.LiveTelemetrySample
import com.example.data.model.SensorCategory
import com.example.data.model.SensorDescriptor
import com.example.data.model.SensorKind
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

class DeviceSensorManager(context: Context) {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)

    fun discoverAllSensors(): List<SensorDescriptor> {
        val result = mutableListOf<SensorDescriptor>()

        // 1. Hardware & Composite Sensors from Android SensorManager
        val rawSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        rawSensors.forEachIndexed { index, sensor ->
            val meta = mapHardwareSensorMeta(sensor)
            val id = "hw_${sensor.type}_${index}"
            result += SensorDescriptor(
                id = id,
                name = sensor.name.ifBlank { meta.fallbackName },
                category = meta.category,
                kind = SensorKind.HARDWARE_SENSOR,
                androidSensorType = sensor.type,
                requiredPermissions = meta.permissions,
                permissionRationale = meta.rationale,
                unit = meta.unit,
                axisLabels = meta.axisLabels,
                vendor = sensor.vendor.ifBlank { "Android Hardware" },
                maxRange = sensor.maximumRange,
                resolution = sensor.resolution,
                powerMa = sensor.power,
                isOneShotTrigger = sensor.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT
            )
        }

        // 2. GPS / Fused Location Sensor
        if (appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION) ||
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
        ) {
            result += SensorDescriptor(
                id = SENSOR_ID_GPS,
                name = "GPS & Location Telemetry",
                category = SensorCategory.LOCATION,
                kind = SensorKind.GPS_LOCATION,
                requiredPermissions = listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                permissionRationale = "Location permission is required to read live GPS latitude, longitude, altitude, speed, and accuracy.",
                unit = "deg / m",
                axisLabels = listOf("Lat", "Lon", "Alt (m)", "Accuracy (m)", "Speed (m/s)"),
                vendor = "Fused Location Provider"
            )
        }

        // 3. Microphone Acoustic Level Sensor
        if (appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            result += SensorDescriptor(
                id = SENSOR_ID_MIC,
                name = "Microphone Acoustic Meter",
                category = SensorCategory.AUDIO,
                kind = SensorKind.MICROPHONE_AUDIO,
                requiredPermissions = listOf(Manifest.permission.RECORD_AUDIO),
                permissionRationale = "Microphone permission is required to sample live ambient sound amplitude and decibel levels (no audio is recorded or uploaded).",
                unit = "dBFS",
                axisLabels = listOf("Level (dBFS)", "Rel SPL (dB)", "Peak RMS"),
                vendor = "AudioRecord PCM-16"
            )
        }

        // 4. Camera Optical Sensor (Always stored differently: local JPEG + Firestore optical metadata)
        if (appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            result += SensorDescriptor(
                id = SENSOR_ID_CAMERA,
                name = "Camera Optical Sensor",
                category = SensorCategory.CAMERA,
                kind = SensorKind.CAMERA_OPTICAL,
                requiredPermissions = listOf(Manifest.permission.CAMERA),
                permissionRationale = "Camera permission is required to open the live viewfinder, inspect optics metadata, and store JPEG captures separately in the Camera Vault.",
                unit = "MP / mm",
                axisLabels = listOf("Resolution", "Focal Length"),
                vendor = "CameraX Optical Pipeline"
            )
        }

        return result
    }

    fun hasSystemPermissions(descriptor: SensorDescriptor): Boolean {
        if (descriptor.requiredPermissions.isEmpty()) return true
        // For location, either FINE or COARSE satisfies foreground streaming
        if (descriptor.kind == SensorKind.GPS_LOCATION) {
            val fine = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            return fine || coarse
        }
        return descriptor.requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(appContext, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun streamHardwareSensor(descriptor: SensorDescriptor): Flow<LiveTelemetrySample> = callbackFlow {
        val sensorType = descriptor.androidSensorType
        val targetSensor = sensorManager.getSensorList(Sensor.TYPE_ALL).firstOrNull {
            it.type == sensorType && it.name == descriptor.name
        } ?: sensorType?.let { sensorManager.getDefaultSensor(it) }

        if (targetSensor == null) {
            trySend(
                LiveTelemetrySample(
                    values = listOf(0.0),
                    axisLabels = descriptor.axisLabels,
                    unit = descriptor.unit,
                    accuracy = 0,
                    timestampMs = System.currentTimeMillis(),
                    statusNote = "Sensor unavailable on this device."
                )
            )
            awaitClose { }
            return@callbackFlow
        }

        if (targetSensor.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT) {
            trySend(
                LiveTelemetrySample(
                    values = listOf(0.0),
                    axisLabels = descriptor.axisLabels,
                    unit = descriptor.unit,
                    accuracy = 3,
                    timestampMs = System.currentTimeMillis(),
                    statusNote = "Armed one-shot trigger sensor. Waiting for hardware motion event…"
                )
            )
            val triggerListener = object : TriggerEventListener() {
                override fun onTrigger(event: TriggerEvent?) {
                    val vals = event?.values?.map { it.toDouble() } ?: listOf(1.0)
                    trySend(
                        LiveTelemetrySample(
                            values = vals,
                            axisLabels = descriptor.axisLabels,
                            unit = descriptor.unit,
                            accuracy = 3,
                            timestampMs = System.currentTimeMillis(),
                            statusNote = "Trigger fired!"
                        )
                    )
                }
            }
            sensorManager.requestTriggerSensor(triggerListener, targetSensor)
            awaitClose {
                sensorManager.cancelTriggerSensor(triggerListener, targetSensor)
            }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event ?: return
                    val vals = event.values.map { it.toDouble() }.take(16).ifEmpty { listOf(0.0) }
                    trySend(
                        LiveTelemetrySample(
                            values = vals,
                            axisLabels = descriptor.axisLabels,
                            unit = descriptor.unit,
                            accuracy = event.accuracy.coerceIn(0, 5),
                            timestampMs = System.currentTimeMillis()
                        )
                    )
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, targetSensor, SensorManager.SENSOR_DELAY_UI)
            awaitClose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun streamLocation(descriptor: SensorDescriptor): Flow<LiveTelemetrySample> = callbackFlow {
        if (!hasSystemPermissions(descriptor)) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                trySend(
                    LiveTelemetrySample(
                        values = listOf(
                            loc.latitude,
                            loc.longitude,
                            loc.altitude,
                            loc.accuracy.toDouble(),
                            loc.speed.toDouble()
                        ),
                        axisLabels = descriptor.axisLabels,
                        unit = descriptor.unit,
                        accuracy = 3,
                        timestampMs = System.currentTimeMillis(),
                        statusNote = "Last known fix (acquiring live GPS…)"
                    )
                )
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
            .setMinUpdateIntervalMillis(800L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(
                    LiveTelemetrySample(
                        values = listOf(
                            loc.latitude,
                            loc.longitude,
                            loc.altitude,
                            loc.accuracy.toDouble(),
                            loc.speed.toDouble()
                        ),
                        axisLabels = descriptor.axisLabels,
                        unit = descriptor.unit,
                        accuracy = if (loc.accuracy < 15f) 3 else 2,
                        timestampMs = System.currentTimeMillis(),
                        statusNote = "Live GPS lock"
                    )
                )
            }
        }

        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    @SuppressLint("MissingPermission")
    fun streamMicrophoneDb(descriptor: SensorDescriptor): Flow<LiveTelemetrySample> = callbackFlow {
        if (!hasSystemPermissions(descriptor)) {
            close(SecurityException("Microphone permission not granted"))
            return@callbackFlow
        }

        val sampleRate = 44100
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            trySend(
                LiveTelemetrySample(
                    values = listOf(-90.0, 0.0, 0.0),
                    axisLabels = descriptor.axisLabels,
                    unit = descriptor.unit,
                    accuracy = 0,
                    timestampMs = System.currentTimeMillis(),
                    statusNote = "Microphone hardware busy or unavailable."
                )
            )
            awaitClose { }
            return@callbackFlow
        }

        val buffer = ShortArray(minBuf / 2)
        recorder.startRecording()

        while (isActive) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read > 0) {
                var sumSquares = 0.0
                var peak = 0
                for (i in 0 until read) {
                    val sample = kotlin.math.abs(buffer[i].toInt())
                    if (sample > peak) peak = sample
                    sumSquares += sample.toDouble() * sample.toDouble()
                }
                val rms = sqrt(sumSquares / read)
                val dbfs = if (rms > 1.0) (20.0 * log10(rms / 32768.0)).coerceIn(-90.0, 0.0) else -90.0
                val relSpl = (dbfs + 90.0).coerceIn(0.0, 90.0)
                val peakNorm = (peak / 32768.0).coerceIn(0.0, 1.0)

                trySend(
                    LiveTelemetrySample(
                        values = listOf(dbfs, relSpl, peakNorm),
                        axisLabels = descriptor.axisLabels,
                        unit = descriptor.unit,
                        accuracy = 3,
                        timestampMs = System.currentTimeMillis()
                    )
                )
            }
            kotlinx.coroutines.delay(90L)
        }

        awaitClose {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)

    private data class HardwareMeta(
        val fallbackName: String,
        val category: SensorCategory,
        val unit: String,
        val axisLabels: List<String>,
        val permissions: List<String> = emptyList(),
        val rationale: String = "Grant SensorPulse permission to stream live readings from this hardware sensor."
    )

    private fun mapHardwareSensorMeta(sensor: Sensor): HardwareMeta {
        val activityPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            emptyList()
        }
        return when (sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> HardwareMeta(
                "Accelerometer", SensorCategory.MOTION, "m/s²", listOf("X", "Y", "Z")
            )
            Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> HardwareMeta(
                "Accelerometer (Uncalibrated)", SensorCategory.MOTION, "m/s²",
                listOf("X", "Y", "Z", "Bias X", "Bias Y", "Bias Z")
            )
            Sensor.TYPE_LINEAR_ACCELERATION -> HardwareMeta(
                "Linear Acceleration", SensorCategory.MOTION, "m/s²", listOf("X", "Y", "Z")
            )
            Sensor.TYPE_GRAVITY -> HardwareMeta(
                "Gravity", SensorCategory.MOTION, "m/s²", listOf("X", "Y", "Z")
            )
            Sensor.TYPE_GYROSCOPE -> HardwareMeta(
                "Gyroscope", SensorCategory.MOTION, "rad/s", listOf("X", "Y", "Z")
            )
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> HardwareMeta(
                "Gyroscope (Uncalibrated)", SensorCategory.MOTION, "rad/s",
                listOf("X", "Y", "Z", "Drift X", "Drift Y", "Drift Z")
            )
            Sensor.TYPE_SIGNIFICANT_MOTION -> HardwareMeta(
                "Significant Motion", SensorCategory.MOTION, "trigger", listOf("Trigger")
            )
            Sensor.TYPE_STEP_DETECTOR -> HardwareMeta(
                "Step Detector", SensorCategory.MOTION, "event", listOf("Step"),
                permissions = activityPerms,
                rationale = "Activity Recognition permission is required to detect physical step events."
            )
            Sensor.TYPE_STEP_COUNTER -> HardwareMeta(
                "Step Counter", SensorCategory.MOTION, "steps", listOf("Total Steps"),
                permissions = activityPerms,
                rationale = "Activity Recognition permission is required to read cumulative hardware step counts."
            )
            Sensor.TYPE_MAGNETIC_FIELD -> HardwareMeta(
                "Magnetometer", SensorCategory.POSITION, "µT", listOf("X", "Y", "Z")
            )
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> HardwareMeta(
                "Magnetometer (Uncalibrated)", SensorCategory.POSITION, "µT",
                listOf("X", "Y", "Z", "Iron X", "Iron Y", "Iron Z")
            )
            Sensor.TYPE_ROTATION_VECTOR -> HardwareMeta(
                "Rotation Vector", SensorCategory.POSITION, "unitless",
                listOf("X·sin(θ/2)", "Y·sin(θ/2)", "Z·sin(θ/2)", "cos(θ/2)", "Accuracy")
            )
            Sensor.TYPE_GAME_ROTATION_VECTOR -> HardwareMeta(
                "Game Rotation Vector", SensorCategory.POSITION, "unitless",
                listOf("X", "Y", "Z", "W")
            )
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> HardwareMeta(
                "Geomagnetic Rotation Vector", SensorCategory.POSITION, "unitless",
                listOf("X", "Y", "Z", "W", "Accuracy")
            )
            Sensor.TYPE_PROXIMITY -> HardwareMeta(
                "Proximity", SensorCategory.POSITION, "cm", listOf("Distance")
            )
            @Suppress("DEPRECATION")
            Sensor.TYPE_ORIENTATION -> HardwareMeta(
                "Orientation", SensorCategory.POSITION, "deg", listOf("Azimuth", "Pitch", "Roll")
            )
            Sensor.TYPE_LIGHT -> HardwareMeta(
                "Ambient Light", SensorCategory.ENVIRONMENT, "lx", listOf("Illuminance")
            )
            Sensor.TYPE_PRESSURE -> HardwareMeta(
                "Barometer (Pressure)", SensorCategory.ENVIRONMENT, "hPa", listOf("Pressure")
            )
            Sensor.TYPE_AMBIENT_TEMPERATURE -> HardwareMeta(
                "Ambient Temperature", SensorCategory.ENVIRONMENT, "°C", listOf("Temperature")
            )
            Sensor.TYPE_RELATIVE_HUMIDITY -> HardwareMeta(
                "Relative Humidity", SensorCategory.ENVIRONMENT, "%", listOf("Humidity")
            )
            @Suppress("DEPRECATION")
            Sensor.TYPE_TEMPERATURE -> HardwareMeta(
                "Device Temperature", SensorCategory.ENVIRONMENT, "°C", listOf("Temp")
            )
            Sensor.TYPE_HEART_RATE -> HardwareMeta(
                "Heart Rate", SensorCategory.BIOMETRICS, "bpm", listOf("BPM"),
                permissions = listOf(Manifest.permission.BODY_SENSORS),
                rationale = "Body Sensors permission is required to read live heart rate telemetry."
            )
            Sensor.TYPE_HEART_BEAT -> HardwareMeta(
                "Heart Beat", SensorCategory.BIOMETRICS, "confidence", listOf("Confidence"),
                permissions = listOf(Manifest.permission.BODY_SENSORS),
                rationale = "Body Sensors permission is required to detect cardiac beat peaks."
            )
            Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> HardwareMeta(
                "Off-Body Detect", SensorCategory.BIOMETRICS, "state", listOf("On-Body")
            )
            Sensor.TYPE_HINGE_ANGLE -> HardwareMeta(
                "Hinge Angle", SensorCategory.POSITION, "deg", listOf("Angle")
            )
            Sensor.TYPE_HEADING -> HardwareMeta(
                "Heading", SensorCategory.POSITION, "deg", listOf("Heading", "Accuracy")
            )
            else -> HardwareMeta(
                sensor.name.ifBlank { "Sensor #${sensor.type}" },
                SensorCategory.OTHER,
                "raw",
                listOf("Ch 0", "Ch 1", "Ch 2", "Ch 3")
            )
        }
    }

    companion object {
        const val SENSOR_ID_GPS = "module_gps_location"
        const val SENSOR_ID_MIC = "module_microphone_audio"
        const val SENSOR_ID_CAMERA = "module_camera_optical"
    }
}
