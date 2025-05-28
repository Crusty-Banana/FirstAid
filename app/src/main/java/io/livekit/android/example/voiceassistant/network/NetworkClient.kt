package io.livekit.android.example.voiceassistant.network

import com.github.ajalt.timberkt.Timber
import com.google.gson.Gson
import io.livekit.android.example.voiceassistant.BuildConfig // Ensure this import is correct and BuildConfig is generated
import io.livekit.android.example.voiceassistant.auth.AuthInterceptor
import io.livekit.android.example.voiceassistant.auth.AuthManager
import io.livekit.android.example.voiceassistant.auth.TokenRefreshAuthenticator
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object NetworkClient {

    private const val TIMEOUT_SECONDS = 30L
    // API_BASE_URL needed for TokenRefreshAuthenticator, should be configurable
    // private const val API_BASE_URL = "https://medbot-backend.fly.dev" // TODO: Make this configurable

    // Client for calls that DO NOT require authentication (e.g., login, register)
    val unauthenticatedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    val loggingInterceptor = HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
                        override fun log(message: String) {
                            Timber.tag("OkHttp-Unauth").d(message)
                        }
                    }).apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                    addInterceptor(loggingInterceptor)
                }
            }
            .build()
    }

    // Client for calls that DO require authentication
    val authenticatedClient: OkHttpClient by lazy {
        val gson = Gson() // Gson instance for the authenticator
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(AuthManager))
            .authenticator(TokenRefreshAuthenticator(AuthManager, BuildConfig.API_BASE_URL, gson))
            .apply {
                if (BuildConfig.DEBUG) {
                    val loggingInterceptor = HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
                        override fun log(message: String) {
                            Timber.tag("OkHttp-Auth").d(message)
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