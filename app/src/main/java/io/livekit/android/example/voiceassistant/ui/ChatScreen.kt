package io.livekit.android.example.voiceassistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester.Companion.createRefs
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

// Placeholder for your actual VoiceAssistant composable
// import io.livekit.android.example.voiceassistant.VoiceAssistant

// Placeholder VoiceAssistant - Replace with your actual LiveKit VoiceAssistant composable
@OptIn(Beta::class)
//@Composable
//fun VoiceAssistant(url: String, token: String, modifier: Modifier = Modifier) {
//    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//        Column(horizontalAlignment = Alignment.CenterHorizontally) {
//            Text("LiveKit Voice Assistant UI Placeholder", style = MaterialTheme.typography.titleMedium)
//            Spacer(modifier = Modifier.height(8.dp))
//            Text("URL: $url", style = MaterialTheme.typography.bodySmall)
//            Text("Token: ${token.take(10)}...", style = MaterialTheme.typography.bodySmall)
//            // Add your actual VoiceAssistantBarVisualizer, transcriptions list etc. here
//            RoomScope(
//                url = url,
//                token = token,
//                audio = true,
//                connect = true,
//            ) { room ->
//                val voiceAssistant = rememberVoiceAssistant()
//            }
//        }
//    }
//}

@Composable
fun VoiceAssistantScreen(
    url: String,
    token: String,
    lastSentLocalSegmentId: String?,
    setLastSentLocalSegmentId: (String) -> Unit,
    lastSentAssistantSegmentId: String?,
    setLastSentAssistantSegmentId: (String) -> Unit,
    activeConversationIdFromArgs: String,
    modifier: Modifier = Modifier,
    chatViewModel: ChatViewModel = viewModel(),
    content: @Composable (
        room: Room,
        voiceAssistant: VoiceAssistant,
        segments: List<TranscriptionSegment>,
        localSegments: List<TranscriptionSegment>
    ) -> Unit
) {
    ConstraintLayout(modifier = modifier) {
        RoomScope(
            url,
            token,
            audio = true,
            connect = true
        ) { room ->
            val (audioVisualizer, chatLog) = createRefs()

            val voiceAssistant = rememberVoiceAssistant()

            val agentState = voiceAssistant.state
            LaunchedEffect(key1 = agentState) {
                Timber.i { "agent state: $agentState" }
            }

            val segments = rememberTranscriptions()
            val localSegments = rememberParticipantTranscriptions(room.localParticipant)
            val lazyListState = rememberLazyListState()

            LaunchedEffect(localSegments.lastOrNull()) {
                val latestLocalSegment = localSegments.lastOrNull()
                if (latestLocalSegment != null && latestLocalSegment.final && latestLocalSegment.id != lastSentLocalSegmentId) {
                    chatViewModel.sendMessage(role = "user", messageContent = latestLocalSegment.text, conversationId = activeConversationIdFromArgs)
                    setLastSentLocalSegmentId(latestLocalSegment.id)
                }
            }

            LaunchedEffect(segments.lastOrNull()) {
                val latestSegment = segments.lastOrNull()
                val latestLocalSegment = localSegments.lastOrNull()

                if (latestSegment != latestLocalSegment) {
                    if (latestSegment != null && latestSegment.final && latestSegment.id != lastSentAssistantSegmentId) {
                        chatViewModel.sendMessage(
                            role = "assistant",
                            messageContent = latestSegment.text,
                            conversationId = activeConversationIdFromArgs
                        )
                        setLastSentAssistantSegmentId(latestSegment.id)
                    }
                }
            }
            content(room, voiceAssistant, segments, localSegments)
//            LazyColumn(
//                userScrollEnabled = true,
//                state = lazyListState,
//                modifier = Modifier
//                    .constrainAs(chatLog) {
//                        bottom.linkTo(parent.bottom)
//                        start.linkTo(parent.start)
//                        end.linkTo(parent.end)
//                        height = Dimension.percent(0.9f) // Ensure this doesn't overlap with tabs
//                        width = Dimension.fillToConstraints
//                    }
//            ) {
//                items(
//                    items = segments,
//                    key = { segment -> segment.id },
//                ) { segment ->
//                    Box(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(8.dp)
//                    ) {
//                        if (localSegments.contains(segment)) {
//                            UserTranscription(
//                                segment = segment,
//                                modifier = Modifier.align(Alignment.CenterEnd)
//                            )
//                        } else {
//                            Text(
//                                text = segment.text,
//                                modifier = Modifier.align(Alignment.CenterStart)
//                            )
//                        }
//                    }
//                }
//            }
//
//            VoiceAssistantBarVisualizer(
//                voiceAssistant = voiceAssistant,
//                modifier = Modifier
//                    .padding(8.dp)
//                    .fillMaxWidth()
//                    .height(70.dp)
//                    .constrainAs(audioVisualizer) {
//                        height = Dimension.percent(0.1f)
//                        width = Dimension.percent(0.8f)
//                        top.linkTo(parent.top)
//                        start.linkTo(parent.start)
//                        end.linkTo(parent.end)
//                    }
//            )
//
//            LaunchedEffect(segments) {
//                if (segments.isNotEmpty()) {
//                    lazyListState.scrollToItem((segments.size - 1).coerceAtLeast(0))
//                }
//            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    activeConversationIdFromArgs: String?, // Renamed to avoid conflict with local var
    onUpdateActiveConversationId: (String?) -> Unit, // Callback to TopLevelApp
    chatViewModel: ChatViewModel = viewModel()
) {
    val isLoggedIn by chatViewModel.isLoggedIn.collectAsState()
    val liveKitToken by chatViewModel.liveKitToken.collectAsState()
    val liveKitUrl = chatViewModel.LIVEKIT_WS_URL
    val isLoading by chatViewModel.isLoading.collectAsState()
    val error by chatViewModel.error.collectAsState()
    val messages by chatViewModel.messages.collectAsState()
    val lazyListState = rememberLazyListState()

    var showCreateConversationDialog by remember { mutableStateOf(false) }
    var newConversationTitleInput by remember { mutableStateOf("") }
    var lastSentLocalSegmentId by remember { mutableStateOf<String?>(null) }
    var lastSentAssistantId by remember { mutableStateOf<String?>(null) }

    // Sync ViewModel with activeConversationId from arguments
    LaunchedEffect(activeConversationIdFromArgs, isLoggedIn) {
        Timber.d { "ChatScreen: activeConversationIdFromArgs is '$activeConversationIdFromArgs', isLoggedIn: $isLoggedIn" }
        if (isLoggedIn) {
            chatViewModel.setActiveConversation(activeConversationIdFromArgs)
        } else {
            chatViewModel.setActiveConversation(null) // Clear if logged out
        }
    }

    // Scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(top = 20.dp, bottom = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isLoggedIn) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Please log in via Settings to use Chat.", style = MaterialTheme.typography.bodyLarge)
            }
        } else if (activeConversationIdFromArgs == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No active conversation.", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        chatViewModel.clearError()
                        newConversationTitleInput = ""
                        showCreateConversationDialog = true
                    }) {
                        Text("Start New Chat")
                    }
                    error?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        } else {
            // Active conversation selected
            if (isLoading) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                    Text("Loading chat session...", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top=8.dp))
                }
            } else if (error != null) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Error: $error", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { chatViewModel.setActiveConversation(activeConversationIdFromArgs) }) { // Retry
                        Text("Retry Load Session")
                    }
                }
            } else if (liveKitToken != null) {
                // Main chat UI with VoiceAssistant and messages
                Column(modifier = Modifier.fillMaxSize()) {
                    Text("Chat: ${activeConversationIdFromArgs.take(8)}...", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Divider(modifier = Modifier.padding(vertical = 8.dp))
                    // Messages take up the rest

                    // VoiceAssistant takes up some space (e.g., top 30%)
//                    Box(modifier = Modifier.weight(0.3f).fillMaxWidth()) {
                    VoiceAssistantScreen(
                        url = liveKitUrl,
                        token = liveKitToken!!, // Not null here
                        modifier = Modifier.fillMaxWidth(),
                        lastSentLocalSegmentId = lastSentLocalSegmentId,
                        setLastSentLocalSegmentId = { lastSentLocalSegmentId = it },
                        lastSentAssistantSegmentId = lastSentAssistantId,
                        setLastSentAssistantSegmentId = { lastSentAssistantId = it },
                        chatViewModel = chatViewModel,
                        activeConversationIdFromArgs = activeConversationIdFromArgs
                    ) { room, voiceAssistant, segments, localSegments ->
                        // Your chat UI goes here
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.weight(0.7f).fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(messages, key = { message -> "msg-${message.id}" }) { message ->
                                    MessageItem(message)
                                }

                                items(
                                    items = segments,
                                    key = { segment -> "seg-${segment.id}" }
                                ) { segment ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        if (localSegments.contains(segment)) {
                                            UserTranscriptionDisplay( // Using the new display composable
                                                segment = segment,
                                                modifier = Modifier.align(Alignment.CenterEnd)
                                            )
                                        } else {
                                            // Assistant/Remote participant's transcription
                                            Card(
                                                modifier = Modifier.align(Alignment.CenterStart)
                                                    .fillMaxWidth(0.8f),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                                )
                                            ) {
                                                Column(Modifier.padding(8.dp)) {
                                                    Text(
                                                        text = "Assistant",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        segment.text,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = if (segment.final) "" else "typing...", // Indicate if not final
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.align(Alignment.End)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        LaunchedEffect(messages, segments) {
                            // Determine the index of the last item to scroll to
                            val lastIndex = if (segments.isNotEmpty()) {
                                messages.size + segments.lastIndex
                            } else if (messages.isNotEmpty()) {
                                messages.lastIndex
                            } else {
                                // If both lists are empty, no need to scroll
                                return@LaunchedEffect
                            }

                            // Animate the scroll for a smoother transition
                            lazyListState.animateScrollToItem(lastIndex)
                        }
                    }
//                    }
                    // TODO: Message Input Row
                    // MessageInput(...)
                }

            } else {
                // This state means liveKitToken is null, but not loading and no error
                // Could be initial state before effect runs, or if LK token fetch silently failed
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Initializing voice session...", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { chatViewModel.setActiveConversation(activeConversationIdFromArgs) }) { // Retry
                        Text("Retry Init Session")
                    }
                }
            }
        }
    }

    if (showCreateConversationDialog) {
        AlertDialog(
            onDismissRequest = { showCreateConversationDialog = false },
            title = { Text("Start New Conversation") },
            text = {
                OutlinedTextField(
                    value = newConversationTitleInput,
                    onValueChange = { newConversationTitleInput = it },
                    label = { Text("Conversation Title (Optional)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = newConversationTitleInput.ifBlank { "New Chat @ ${java.text.SimpleDateFormat("HH:mm").format(java.util.Date())}" }
                        chatViewModel.createNewConversationAndSelect(title) { newConvId ->
                            onUpdateActiveConversationId(newConvId) // Update TopLevelApp's state
                        }
                        showCreateConversationDialog = false
                    }
                ) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateConversationDialog = false }) { Text("Cancel") }
            }
        )
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

@Composable
fun UserTranscriptionDisplay(segment: TranscriptionSegment, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(0.8f), // Apply alignment from caller, fill a portion of width
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(
                text = "You", // Or segment.participant?.identity?.value ?: "You"
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(segment.text, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (segment.final) "" else "typing...", // Indicate if not final (interim)
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}