package io.livekit.android.example.voiceassistant.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.github.ajalt.timberkt.Timber
import com.google.gson.Gson
import io.livekit.android.example.voiceassistant.data.RefreshTokenRequest
import io.livekit.android.example.voiceassistant.data.RefreshTokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object AuthManager {
    private const val PREF_NAME = "secure_auth_prefs_v1" // Use a unique name
    private const val KEY_ACCESS_TOKEN = "access_token_key"
    private const val KEY_REFRESH_TOKEN = "refresh_token_key"
    private const val KEY_LOGGED_IN_EMAIL = "logged_in_email_key"

    @Volatile
    private var encryptedPrefs: SharedPreferences? = null

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    private val _refreshToken = MutableStateFlow<String?>(null)
    val refreshToken: StateFlow<String?> = _refreshToken.asStateFlow() // Expose if needed for refresh logic

    private val _loggedInUserEmail = MutableStateFlow<String?>(null)
    val loggedInUserEmail: StateFlow<String?> = _loggedInUserEmail.asStateFlow()

    val isLoggedIn: Boolean
        get() = _accessToken.value != null

    private val refreshMutex = Mutex()

    fun initialize(context: Context) {
        if (encryptedPrefs != null) {
            Timber.i { "AuthManager already initialized." }
            return
        }
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            encryptedPrefs = EncryptedSharedPreferences.create(
                PREF_NAME,
                masterKeyAlias,
                context.applicationContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            Timber.i { "AuthManager initialized with EncryptedSharedPreferences." }
            loadSession()
        } catch (e: Exception) {
            Timber.e(e) { "AuthManager: Failed to initialize EncryptedSharedPreferences. Tokens will be in-memory only." }
        }
    }

    private fun loadSession() {
        encryptedPrefs?.let { prefs ->
            _accessToken.value = prefs.getString(KEY_ACCESS_TOKEN, null)
            _refreshToken.value = prefs.getString(KEY_REFRESH_TOKEN, null)
            _loggedInUserEmail.value = prefs.getString(KEY_LOGGED_IN_EMAIL, null)
            Timber.i { "AuthManager: Session loaded. User: ${_loggedInUserEmail.value ?: "None"}" }
        } ?: Timber.w { "AuthManager: Cannot load session, EncryptedSharedPreferences not ready." }
    }

    fun loginUser(accToken: String, refToken: String, email: String) {
        _accessToken.value = accToken
        _refreshToken.value = refToken
        _loggedInUserEmail.value = email

        encryptedPrefs?.edit()?.apply {
            putString(KEY_ACCESS_TOKEN, accToken)
            putString(KEY_REFRESH_TOKEN, refToken)
            putString(KEY_LOGGED_IN_EMAIL, email)
            apply()
            Timber.i { "AuthManager: User logged in ($email). Tokens saved." }
        } ?: Timber.w { "AuthManager: EncryptedPrefs not ready. Tokens in-memory for login." }
    }

    fun refreshToken(accToken: String, refToken: String) {
        _accessToken.value = accToken
        _refreshToken.value = refToken

        encryptedPrefs?.edit()?.apply {
            putString(KEY_ACCESS_TOKEN, accToken)
            putString(KEY_REFRESH_TOKEN, refToken)
            apply()
            Timber.i { "AuthManager: Token refreshed. Tokens saved." }
        } ?: Timber.w { "AuthManager: EncryptedPrefs not ready. Tokens in-memory for login." }
    }

    fun logoutUser() {
        val email = _loggedInUserEmail.value ?: "Unknown"
        _accessToken.value = null
        _refreshToken.value = null
        _loggedInUserEmail.value = null

        encryptedPrefs?.edit()?.apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_LOGGED_IN_EMAIL)
            apply()
            Timber.i { "AuthManager: User ($email) logged out. Tokens cleared." }
        } ?: Timber.w { "AuthManager: EncryptedPrefs not ready. Tokens cleared in-memory." }
    }

    fun getAccessTokenValue(): String? = _accessToken.value
    fun getRefreshTokenValue(): String? = _refreshToken.value

    fun refreshAccessTokenSynchronously(
        unauthenticatedClient: OkHttpClient,
        apiBaseUrl: String,
        gson: Gson
    ): Boolean {
        // Use runBlocking to call the suspend version from a synchronous context (OkHttp Authenticator)
        // This is a pragmatic choice for integrating with OkHttp's synchronous Authenticator.
        return runBlocking {
            refreshAccessToken(unauthenticatedClient, apiBaseUrl, gson)
        }
    }

    // Make this suspend function public to be called from SettingsViewModel
    suspend fun refreshAccessToken(
        unauthenticatedClient: OkHttpClient,
        apiBaseUrl: String,
        gson: Gson
    ): Boolean {
        return refreshMutex.withLock { // Ensure only one refresh attempt at a time
            val currentRefreshToken = _refreshToken.value
            if (currentRefreshToken == null) {
                Timber.w { "AuthManager: No refresh token available. Cannot refresh." }
                // Consider logging out user if refresh is critical and no token exists
                // logoutUser()
                return false
            }

            Timber.i { "AuthManager: Attempting to refresh access token." }
            val fullUrl = "$apiBaseUrl/api/v1/auth/refresh" // Ensure this is your correct refresh endpoint
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()

            try {
                val refreshTokenRequest = RefreshTokenRequest(currentRefreshToken)
                val requestBodyJson = gson.toJson(refreshTokenRequest)
                val requestBody = requestBodyJson.toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(fullUrl)
                    .post(requestBody)
                    .build()

                // Execute the call using the unauthenticated client to avoid authenticator loops
                val response = withContext(Dispatchers.IO) {
                    unauthenticatedClient.newCall(request).execute()
                }
                val responseBodyString = response.body?.string() // Read body once

                if (response.isSuccessful && !responseBodyString.isNullOrEmpty()) {
                    val refreshResponse = gson.fromJson(responseBodyString, RefreshTokenResponse::class.java)
                    // Update tokens. Assume email doesn't change on refresh.
                    // If your refresh endpoint also returns a new refresh token, use it.
                    // Otherwise, keep the old one.
                    val newRefreshToken = refreshResponse.refresh_token
                    loginUser(refreshResponse.access_token, newRefreshToken, _loggedInUserEmail.value ?: "Unknown")
                    Timber.i { "AuthManager: Access token refreshed successfully." }
                    return true
                } else {
                    Timber.e { "AuthManager: Token refresh failed. Code: ${response.code}, Body: $responseBodyString" }
                    // If refresh fails (e.g., 401 on refresh token itself), logout the user
                    if (response.code == 401 || response.code == 400 || response.code == 403) {
                        Timber.w { "AuthManager: Refresh token invalid or expired. Logging out user." }
                        logoutUser() // Critical: if refresh fails, the session is invalid
                    }
                    return false
                }
            } catch (e: IOException) {
                Timber.e(e) { "AuthManager: Network error during token refresh." }
                return false
            } catch (e: Exception) {
                Timber.e(e) { "AuthManager: Unexpected error during token refresh." }
                return false
            }
        }
    }
}