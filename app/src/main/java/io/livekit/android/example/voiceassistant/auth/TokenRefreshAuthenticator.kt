package io.livekit.android.example.voiceassistant.auth

import com.github.ajalt.timberkt.Timber
import com.google.gson.Gson
import io.livekit.android.example.voiceassistant.network.NetworkClient // To access unauthenticatedClient
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenRefreshAuthenticator(
    private val authManager: AuthManager,
    private val apiBaseUrl: String, // Pass your API base URL
    private val gson: Gson         // Pass Gson instance
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        Timber.d { "TokenRefreshAuthenticator: authenticate called for response code ${response.code} to ${response.request.url}" }

        // We only care about 400s
        if (response.code != 401 && response.code != 400) {
            return null // Give up, let other interceptors or the caller handle it.
        }

        // Check if the request already has an Authorization header. If it's a login/register/refresh, don't retry with auth.
        val path = response.request.url.encodedPath
        if (path.contains("/auth/login") || path.contains("/auth/register") || path.contains("/auth/refresh")) {
            Timber.d { "TokenRefreshAuthenticator: Path is auth related ($path), not attempting refresh."}
            return null // Don't attempt to refresh for auth endpoints themselves
        }

        // Prevent multiple refresh attempts if one is already in progress or just completed
        // by comparing the token used in the failed request with the current token.
        val requestAccessToken = response.request.header("Authorization")?.substringAfter("Bearer ")
        val currentAccessTokenInManager = authManager.getAccessTokenValue()

        // If the token in the request is different from the current one in the manager,
        // it means a refresh might have happened while this request was in flight.
        // Retry with the current token from the manager.
        if (requestAccessToken != currentAccessTokenInManager && currentAccessTokenInManager != null) {
            Timber.d { "TokenRefreshAuthenticator: Token mismatch, retrying with current token from AuthManager." }
            return newRequestWithToken(response.request, currentAccessTokenInManager)
        }

        Timber.i { "TokenRefreshAuthenticator: Access token expired or invalid. Attempting refresh." }

        // Call the synchronous refresh function.
        // The unauthenticatedClient is crucial to avoid an infinite loop if the refresh endpoint itself needs authentication
        // or if the Authenticator was mistakenly added to the client used for refreshing.
        val refreshedSuccessfully = authManager.refreshAccessTokenSynchronously(
            NetworkClient.unauthenticatedClient, // Use the unauthenticated client
            apiBaseUrl,
            gson
        )

        return if (refreshedSuccessfully) {
            val newAccessToken = authManager.getAccessTokenValue()
            if (newAccessToken != null) {
                Timber.i { "TokenRefreshAuthenticator: Token refreshed. Retrying original request with new token." }
                newRequestWithToken(response.request, newAccessToken)
            } else {
                Timber.e { "TokenRefreshAuthenticator: Refresh reported success, but new token is null." }
                // This case should ideally not happen if refreshAccessTokenSynchronously is correct
                // and logoutUser hasn't been called.
                null // Give up.
            }
        } else {
            Timber.w { "TokenRefreshAuthenticator: Token refresh failed. Not retrying request." }
            // AuthManager.logoutUser() should have been called within refreshAccessToken if refresh definitively failed.
            null // Give up. The user should be logged out.
        }
    }

    private fun newRequestWithToken(originalRequest: Request, accessToken: String): Request {
        return originalRequest.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .header("X-API-Auth", "bearer $accessToken") // Also update your custom header
            .build()
    }
}