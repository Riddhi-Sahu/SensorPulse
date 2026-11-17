package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SensorCategory
import com.example.data.model.SensorDescriptor
import com.example.data.model.SensorKind
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudBorder
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryAmber

fun sensorCategoryIcon(category: SensorCategory, kind: SensorKind): ImageVector {
    return when (kind) {
        SensorKind.CAMERA_OPTICAL -> Icons.Filled.CameraAlt
        SensorKind.GPS_LOCATION -> Icons.Filled.GpsFixed
        SensorKind.MICROPHONE_AUDIO -> Icons.Filled.Mic
        SensorKind.HARDWARE_SENSOR -> when (category) {
            SensorCategory.MOTION -> Icons.Filled.Speed
            SensorCategory.ENVIRONMENT -> Icons.Filled.LightMode
            SensorCategory.POSITION -> Icons.Filled.Explore
            SensorCategory.BIOMETRICS -> Icons.Filled.Favorite
            SensorCategory.LOCATION -> Icons.Filled.GpsFixed
            SensorCategory.AUDIO -> Icons.Filled.Mic
            SensorCategory.CAMERA -> Icons.Filled.CameraAlt
            SensorCategory.OTHER -> Icons.Filled.CompassCalibration
        }
    }
}

fun sensorCategoryColor(category: SensorCategory): Color {
    return when (category) {
        SensorCategory.MOTION -> ElectricCyan
        SensorCategory.ENVIRONMENT -> TelemetryAmber
        SensorCategory.POSITION -> Color(0xFF7C4DFF)
        SensorCategory.BIOMETRICS -> AlertCrimson
        SensorCategory.LOCATION -> SignalEmerald
        SensorCategory.AUDIO -> Color(0xFFFF6D00)
        SensorCategory.CAMERA -> ElectricCyan
        SensorCategory.OTHER -> Color(0xFF90CAF9)
    }
}

@Composable
fun PermissionGateCard(
    sensor: SensorDescriptor,
    wasDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = sensorCategoryColor(sensor.category)
    val usesOsRuntime = sensor.requiredPermissions.isNotEmpty()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(accent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (wasDenied) Icons.Filled.Lock else Icons.Filled.VerifiedUser,
                    contentDescription = "Sensor permission required",
                    tint = if (wasDenied) AlertCrimson else accent,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = stringResource(R.string.permission_required_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, HudBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Sensors,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (usesOsRuntime) {
                            "OS RUNTIME PERMISSION"
                        } else {
                            "HARDWARE STREAM CONSENT"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }
            }

            Text(
                text = sensor.permissionRationale,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (sensor.requiredPermissions.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, HudBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "REQUIRED ANDROID PERMISSIONS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        sensor.requiredPermissions.forEach { perm ->
                            Text(
                                text = "• ${perm.substringAfterLast('.')}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TelemetryAmber
                            )
                        }
                    }
                }
            }

            if (wasDenied) {
                Text(
                    text = "Permission was denied. Grant access to unlock live telemetry readings, or open system settings if permanently denied.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlertCrimson
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("grant_permission_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = stringResource(R.string.permission_grant_cta),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (wasDenied && usesOsRuntime) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_settings_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HudBorder)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.permission_settings_cta),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
