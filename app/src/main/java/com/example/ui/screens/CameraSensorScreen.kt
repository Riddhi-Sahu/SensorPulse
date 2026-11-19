package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.model.SensorDescriptor
import com.example.ui.components.PermissionGateCard
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryAmber
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSensorScreen(
    sensor: SensorDescriptor,
    isAuthorized: Boolean,
    isSaving: Boolean,
    snackbarHostState: SnackbarHostState,
    createOutputFile: () -> File,
    onPermissionGranted: () -> Unit,
    onCaptureStored: (
        savedFile: File,
        lensFacing: String,
        flashUsed: Boolean,
        focalLengthMm: Double?,
        caption: String?
    ) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var wasDenied by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }
    var focalLengthMm by remember { mutableStateOf<Double?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            wasDenied = false
            onPermissionGranted()
        } else {
            wasDenied = true
        }
    }

    Scaffold(
        containerColor = ObsidianCanvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SEPARATE OPTICS PIPELINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryAmber
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
                        modifier = Modifier.testTag("camera_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to sensors"
                        )
                    }
                },
                actions = {
                    if (isAuthorized) {
                        IconButton(
                            onClick = { flashEnabled = !flashEnabled },
                            modifier = Modifier.testTag("camera_flash_toggle")
                        ) {
                            Icon(
                                imageVector = if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                contentDescription = "Toggle flash",
                                tint = if (flashEnabled) TelemetryAmber else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { useFrontCamera = !useFrontCamera },
                            modifier = Modifier.testTag("camera_lens_switch")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cameraswitch,
                                contentDescription = "Switch camera lens",
                                tint = ElectricCyan
                            )
                        }
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isAuthorized) {
                    PermissionGateCard(
                        sensor = sensor,
                        wasDenied = wasDenied,
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onOpenSettings = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        }
                    )
                } else {
                    if (cameraError != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = cameraError ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AlertCrimson,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Live CameraX PreviewView (COMPATIBLE TextureView avoids SurfaceView sync stalls)
                    val previewView = remember {
                        PreviewView(context).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                    }

                    DisposableEffect(lifecycleOwner, useFrontCamera, flashEnabled) {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        val executor = ContextCompat.getMainExecutor(context)
                        var boundProvider: ProcessCameraProvider? = null

                        cameraProviderFuture.addListener({
                            try {
                                val provider = cameraProviderFuture.get()
                                boundProvider = provider
                                val selector = if (useFrontCamera) {
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                } else {
                                    CameraSelector.DEFAULT_BACK_CAMERA
                                }

                                val preview = Preview.Builder().build().apply {
                                    surfaceProvider = previewView.surfaceProvider
                                }
                                imageCapture.flashMode = if (flashEnabled) {
                                    ImageCapture.FLASH_MODE_ON
                                } else {
                                    ImageCapture.FLASH_MODE_OFF
                                }

                                provider.unbindAll()
                                val camera = provider.bindToLifecycle(
                                    lifecycleOwner,
                                    selector,
                                    preview,
                                    imageCapture
                                )
                                focalLengthMm = extractFocalLengthMm(camera.cameraInfo)
                                cameraError = null
                            } catch (e: Exception) {
                                cameraError = e.localizedMessage ?: "Camera preview unavailable on this device"
                            }
                        }, executor)

                        onDispose {
                            boundProvider?.unbindAll()
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, HudBorder),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                            )

                            // Live Optical Telemetry Strip
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianCanvas.copy(alpha = 0.78f),
                                border = BorderStroke(1.dp, HudBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = if (useFrontCamera) "LENS: FRONT" else "LENS: REAR",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan
                                    )
                                    Text(
                                        text = focalLengthMm?.let {
                                            String.format(Locale.US, "%.1f mm", it)
                                        } ?: "OPTICS LIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TelemetryAmber
                                    )
                                    Text(
                                        text = if (flashEnabled) "FLASH: ON" else "FLASH: OFF",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SignalEmerald
                                    )
                                }
                            }
                        }
                    }

                    // Separate Storage Explanation + Capture Action
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, HudBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "LOCAL JPEG FILE + LOCAL CAMERA VAULT DATABASE",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetryAmber
                            )
                            OutlinedTextField(
                                value = caption,
                                onValueChange = { if (it.length <= 240) caption = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("camera_caption_input"),
                                label = { Text("Optional capture caption") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = HudBorder
                                )
                            )
                            Button(
                                onClick = {
                                    if (isCapturing || isSaving) return@Button
                                    isCapturing = true
                                    val destFile = createOutputFile()
                                    val outputOpts = ImageCapture.OutputFileOptions.Builder(destFile).build()
                                    val executor = ContextCompat.getMainExecutor(context)
                                    imageCapture.takePicture(
                                        outputOpts,
                                        executor,
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                isCapturing = false
                                                onCaptureStored(
                                                    destFile,
                                                    if (useFrontCamera) "front" else "back",
                                                    flashEnabled,
                                                    focalLengthMm,
                                                    caption
                                                )
                                                caption = ""
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                isCapturing = false
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        exception.localizedMessage ?: "Camera capture failed"
                                                    )
                                                }
                                            }
                                        }
                                    )
                                },
                                enabled = !isCapturing && !isSaving && cameraError == null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("capture_store_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TelemetryAmber,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            ) {
                                if (isCapturing || isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Saving Frame & Metadata…")
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.CameraAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.capture_store_cta),
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

@OptIn(ExperimentalCamera2Interop::class)
private fun extractFocalLengthMm(cameraInfo: androidx.camera.core.CameraInfo): Double? {
    return runCatching {
        val c2 = Camera2CameraInfo.from(cameraInfo)
        c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull()
            ?.toDouble()
    }.getOrNull()
}
