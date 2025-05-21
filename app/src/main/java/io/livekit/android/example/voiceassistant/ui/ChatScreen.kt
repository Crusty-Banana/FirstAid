package io.livekit.android.example.voiceassistant.ui

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester.Companion.createRefs
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.ajalt.timberkt.Timber
import io.livekit.android.annotations.Beta
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.state.VoiceAssistant
import io.livekit.android.compose.state.rememberVoiceAssistant
import io.livekit.android.compose.state.transcriptions.rememberParticipantTranscriptions
import io.livekit.android.compose.state.transcriptions.rememberTranscriptions
import io.livekit.android.compose.ui.audio.VoiceAssistantBarVisualizer
import io.livekit.android.example.voiceassistant.viewmodels.ChatViewModel
import io.livekit.android.example.voiceassistant.data.Message // Import Message
import io.livekit.android.room.Room
import io.livekit.android.room.types.TranscriptionSegment
import io.livekit.android.example.voiceassistant.R

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
    setShowVoiceChat: (Boolean) -> Unit,
    setShowTabBar: (Boolean) -> Unit,
    chatViewModel: ChatViewModel = viewModel()
) {
    Timber.i {"Load OpenVoiceChatButton"}
    Timber.i {"ViewModel instance in X: $chatViewModel"}
    val context = LocalContext.current

    FloatingActionButton(onClick = {
        setShowTabBar(false)
        setShowVoiceChat(true)
        useSpeakerMode(context)
        if (activeConversationIdFromTopLevel == null) {
            chatViewModel.createNewConversationAndSelect("new chat") { newConvId ->
                setActiveConversationIdFromTopLevel(newConvId)
                chatViewModel.initVoiceSession(newConvId)
            }
        } else {
            chatViewModel.initVoiceSession(activeConversationIdFromTopLevel)
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
    Timber.i {"Load VoiceChatScreen"}
    Timber.i {"ViewModel instance in X: $chatViewModel"}
    RoomScope(
        url,
        token,
        audio = true,
        connect = true,
        onConnected = { room -> Timber.d {"Connected to room ${room.name} "}},
        onDisconnected = { room -> Timber.d {"Disconnected to room ${room.name}"}}
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
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        modifier = Modifier.size(26.dp) // Icon size
                    )
                }

                Spacer(modifier = Modifier.width(24.dp)) // Space between buttons

                // Close Button
                Button(
                    onClick = {
                        room.disconnect()
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
    chatViewModel: ChatViewModel = ChatViewModel()
) {
    Timber.i {"Load ChatMessageScreen"}
    Timber.i {"ViewModel instance in X: $chatViewModel"}
    val lazyListState = rememberLazyListState()
    val messages by chatViewModel.messages.collectAsState()

    LaunchedEffect(messages.size) {
        if (messages.lastOrNull() != null) {
            lazyListState.scrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    "No messages yet. Click voice button to start.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(0.7f).fillMaxWidth(),
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
    setShowTabBar: (Boolean) -> Unit,
    chatViewModel: ChatViewModel = viewModel()
) {
    Timber.i {"Load NewChatScreen"}
    Timber.i {"ViewModel instance in X: $chatViewModel"}
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
        modifier = Modifier
            .padding(bottom = 25.dp),
        floatingActionButton = {
            if (!showVoiceChat) {
                OpenVoiceChatButton(
                    activeConversationIdFromTopLevel = activeConversationIdFromTopLevel,
                    setActiveConversationIdFromTopLevel = setActiveConversationIdFromTopLevel,
                    setShowVoiceChat = { showVoiceChat = it },
                    setShowTabBar = setShowTabBar,
                    chatViewModel = chatViewModel
                )
            }
        },
        content = { paddingValues ->
            if (!isLoggedIn) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Please log in via Settings to use Chat.", style = MaterialTheme.typography.bodyLarge)
                }
            } else if (isAuthExpired) {
                setShowTabBar(true)
                showVoiceChat = false
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Authentication Expired", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp, vertical = 8.dp), // Adjusted padding
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showVoiceChat) {
                        Timber.i {"PRE-IF. url: $liveKitUrl\n token: $liveKitToken\n activeConversationIdFromTopLevel:$activeConversationIdFromTopLevel" }
                        if (liveKitToken == null || activeConversationIdFromTopLevel == null) {
                            Timber.i {"IF. url: $liveKitUrl\n token: $liveKitToken\n activeConversationIdFromTopLevel:$activeConversationIdFromTopLevel" }
                            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                CircularProgressIndicator()
                                Text("Loading voice chat session...", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top=8.dp))
                            }
                        } else {
                            Timber.i {"ELSE. url: $liveKitUrl\n token: $liveKitToken\n activeConversationIdFromTopLevel:$activeConversationIdFromTopLevel" }
                            VoiceChatScreen(
                                url = liveKitUrl,
                                token = liveKitToken!!,
                                setShowVoiceChat = { showVoiceChat = it },
                                setShowTabBar = setShowTabBar,
                                activeConversationIdFromTopLevel = activeConversationIdFromTopLevel!!,
                                modifier = Modifier.fillMaxSize(),
                                chatViewModel = chatViewModel
                            )
                        }
                    } else {
                        if (isLoading) {
                            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                CircularProgressIndicator()
                                Text("Loading chat messages...", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top=8.dp))
                            }
                        } else {
                            ChatMessageScreen(chatViewModel = chatViewModel)
                        }
                    }
                }
            }
        }
    )
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