package io.livekit.android.example.voiceassistant.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ajalt.timberkt.Timber
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.livekit.android.example.voiceassistant.auth.AuthInterceptor
import io.livekit.android.example.voiceassistant.auth.AuthManager
import io.livekit.android.example.voiceassistant.data.* // Your API models
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

class ChatViewModel : ViewModel() {
    // !!! REPLACE WITH YOUR ACTUAL API BASE URL !!!
    private val API_BASE_URL = "https://medbot-backend.fly.dev"
    val LIVEKIT_WS_URL = "wss://clinical-chatbot-1dewlazs.livekit.cloud"

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val httpClient = NetworkClient.authenticatedClient

    private val _currentConversationId = MutableStateFlow<String?>(null)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _liveKitToken = MutableStateFlow<String?>(null)
    val liveKitToken: StateFlow<String?> = _liveKitToken.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = AuthManager.accessToken
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AuthManager.isLoggedIn)

    fun setActiveConversation(conversationId: String?) {
        if (_currentConversationId.value == conversationId && conversationId != null) {
            // If already loading/loaded this conversation and no error, don't re-trigger unless forced
            if (_liveKitToken.value != null && _error.value == null) {
                Timber.d { "Conversation $conversationId already active and LiveKit token present." }
                return
            }
        }
        _currentConversationId.value = conversationId
        if (conversationId != null) {
            loadConversationAndInitSession(conversationId)
        } else {
            resetChatState()
        }
    }

    private fun loadConversationAndInitSession(conversationId: String) {
        if (!AuthManager.isLoggedIn) {
            _error.value = "Please log in to use chat."
            resetChatState()
            return
        }
        Timber.d { "Loading conversation and session for ID: $conversationId" }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _liveKitToken.value = null // Reset previous token
            _messages.value = emptyList() // Clear previous messages

            try {
                // Step 1: Create Voice Session to get LiveKit token
                val voiceSessionRequest = CreateVoiceSessionRequest(conversation_id = conversationId)
                val vsRequestBody = gson.toJson(voiceSessionRequest).toRequestBody(jsonMediaType)
                val vsRequest = Request.Builder()
                    .url("$API_BASE_URL/api/v1/voice/session/create")
                    .post(vsRequestBody)
                    .build()

                val vsResponse = withContext(Dispatchers.IO) { httpClient.newCall(vsRequest).execute() }
                val vsResponseBodyString = withContext(Dispatchers.IO) { vsResponse.body?.string() }

                if (vsResponse.isSuccessful && !vsResponseBodyString.isNullOrEmpty()) {
                    val voiceSession = gson.fromJson(vsResponseBodyString, VoiceSessionResponse::class.java)
                    _liveKitToken.value = voiceSession.token
                    Timber.i { "LiveKit voice session created, token received for conversation $conversationId" }
                    // Step 2: Fetch messages for this conversation
                    fetchMessages(conversationId) // This will set isLoading = false at its end
                } else {
                    _error.value = parseError(vsResponseBodyString, vsResponse.code, vsResponse.message, "Voice session creation failed")
                    _isLoading.value = false // Failed to get LK token, stop loading
                }
            } catch (e: Exception) {
                handleException(e, "initialize chat session for $conversationId", "")
                _isLoading.value = false
            }
            // isLoading will be set to false by fetchMessages or catch block
        }
    }

    private fun fetchMessages(conversationId: String) {
        viewModelScope.launch {
            // isLoading is managed by the caller (loadConversationAndInitSession)
            // _error.value = null; // Don't clear potential voice session error
            val fullUrl = "$API_BASE_URL/api/v1/conversations/$conversationId/messages"
            Timber.d { "Fetching messages for $conversationId from $fullUrl" }
            try {
                val request = Request.Builder().url(fullUrl).get().build()
                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                val responseBodyString = withContext(Dispatchers.IO) { response.body?.string() }

                if (response.isSuccessful) {
                    if (!responseBodyString.isNullOrEmpty()) {
                        val messageListType = object : TypeToken<List<Message>>() {}.type
                        _messages.value = gson.fromJson(responseBodyString, messageListType)
                        Timber.i { "Fetched ${_messages.value.size} messages." }
                    } else {
                        _messages.value = emptyList()
                    }
                } else {
                    val fetchMsgError = parseError(responseBodyString, response.code, response.message, "Fetch messages failed")
                    _error.value = _error.value?.let { "$it\n$fetchMsgError" } ?: fetchMsgError
                }
            } catch (e: Exception) {
                handleException(e, "fetch messages for $conversationId", fullUrl)
            } finally {
                _isLoading.value = false // End of loading sequence
            }
        }
    }

    fun createNewConversationAndSelect(title: String, onCreatedAndSelected: (newConversationId: String) -> Unit) {
        if (!AuthManager.isLoggedIn) {
            _error.value = "Please log in to start a new chat."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            resetChatState(keepLoading = true)
            val fullUrl = "$API_BASE_URL/api/v1/conversations/"
            Timber.d { "Creating new conversation via ChatVM: '$title' at $fullUrl" }
            try {
                val createRequest = CreateConversationRequest(title = title)
                val requestBody = gson.toJson(createRequest).toRequestBody(jsonMediaType)
                val request = Request.Builder().url(fullUrl).post(requestBody).build()

                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                val responseBodyString = withContext(Dispatchers.IO) { response.body?.string() }

                if (response.code == 201 && !responseBodyString.isNullOrEmpty()) {
                    val newConversation = gson.fromJson(responseBodyString, Conversation::class.java)
                    Timber.i { "New conversation created (ID: ${newConversation.id}). Signaling selection." }
                    onCreatedAndSelected(newConversation.id) // This callback should update TopLevelApp's activeConversationId
                    // setActiveConversation(newConversation.id) will be called due to state change in TopLevelApp
                } else {
                    _error.value = parseError(responseBodyString, response.code, response.message, "Create new conversation failed")
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                handleException(e, "create new conversation", fullUrl)
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun resetChatState(keepLoading: Boolean = false) {
        // _currentConversationId.value = null // This is controlled externally by setActiveConversation
        _messages.value = emptyList()
        _liveKitToken.value = null
        if(!keepLoading) _isLoading.value = false
        // Don't clear error here as it might be useful feedback before reset
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

    // TODO: Implement sendMessage(conversationId, messageContent)

    fun sendMessage(conversationId: String, role: String, messageContent: String) {
        if (!AuthManager.isLoggedIn) {
            _error.value = "Please log in to send a message"
            return
        }
        viewModelScope.launch {
            _error.value = null
            val fullUrl = "$API_BASE_URL/api/v1/conversations/$conversationId/messages"
            Timber.d { "Send Messages to Conversation" }
            try {
                val createRequest = CreateMessageRequest(role = role, content = messageContent, message_type = "text")
                val requestBody = gson.toJson(createRequest).toRequestBody(jsonMediaType)
                val request = Request.Builder().url(fullUrl).post(requestBody).build()

                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                val responseBodyString = withContext(Dispatchers.IO) { response.body?.string() }

                if (response.code == 200 && !responseBodyString.isNullOrEmpty()) {
                    val newConversation = gson.fromJson(responseBodyString, Conversation::class.java)
                    Timber.i { "Message sent (ID: ${newConversation.id})" }
                } else {
                    _error.value = parseError(responseBodyString, response.code, response.message, "Send message failed")
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                handleException(e, "send message", fullUrl)
                _isLoading.value = false
            }
        }
    }
}