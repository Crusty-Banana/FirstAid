package io.livekit.android.example.voiceassistant.auth

import com.github.ajalt.timberkt.Timber
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val authManager: AuthManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val tokenValue = authManager.getAccessTokenValue() // Get the raw access token
        val originalRequest = chain.request()
        var requestBuilder = originalRequest.newBuilder() // Use var to allow reassignment

        // Determine if auth headers should be added based on the request path
        val path = originalRequest.url.encodedPath
        val requiresAuthHeaders = !(path.contains("/auth/login") ||
                path.contains("/auth/register") ||
                path.contains("/auth/refresh"))

        if (requiresAuthHeaders) {
            if (tokenValue != null) {
                Timber.d { "AuthInterceptor: Adding Authorization and X-API-Auth headers for path: $path" }
                // Standard Authorization header
                requestBuilder.header("Authorization", "Bearer $tokenValue")
                // Custom X-API-Auth header
                requestBuilder.header("X-API-Auth", "bearer $tokenValue") // Ensure format "bearer <space> token"
            } else {
                Timber.w { "AuthInterceptor: No token available for authenticated request to path: $path" }
                // Depending on your app's strictness, you might want to block the request
                // or let it proceed (server will likely reject with 401).
                // For now, we let it proceed, and the server will handle unauthorized access.
            }
        } else {
            Timber.d { "AuthInterceptor: Skipping auth headers for path: $path" }
        }

        return chain.proceed(requestBuilder.build())
    }
}