package io.livekit.android.example.voiceassistant.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ajalt.timberkt.Timber
import com.google.gson.Gson
import io.livekit.android.example.voiceassistant.auth.AuthManager
import io.livekit.android.example.voiceassistant.data.* // Import all from ApiModels
import io.livekit.android.example.voiceassistant.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class SettingsViewModel : ViewModel() {

    // !!! REPLACE WITH YOUR ACTUAL API BASE URL !!!
    private val API_BASE_URL = "https://medbot-backend.fly.dev"

    private val httpClient = OkHttpClient() // For a real app, inject or share this client
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

    private val unauthHttpClient = NetworkClient.unauthenticatedClient
    private val authHttpClient = NetworkClient.authenticatedClient

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
                    _operationError.value = parseError(responseBodyString, response.code, response.message)
                }
            } catch (e: Exception) {
                handleException(e, "login", fullUrl)
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
                    _operationError.value = parseError(responseBodyString, response.code, response.message, "Registration failed")
                }
            } catch (e: Exception) {
                handleException(e, "registration", fullUrl)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val token = AuthManager.getAccessTokenValue()
            if (token == null) {
                _operationError.value = "Not logged in."
                return@launch
            }
            _isLoading.value = true
            _operationError.value = null
            val fullUrl = "$API_BASE_URL/api/v1/auth/logout"
            Timber.d { "Attempting logout from $fullUrl" }
            try {
                val requestBody = "".toRequestBody(jsonMediaType)
                val request = Request.Builder().url(fullUrl).post(requestBody)
                    .addHeader("Authorization", "Bearer $token").build()
                val response = withContext(Dispatchers.IO) { authHttpClient.newCall(request).execute() }
                val responseBodyString = withContext(Dispatchers.IO) { response.body?.string() }

                if (response.isSuccessful) {
                    Timber.i { "Logout successful." }
                    AuthManager.logoutUser()
                } else {
                    _operationError.value = parseError(responseBodyString, response.code, response.message, "Logout failed")
                    if (response.code == 401) AuthManager.logoutUser() // Token likely invalid
                }
            } catch (e: Exception) {
                handleException(e, "logout", fullUrl)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshAccessToken() {
        viewModelScope.launch {
            val refreshToken = AuthManager.getRefreshTokenValue()
            if (refreshToken == null) {
                _operationError.value = "No refresh token"
                return@launch
            }

            _isLoading.value = true
            _operationError.value = null
            val fullUrl = "$API_BASE_URL/api/v1/auth/refresh"
            val newUrlWithQuery = "$fullUrl?refresh_token=$refreshToken"

            Timber.d { "Attempting to refresh access token from $fullUrl" }
            try {
//                val refreshRequest = RefreshTokenRequest(refreshToken)
//                val requestBodyJson = gson.toJson(refreshRequest)
//                val requestBody = requestBodyJson.toRequestBody(jsonMediaType)
                val emptyJsonBody = "{}".toRequestBody(jsonMediaType)
                val request = Request.Builder().url(newUrlWithQuery).post(emptyJsonBody).build()

                val response = withContext(Dispatchers.IO) { unauthHttpClient.newCall(request).execute() }
                val responseBodyString = withContext(Dispatchers.IO) { response.body.string() }

                if (response.isSuccessful) {
                    if (responseBodyString.isNotEmpty()) {
                        val refreshTokenResponse = gson.fromJson(responseBodyString, RefreshTokenResponse::class.java)
                        AuthManager.refreshToken(
                            refreshTokenResponse.access_token,
                            refreshTokenResponse.refresh_token
                        )
                    } else {
                        _operationError.value = "Refresh successful but return empty response."
                    }
                } else {
                    _operationError.value = parseError(responseBodyString, response.code, response.message)
                }
            } catch (e: Exception) {
                handleException(e, "refresh token", fullUrl)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearErrorAndStatus() {
        _operationError.value = null
        _registrationStatus.value = null
    }

    private fun handleException(e: Exception, operation: String, url: String) {
        when (e) {
            is IOException -> {
                Timber.e(e) { "Network error during $operation to $url" }
                _operationError.value = "Network error: ${e.localizedMessage ?: "Check connection."}"
            }
            is com.google.gson.JsonSyntaxException -> {
                Timber.e(e) { "JSON parsing error during $operation from $url" }
                _operationError.value = "Error parsing server response."
            }
            else -> {
                Timber.e(e) { "Unexpected error during $operation to $url" }
                _operationError.value = "An unexpected error occurred: ${e.localizedMessage}"
            }
        }
    }

    private fun parseError(body: String?, code: Int, httpMessage: String, context: String = "Operation failed"): String {
        return if (!body.isNullOrEmpty()) {
            try {
                gson.fromJson(body, ApiError::class.java)?.let { apiError ->
                    apiError.message ?: apiError.detail ?: apiError.error ?: apiError.errors?.joinToString() ?: "$context (Code: $code)"
                } ?: "$context: $httpMessage (Code: $code)"
            } catch (e: Exception) {
                "$context: Invalid error format (Code: $code). Details: $httpMessage"
            }
        } else {
            "$context: $httpMessage (Code: $code)"
        }
    }
}