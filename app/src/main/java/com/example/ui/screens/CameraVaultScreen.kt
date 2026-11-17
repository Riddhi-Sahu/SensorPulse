package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.CameraCaptureRecord
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudBorder
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryAmber
import com.example.ui.viewmodel.UiState
import com.google.firebase.Timestamp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CameraVaultScreen(
    capturesState: UiState<List<CameraCaptureRecord>>,
    onUpdateCaption: (captureId: String, caption: String) -> Unit,
    onDeleteCapture: (captureId: String, localFilePath: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingCapture by remember { mutableStateOf<CameraCaptureRecord?>(null) }
    var captionText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        when (capturesState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TelemetryAmber)
                }
            }

            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = capturesState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AlertCrimson,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                val sorted = remember(capturesState.data) {
                    capturesState.data.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                if (sorted.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                tint = TelemetryAmber,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = stringResource(R.string.no_captures_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.no_captures_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(sorted, key = { it.id }) { capture ->
                            CameraVaultCard(
                                record = capture,
                                onEditCaption = {
                                    editingCapture = capture
                                    captionText = capture.caption.orEmpty()
                                },
                                onDelete = { onDeleteCapture(capture.id, capture.localFilePath) }
                            )
                        }
                    }
                }
            }
        }
    }

    editingCapture?.let { target ->
        AlertDialog(
            onDismissRequest = { editingCapture = null },
            title = { Text("Update Capture Caption") },
            text = {
                OutlinedTextField(
                    value = captionText,
                    onValueChange = { if (it.length <= 240) captionText = it },
                    label = { Text("Optical capture caption") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateCaption(target.id, captionText)
                        editingCapture = null
                    }
                ) {
                    Text("Save", color = TelemetryAmber)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCapture = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CameraVaultCard(
    record: CameraCaptureRecord,
    onEditCaption: () -> Unit,
    onDelete: () -> Unit
) {
    val localFile = remember(record.localFilePath) { File(record.localFilePath) }
    val fileExists = remember(record.localFilePath) { localFile.exists() }
    val formattedDate = remember(record.createdAt, record.updatedAt) {
        val ts = record.updatedAt ?: record.createdAt ?: Timestamp.now()
        SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(ts.toDate())
    }
    val sizeKb = remember(record.fileSizeBytes) {
        String.format(Locale.US, "%.1f KB", record.fileSizeBytes / 1024.0)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_card_${record.id}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TelemetryAmber.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (fileExists) {
                AsyncImage(
                    model = localFile,
                    contentDescription = record.fileName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.BrokenImage,
                            contentDescription = "Local file stored on capture device",
                            tint = TelemetryAmber,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "JPEG stored locally on capture device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${record.lensFacing.uppercase()} LENS • $formattedDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryAmber
                    )
                    Text(
                        text = record.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row {
                    IconButton(onClick = onEditCaption) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit caption",
                            tint = ElectricCyan
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete capture",
                            tint = AlertCrimson
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VaultSpecPill("${record.widthPx}×${record.heightPx}")
                VaultSpecPill(sizeKb)
                record.focalLengthMm?.let {
                    VaultSpecPill(String.format(Locale.US, "%.1f mm", it))
                }
                if (record.flashUsed) {
                    VaultSpecPill("FLASH")
                }
            }

            if (!record.caption.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = record.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SignalEmerald
                )
            }
        }
    }
}

@Composable
private fun VaultSpecPill(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = ElectricCyan,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
