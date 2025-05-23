package io.livekit.android.example.voiceassistant.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ajalt.timberkt.Timber
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.livekit.android.example.voiceassistant.auth.AuthManager
import io.livekit.android.example.voiceassistant.data.Conversation
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
import okhttp3.Request

class HistoryViewModel : ViewModel() {
    // TODO: Consider moving this to BuildConfig or a configuration file
    private val API_BASE_URL = "https://medbot-backend.fly.dev"
    private val gson = Gson()

    private val httpClient = NetworkClient.authenticatedClient

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

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
                    _error.value = ViewModelUtils.parseError(responseBodyString, response.code, response.message, "Fetch conversations failed")
                }
            } catch (e: Exception) {
                ViewModelUtils.handleException(e, "fetch conversations", fullUrl, _error)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}