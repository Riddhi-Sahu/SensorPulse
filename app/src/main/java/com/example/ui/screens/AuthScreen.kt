package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.auth.LocalAuthRepository
import com.example.data.model.LocalUser
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    currentUser: LocalUser?,
    allowDismiss: Boolean,
    isSubmitting: Boolean,
    errorMessage: String?,
    snackbarHostState: SnackbarHostState,
    onClearError: () -> Unit,
    onSignIn: (username: String, password: String) -> Unit,
    onCreateAccount: (username: String, password: String, confirmPassword: String) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = allowDismiss, onBack = onBack)

    var isCreateMode by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = ObsidianCanvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (allowDismiss) "OPERATOR ACCOUNT" else "SENSORPULSE HUD",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                        Text(
                            text = if (currentUser != null) {
                                "Signed In • ${currentUser.username}"
                            } else if (isCreateMode) {
                                "Create Account"
                            } else {
                                "Login"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                navigationIcon = {
                    if (allowDismiss) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("auth_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to SensorPulse"
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
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (currentUser != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, SignalEmerald.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(SignalEmerald.copy(alpha = 0.16f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AccountCircle,
                                        contentDescription = "Active account",
                                        tint = SignalEmerald,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "ACTIVE OPERATOR",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SignalEmerald
                                    )
                                    Text(
                                        text = currentUser.username,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = onSignOut,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("auth_sign_out_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, AlertCrimson.copy(alpha = 0.7f))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    tint = AlertCrimson,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sign Out",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AlertCrimson
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, HudBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Mode Switcher: Login vs Create One
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ModeToggleButton(
                                label = "Login",
                                selected = !isCreateMode,
                                onClick = {
                                    isCreateMode = false
                                    onClearError()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_mode_login")
                            )
                            ModeToggleButton(
                                label = "Create Account",
                                selected = isCreateMode,
                                onClick = {
                                    isCreateMode = true
                                    onClearError()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_mode_create")
                            )
                        }

                        Text(
                            text = if (isCreateMode) {
                                "Create a local username and password to store on this device."
                            } else if (currentUser != null) {
                                "Sign in with a different username and password or create a new account."
                            } else {
                                "Sign in with your username and password, or create a new account."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                onClearError()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_username_input"),
                            label = {
                                Text(if (isCreateMode) "Username (name_xxx)" else "Username")
                            },
                            placeholder = {
                                Text(if (isCreateMode) "e.g. alex_104" else "alex_104")
                            },
                            supportingText = if (isCreateMode) {
                                {
                                    Text(
                                        text = "Format: name_xxx (letters + _ + 3 numbers, e.g. alex_104)",
                                        color = if (LocalAuthRepository.isValidUsernameFormat(username)) {
                                            SignalEmerald
                                        } else {
                                            TelemetryAmber
                                        }
                                    )
                                }
                            } else {
                                null
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = ElectricCyan
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = HudBorder
                            )
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                onClearError()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input"),
                            label = { Text("Password") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = ElectricCyan
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    modifier = Modifier.testTag("auth_password_visibility")
                                ) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            Icons.Filled.VisibilityOff
                                        } else {
                                            Icons.Filled.Visibility
                                        },
                                        contentDescription = if (passwordVisible) {
                                            "Hide password"
                                        } else {
                                            "Show password"
                                        }
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = if (isCreateMode) ImeAction.Next else ImeAction.Done
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = HudBorder
                            )
                        )

                        AnimatedVisibility(visible = isCreateMode) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = {
                                        confirmPassword = it
                                        onClearError()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_confirm_password_input"),
                                    label = { Text("Confirm Password") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = null,
                                            tint = TelemetryAmber
                                        )
                                    },
                                    visualTransformation = if (passwordVisible) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElectricCyan,
                                        unfocusedBorderColor = HudBorder
                                    )
                                )

                                AccountRulesChecklistCard(
                                    username = username,
                                    password = password
                                )
                            }
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_error_banner"),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AlertCrimson,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (isCreateMode) {
                                    onCreateAccount(username, password, confirmPassword)
                                } else {
                                    onSignIn(username, password)
                                }
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_submit_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCreateMode) SignalEmerald else ElectricCyan,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (isCreateMode) "Create Account" else "Login",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                isCreateMode = !isCreateMode
                                onClearError()
                            },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .testTag("auth_switch_mode_link")
                        ) {
                            Text(
                                text = if (isCreateMode) {
                                    "Already have an account? Login"
                                } else {
                                    "New here? Create an account"
                                },
                                color = ElectricCyan
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeToggleButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = if (selected) {
            ButtonDefaults.buttonColors(
                containerColor = ElectricCyan,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun AccountRulesChecklistCard(
    username: String,
    password: String
) {
    val userValid = LocalAuthRepository.isValidUsernameFormat(username)
    val lenValid = LocalAuthRepository.hasValidPasswordLength(password)
    val letterValid = LocalAuthRepository.hasPasswordLetter(password)
    val digitValid = LocalAuthRepository.hasPasswordDigit(password)
    val symbolValid = LocalAuthRepository.hasPasswordSpecialSymbol(password)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("create_account_rules_card"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "ACCOUNT REQUIREMENTS",
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCyan
            )
            RuleCheckRow(
                text = "Username format: name_xxx (e.g. alex_104)",
                met = userValid
            )
            RuleCheckRow(
                text = "Password length: 8–20 characters (${password.length}/20)",
                met = lenValid
            )
            RuleCheckRow(
                text = "Contains a letter (a–z / A–Z)",
                met = letterValid
            )
            RuleCheckRow(
                text = "Contains a number (0–9)",
                met = digitValid
            )
            RuleCheckRow(
                text = "Contains a special symbol (!@#\$%...)",
                met = symbolValid
            )
        }
    }
}

@Composable
private fun RuleCheckRow(
    text: String,
    met: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (met) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (met) "Requirement met" else "Requirement not met",
            tint = if (met) SignalEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (met) SignalEmerald else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
