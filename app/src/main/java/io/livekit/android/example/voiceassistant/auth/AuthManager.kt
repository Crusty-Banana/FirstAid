package io.livekit.android.example.voiceassistant.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.github.ajalt.timberkt.Timber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    // val refreshToken: StateFlow<String?> = _refreshToken.asStateFlow() // Expose if needed for refresh logic

    private val _loggedInUserEmail = MutableStateFlow<String?>(null)
    val loggedInUserEmail: StateFlow<String?> = _loggedInUserEmail.asStateFlow()

    val isLoggedIn: Boolean
        get() = _accessToken.value != null

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
}