package io.livekit.android.example.voiceassistant.network

import com.github.ajalt.timberkt.Timber
import io.livekit.android.example.voiceassistant.auth.AuthInterceptor
import io.livekit.android.example.voiceassistant.auth.AuthManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object NetworkClient {

    private const val TIMEOUT_SECONDS = 30L
    private const val DEBUG = true

    // Client for calls that DO NOT require authentication (e.g., login, register)
    val unauthenticatedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .apply {
                if (DEBUG) { // Only add detailed logging in debug builds
                    val loggingInterceptor = HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
                        override fun log(message: String) {
                            Timber.tag("OkHttp-Unauth").d(message) // Log with Timber
                        }
                    }).apply {
                        // Log request and response lines and their respective headers and bodies (if present).
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                    addInterceptor(loggingInterceptor)
                }
            }
            .build()
    }

    // Client for calls that DO require authentication
    val authenticatedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(AuthManager)) // Your existing AuthInterceptor
            .apply {
                if (DEBUG) {
                    val loggingInterceptor = HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
                        override fun log(message: String) {
                            Timber.tag("OkHttp-Auth").d(message) // Different tag for clarity
                        }
                    }).apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                    addInterceptor(loggingInterceptor)
                }
            }
            .build()
    }
}