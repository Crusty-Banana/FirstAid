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
        return if (!body.isNullOrEmpty()) {
            try {
                gson.fromJson(body, ApiError::class.java)?.let { apiError ->
                    apiError.message ?: apiError.detail ?: apiError.error ?: apiError.errors?.joinToString() ?: "$context (Code: $code)"
                } ?: "$context: $httpMessage (Code: $code)"
            } catch (e: com.google.gson.JsonSyntaxException) { // Be specific about the exception type
                Timber.w(e) { "JSON syntax error parsing error body: $body" }
                "$context: Invalid error format (Code: $code). Details: $httpMessage"
            } catch (e: Exception) {
                Timber.w(e) { "Unexpected error parsing error body: $body" }
                "$context: Error processing error response (Code: $code). Details: $httpMessage"
            }
        } else {
            "$context: $httpMessage (Code: $code)"
        }
    }
} 