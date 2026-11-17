package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.SensorCategory
import com.example.data.model.SensorDescriptor
import com.example.data.model.SensorKind
import com.example.ui.components.sensorCategoryColor
import com.example.ui.components.sensorCategoryIcon
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudBorder
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryAmber

@Composable
fun SensorDashboardScreen(
    sensors: List<SensorDescriptor>,
    selectedCategory: SensorCategory?,
    searchQuery: String,
    hasPermissionsCheck: (SensorDescriptor) -> Boolean,
    onCategorySelected: (SensorCategory?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSensorClick: (SensorDescriptor) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredSensors by remember(sensors, selectedCategory, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            sensors.filter { s ->
                val catMatch = selectedCategory == null || s.category == selectedCategory
                val queryMatch = q.isEmpty() ||
                    s.name.lowercase().contains(q) ||
                    s.vendor.lowercase().contains(q) ||
                    s.category.label.lowercase().contains(q)
                catMatch && queryMatch
            }
        }
    }

    val categoriesPresent = remember(sensors) {
        sensors.map { it.category }.distinct()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Summary Telemetry Banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, HudBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(ElectricCyan.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sensors,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${sensors.size} SENSORS DETECTED",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                        Text(
                            text = "Tap any module to authorize & stream",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sensor_search_input"),
            placeholder = {
                Text(
                    text = "Filter sensors by name, vendor, or type…",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search sensors",
                    tint = ElectricCyan
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = HudBorder,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelected(null) },
                label = { Text("All (${sensors.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                    selectedLabelColor = ElectricCyan
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedCategory == null,
                    borderColor = HudBorder,
                    selectedBorderColor = ElectricCyan
                )
            )
            categoriesPresent.forEach { cat ->
                val count = sensors.count { it.category == cat }
                val accent = sensorCategoryColor(cat)
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onCategorySelected(if (selectedCategory == cat) null else cat) },
                    label = { Text("${cat.label} ($count)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.2f),
                        selectedLabelColor = accent
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedCategory == cat,
                        borderColor = HudBorder,
                        selectedBorderColor = accent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredSensors.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Sensors,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No matching sensors found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 166.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredSensors, key = { it.id }) { sensor ->
                    SensorTileCard(
                        sensor = sensor,
                        isAuthorized = hasPermissionsCheck(sensor),
                        onClick = { onSensorClick(sensor) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorTileCard(
    sensor: SensorDescriptor,
    isAuthorized: Boolean,
    onClick: () -> Unit
) {
    val accent = sensorCategoryColor(sensor.category)
    val icon = sensorCategoryIcon(sensor.category, sensor.kind)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("sensor_card_${sensor.id}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (sensor.kind == SensorKind.CAMERA_OPTICAL) TelemetryAmber.copy(alpha = 0.65f) else HudBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = sensor.name,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Icon(
                    imageVector = if (isAuthorized) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                    contentDescription = if (isAuthorized) "Authorized" else "Permission required",
                    tint = if (isAuthorized) SignalEmerald else TelemetryAmber,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = sensor.category.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = sensor.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sensor.vendor,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (sensor.kind == SensorKind.CAMERA_OPTICAL) "VAULT" else sensor.unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sensor.kind == SensorKind.CAMERA_OPTICAL) TelemetryAmber else ElectricCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
