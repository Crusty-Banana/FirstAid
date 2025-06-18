package io.livekit.android.example.voiceassistant.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText // Import ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext // Import LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.util.PatternsCompat
import io.livekit.android.example.voiceassistant.BuildConfig
import io.livekit.android.example.voiceassistant.R
import io.livekit.android.example.voiceassistant.viewmodels.ChatViewModel
import io.livekit.android.example.voiceassistant.viewmodels.SettingsViewModel

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel
) {
    val isLoading by settingsViewModel.isLoading.collectAsState()
    val operationError by settingsViewModel.operationError.collectAsState()
    val loggedInUserEmail by settingsViewModel.loggedInUserEmail.collectAsState()
    val accessToken by settingsViewModel.accessToken.collectAsState()
    val registrationStatus by settingsViewModel.registrationStatus.collectAsState()
    val userProfile by settingsViewModel.userProfile.collectAsState()

    var uiMode by rememberSaveable { mutableStateOf("Login") } // "Login" or "Register"

    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var firstNameInput by rememberSaveable { mutableStateOf("") }
    var lastNameInput by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current // Get the current context

    // Input validation states
    var emailError by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordError by rememberSaveable { mutableStateOf<String?>(null) }

    // State for password visibility
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    // Profile edit states
    var editFirstName by rememberSaveable { mutableStateOf("") }
    var editLastName by rememberSaveable { mutableStateOf("") }

    // Populate edit fields when profile is loaded or changes
    LaunchedEffect(userProfile) {
        userProfile?.let {
            editFirstName = it.first_name
            editLastName = it.last_name
        }
    }

    // Clear errors/statuses when switching mode or when user logs in/out
    LaunchedEffect(uiMode, accessToken) {
        settingsViewModel.clearErrorAndStatus()
        if (accessToken != null && uiMode == "Login") { // If user just logged in
            settingsViewModel.fetchUserProfile() // Fetch profile right after login
        }
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
                    Text(stringResource(id = R.string.login), style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it; emailError = null },
                        label = { Text(stringResource(id = R.string.email)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                        isError = emailError != null,
                        supportingText = { emailError?.let { Text(it) } }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it; passwordError = null },
                        label = { Text(stringResource(id = R.string.password)) },
                        singleLine = true,
                        // Apply visual transformation based on passwordVisible state
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if (validateLoginInputs(emailInput, passwordInput, { e -> emailError = e }, { p -> passwordError = p })) {
                                settingsViewModel.login(emailInput, passwordInput)
                            }
                        }),
                        trailingIcon = {
                            val description = if (passwordVisible) stringResource(id = R.string.hide_password) else stringResource(id = R.string.show_password)
                            val icon = if (passwordVisible) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_visibility),
                                    contentDescription = description,
                                    modifier = Modifier.size(26.dp)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_visibility_off),
                                    contentDescription = description,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                icon
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isError = passwordError != null,
                        supportingText = { passwordError?.let { Text(it) } }
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                if (validateLoginInputs(emailInput, passwordInput, { e -> emailError = e }, { p -> passwordError = p })) {
                                    settingsViewModel.login(emailInput, passwordInput)
                                    chatViewModel.setIsAuthExpired(false)
                                }
                            },
                            enabled = emailInput.isNotBlank() && passwordInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(id = R.string.login)) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { uiMode = "Register" }) {
                        Text(stringResource(id = R.string.dont_have_account))
                    }
                } else { // Register mode
                    Text(stringResource(id = R.string.register), style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = firstNameInput,
                        onValueChange = { firstNameInput = it },
                        label = { Text(stringResource(id = R.string.first_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = lastNameInput,
                        onValueChange = { lastNameInput = it },
                        label = { Text(stringResource(id = R.string.last_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it; emailError = null },
                        label = { Text(stringResource(id = R.string.email)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = emailError != null,
                        supportingText = { emailError?.let { Text(it) } }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it; passwordError = null },
                        label = { Text(stringResource(id = R.string.password)) },
                        // Apply visual transformation based on passwordVisible state
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if (validateRegistrationInputs(emailInput, passwordInput, firstNameInput, lastNameInput, { e -> emailError = e }, { p -> passwordError = p })) {
                                settingsViewModel.registerUser(emailInput, passwordInput, firstNameInput, lastNameInput)
                            }
                        }),
                        trailingIcon = {
                            val description = if (passwordVisible) stringResource(id = R.string.hide_password) else stringResource(id = R.string.show_password)
                            val icon = if (passwordVisible) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_visibility),
                                    contentDescription = description,
                                    modifier = Modifier.size(26.dp)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_visibility_off),
                                    contentDescription = description,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                icon
                            }
                        },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        isError = passwordError != null,
                        supportingText = { passwordError?.let { Text(it) } }
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Terms and Privacy Policy Text
                    val annotatedString = buildAnnotatedString {
                        append(stringResource(id = R.string.terms_agreement_prefix))
                        pushStringAnnotation(tag = "TERMS", annotation = BuildConfig.TERM_URL)
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append(stringResource(id = R.string.terms))
                        }
                        pop()
                        append(stringResource(id = R.string.terms_agreement_and))
                        pushStringAnnotation(tag = "PRIVACY", annotation = BuildConfig.PRIVACY_POLICY_URL)
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append(stringResource(id = R.string.privacy_policy))
                        }
                        pop()
                    }

                    ClickableText(
                        text = annotatedString,
                        onClick = { offset ->
                            annotatedString.getStringAnnotations(tag = "TERMS", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    val intent = Intent(Intent.ACTION_VIEW, annotation.item.toUri())
                                    context.startActivity(intent)
                                }
                            annotatedString.getStringAnnotations(tag = "PRIVACY", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    val intent = Intent(Intent.ACTION_VIEW, annotation.item.toUri())
                                    context.startActivity(intent)
                                }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))


                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                if (validateRegistrationInputs(emailInput, passwordInput, firstNameInput, lastNameInput, { e -> emailError = e }, { p -> passwordError = p })) {
                                    settingsViewModel.registerUser(emailInput, passwordInput, firstNameInput, lastNameInput)
                                }
                            },
                            enabled = emailInput.isNotBlank() && passwordInput.isNotBlank() && firstNameInput.isNotBlank() && lastNameInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(id = R.string.register)) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { uiMode = "Login" }) {
                        Text(stringResource(id = R.string.already_have_account))
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
                Text(stringResource(id = R.string.user_profile), style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(id = R.string.email_label, loggedInUserEmail ?: "N/A"), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))

                // Profile Information Section
                Text(stringResource(id = R.string.profile_information), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = editFirstName,
                    onValueChange = { editFirstName = it },
                    label = { Text(stringResource(id = R.string.first_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editLastName,
                    onValueChange = { editLastName = it },
                    label = { Text(stringResource(id = R.string.last_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        settingsViewModel.updateUserProfile(editFirstName, editLastName, userProfile?.preferences)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isLoading.not() && (editFirstName != userProfile?.first_name || editLastName != userProfile?.last_name)
                ) { Text(stringResource(id = R.string.update_profile)) }

                Spacer(modifier = Modifier.height(24.dp))

                // Preferences Section
                Text(stringResource(id = R.string.preferences), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(id = R.string.vietnamese_language))
                    Switch(
                        checked = userProfile?.preferences?.isVietnamese ?: false,
                        onCheckedChange = { isVietnamese ->
                            settingsViewModel.updateUserPreferences(
                                isVietnamese = isVietnamese,
                                useRAG = userProfile?.preferences?.useRAG ?: false
                            )
                        },
                        enabled = isLoading.not()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(id = R.string.use_rag))
                    Switch(
                        checked = userProfile?.preferences?.useRAG ?: false,
                        onCheckedChange = { useRAG ->
                            settingsViewModel.updateUserPreferences(
                                isVietnamese = userProfile?.preferences?.isVietnamese ?: false,
                                useRAG = useRAG
                            )
                        },
                        enabled = isLoading.not()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))


                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = { settingsViewModel.logout() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(id = R.string.logout)) }
                }
                operationError?.let { // Display logout errors too
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(32.dp)) // Add some space before the footer
            // Support Email Footer
            Text(stringResource(id = R.string.contact_us, "dodoankhac@gmail.com"), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun validateLoginInputs(email: String, pass: String, setEmailError: (String?) -> Unit, setPassError: (String?) -> Unit): Boolean {
    var isValid = true
    if (!PatternsCompat.EMAIL_ADDRESS.matcher(email).matches()) {
        setEmailError("Invalid email address")
        isValid = false
    } else {
        setEmailError(null)
    }
    if (pass.length < 8) { // Example: min 6 chars for password
        setPassError("Password must be at least 8 characters")
        isValid = false
    } else if (!pass.any { it.isDigit() }) {
        setPassError("Password contain at least 1 number")
        isValid = false
    } else if (!pass.any { it in "!@#$%^&*()_+-=[]{}|;:'\",.<>/?`~" }) {
        setPassError("Password contain at least 1 special character")
        isValid = false
    } else {
        setPassError(null)
    }
    return isValid
}

private fun validateRegistrationInputs(email: String, pass: String, firstName: String, lastName: String, setEmailError: (String?) -> Unit, setPassError: (String?) -> Unit): Boolean {
    // Reusing login validation for email and pass, add checks for firstName, lastName if needed
    val loginValid = validateLoginInputs(email, pass, setEmailError, setPassError)
    var isValid = loginValid
    // Add specific checks for firstName, lastName if desired (e.g., not blank)
    if (firstName.isBlank()) {
        // Consider adding a specific error state for firstName if you have a separate field for its error
        isValid = false
    }
    if (lastName.isBlank()) {
        // Consider adding a specific error state for lastName
        isValid = false
    }

    return isValid
}