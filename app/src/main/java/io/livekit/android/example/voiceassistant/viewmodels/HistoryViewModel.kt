package io.livekit.android.example.voiceassistant.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ajalt.timberkt.Timber
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.livekit.android.example.voiceassistant.auth.AuthInterceptor
import io.livekit.android.example.voiceassistant.auth.AuthManager
import io.livekit.android.example.voiceassistant.data.ApiError
import io.livekit.android.example.voiceassistant.data.Conversation
import io.livekit.android.example.voiceassistant.data.CreateConversationRequest
import io.livekit.android.example.voiceassistant.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class HistoryViewModel : ViewModel() {
    // !!! REPLACE WITH YOUR ACTUAL API BASE URL !!!
    private val API_BASE_URL = "https://medbot-backend.fly.dev"
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = NetworkClient.authenticatedClient

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _newlyCreatedConversationId = MutableSharedFlow<String>(replay = 0)
    val newlyCreatedConversationId: SharedFlow<String> = _newlyCreatedConversationId.asSharedFlow()

    val isLoggedIn: StateFlow<Boolean> = AuthManager.accessToken
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthManager.isLoggedIn)

    fun fetchConversations() {
        if (!AuthManager.isLoggedIn) {
            _error.value = "Please log in to view history."
            _conversations.value = emptyList() // Clear old data
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val fullUrl = "$API_BASE_URL/api/v1/conversations/"
            Timber.d { "Fetching conversations from $fullUrl" }
            try {
                val request = Request.Builder().url(fullUrl).get().build()
                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                val responseBodyString = withContext(Dispatchers.IO) { response.body?.string() }

                if (response.isSuccessful) {
                    if (!responseBodyString.isNullOrEmpty()) {
                        val convListType = object : TypeToken<List<Conversation>>() {}.type
                        _conversations.value = gson.fromJson(responseBodyString, convListType)
                        Timber.i { "Fetched ${_conversations.value.size} conversations." }
                    } else {
                        _conversations.value = emptyList()
                    }
                } else {
                    _error.value = parseError(responseBodyString, response.code, response.message, "Fetch conversations failed")
                }
            } catch (e: Exception) {
                handleException(e, "fetch conversations", fullUrl)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createNewConversation(title: String) {
        if (!AuthManager.isLoggedIn) {
            _error.value = "Please log in to create a conversation."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val fullUrl = "$API_BASE_URL/api/v1/conversations/"
            Timber.d { "Creating new conversation: '$title' at $fullUrl" }
            try {
                val createRequest = CreateConversationRequest(title = title)
                val requestBody = gson.toJson(createRequest).toRequestBody(jsonMediaType)
                val request = Request.Builder().url(fullUrl).post(requestBody).build()

                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                val responseBodyString = withContext(Dispatchers.IO) { response.body?.string() }

                if (response.code == 201) { // Created
                    if (!responseBodyString.isNullOrEmpty()) {
                        val newConversation = gson.fromJson(responseBodyString, Conversation::class.java)
                        Timber.i { "New conversation created: ${newConversation.id}" }
                        _newlyCreatedConversationId.emit(newConversation.id) // Signal success
                        // fetchConversations() // List will be refreshed by observer or upon re-entering screen
                    } else {
                        _error.value = "Conversation created but no data returned."
                    }
                } else {
                    _error.value = parseError(responseBodyString, response.code, response.message, "Create conversation failed")
                }
            } catch (e: Exception) {
                handleException(e, "create conversation", fullUrl)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun handleException(e: Exception, operation: String, url: String) {
        // (Similar to SettingsViewModel's handleException)
        when (e) {
            is IOException -> {
                Timber.e(e) { "Network error during $operation from $url" }
                _error.value = "Network error: ${e.localizedMessage ?: "Check connection."}"
            }
            is com.google.gson.JsonSyntaxException -> {
                Timber.e(e) { "JSON parsing error during $operation from $url" }
                _error.value = "Error parsing server response."
            }
            else -> {
                Timber.e(e) { "Unexpected error during $operation from $url" }
                _error.value = "An unexpected error occurred: ${e.localizedMessage}"
            }
        }
    }

    private fun parseError(body: String?, code: Int, httpMessage: String, context: String = "Operation failed"): String {
        // (Similar to SettingsViewModel's parseError)
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