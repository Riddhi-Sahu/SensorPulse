package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.LiveTelemetrySample
import com.example.data.model.SensorCategory
import com.example.data.model.SensorDescriptor
import com.example.data.model.SensorKind
import com.example.ui.components.OscilloscopeChart
import com.example.ui.components.PermissionGateCard
import com.example.ui.components.sensorCategoryColor
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryAmber
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorDetailScreen(
    sensor: SensorDescriptor,
    isAuthorized: Boolean,
    isStreaming: Boolean,
    isSessionActive: Boolean,
    isRecordingInSession: Boolean,
    motionStepCount: Int,
    isUserInMotion: Boolean,
    liveSample: LiveTelemetrySample?,
    waveformHistory: List<List<Double>>,
    streamError: String?,
    isSaving: Boolean,
    snackbarHostState: SnackbarHostState,
    onPermissionGranted: () -> Unit,
    onStopSensor: () -> Unit,
    onResumeSensor: () -> Unit,
    onResetSteps: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onSaveSnapshot: (String?) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var wasDenied by remember(sensor.id) { mutableStateOf(false) }
    var noteText by remember(sensor.id) { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = if (sensor.kind == SensorKind.GPS_LOCATION) {
            grants.values.any { it }
        } else {
            grants.values.all { it }
        }
        if (granted) {
            wasDenied = false
            onPermissionGranted()
        } else {
            wasDenied = true
        }
    }

    val accent = sensorCategoryColor(sensor.category)

    Scaffold(
        containerColor = ObsidianCanvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = sensor.category.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent
                        )
                        Text(
                            text = sensor.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("sensor_detail_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to sensors"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianCanvas,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AnimatedVisibility(
                    visible = !isAuthorized,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    PermissionGateCard(
                        sensor = sensor,
                        wasDenied = wasDenied,
                        onRequestPermission = {
                            if (sensor.requiredPermissions.isEmpty()) {
                                onPermissionGranted()
                            } else {
                                permissionLauncher.launch(sensor.requiredPermissions.toTypedArray())
                            }
                        },
                        onOpenSettings = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        }
                    )
                }

                AnimatedVisibility(
                    visible = isAuthorized,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (streamError != null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = streamError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AlertCrimson,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }

                        // Sensor Stream & Session Control Bar
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isStreaming) SignalEmerald.copy(alpha = 0.6f) else TelemetryAmber.copy(alpha = 0.6f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isStreaming) "SENSOR ACTIVE" else "SENSOR STOPPED",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isStreaming) SignalEmerald else TelemetryAmber
                                        )
                                        Text(
                                            text = when {
                                                isSessionActive && isRecordingInSession -> "Recording into active session"
                                                isSessionActive -> "Session active (sensor paused)"
                                                else -> "Tap Stop Sensor anytime to pause sampling"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (isStreaming) {
                                        Button(
                                            onClick = onStopSensor,
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("stop_sensor_button"),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AlertCrimson,
                                                contentColor = MaterialTheme.colorScheme.onError
                                            ),
                                            contentPadding = PaddingValues(horizontal = 14.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Stop,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Stop Sensor", style = MaterialTheme.typography.labelLarge)
                                        }
                                    } else {
                                        Button(
                                            onClick = onResumeSensor,
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("resume_sensor_button"),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = SignalEmerald,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            contentPadding = PaddingValues(horizontal = 14.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Start Sensor", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isSessionActive) {
                                        OutlinedButton(
                                            onClick = onStopSession,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("detail_stop_session_button"),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, AlertCrimson.copy(alpha = 0.7f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Stop,
                                                contentDescription = null,
                                                tint = AlertCrimson,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Stop Recording Session",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = AlertCrimson
                                            )
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = onStartSession,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("detail_start_session_button"),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.FiberManualRecord,
                                                contentDescription = null,
                                                tint = ElectricCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Start Multi-Sensor Session",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = ElectricCyan
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Motion Step Counter Card for all Motion Sensors
                        if (sensor.category == SensorCategory.MOTION) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("motion_step_counter_card"),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isUserInMotion) SignalEmerald.copy(alpha = 0.75f) else HudBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(
                                                    (if (isUserInMotion) SignalEmerald else TelemetryAmber)
                                                        .copy(alpha = 0.16f),
                                                    RoundedCornerShape(12.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                                contentDescription = "Motion step counter",
                                                tint = if (isUserInMotion) SignalEmerald else TelemetryAmber,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = if (isUserInMotion) "USER IN MOTION" else "STATIONARY",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isUserInMotion) SignalEmerald else TelemetryAmber
                                            )
                                            Text(
                                                text = "$motionStepCount Steps",
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = ElectricCyan
                                            )
                                        }
                                    }

                                    TextButton(
                                        onClick = onResetSteps,
                                        modifier = Modifier.testTag("reset_steps_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = "Reset step count",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Reset",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Real-time Waveform Oscilloscope
                        OscilloscopeChart(
                            history = waveformHistory,
                            axisLabels = sensor.axisLabels,
                            unit = sensor.unit
                        )

                        // Live Tabular Channel Values
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, HudBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "CHANNEL TELEMETRY",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ElectricCyan
                                    )
                                    Text(
                                        text = when {
                                            !isStreaming -> "PAUSED"
                                            liveSample != null -> "ACCURACY: ${liveSample.accuracy}/3"
                                            else -> "CONNECTING…"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isStreaming) SignalEmerald else TelemetryAmber
                                    )
                                }

                                if (liveSample?.statusNote != null) {
                                    Text(
                                        text = liveSample.statusNote,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TelemetryAmber
                                    )
                                }

                                val values = liveSample?.values ?: emptyList()
                                if (values.isEmpty()) {
                                    Text(
                                        text = "Waiting for initial sensor frame…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    values.forEachIndexed { idx, v ->
                                        val label = sensor.axisLabels.getOrElse(idx) { "Channel $idx" }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    MaterialTheme.colorScheme.background,
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = formatTelemetryValue(v, sensor.kind),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontFamily = MaterialTheme.typography.labelMedium.fontFamily
                                                ),
                                                color = ElectricCyan
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Hardware Metadata Panel
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, HudBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "HARDWARE SPECIFICATIONS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TelemetryAmber
                                )
                                if (sensor.hardwareName.isNotBlank() && sensor.hardwareName != sensor.name) {
                                    SpecRow("Hardware Driver", sensor.hardwareName)
                                }
                                SpecRow("Vendor", sensor.vendor)
                                SpecRow("Measurement Unit", sensor.unit)
                                if (sensor.maxRange > 0f) {
                                    SpecRow("Max Range", "${sensor.maxRange} ${sensor.unit}")
                                }
                                if (sensor.resolution > 0f) {
                                    SpecRow("Resolution", "${sensor.resolution}")
                                }
                                if (sensor.powerMa > 0f) {
                                    SpecRow("Power Draw", "${sensor.powerMa} mA")
                                }
                            }
                        }

                        // Snapshot Save Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, HudBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = if (isSessionActive) {
                                        "RECORD SNAPSHOT INTO ACTIVE SESSION"
                                    } else {
                                        "SAVE TO LOCAL TELEMETRY DATABASE"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SignalEmerald
                                )
                                OutlinedTextField(
                                    value = noteText,
                                    onValueChange = { if (it.length <= 300) noteText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("snapshot_note_input"),
                                    label = { Text("Optional calibration note") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElectricCyan,
                                        unfocusedBorderColor = HudBorder
                                    )
                                )
                                Button(
                                    onClick = { onSaveSnapshot(noteText) },
                                    enabled = liveSample != null && !isSaving,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("save_snapshot_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricCyan,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    if (isSaving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Saving…")
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.CloudUpload,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.save_snapshot_cta),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatTelemetryValue(value: Double, kind: SensorKind): String {
    return when (kind) {
        SensorKind.GPS_LOCATION -> String.format(Locale.US, "%.6f", value)
        else -> String.format(Locale.US, "%.4f", value)
    }
}
