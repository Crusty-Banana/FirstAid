package io.livekit.android.example.voiceassistant.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ajalt.timberkt.Timber
import com.google.gson.Gson
import io.livekit.android.example.voiceassistant.auth.AuthManager
import io.livekit.android.example.voiceassistant.data.LoginRequest
import io.livekit.android.example.voiceassistant.data.LoginResponse
import io.livekit.android.example.voiceassistant.data.RegisterRequest
import io.livekit.android.example.voiceassistant.data.UserProfileResponse
import io.livekit.android.example.voiceassistant.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SettingsViewModel : ViewModel() {

    // TODO: Consider moving this to BuildConfig or a configuration file
    private val API_BASE_URL = "https://medbot-backend.fly.dev"

    // Using NetworkClient for consistency and shared configuration
    private val unauthHttpClient = NetworkClient.unauthenticatedClient
    private val authHttpClient = NetworkClient.authenticatedClient
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private val _registrationStatus = MutableStateFlow<String?>(null)
    val registrationStatus: StateFlow<String?> = _registrationStatus.asStateFlow()

    // Observe AuthManager for login state changes
    val accessToken: StateFlow<String?> = AuthManager.accessToken
    val loggedInUserEmail: StateFlow<String?> = AuthManager.loggedInUserEmail

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _operationError.value = null
            _registrationStatus.value = null // Clear previous reg status
            val fullUrl = "$API_BASE_URL/api/v1/auth/login"
            Timber.d { "Attempting login for $email to $fullUrl" }

            try {
                val loginRequest = LoginRequest(email, password)
                val requestBodyJson = gson.toJson(loginRequest)
                val requestBody = requestBodyJson.toRequestBody(jsonMediaType)
                val request = Request.Builder().url(fullUrl).post(requestBody).build()

                val response = withContext(Dispatchers.IO) { unauthHttpClient.newCall(request).execute() }
                val responseBodyString = withContext(Dispatchers.IO) { response.body?.string() }

                if (response.isSuccessful) {
                    if (!responseBodyString.isNullOrEmpty()) {
                        val loginResponse = gson.fromJson(responseBodyString, LoginResponse::class.java)
                        AuthManager.loginUser(
                            loginResponse.access_token,
                            loginResponse.refresh_token,
                            email
                        )
                    } else {
                        _operationError.value = "Login successful but received an empty response."
                    }
                } else {
                    _operationError.value = ViewModelUtils.parseError(responseBodyString, response.code, response.message)
                }
            } catch (e: Exception) {
                ViewModelUtils.handleException(e, "login", fullUrl, _operationError)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun registerUser(email: String, pass: String, firstName: String, lastName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _operationError.value = null
            _registrationStatus.value = null
            val fullUrl = "$API_BASE_URL/api/v1/auth/register"
            Timber.d { "Attempting registration for $email to $fullUrl" }

            try {
                val registerRequest = RegisterRequest(email, pass, firstName, lastName)
                val requestBodyJson = gson.toJson(registerRequest)
                val requestBody = requestBodyJson.toRequestBody(jsonMediaType)
                val request = Request.Builder().url(fullUrl).post(requestBody).build()

                val response = withContext(Dispatchers.IO) { unauthHttpClient.newCall(request).execute() }
                val responseBodyString = withContext(Dispatchers.IO) { response.body?.string() }

                if (response.code == 201) { // Created
                    if (!responseBodyString.isNullOrEmpty()) {
                        val userProfile = gson.fromJson(responseBodyString, UserProfileResponse::class.java)
                        _registrationStatus.value = "Registration successful for ${userProfile.email}. Please log in."
                        Timber.i { "Registration successful for: ${userProfile.email}" }
                    } else {
                        _operationError.value = "Registration successful but no user data returned."
                    }
                } else {
                    _operationError.value = ViewModelUtils.parseError(responseBodyString, response.code, response.message, "Registration failed")
                }
            } catch (e: Exception) {
                ViewModelUtils.handleException(e, "registration", fullUrl, _operationError)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val token = AuthManager.getAccessTokenValue() // Still needed for the check, but not for header
            if (token == null) {
                _operationError.value = "Not logged in."
                return@launch
            }
            _isLoading.value = true
            _operationError.value = null
            val fullUrl = "$API_BASE_URL/api/v1/auth/logout"
            Timber.d { "Attempting logout from $fullUrl" }
            try {
                val requestBody = "".toRequestBody(jsonMediaType) // Empty body for logout as per existing logic
                // Rely on AuthInterceptor in authHttpClient to add the token
                val request = Request.Builder().url(fullUrl).post(requestBody).build()
                val response = withContext(Dispatchers.IO) { authHttpClient.newCall(request).execute() }
                val responseBodyString = withContext(Dispatchers.IO) { response.body?.string() }

                if (response.isSuccessful) {
                    Timber.i { "Logout successful." }
                    AuthManager.logoutUser()
                } else {
                    _operationError.value = ViewModelUtils.parseError(responseBodyString, response.code, response.message, "Logout failed")
                    if (response.code == 401) AuthManager.logoutUser() // Token likely invalid
                }
            } catch (e: Exception) {
                ViewModelUtils.handleException(e, "logout", fullUrl, _operationError)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshAccessToken() {
        viewModelScope.launch {
            _isLoading.value = true
            _operationError.value = null
            Timber.d { "Attempting to refresh access token via AuthManager." }
            try {
                val success = AuthManager.refreshAccessToken(
                    unauthHttpClient, // Pass the unauthenticated client
                    API_BASE_URL,     // Pass the API base URL
                    gson              // Pass the Gson instance
                )

                if (success) {
                    Timber.i { "SettingsViewModel: Token refresh successful via AuthManager." }
                    // Optionally, trigger UI updates or clear specific errors if needed
                } else {
                    Timber.w { "SettingsViewModel: Token refresh failed via AuthManager." }
                    // AuthManager's refreshAccessToken should handle logging out on critical failures.
                    // _operationError might already be set by AuthManager or its internal error handling if it modifies a shared state,
                    // or you can set a generic error here if AuthManager doesn't expose it directly.
                    // For now, assume AuthManager handles its own error state or logout.
                    // If a specific error message is needed here, it could be:
                    // _operationError.value = "Failed to refresh token. Please try logging in again."
                }
            } catch (e: Exception) {
                // This catch block might be redundant if AuthManager.refreshAccessToken handles all its exceptions.
                // However, keeping it for safety in case AuthManager.refreshAccessToken throws an unexpected exception directly.
                Timber.e(e) { "SettingsViewModel: Exception during AuthManager.refreshAccessToken call." }
                _operationError.value = "An unexpected error occurred during token refresh: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearErrorAndStatus() {
        _operationError.value = null
        _registrationStatus.value = null
    }
}