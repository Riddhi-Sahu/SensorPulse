package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.example.auth.authStateFlow
import com.example.auth.signOut
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
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TelemetryAmber
import com.example.ui.viewmodel.SensorHubViewModel
import com.example.ui.viewmodel.TelemetryVaultViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

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
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener updates currentUser reactively */ })
    } else {
        key(user.uid) {
            AuthenticatedTelemetryHost(currentUser = user)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedTelemetryHost(currentUser: FirebaseUser) {
    val uid = currentUser.uid
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val hubViewModel: SensorHubViewModel = viewModel(
        key = "hub_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                SensorHubViewModel(
                    repository = TelemetryRepository(db),
                    sensorManager = DeviceSensorManager(app),
                    cameraFileStore = CameraLocalFileStore(app),
                    currentUserId = uid
                )
            }
        }
    )

    val vaultViewModel: TelemetryVaultViewModel = viewModel(
        key = "vault_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                TelemetryVaultViewModel(
                    repository = TelemetryRepository(db),
                    currentUserId = uid
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
    val isSaving by hubViewModel.isSaving.collectAsStateWithLifecycle()

    val readingsState by vaultViewModel.sensorReadings.collectAsStateWithLifecycle()
    val capturesState by vaultViewModel.cameraCaptures.collectAsStateWithLifecycle()
    val logFilterCategory by vaultViewModel.logFilterCategory.collectAsStateWithLifecycle()

    LaunchedEffect(hubViewModel) {
        hubViewModel.feedback.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }
    LaunchedEffect(vaultViewModel) {
        vaultViewModel.vaultFeedback.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }

    val currentActiveSensor = activeSensor
    if (currentActiveSensor != null) {
        val isAuthorized = remember(currentActiveSensor, consentedIds) {
            hubViewModel.hasRequiredPermissions(currentActiveSensor)
        }
        if (currentActiveSensor.kind == SensorKind.CAMERA_OPTICAL) {
            CameraSensorScreen(
                sensor = currentActiveSensor,
                isAuthorized = isAuthorized,
                isSaving = isSaving,
                snackbarHostState = snackbarHostState,
                createOutputFile = { hubViewModel.createCaptureOutputFile() },
                onPermissionGranted = { hubViewModel.onPermissionGranted(currentActiveSensor) },
                onCaptureStored = { file, facing, flash, focal, caption ->
                    hubViewModel.persistCameraCapture(file, facing, flash, focal, caption)
                },
                onBack = { hubViewModel.closeActiveSensor() }
            )
        } else {
            SensorDetailScreen(
                sensor = currentActiveSensor,
                isAuthorized = isAuthorized,
                liveSample = liveSample,
                waveformHistory = waveformHistory,
                streamError = streamError,
                isSaving = isSaving,
                snackbarHostState = snackbarHostState,
                onPermissionGranted = { hubViewModel.onPermissionGranted(currentActiveSensor) },
                onSaveSnapshot = { notes -> hubViewModel.saveCurrentSnapshot(notes) },
                onBack = { hubViewModel.closeActiveSensor() }
            )
        }
        return
    }

    var currentTab by rememberSaveable { mutableStateOf(HudTab.SENSORS) }
    var showAccountMenu by remember { mutableStateOf(false) }

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
                                text = currentUser.email ?: currentUser.displayName ?: "Authenticated Operator",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(
                                onClick = { showAccountMenu = true },
                                modifier = Modifier.testTag("account_menu_button")
                            ) {
                                val photoUrl = currentUser.photoUrl
                                if (photoUrl != null) {
                                    AsyncImage(
                                        model = photoUrl,
                                        contentDescription = "Account menu",
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(ElectricCyan.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = (currentUser.displayName ?: currentUser.email ?: "U")
                                                .take(1)
                                                .uppercase(),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = ElectricCyan
                                        )
                                    }
                                }
                            }
                            DropdownMenu(
                                expanded = showAccountMenu,
                                onDismissRequest = { showAccountMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sign_out)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Logout,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showAccountMenu = false
                                        signOut(credentialManager, scope)
                                    },
                                    modifier = Modifier.testTag("sign_out_menu_item")
                                )
                            }
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
                            hasPermissionsCheck = { s ->
                                s.id in consentedIds && hubViewModel.hasRequiredPermissions(s)
                            },
                            onCategorySelected = { hubViewModel.selectCategory(it) },
                            onSearchQueryChange = { hubViewModel.updateSearchQuery(it) },
                            onSensorClick = { hubViewModel.openSensor(it) }
                        )

                        HudTab.LOGS -> TelemetryLogScreen(
                            readingsState = readingsState,
                            filterCategory = logFilterCategory,
                            onFilterCategoryChange = { vaultViewModel.setLogFilterCategory(it) },
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
