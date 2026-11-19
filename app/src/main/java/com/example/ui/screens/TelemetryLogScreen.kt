package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SensorCategory
import com.example.data.model.SensorReading
import com.example.ui.components.sensorCategoryColor
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudBorder
import com.example.ui.theme.SignalEmerald
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TelemetryLogScreen(
    readingsState: UiState<List<SensorReading>>,
    filterCategory: String?,
    onFilterCategoryChange: (String?) -> Unit,
    onUpdateNote: (readingId: Long, notes: String) -> Unit,
    onDeleteReading: (readingId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingReading by remember { mutableStateOf<SensorReading?>(null) }
    var editingNoteText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        when (readingsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricCyan)
                }
            }

            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = readingsState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AlertCrimson,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                val allReadings = readingsState.data
                val distinctCats = remember(allReadings) {
                    allReadings.map { it.sensorCategory }.distinct()
                }
                val visibleReadings = remember(allReadings, filterCategory) {
                    val sorted = allReadings.sortedByDescending { it.createdAtMs }
                    if (filterCategory == null) sorted else sorted.filter { it.sensorCategory == filterCategory }
                }

                if (distinctCats.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filterCategory == null,
                            onClick = { onFilterCategoryChange(null) },
                            label = { Text("All (${allReadings.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                selectedLabelColor = ElectricCyan
                            )
                        )
                        distinctCats.forEach { catCode ->
                            val cat = SensorCategory.fromCode(catCode)
                            val accent = sensorCategoryColor(cat)
                            FilterChip(
                                selected = filterCategory == catCode,
                                onClick = {
                                    onFilterCategoryChange(if (filterCategory == catCode) null else catCode)
                                },
                                label = { Text(cat.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.2f),
                                    selectedLabelColor = accent
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (visibleReadings.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storage,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = stringResource(R.string.no_logs_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.no_logs_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visibleReadings, key = { it.id }) { item ->
                            SensorReadingCard(
                                item = item,
                                onEditNote = {
                                    editingReading = item
                                    editingNoteText = item.notes.orEmpty()
                                },
                                onDelete = { onDeleteReading(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    editingReading?.let { target ->
        AlertDialog(
            onDismissRequest = { editingReading = null },
            title = { Text("Update Snapshot Note") },
            text = {
                OutlinedTextField(
                    value = editingNoteText,
                    onValueChange = { if (it.length <= 300) editingNoteText = it },
                    label = { Text("Calibration / observation note") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateNote(target.id, editingNoteText)
                        editingReading = null
                    }
                ) {
                    Text("Save", color = ElectricCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingReading = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SensorReadingCard(
    item: SensorReading,
    onEditNote: () -> Unit,
    onDelete: () -> Unit
) {
    val category = SensorCategory.fromCode(item.sensorCategory)
    val accent = sensorCategoryColor(category)
    val formattedDate = remember(item.createdAtMs, item.updatedAtMs) {
        val ts = if (item.updatedAtMs > 0L) item.updatedAtMs else item.createdAtMs
        SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(ts))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("log_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${category.label.uppercase()} • $formattedDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                    Text(
                        text = item.sensorName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row {
                    IconButton(onClick = onEditNote) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit note",
                            tint = ElectricCyan
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete reading",
                            tint = AlertCrimson
                        )
                    }
                }
            }

            // Tabular readings strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.values.joinToString("  |  ") {
                        String.format(Locale.US, "%.3f", it)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalEmerald
                )
            }

            if (!item.notes.isNullOrBlank()) {
                Text(
                    text = item.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
