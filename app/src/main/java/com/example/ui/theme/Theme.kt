package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TelemetryHudDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnElectricCyan,
    primaryContainer = CyanMuted,
    onPrimaryContainer = ElectricCyan,
    secondary = TelemetryAmber,
    onSecondary = OnTelemetryAmber,
    secondaryContainer = AmberMuted,
    onSecondaryContainer = TelemetryAmber,
    tertiary = SignalEmerald,
    onTertiary = OnSignalEmerald,
    tertiaryContainer = EmeraldMuted,
    onTertiaryContainer = SignalEmerald,
    background = ObsidianCanvas,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateSurfaceElevated,
    surfaceContainerHigh = SlateSurfaceHighest,
    outline = HudBorder,
    outlineVariant = HudBorder,
    error = AlertCrimson,
    onError = ObsidianCanvas,
    errorContainer = CrimsonMuted,
    onErrorContainer = AlertCrimson
)

@Composable
fun SensorPulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TelemetryHudDarkColorScheme,
        typography = SensorPulseTypography,
        content = content
    )
}
