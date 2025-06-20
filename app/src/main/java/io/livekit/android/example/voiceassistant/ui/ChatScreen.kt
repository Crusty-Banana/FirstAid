package io.livekit.android.example.voiceassistant.ui

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.ajalt.timberkt.Timber
import io.livekit.android.annotations.Beta
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.state.rememberVoiceAssistant
import io.livekit.android.compose.state.transcriptions.rememberParticipantTranscriptions
import io.livekit.android.compose.state.transcriptions.rememberTranscriptions
import io.livekit.android.compose.ui.audio.VoiceAssistantBarVisualizer
import io.livekit.android.example.voiceassistant.R
import io.livekit.android.example.voiceassistant.data.Message
import io.livekit.android.example.voiceassistant.viewmodels.ChatViewModel
import io.livekit.android.example.voiceassistant.viewmodels.HistoryViewModel
import io.livekit.android.example.voiceassistant.viewmodels.SettingsViewModel

fun useSpeakerMode(context: Context) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
        val devices = audioManager.availableCommunicationDevices
        val speakerDevice = devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

        if (speakerDevice != null) {
            val success = audioManager.setCommunicationDevice(speakerDevice)
            Timber.d { "Turn on speaker mode, success: $success" }
        }
    } else {
        // Fallback for older devices
        @Suppress("DEPRECATION")
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = true
    }
}

