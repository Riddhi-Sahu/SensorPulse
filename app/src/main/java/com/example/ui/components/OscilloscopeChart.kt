package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudBorder
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryAmber
import kotlin.math.max
import kotlin.math.min

private val TracePalette = listOf(
    ElectricCyan,
    TelemetryAmber,
    SignalEmerald,
    Color(0xFFB388FF),
    Color(0xFFFF80AB)
)

@Composable
fun OscilloscopeChart(
    history: List<List<Double>>,
    axisLabels: List<String>,
    unit: String,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val channelCount = history.lastOrNull()?.size?.coerceIn(1, 5) ?: 1

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(pulseAlpha)
                            .background(SignalEmerald, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE OSCILLOSCOPE",
                        style = MaterialTheme.typography.labelMedium,
                        color = SignalEmerald
                    )
                }
                Text(
                    text = if (unit.isNotBlank()) "UNIT: $unit" else "STREAMING",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                if (history.size < 2) {
                    Text(
                        text = "Acquiring live signal waveform…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        val w = size.width
                        val h = size.height

                        // Horizontal & vertical calibration grid
                        val rows = 4
                        val cols = 6
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
                                color = HudBorder.copy(alpha = 0.35f),
                                start = Offset(x, 0f),
                                end = Offset(x, h),
                                strokeWidth = 1f
                            )
                        }

                        var minVal = Double.MAX_VALUE
                        var maxVal = -Double.MAX_VALUE
                        for (frame in history) {
                            for (ch in 0 until min(channelCount, frame.size)) {
                                val v = frame[ch]
                                if (v.isFinite()) {
                                    minVal = min(minVal, v)
                                    maxVal = max(maxVal, v)
                                }
                            }
                        }
                        if (minVal == Double.MAX_VALUE || maxVal == -Double.MAX_VALUE) {
                            minVal = -1.0
                            maxVal = 1.0
                        }
                        val span = max(maxVal - minVal, 1e-3)
                        val padMin = minVal - span * 0.1
                        val padSpan = span * 1.2

                        val count = history.size
                        for (ch in 0 until channelCount) {
                            val path = Path()
                            var started = false
                            for (i in 0 until count) {
                                val frame = history[i]
                                if (ch >= frame.size) continue
                                val raw = frame[ch]
                                if (!raw.isFinite()) continue
                                val x = (i.toFloat() / (count - 1).coerceAtLeast(1)) * w
                                val normalized = ((raw - padMin) / padSpan).toFloat().coerceIn(0f, 1f)
                                val y = h - (normalized * h)
                                if (!started) {
                                    path.moveTo(x, y)
                                    started = true
                                } else {
                                    path.lineTo(x, y)
                                }
                            }
                            if (started) {
                                drawPath(
                                    path = path,
                                    color = TracePalette[ch % TracePalette.size],
                                    style = Stroke(
                                        width = 4f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (ch in 0 until channelCount) {
                    val label = axisLabels.getOrElse(ch) { "Ch $ch" }
                    val traceColor = TracePalette[ch % TracePalette.size]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(traceColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
