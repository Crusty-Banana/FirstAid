package io.livekit.android.example.voiceassistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.livekit.android.example.voiceassistant.viewmodels.ChatViewModel
import io.livekit.android.example.voiceassistant.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val isLoading by settingsViewModel.isLoading.collectAsState()
    val operationError by settingsViewModel.operationError.collectAsState()
    val loggedInUserEmail by settingsViewModel.loggedInUserEmail.collectAsState()
    val accessToken by settingsViewModel.accessToken.collectAsState()
    val registrationStatus by settingsViewModel.registrationStatus.collectAsState()

    var uiMode by rememberSaveable { mutableStateOf("Login") } // "Login" or "Register"

    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var firstNameInput by rememberSaveable { mutableStateOf("") }
    var lastNameInput by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // Clear errors/statuses when switching mode
    LaunchedEffect(uiMode) {
        settingsViewModel.clearErrorAndStatus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .verticalScroll(rememberScrollState()), // For smaller screens
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (accessToken == null) { // Not logged in
                if (uiMode == "Login") {
                    Text("Login", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if(emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                                settingsViewModel.login(emailInput, passwordInput)
                            }
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                settingsViewModel.login(emailInput, passwordInput)
                                chatViewModel.setIsAuthExpired(false)
                            },
                            enabled = emailInput.isNotBlank() && passwordInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Login") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { uiMode = "Register" }) {
                        Text("Don't have an account? Register")
                    }
                } else { // Register mode
                    Text("Register", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = firstNameInput,
                        onValueChange = { firstNameInput = it },
                        label = { Text("First Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = lastNameInput,
                        onValueChange = { lastNameInput = it },
                        label = { Text("Last Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if (emailInput.isNotBlank() && passwordInput.isNotBlank() && firstNameInput.isNotBlank() && lastNameInput.isNotBlank()) {
                                settingsViewModel.registerUser(emailInput, passwordInput, firstNameInput, lastNameInput)
                        }
                    }), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(24.dp))

                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                settingsViewModel.registerUser(emailInput, passwordInput, firstNameInput, lastNameInput)
                            },
                            enabled = emailInput.isNotBlank() && passwordInput.isNotBlank() && firstNameInput.isNotBlank() && lastNameInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Register") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { uiMode = "Login" }) {
                        Text("Already have an account? Login")
                    }
                }

                operationError?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                registrationStatus?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }

            } else { // User is logged in
                Text("User Profile", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Email: ${loggedInUserEmail ?: "N/A"}", style = MaterialTheme.typography.bodyLarge)
                // You could add First Name / Last Name here if AuthManager stored them
                // after a successful /me call post-login, or from JWT.
                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = { settingsViewModel.logout() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Logout") }

                    Button(
                        onClick = {
                            settingsViewModel.refreshAccessToken()
                            chatViewModel.setIsAuthExpired(false)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Refresh Token") }
                }
                operationError?.let { // Display logout errors too
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}