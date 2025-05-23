package io.livekit.android.example.voiceassistant.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ajalt.timberkt.Timber
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.livekit.android.example.voiceassistant.auth.AuthManager
import io.livekit.android.example.voiceassistant.data.Conversation
import io.livekit.android.example.voiceassistant.data.CreateConversationRequest
import io.livekit.android.example.voiceassistant.data.CreateMessageRequest
import io.livekit.android.example.voiceassistant.data.CreateVoiceSessionRequest
import io.livekit.android.example.voiceassistant.data.Message
import io.livekit.android.example.voiceassistant.data.VoiceSessionResponse
import io.livekit.android.example.voiceassistant.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ChatViewModel : ViewModel() {
    // TODO: Consider moving these to BuildConfig or a configuration file
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

    private val _voiceSessionId = MutableStateFlow<String?>(null)
    val voiceSessionId: StateFlow<String?> = _voiceSessionId.asStateFlow()

    private val _isLoading = MutableStateFlow(false) // General loading for conversations/messages
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isAuthExpired = MutableStateFlow(false)
    val isAuthExpired: StateFlow<Boolean> = _isAuthExpired.asStateFlow()

    fun setIsAuthExpired(isAuthExpired: Boolean) {
        Timber.d { "Setting auth expired to $isAuthExpired." }
        _isAuthExpired.value = isAuthExpired
    }

    val isLoggedIn: StateFlow<Boolean> = AuthManager.accessToken
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AuthManager.isLoggedIn)

    fun setActiveConversation(conversationId: String?) {
        if (_currentConversationId.value == conversationId && conversationId != null) {
            if (_liveKitToken.value != null && _error.value == null) {
                Timber.d { "Conversation $conversationId already active and LiveKit token present." }
                return
            }
        }
        _currentConversationId.value = conversationId
        if (conversationId != null) {
            fetchMessages(conversationId)
        } else {
            resetChatState()
        }
    }

    fun initVoiceSession(conversationId: String) {
        if (!AuthManager.isLoggedIn) {
            _error.value = "Please log in to use voice chat."
            resetChatState()
            return
        }
        Timber.d { "Initiate Voice Session for ID: $conversationId" }
        viewModelScope.launch {
            _error.value = null
            _liveKitToken.value = null

            try {
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
                    _voiceSessionId.value = voiceSession.id
                    Timber.i { "LiveKit voice session (${voiceSession.id}) created, token received for conversation $conversationId" }
                } else if (vsResponse.code == 401) {
                    _isAuthExpired.value = true
                    _error.value = ViewModelUtils.parseError(vsResponseBodyString, vsResponse.code, vsResponse.message, "Voice session creation failed")
                } else {
                    _error.value = ViewModelUtils.parseError(vsResponseBodyString, vsResponse.code, vsResponse.message, "Voice session creation failed")
                }
            } catch (e: Exception) {
                ViewModelUtils.handleException(e, "Error while initialize voice session for $conversationId", "", _error)
            } finally {
                // Reserve for implementing loading mechanism
            }
        }
    }

    fun endVoiceSession() {
        if (!AuthManager.isLoggedIn) {
            _error.value = "Please log in to end voice chat."
            resetChatState()
            return
        }
        Timber.d { "End Voice Session (${_voiceSessionId.value}) for ID: ${_currentConversationId.value}" }
        viewModelScope.launch {
            _error.value = null

            try {
                val request = Request.Builder()
                    .url("$API_BASE_URL/api/v1/voice/session/${_voiceSessionId.value}")
                    .delete()
                    .build()

                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                val responseBodyString = withContext(Dispatchers.IO) { response.body?.string() }

                if (response.isSuccessful && !responseBodyString.isNullOrEmpty()) {
                    _liveKitToken.value = null
                    _voiceSessionId.value = null
                    Timber.i { "LiveKit voice session (${_voiceSessionId.value}) Ended" }
                } else if (response.code == 401) {
                    _isAuthExpired.value = true
                    _error.value = ViewModelUtils.parseError(responseBodyString, response.code, response.message, "Voice session creation failed")
                } else {
                    _error.value = ViewModelUtils.parseError(responseBodyString, response.code, response.message, "Voice session creation failed")
                }
            } catch (e: Exception) {
                ViewModelUtils.handleException(e, "Error while initialize voice session for ${_currentConversationId.value}", "", _error)
            } finally {
                // Reserve for implementing loading mechanism
            }
        }
    }

    fun fetchMessages(conversationId: String, enableLoading: Boolean = false) {
        viewModelScope.launch {
            if (enableLoading) {
                _isLoading.value = true
            }
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
                    val fetchMsgError = ViewModelUtils.parseError(responseBodyString, response.code, response.message, "Fetch messages failed")
                    _error.value = _error.value?.let { "$it\n$fetchMsgError" } ?: fetchMsgError
                }
            } catch (e: Exception) {
                ViewModelUtils.handleException(e, "fetch messages for $conversationId", fullUrl, _error)
            } finally {
                if (enableLoading) { // Only turn off general loading if it was turned on by this call
                    _isLoading.value = false
                }
            }
        }
    }

    fun createNewConversationAndSelect(title: String, onCreatedAndSelected: (newConversationId: String) -> Unit) {
        if (!AuthManager.isLoggedIn) {
            _error.value = "Please log in to start a new chat."
            return
        }
        viewModelScope.launch {
            _error.value = null
            // Do not call resetChatState here, as it clears messages and token, which might not be desired
            // until the new conversation is actually selected and active.
            // The caller (UI) should manage navigation and then call setActiveConversation.

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
                    onCreatedAndSelected(newConversation.id)
                } else if (response.code == 401) {
                    _isAuthExpired.value = true
                    _error.value = ViewModelUtils.parseError(responseBodyString, response.code, response.message, "Create new conversation failed")
                } else {
                    _error.value = ViewModelUtils.parseError(responseBodyString, response.code, response.message, "Create new conversation failed")
                }
            } catch (e: Exception) {
                ViewModelUtils.handleException(e, "create new conversation", fullUrl, _error)
            } finally {
                // Reserve for implementing loading mechanism
            }
        }
    }

    fun sendMessage(conversationId: String, role: String, messageContent: String) {
        if (!AuthManager.isLoggedIn) {
            _error.value = "Please log in to send a message"
            return
        }
        viewModelScope.launch {
            // _isLoading can be used here if sending a message should show a general loading indicator
            // For now, assuming sending is quick or has its own UI feedback.
            // If a dedicated loading state for sending is needed, a new StateFlow could be added.
            _error.value = null
            val fullUrl = "$API_BASE_URL/api/v1/conversations/$conversationId/messages"
            Timber.d { "Send Messages to Conversation" }
            try {
                val createRequest = CreateMessageRequest(role = role, content = messageContent, message_type = "text")
                val requestBody = gson.toJson(createRequest).toRequestBody(jsonMediaType)
                val request = Request.Builder().url(fullUrl).post(requestBody).build()

                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                val responseBodyString = withContext(Dispatchers.IO) { response.body?.string() }

                if (response.isSuccessful && !responseBodyString.isNullOrEmpty()) { // Check for isSuccessful()
                    // Assuming the response is the sent message or updated conversation.
                    // For now, just log success. Consider updating _messages or fetching new messages.
                    val sentMessageConfirmation = gson.fromJson(responseBodyString, Message::class.java) // Or appropriate response type
                    Timber.i { "Message sent successfully (Confirmed ID: ${sentMessageConfirmation.id})" }
                    // Potentially fetch messages again to update the list:
                    // fetchMessages(conversationId)
                } else if (response.code == 401) {
                    _isAuthExpired.value = true
                    _error.value = ViewModelUtils.parseError(responseBodyString, response.code, response.message, "Send message failed")
                } else {
                    _error.value = ViewModelUtils.parseError(responseBodyString, response.code, response.message, "Send message failed")
                }
            } catch (e: Exception) {
                ViewModelUtils.handleException(e, "send message", fullUrl, _error)
            } finally {
                // If a general isLoading was true for sending, set it to false here.
            }
        }
    }

    private fun resetChatState(keepLoading: Boolean = false) {
        _messages.value = emptyList()
        _liveKitToken.value = null
        if(!keepLoading) {
            _isLoading.value = false
        }
        // Don't clear error here as it might be useful feedback before reset
    }
}