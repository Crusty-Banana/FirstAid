package io.livekit.android.example.voiceassistant.viewmodels

import com.github.ajalt.timberkt.Timber
import com.google.gson.Gson
import io.livekit.android.example.voiceassistant.data.ApiError
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException

object ViewModelUtils {

    private val gson = Gson() // Keep Gson instance internal if only used here

    fun handleException(
        e: Exception,
        operation: String,
        url: String,
        errorState: MutableStateFlow<String?>
    ) {
        when (e) {
            is IOException -> {
                Timber.e(e) { "Network error during $operation from $url" }
                errorState.value = "Network error: ${e.localizedMessage ?: "Check connection."}"
            }
            is com.google.gson.JsonSyntaxException -> {
                Timber.e(e) { "JSON parsing error during $operation from $url" }
                errorState.value = "Error parsing server response."
            }
            else -> {
                Timber.e(e) { "Unexpected error during $operation from $url" }
                errorState.value = "An unexpected error occurred: ${e.localizedMessage}"
            }
        }
    }

    fun parseError(
        body: String?,
        code: Int,
        httpMessage: String,
        context: String = "Operation failed"
    ): String {
        Timber.w {"API Error: Code: $code, Message: $httpMessage, Body: $body, Context: $context"} // Log detailed error
        return if (!body.isNullOrEmpty()) {
            try {
                gson.fromJson(body, ApiError::class.java)?.let { apiError ->
                    // Prefer specific messages if available, otherwise generic
                    val userFacingMessage = apiError.message ?: apiError.detail ?: apiError.error ?: apiError.errors?.joinToString()
                    if (!userFacingMessage.isNullOrBlank()) {
                        userFacingMessage // Return specific backend message if considered safe
                    } else {
                        "$context. Please try again later. (Code: $code)" // Generic user-facing message
                    }
                } ?: "$context: An issue occurred. (Code: $code)" // More generic if parsing ApiError fails
            } catch (e: com.google.gson.JsonSyntaxException) { // Be specific about the exception type
                Timber.w(e) { "JSON syntax error parsing error body: $body" }
                "An unexpected error occurred while processing the server response. Please try again." // Generic
            } catch (e: Exception) {
                Timber.w(e) { "Unexpected error parsing error body: $body" }
                "An unexpected server error occurred. Please try again." // Generic
            }
        } else {
            // If body is null or empty, provide a generic message based on HTTP code
            when (code) {
                400 -> "Invalid request. Please check your input and try again."
                401 -> "Authentication failed. Please log in again."
                403 -> "You do not have permission to perform this action."
                404 -> "The requested information could not be found."
                in 500..599 -> "The server encountered an error. Please try again later."
                else -> "$context: $httpMessage (Code: $code)" // Fallback, but try to make this more generic too
            }
        }
    }
} 