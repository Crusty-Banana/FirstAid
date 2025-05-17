package io.livekit.android.example.voiceassistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.ajalt.timberkt.Timber
import io.livekit.android.example.voiceassistant.viewmodels.ChatViewModel
import io.livekit.android.example.voiceassistant.data.Message // Import Message
// Placeholder for your actual VoiceAssistant composable
// import io.livekit.android.example.voiceassistant.VoiceAssistant

// Placeholder VoiceAssistant - Replace with your actual LiveKit VoiceAssistant composable
@Composable
fun VoiceAssistant(url: String, token: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("LiveKit Voice Assistant UI Placeholder", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("URL: $url", style = MaterialTheme.typography.bodySmall)
            Text("Token: ${token.take(10)}...", style = MaterialTheme.typography.bodySmall)
            // Add your actual VoiceAssistantBarVisualizer, transcriptions list etc. here
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
            .padding(16.dp),
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

                    // VoiceAssistant takes up some space (e.g., top 30%)
                    Box(modifier = Modifier.weight(0.3f).fillMaxWidth()) {
                        VoiceAssistant(
                            url = liveKitUrl,
                            token = liveKitToken!!, // Not null here
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    // Messages take up the rest
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.weight(0.7f).fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageItem(message) // You need to create this Composable
                        }
                    }
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