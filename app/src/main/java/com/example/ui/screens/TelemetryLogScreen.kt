package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.CameraCaptureRecord
import com.example.data.model.SensorCategory
import com.example.data.model.SensorReading
import com.example.ui.components.sensorCategoryColor
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudBorder
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryAmber
import com.example.ui.viewmodel.SessionWithReadings
import com.example.ui.viewmodel.TelemetryLogBundle
import com.example.ui.viewmodel.UiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TelemetryLogScreen(
    logState: UiState<TelemetryLogBundle>,
    filterCategory: String?,
    onFilterCategoryChange: (String?) -> Unit,
    onDeleteSession: (sessionId: Long) -> Unit,
    onUpdateNote: (readingId: Long, notes: String) -> Unit,
    onDeleteReading: (readingId: Long) -> Unit,
    isExporting: Boolean = false,
    onExportCsv: (targetUri: Uri, sessionId: Long?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var editingReading by remember { mutableStateOf<SensorReading?>(null) }
    var editingNoteText by remember { mutableStateOf("") }
    val expandedSessions = remember { mutableStateMapOf<Long, Boolean>() }
    var pendingExportSessionId by rememberSaveable { mutableStateOf<Long?>(null) }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val targetSessionId = pendingExportSessionId
        pendingExportSessionId = null
        if (uri != null) {
            onExportCsv(uri, targetSessionId)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        when (logState) {
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
                            text = logState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AlertCrimson,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                val bundle = logState.data
                if (bundle.sessions.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ROOM TELEMETRY EXPORT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = "${bundle.sessions.size} session(s) ready for analysis",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    pendingExportSessionId = null
                                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                    csvExportLauncher.launch("sensorpulse_sessions_$stamp.csv")
                                },
                                enabled = !isExporting,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("export_all_sessions_csv_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f)),
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = ElectricCyan
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.FileDownload,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isExporting) "Exporting..." else "Export CSV",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ElectricCyan
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                val allReadings = remember(bundle) {
                    bundle.sessions.flatMap { it.readings } + bundle.standaloneReadings
                }
                val totalCaptureCount = remember(bundle) {
                    bundle.sessions.sumOf { it.captures.size }
                }
                val distinctCats = remember(allReadings, totalCaptureCount) {
                    buildList {
                        addAll(allReadings.map { it.sensorCategory }.distinct())
                        if (totalCaptureCount > 0 && SensorCategory.CAMERA.firestoreValue !in this) {
                            add(SensorCategory.CAMERA.firestoreValue)
                        }
                    }
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

                val filteredSessions = remember(bundle.sessions, filterCategory) {
                    if (filterCategory == null) {
                        bundle.sessions
                    } else {
                        bundle.sessions.mapNotNull { swr ->
                            val matching = swr.readings.filter { it.sensorCategory == filterCategory }
                            val matchingCaptures = if (filterCategory == SensorCategory.CAMERA.firestoreValue) {
                                swr.captures
                            } else {
                                emptyList()
                            }
                            if (matching.isEmpty() && matchingCaptures.isEmpty()) {
                                null
                            } else {
                                swr.copy(readings = matching, captures = matchingCaptures)
                            }
                        }
                    }
                }
                val filteredStandalone = remember(bundle.standaloneReadings, filterCategory) {
                    if (filterCategory == null) {
                        bundle.standaloneReadings
                    } else {
                        bundle.standaloneReadings.filter { it.sensorCategory == filterCategory }
                    }
                }

                if (filteredSessions.isEmpty() && filteredStandalone.isEmpty()) {
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
                        items(filteredSessions, key = { "session_${it.session.id}" }) { bundleItem ->
                            val isExpanded = expandedSessions[bundleItem.session.id]
                                ?: (filteredSessions.firstOrNull()?.session?.id == bundleItem.session.id)
                            SessionGroupCard(
                                item = bundleItem,
                                expanded = isExpanded,
                                isExporting = isExporting,
                                onToggleExpand = {
                                    expandedSessions[bundleItem.session.id] = !isExpanded
                                },
                                onExportSession = {
                                    pendingExportSessionId = bundleItem.session.id
                                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                    csvExportLauncher.launch(
                                        "sensorpulse_session_${bundleItem.session.id}_$stamp.csv"
                                    )
                                },
                                onDeleteSession = { onDeleteSession(bundleItem.session.id) },
                                onEditNote = { reading ->
                                    editingReading = reading
                                    editingNoteText = reading.notes.orEmpty()
                                },
                                onDeleteReading = onDeleteReading
                            )
                        }

                        if (filteredStandalone.isNotEmpty()) {
                            item(key = "standalone_header") {
                                Text(
                                    text = "INDIVIDUAL SNAPSHOTS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                            items(filteredStandalone, key = { "reading_${it.id}" }) { item ->
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
private fun SessionGroupCard(
    item: SessionWithReadings,
    expanded: Boolean,
    isExporting: Boolean,
    onToggleExpand: () -> Unit,
    onExportSession: () -> Unit,
    onDeleteSession: () -> Unit,
    onEditNote: (SensorReading) -> Unit,
    onDeleteReading: (Long) -> Unit
) {
    val session = item.session
    val distinctSensors = remember(item.readings) {
        item.readings.map { it.sensorName }.distinct()
    }
    val durationText = remember(session.startedAtMs, session.endedAtMs, session.isActive) {
        if (session.isActive) {
            "LIVE"
        } else {
            val end = session.endedAtMs ?: session.startedAtMs
            val totalSec = ((end - session.startedAtMs) / 1000L).coerceAtLeast(0L)
            String.format(Locale.US, "%02d:%02d", totalSec / 60L, totalSec % 60L)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_card_${session.id}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (session.isActive) SignalEmerald.copy(alpha = 0.7f) else ElectricCyan.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleExpand),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (session.isActive) "RECORDING" else "SESSION • $durationText",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (session.isActive) AlertCrimson else SignalEmerald
                        )
                        if (session.totalSteps > 0) {
                            Text(
                                text = "• ${session.totalSteps} STEPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetryAmber
                            )
                        }
                    }
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val photoLabel = if (item.captures.isNotEmpty()) {
                        " • ${item.captures.size} photo(s)"
                    } else {
                        ""
                    }
                    val ownerLabel = session.ownerUsername?.let { " • @$it" }.orEmpty()
                    Text(
                        text = "${distinctSensors.size} sensor(s) • ${item.readings.size} reading(s)$photoLabel$ownerLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onExportSession,
                        enabled = !isExporting,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("export_session_csv_${session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileDownload,
                            contentDescription = "Export session as CSV",
                            tint = ElectricCyan
                        )
                    }
                    if (!session.isActive) {
                        IconButton(
                            onClick = onDeleteSession,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete session",
                                tint = AlertCrimson
                            )
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse session" else "Expand session",
                        tint = ElectricCyan
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (item.captures.isEmpty() && item.readings.isEmpty()) {
                        Text(
                            text = "No readings or photos captured in this session yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        item.captures.forEach { capture ->
                            SessionPhotoCard(capture = capture)
                        }
                        item.readings.forEach { reading ->
                            SensorReadingCard(
                                item = reading,
                                onEditNote = { onEditNote(reading) },
                                onDelete = { onDeleteReading(reading.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionPhotoCard(capture: CameraCaptureRecord) {
    val file = remember(capture.localFilePath) { File(capture.localFilePath) }
    val exists = remember(capture.localFilePath) { file.exists() }
    val formattedDate = remember(capture.createdAtMs) {
        SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(capture.createdAtMs))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_photo_card_${capture.id}"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TelemetryAmber.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = TelemetryAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "SESSION PHOTO • ${capture.lensFacing.uppercase()} • $formattedDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryAmber
                    )
                }
                Text(
                    text = "${capture.widthPx}×${capture.heightPx}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }

            if (exists) {
                AsyncImage(
                    model = file,
                    contentDescription = capture.fileName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BrokenImage,
                            contentDescription = null,
                            tint = TelemetryAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = capture.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!capture.caption.isNullOrBlank()) {
                Text(
                    text = capture.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SignalEmerald
                )
            }
        }
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildString {
                            append("${category.label.uppercase()} • $formattedDate")
                            if (item.stepCount > 0) append(" • ${item.stepCount} STEPS")
                        },
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
