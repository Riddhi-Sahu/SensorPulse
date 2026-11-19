package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.LiveTelemetrySample
import com.example.data.model.SensorDescriptor
import com.example.data.model.SensorKind
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudBorder
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val TrendSeriesPalette = listOf(
    ElectricCyan,
    TelemetryAmber,
    SignalEmerald,
    Color(0xFFB388FF),
    Color(0xFFFF80AB)
)

@Composable
fun LiveTelemetryTrendCard(
    allSensors: List<SensorDescriptor>,
    activeTrendSensor: SensorDescriptor?,
    samples: List<LiveTelemetrySample>,
    hasPermissionsCheck: (SensorDescriptor) -> Boolean,
    onSelectSensor: (SensorDescriptor) -> Unit,
    onStopTrend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "dashboard_trend_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dashboard_trend_alpha"
    )

    val quickSensors = remember(allSensors, activeTrendSensor) {
        val streamable = allSensors.filter { it.kind != SensorKind.CAMERA_OPTICAL }
        val activeOrConsented = streamable.filter {
            it.id == activeTrendSensor?.id || hasPermissionsCheck(it)
        }
        val zeroPermSensors = streamable.filter {
            it.requiredPermissions.isEmpty() && activeOrConsented.none { s -> s.id == it.id }
        }
        (activeOrConsented + zeroPermSensors).take(6)
    }

    var inspectedIndex by remember(activeTrendSensor?.id) { mutableStateOf<Int?>(null) }

    val latestSample = samples.lastOrNull()
    val axisLabels = activeTrendSensor?.axisLabels ?: latestSample?.axisLabels.orEmpty()
    val channelCount = (latestSample?.values?.size ?: axisLabels.size).coerceIn(1, 4)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_live_trend_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (activeTrendSensor != null) ElectricCyan.copy(alpha = 0.55f) else HudBorder
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(if (activeTrendSensor != null) pulseAlpha else 1f)
                            .background(
                                color = if (activeTrendSensor != null) SignalEmerald else ElectricCyan.copy(alpha = 0.6f),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "LIVE TELEMETRY TRENDS",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (activeTrendSensor != null) SignalEmerald else ElectricCyan
                        )
                        Text(
                            text = if (activeTrendSensor != null) {
                                "${activeTrendSensor.name} • ${activeTrendSensor.unit} • ${samples.size} pts"
                            } else {
                                "Real-time multi-axis area & line telemetry"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (activeTrendSensor != null) {
                    IconButton(
                        onClick = {
                            inspectedIndex = null
                            onStopTrend()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("stop_dashboard_trend_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Stop live trend stream",
                            tint = AlertCrimson
                        )
                    }
                }
            }

            if (quickSensors.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickSensors.forEach { sensor ->
                        val isSelected = activeTrendSensor?.id == sensor.id
                        val accent = sensorCategoryColor(sensor.category)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                inspectedIndex = null
                                onSelectSensor(sensor)
                            },
                            label = {
                                Text(
                                    text = sensor.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.22f),
                                selectedLabelColor = accent
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = HudBorder,
                                selectedBorderColor = accent
                            ),
                            modifier = Modifier.testTag("trend_chip_${sensor.id}")
                        )
                    }
                }
            }

            val stats = remember(samples, channelCount) {
                computeRangeStats(samples, channelCount)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .background(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                when {
                    activeTrendSensor == null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ShowChart,
                                contentDescription = null,
                                tint = ElectricCyan.copy(alpha = 0.7f),
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "Select a sensor chip above to stream live trends",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    samples.size < 2 -> {
                        Text(
                            text = "Acquiring live ${activeTrendSensor.name} samples…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    else -> {
                        val clampedInspectIdx = inspectedIndex?.coerceIn(0, samples.lastIndex)
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Y-Axis ticks column
                            Column(
                                modifier = Modifier
                                    .width(46.dp)
                                    .fillMaxHeight()
                                    .padding(end = 6.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = formatScaleTick(stats.paddedMax),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Text(
                                    text = formatScaleTick((stats.paddedMax + stats.paddedMin) * 0.5),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Text(
                                    text = formatScaleTick(stats.paddedMin),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }

                            // Interactive multi-series trend canvas
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .pointerInput(samples.size) {
                                        detectTapGestures(
                                            onTap = { offset ->
                                                val w = size.width.toFloat().coerceAtLeast(1f)
                                                val idx = ((offset.x / w) * (samples.size - 1))
                                                    .roundToInt()
                                                    .coerceIn(0, samples.lastIndex)
                                                inspectedIndex = if (inspectedIndex == idx) null else idx
                                            }
                                        )
                                    }
                                    .pointerInput(samples.size) {
                                        detectHorizontalDragGestures(
                                            onDragStart = { offset ->
                                                val w = size.width.toFloat().coerceAtLeast(1f)
                                                inspectedIndex = ((offset.x / w) * (samples.size - 1))
                                                    .roundToInt()
                                                    .coerceIn(0, samples.lastIndex)
                                            },
                                            onHorizontalDrag = { change, _ ->
                                                change.consume()
                                                val w = size.width.toFloat().coerceAtLeast(1f)
                                                inspectedIndex = ((change.position.x / w) * (samples.size - 1))
                                                    .roundToInt()
                                                    .coerceIn(0, samples.lastIndex)
                                            },
                                            onDragEnd = { inspectedIndex = null },
                                            onDragCancel = { inspectedIndex = null }
                                        )
                                    }
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 6.dp)
                                ) {
                                    val w = size.width
                                    val h = size.height
                                    val rows = 4
                                    val cols = 5
                                    for (r in 0..rows) {
                                        val y = h * (r.toFloat() / rows)
                                        drawLine(
                                            color = HudBorder.copy(alpha = 0.45f),
                                            start = Offset(0f, y),
                                            end = Offset(w, y),
                                            strokeWidth = 1f
                                        )
                                    }
                                    for (c in 0..cols) {
                                        val x = w * (c.toFloat() / cols)
                                        drawLine(
                                            color = HudBorder.copy(alpha = 0.3f),
                                            start = Offset(x, 0f),
                                            end = Offset(x, h),
                                            strokeWidth = 1f
                                        )
                                    }

                                    val count = samples.size
                                    val span = max(stats.paddedMax - stats.paddedMin, 1e-4)

                                    for (ch in 0 until channelCount) {
                                        val traceColor = TrendSeriesPalette[ch % TrendSeriesPalette.size]
                                        val strokePath = Path()
                                        val areaPath = Path()
                                        var started = false
                                        var firstX = 0f
                                        var lastX = 0f
                                        var lastY = h

                                        for (i in 0 until count) {
                                            val values = samples[i].values
                                            if (ch >= values.size) continue
                                            val raw = values[ch]
                                            if (!raw.isFinite()) continue
                                            val x = (i.toFloat() / (count - 1).coerceAtLeast(1)) * w
                                            val norm = ((raw - stats.paddedMin) / span).toFloat().coerceIn(0f, 1f)
                                            val y = h - (norm * h)

                                            if (!started) {
                                                strokePath.moveTo(x, y)
                                                areaPath.moveTo(x, h)
                                                areaPath.lineTo(x, y)
                                                firstX = x
                                                started = true
                                            } else {
                                                strokePath.lineTo(x, y)
                                                areaPath.lineTo(x, y)
                                            }
                                            lastX = x
                                            lastY = y
                                        }

                                        if (started) {
                                            areaPath.lineTo(lastX, h)
                                            areaPath.lineTo(firstX, h)
                                            areaPath.close()

                                            if (ch == 0 || channelCount <= 2) {
                                                drawPath(
                                                    path = areaPath,
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            traceColor.copy(alpha = if (ch == 0) 0.26f else 0.14f),
                                                            Color.Transparent
                                                        ),
                                                        startY = 0f,
                                                        endY = h
                                                    )
                                                )
                                            }

                                            drawPath(
                                                path = strokePath,
                                                color = traceColor,
                                                style = Stroke(
                                                    width = 3.5f,
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round
                                                )
                                            )

                                            // Live tail endpoint marker
                                            drawCircle(
                                                color = traceColor,
                                                radius = 5f,
                                                center = Offset(lastX, lastY)
                                            )
                                        }
                                    }

                                    // Interactive scrub crosshair
                                    if (clampedInspectIdx != null) {
                                        val crossX = (clampedInspectIdx.toFloat() / (count - 1).coerceAtLeast(1)) * w
                                        drawLine(
                                            color = ElectricCyan.copy(alpha = 0.85f),
                                            start = Offset(crossX, 0f),
                                            end = Offset(crossX, h),
                                            strokeWidth = 2f
                                        )
                                        val inspectedValues = samples[clampedInspectIdx].values
                                        for (ch in 0 until min(channelCount, inspectedValues.size)) {
                                            val raw = inspectedValues[ch]
                                            if (!raw.isFinite()) continue
                                            val norm = ((raw - stats.paddedMin) / span).toFloat().coerceIn(0f, 1f)
                                            val y = h - (norm * h)
                                            val traceColor = TrendSeriesPalette[ch % TrendSeriesPalette.size]
                                            drawCircle(
                                                color = traceColor,
                                                radius = 7f,
                                                center = Offset(crossX, y)
                                            )
                                        }
                                    }
                                }

                                // Tooltip pill when user taps/drags across the chart
                                if (clampedInspectIdx != null) {
                                    val inspected = samples[clampedInspectIdx]
                                    val timeFmt = remember(inspected.timestampMs) {
                                        SimpleDateFormat("HH:mm:ss", Locale.US)
                                            .format(Date(inspected.timestampMs))
                                    }
                                    val readingSummary = (0 until min(channelCount, inspected.values.size))
                                        .joinToString("  ") { ch ->
                                            val label = axisLabels.getOrElse(ch) { "C$ch" }
                                            "$label:${formatValueShort(inspected.values[ch])}"
                                        }
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = "$timeFmt • $readingSummary",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricCyan,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (activeTrendSensor != null && latestSample != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (ch in 0 until min(channelCount, latestSample.values.size)) {
                        val label = axisLabels.getOrElse(ch) { "Ch $ch" }
                        val traceColor = TrendSeriesPalette[ch % TrendSeriesPalette.size]
                        val latestVal = latestSample.values[ch]
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, traceColor.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(traceColor, CircleShape)
                                )
                                Text(
                                    text = "$label: ${formatValueShort(latestVal)} ${activeTrendSensor.unit}".trim(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class TrendRangeStats(
    val paddedMin: Double,
    val paddedMax: Double
)

private fun computeRangeStats(
    samples: List<LiveTelemetrySample>,
    channelCount: Int
): TrendRangeStats {
    var minVal = Double.MAX_VALUE
    var maxVal = -Double.MAX_VALUE
    for (sample in samples) {
        val count = min(channelCount, sample.values.size)
        for (ch in 0 until count) {
            val v = sample.values[ch]
            if (v.isFinite()) {
                minVal = min(minVal, v)
                maxVal = max(maxVal, v)
            }
        }
    }
    if (minVal == Double.MAX_VALUE || maxVal == -Double.MAX_VALUE) {
        return TrendRangeStats(-1.0, 1.0)
    }
    val span = max(maxVal - minVal, 1e-2)
    return TrendRangeStats(
        paddedMin = minVal - span * 0.12,
        paddedMax = maxVal + span * 0.12
    )
}

private fun formatScaleTick(value: Double): String {
    val magnitude = abs(value)
    return when {
        magnitude >= 100.0 -> String.format(Locale.US, "%.0f", value)
        magnitude >= 10.0 -> String.format(Locale.US, "%.1f", value)
        else -> String.format(Locale.US, "%.2f", value)
    }
}

private fun formatValueShort(value: Double): String {
    return if (!value.isFinite()) "--" else String.format(Locale.US, "%.2f", value)
}
