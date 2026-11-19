package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthViewModel
import com.example.auth.LocalAuthRepository
import com.example.data.camera.CameraLocalFileStore
import com.example.data.model.SensorKind
import com.example.data.repository.TelemetryRepository
import com.example.data.sensors.DeviceSensorManager
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CameraSensorScreen
import com.example.ui.screens.CameraVaultScreen
import com.example.ui.screens.SensorDashboardScreen
import com.example.ui.screens.SensorDetailScreen
import com.example.ui.screens.TelemetryLogScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SensorPulseTheme
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TelemetryAmber
import com.example.ui.viewmodel.SensorHubViewModel
import com.example.ui.viewmodel.TelemetryVaultViewModel

private enum class HudTab(val labelRes: Int) {
    SENSORS(R.string.tab_sensors),
    LOGS(R.string.tab_logs),
    VAULT(R.string.tab_vault)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SensorPulseTheme {
                SensorPulseHud()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorPulseHud() {
    val snackbarHostState = remember { SnackbarHostState() }

    val hubViewModel: SensorHubViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                SensorHubViewModel(
                    repository = TelemetryRepository(app),
                    sensorManager = DeviceSensorManager(app),
                    cameraFileStore = CameraLocalFileStore(app)
                )
            }
        }
    )

    val vaultViewModel: TelemetryVaultViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                TelemetryVaultViewModel(
                    repository = TelemetryRepository(app)
                )
            }
        }
    )

    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                AuthViewModel(
                    repository = LocalAuthRepository(app)
                )
            }
        }
    )

    val sensors by hubViewModel.allSensors.collectAsStateWithLifecycle()
    val selectedCategory by hubViewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by hubViewModel.searchQuery.collectAsStateWithLifecycle()
    val activeSensor by hubViewModel.activeSensor.collectAsStateWithLifecycle()
    val consentedIds by hubViewModel.consentedSensorIds.collectAsStateWithLifecycle()
    val liveSample by hubViewModel.liveSample.collectAsStateWithLifecycle()
    val waveformHistory by hubViewModel.waveformHistory.collectAsStateWithLifecycle()
    val streamError by hubViewModel.streamError.collectAsStateWithLifecycle()
    val isSensorStreaming by hubViewModel.isSensorStreaming.collectAsStateWithLifecycle()
    val isSaving by hubViewModel.isSaving.collectAsStateWithLifecycle()

    val activeSessionId by hubViewModel.activeSessionId.collectAsStateWithLifecycle()
    val sessionStartedAtMs by hubViewModel.sessionStartedAtMs.collectAsStateWithLifecycle()
    val sessionRecordingSensors by hubViewModel.sessionRecordingSensors.collectAsStateWithLifecycle()
    val sessionRecordedCount by hubViewModel.sessionRecordedCount.collectAsStateWithLifecycle()
    val sessionPhotoCount by hubViewModel.sessionPhotoCount.collectAsStateWithLifecycle()
    val motionStepCount by hubViewModel.motionStepCount.collectAsStateWithLifecycle()
    val isUserInMotion by hubViewModel.isUserInMotion.collectAsStateWithLifecycle()

    val logState by vaultViewModel.logBundle.collectAsStateWithLifecycle()
    val capturesState by vaultViewModel.cameraCaptures.collectAsStateWithLifecycle()
    val logFilterCategory by vaultViewModel.logFilterCategory.collectAsStateWithLifecycle()

    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val isRestoringSession by authViewModel.isRestoringSession.collectAsStateWithLifecycle()
    val isAuthSubmitting by authViewModel.isSubmitting.collectAsStateWithLifecycle()
    val authError by authViewModel.errorMessage.collectAsStateWithLifecycle()

    var showAuthScreen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(hubViewModel) {
        hubViewModel.feedback.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }
    LaunchedEffect(vaultViewModel) {
        vaultViewModel.vaultFeedback.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }
    LaunchedEffect(authViewModel) {
        authViewModel.authEvents.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }

    if (isRestoringSession) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianCanvas),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = ElectricCyan)
                Text(
                    text = "SENSORPULSE HUD",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan
                )
            }
        }
        return
    }

    if (currentUser == null || showAuthScreen) {
        AuthScreen(
            currentUser = currentUser,
            allowDismiss = currentUser != null,
            isSubmitting = isAuthSubmitting,
            errorMessage = authError,
            snackbarHostState = snackbarHostState,
            onClearError = { authViewModel.clearError() },
            onSignIn = { u, p ->
                authViewModel.signIn(u, p) { showAuthScreen = false }
            },
            onCreateAccount = { u, p, c ->
                authViewModel.createAccount(u, p, c) { showAuthScreen = false }
            },
            onSignOut = {
                if (activeSessionId != null) {
                    hubViewModel.stopRecordingSession()
                }
                authViewModel.signOut()
                showAuthScreen = false
            },
            onBack = {
                authViewModel.clearError()
                showAuthScreen = false
            }
        )
        return
    }

    val currentActiveSensor = activeSensor
    if (currentActiveSensor != null) {
        val isAuthorized = remember(currentActiveSensor, consentedIds) {
            hubViewModel.hasRequiredPermissions(currentActiveSensor)
        }
        val isRecordingInSession = remember(currentActiveSensor, sessionRecordingSensors) {
            sessionRecordingSensors.any { it.id == currentActiveSensor.id }
        }
        if (currentActiveSensor.kind == SensorKind.CAMERA_OPTICAL) {
            CameraSensorScreen(
                sensor = currentActiveSensor,
                isAuthorized = isAuthorized,
                isSessionActive = activeSessionId != null,
                isSaving = isSaving,
                snackbarHostState = snackbarHostState,
                createOutputFile = { hubViewModel.createCaptureOutputFile() },
                onPermissionGranted = { hubViewModel.onPermissionGranted(currentActiveSensor) },
                onStartSession = { hubViewModel.startRecordingSession() },
                onStopSession = { hubViewModel.stopRecordingSession() },
                onCaptureStored = { file, facing, flash, focal, caption ->
                    hubViewModel.persistCameraCapture(file, facing, flash, focal, caption)
                },
                onBack = { hubViewModel.closeActiveSensor() }
            )
        } else {
            SensorDetailScreen(
                sensor = currentActiveSensor,
                isAuthorized = isAuthorized,
                isStreaming = isSensorStreaming,
                isSessionActive = activeSessionId != null,
                isRecordingInSession = isRecordingInSession,
                motionStepCount = motionStepCount,
                isUserInMotion = isUserInMotion,
                liveSample = liveSample,
                waveformHistory = waveformHistory,
                streamError = streamError,
                isSaving = isSaving,
                snackbarHostState = snackbarHostState,
                onPermissionGranted = { hubViewModel.onPermissionGranted(currentActiveSensor) },
                onStopSensor = { hubViewModel.stopSensor(currentActiveSensor.id) },
                onResumeSensor = { hubViewModel.resumeActiveSensor() },
                onResetSteps = { hubViewModel.resetMotionSteps() },
                onStartSession = { hubViewModel.startRecordingSession() },
                onStopSession = { hubViewModel.stopRecordingSession() },
                onSaveSnapshot = { notes -> hubViewModel.saveCurrentSnapshot(notes) },
                onBack = { hubViewModel.closeActiveSensor() }
            )
        }
        return
    }

    var currentTab by rememberSaveable { mutableStateOf(HudTab.SENSORS) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianCanvas,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "SENSORPULSE HUD",
                                style = MaterialTheme.typography.titleLarge,
                                color = ElectricCyan
                            )
                            Text(
                                text = if (activeSessionId != null) {
                                    "SESSION ACTIVE • ${sessionRecordingSensors.size} RECORDING"
                                } else {
                                    "LOCAL DEVICE TELEMETRY & CAMERA VAULT"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        val user = currentUser
                        OutlinedButton(
                            onClick = { showAuthScreen = true },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(48.dp)
                                .testTag(if (user == null) "top_login_button" else "top_account_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (user == null) ElectricCyan.copy(alpha = 0.7f) else SignalEmerald.copy(alpha = 0.7f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (user == null) {
                                    Icons.AutoMirrored.Filled.Login
                                } else {
                                    Icons.Filled.AccountCircle
                                },
                                contentDescription = if (user == null) "Login" else "Account ${user.username}",
                                tint = if (user == null) ElectricCyan else SignalEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = user?.username ?: "Login",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (user == null) ElectricCyan else SignalEmerald,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ObsidianCanvas
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = SlateSurface) {
                        HudTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            HudTab.SENSORS -> Icons.Filled.Sensors
                                            HudTab.LOGS -> Icons.Filled.Storage
                                            HudTab.VAULT -> Icons.Filled.CameraAlt
                                        },
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = if (tab == HudTab.VAULT) TelemetryAmber else ElectricCyan,
                                    selectedTextColor = if (tab == HudTab.VAULT) TelemetryAmber else ElectricCyan,
                                    indicatorColor = ElectricCyan.copy(alpha = 0.14f)
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = SlateSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(0.1f))
                        HudTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            HudTab.SENSORS -> Icons.Filled.Sensors
                                            HudTab.LOGS -> Icons.Filled.Storage
                                            HudTab.VAULT -> Icons.Filled.CameraAlt
                                        },
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = if (tab == HudTab.VAULT) TelemetryAmber else ElectricCyan,
                                    selectedTextColor = if (tab == HudTab.VAULT) TelemetryAmber else ElectricCyan,
                                    indicatorColor = ElectricCyan.copy(alpha = 0.14f)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (currentTab) {
                        HudTab.SENSORS -> SensorDashboardScreen(
                            sensors = sensors,
                            selectedCategory = selectedCategory,
                            searchQuery = searchQuery,
                            activeSessionId = activeSessionId,
                            sessionStartedAtMs = sessionStartedAtMs,
                            sessionRecordingSensors = sessionRecordingSensors,
                            sessionRecordedCount = sessionRecordedCount,
                            sessionPhotoCount = sessionPhotoCount,
                            motionStepCount = motionStepCount,
                            isUserInMotion = isUserInMotion,
                            hasPermissionsCheck = { s ->
                                s.id in consentedIds && hubViewModel.hasRequiredPermissions(s)
                            },
                            onCategorySelected = { hubViewModel.selectCategory(it) },
                            onSearchQueryChange = { hubViewModel.updateSearchQuery(it) },
                            onStartSession = { hubViewModel.startRecordingSession() },
                            onStopSession = { hubViewModel.stopRecordingSession() },
                            onStopSensor = { hubViewModel.stopSensor(it) },
                            onSensorClick = { hubViewModel.openSensor(it) }
                        )

                        HudTab.LOGS -> TelemetryLogScreen(
                            logState = logState,
                            filterCategory = logFilterCategory,
                            onFilterCategoryChange = { vaultViewModel.setLogFilterCategory(it) },
                            onDeleteSession = { sid -> vaultViewModel.deleteSession(sid) },
                            onUpdateNote = { id, notes -> vaultViewModel.updateReadingNotes(id, notes) },
                            onDeleteReading = { id -> vaultViewModel.deleteSensorReading(id) }
                        )

                        HudTab.VAULT -> CameraVaultScreen(
                            capturesState = capturesState,
                            onUpdateCaption = { id, cap -> vaultViewModel.updateCaptureCaption(id, cap) },
                            onDeleteCapture = { id, path -> vaultViewModel.deleteCameraCapture(id, path) }
                        )
                    }
                }
            }
        }
    }
}