@Composable
fun OpenVoiceChatButton(
    activeConversationIdFromTopLevel: String?,
    setActiveConversationIdFromTopLevel: (String?) -> Unit,
    activeConversationTitleFromTopLevel: String,
    setShowVoiceChat: (Boolean) -> Unit,
    setShowTabBar: (Boolean) -> Unit,
    chatViewModel: ChatViewModel
) {
    Timber.i { "Load OpenVoiceChatButton" }
    Timber.i { "ViewModel instance in X: $chatViewModel" }
    val context = LocalContext.current
    val voiceSessionFailedMessage = stringResource(id = R.string.toast_failed_to_initiate_voice_session)
    val tryAgainMessage = stringResource(id = R.string.toast_try_again)

    FloatingActionButton(onClick = {
        setShowTabBar(false)
        setShowVoiceChat(true)
        useSpeakerMode(context)
        if (activeConversationIdFromTopLevel == null) {
            chatViewModel.createNewConversationAndSelect(activeConversationTitleFromTopLevel) { newConvId ->
                setActiveConversationIdFromTopLevel(newConvId)
                chatViewModel.initVoiceSession(
                    newConvId,
                    onFailed = {
                        setShowTabBar(true)
                        setShowVoiceChat(false)
                        Toast.makeText(context, voiceSessionFailedMessage, Toast.LENGTH_SHORT).show()
                        Toast.makeText(context, tryAgainMessage, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        } else {
            chatViewModel.initVoiceSession(
                activeConversationIdFromTopLevel,
                onFailed = {
                    setShowTabBar(true)
                    setShowVoiceChat(false)
                    Toast.makeText(context, voiceSessionFailedMessage, Toast.LENGTH_SHORT).show()
                    Toast.makeText(context, tryAgainMessage, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }) {
        Icon(
            painter = painterResource(id = R.drawable.ic_audio),
            contentDescription = null,
            modifier = Modifier.size(26.dp)
        )
    }
}

@OptIn(Beta::class)
@Composable
fun VoiceChatScreen(
    url: String,
    token: String,
    setShowVoiceChat: (Boolean) -> Unit,
    setShowTabBar: (Boolean) -> Unit,
    activeConversationIdFromTopLevel: String,
    modifier: Modifier = Modifier,
    chatViewModel: ChatViewModel = ChatViewModel()
) {
    Timber.i { "Load VoiceChatScreen" }
    Timber.i { "ViewModel instance in X: $chatViewModel" }
    RoomScope(
        url,
        token,
        audio = true,
        connect = true,
        onConnected = { room -> Timber.d { "Connected to room ${room.name} " } },
        onDisconnected = { room -> Timber.d { "Disconnected to room ${room.name}" } }
    ) { room ->
        val voiceAssistant = rememberVoiceAssistant()
        var isMuted by remember { mutableStateOf(false) }

        val segments = rememberTranscriptions()
        val localSegments = rememberParticipantTranscriptions(room.localParticipant)

        LaunchedEffect(isMuted) { // Use isMuted as key
            room.localParticipant.setMicrophoneEnabled(!isMuted)
        }

        LaunchedEffect(segments.lastOrNull()) {
            val latestSegment = segments.lastOrNull()
            val latestLocalSegment = localSegments.lastOrNull()

            if (latestSegment != null && latestSegment.final) {
                chatViewModel.sendMessage(
                    role = if (latestSegment != latestLocalSegment) "assistant" else "user",
                    messageContent = latestSegment.text,
                    conversationId = activeConversationIdFromTopLevel
                )
            }
        }
        // Column to occupy the whole screen and push buttons to the bottom
        Column(
            modifier = modifier.fillMaxSize(), // Use the passed modifier and fill the screen
            verticalArrangement = Arrangement.Bottom, // Push content to the bottom
            horizontalAlignment = Alignment.CenterHorizontally // Center content horizontally
        ) {
            VoiceAssistantBarVisualizer(
                voiceAssistant = voiceAssistant,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .height(70.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth() // Row takes full width
                    .padding(bottom = 32.dp), // Some padding from the very bottom
                horizontalArrangement = Arrangement.Center, // Center buttons in the row
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute/Unmute Button
                Button(
                    onClick = { isMuted = !isMuted },
                    shape = CircleShape,
                    modifier = Modifier.size(60.dp), // Define a circular size
                    contentPadding = PaddingValues(0.dp) // Remove default padding if icon is too small
                ) {
                    Icon(
                        painter = painterResource(id = if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic),
                        contentDescription = if (isMuted) stringResource(id = R.string.unmute) else stringResource(id = R.string.mute),
                        modifier = Modifier.size(26.dp) // Icon size
                    )
                }

                Spacer(modifier = Modifier.width(24.dp)) // Space between buttons

                // Close Button
                Button(
                    onClick = {
                        room.disconnect()
                        chatViewModel.endVoiceSession()
                        chatViewModel.fetchMessages(activeConversationIdFromTopLevel, enableLoading = true)
                        setShowVoiceChat(false)
                        setShowTabBar(true)
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red, // Red background color
                        contentColor = Color.White  // White content color for better contrast
                    ),
                    modifier = Modifier.size(60.dp) // Define a circular size
                ) {
                    Text(
                        text = "X",
                        style = MaterialTheme.typography.headlineSmall // Make X a bit larger
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageScreen(
    chatViewModel: ChatViewModel = ChatViewModel(),
    settingsViewModel: SettingsViewModel = SettingsViewModel()
) {
    Timber.i { "Load ChatMessageScreen" }
    Timber.i { "ViewModel instance in X: $chatViewModel" }
    val lazyListState = rememberLazyListState()
    val messages by chatViewModel.messages.collectAsState()
    val userProfile by settingsViewModel.userProfile.collectAsState()

    LaunchedEffect(messages.size) {
        if (messages.lastOrNull() != null) {
            lazyListState.scrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    stringResource(id = R.string.hello_user_start, userProfile?.first_name ?: ""),
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { message -> "msg-${message.id}" }) { message ->
                    MessageItem(message)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    activeConversationIdFromTopLevel: String?, // Renamed to avoid conflict with local var
    setActiveConversationIdFromTopLevel: (String?) -> Unit, // Callback to TopLevelApp
    activeConversationTitleFromTopLevel: String,
    setActiveConversationTitleFromTopLevel: (String) -> Unit,
    setShowTabBar: (Boolean) -> Unit,
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel
) {
    Timber.i { "Load NewChatScreen" }
    Timber.i { "ViewModel instance in X: $chatViewModel" }
    var showVoiceChat by remember { mutableStateOf(false) }
    val liveKitToken by chatViewModel.liveKitToken.collectAsState()
    val isLoggedIn by chatViewModel.isLoggedIn.collectAsState()
    val liveKitUrl = chatViewModel.LIVEKIT_WS_URL
    val isLoading by chatViewModel.isLoading.collectAsState()
    val isAuthExpired by chatViewModel.isAuthExpired.collectAsState()

    LaunchedEffect(activeConversationIdFromTopLevel, isLoggedIn) {
        Timber.d { "ChatScreen: activeConversationIdFromTopLevel is '$activeConversationIdFromTopLevel', isLoggedIn: $isLoggedIn" }
        if (isLoggedIn) {
            chatViewModel.setActiveConversation(activeConversationIdFromTopLevel)
        } else {
            chatViewModel.setActiveConversation(null) // Clear if logged out
        }
    }

    Scaffold(
        floatingActionButton = {
            if (!showVoiceChat && isLoggedIn) {
                OpenVoiceChatButton(
                    activeConversationIdFromTopLevel = activeConversationIdFromTopLevel,
                    setActiveConversationIdFromTopLevel = setActiveConversationIdFromTopLevel,
                    activeConversationTitleFromTopLevel = activeConversationTitleFromTopLevel,
                    setShowVoiceChat = { showVoiceChat = it },
                    setShowTabBar = setShowTabBar,
                    chatViewModel = chatViewModel
                )
            }
        },
        content = { paddingValues ->
            if (!isLoggedIn) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp, vertical = 8.dp), // Adjusted padding
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(id = R.string.login_to_use_chat), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else if (isAuthExpired) {
                setShowTabBar(true)
                showVoiceChat = false
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(id = R.string.auth_expired), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = paddingValues.calculateTopPadding())
                        .padding(horizontal = 16.dp), // Adjusted padding
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        EditableTextWithIcon(
                            initialText = activeConversationTitleFromTopLevel,
                            onTextChanged = {
                                setActiveConversationTitleFromTopLevel(it)
                                if (activeConversationIdFromTopLevel != null) {
                                    chatViewModel.updateActiveConversationTitle(activeConversationIdFromTopLevel, it)
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (showVoiceChat) {
                        if (liveKitToken == null || activeConversationIdFromTopLevel == null) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    stringResource(id = R.string.loading_voice_chat),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        } else {
                            Timber.i { "ELSE. url: $liveKitUrl\n token: $liveKitToken\n activeConversationIdFromTopLevel:$activeConversationIdFromTopLevel" }
                            // Safe call using let
                            val currentToken = liveKitToken!!
                            val currentConversationId = activeConversationIdFromTopLevel
                            VoiceChatScreen(
                                url = liveKitUrl,
                                token = currentToken,
                                setShowVoiceChat = { showVoiceChat = it },
                                setShowTabBar = setShowTabBar,
                                activeConversationIdFromTopLevel = currentConversationId,
                                modifier = Modifier.fillMaxSize(),
                                chatViewModel = chatViewModel
                            )
                        }
                    } else {
                        if (isLoading) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    stringResource(id = R.string.loading_chat_messages),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        } else {
                            ChatMessageScreen(chatViewModel = chatViewModel, settingsViewModel = settingsViewModel)
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun EditableTextWithIcon(
    initialText: String,
    onTextChanged: (String) -> Unit // Callback to notify when text is saved
) {
    var isEditing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(initialText) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (isEditing) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.headlineSmall
            )
        } else {
            Text(
                text = if (text.isBlank()) stringResource(id = R.string.new_chat) else text,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp) // Add some padding so text doesn't overlap icon
            )
        }

        IconButton(onClick = {
            if (isEditing) {
                onTextChanged(text) // Notify the parent about the change
            }
            isEditing = !isEditing
        }) {
            Icon(
                imageVector = if (isEditing) Icons.Filled.Check else Icons.Filled.Edit,
                contentDescription = if (isEditing) stringResource(id = R.string.save_changes) else stringResource(id = R.string.edit_text)
            )
        }
    }
}

@Composable
fun MessageItem(message: Message) { // Example, style as needed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = if (message.role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(Modifier.padding(8.dp)) {
                Text(
                    text = message.role.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(message.content, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = message.created_at.take(16).replace("T", " "), // Basic formatting
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}