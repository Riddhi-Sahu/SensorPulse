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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    liveSample: LiveTelemetrySample?,
    waveformHistory: List<List<Double>>,
    streamError: String?,
    isSaving: Boolean,
    snackbarHostState: SnackbarHostState,
    onPermissionGranted: () -> Unit,
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
                                        text = if (liveSample != null) {
                                            "ACCURACY: ${liveSample.accuracy}/3"
                                        } else {
                                            "CONNECTING…"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SignalEmerald
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
                                    text = "SAVE TO LOCAL TELEMETRY DATABASE",
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
