package io.livekit.android.example.voiceassistant.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.livekit.android.example.voiceassistant.data.Conversation
import io.livekit.android.example.voiceassistant.viewmodels.ChatViewModel
import io.livekit.android.example.voiceassistant.viewmodels.HistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    chatViewModel: ChatViewModel,
    historyViewModel: HistoryViewModel,
    onSelectConversationAndNavigate: (conversationId: String, conversationTitle: String) -> Unit
) {
    val conversations by historyViewModel.conversations.collectAsState()
    val isLoading by historyViewModel.isLoading.collectAsState()
    val error by historyViewModel.error.collectAsState()
    val isLoggedIn by historyViewModel.isLoggedIn.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newConversationTitle by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            historyViewModel.fetchConversations()
        } else {
            // Clear conversations if user logs out while on this screen (or navigates away and back)
            // historyViewModel.clearConversations() // Add this method to ViewModel if needed
        }
    }

    Scaffold(
        modifier = Modifier
            .padding(bottom = 25.dp),
        floatingActionButton = {
            if (isLoggedIn) {
                FloatingActionButton(onClick = {
                    historyViewModel.clearError() // Clear previous errors before showing dialog
                    newConversationTitle = "" // Reset title
                    showCreateDialog = true
                }) {
                    Icon(Icons.Filled.Add, "Create new conversation")
                }
            }
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp), // Adjusted padding
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isLoggedIn) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Please log in via Settings to view history.", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Conversation History", style = MaterialTheme.typography.headlineSmall)
                        Button(onClick = { historyViewModel.fetchConversations() }, enabled = !isLoading) {
                            Text(if (isLoading && conversations.isEmpty()) "Loading..." else "Refresh")
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (isLoading && conversations.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (conversations.isEmpty() && !isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No conversation history found. Tap '+' to create one.", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(conversations, key = { it.id }) { conversation ->
                                ConversationHistoryItem(
                                    conversation = conversation,
                                    onClick = {
                                        onSelectConversationAndNavigate(conversation.id, conversation.title)
                                    },
                                    onDelete = {
                                        historyViewModel.deleteConversation(conversation.id)
                                    }
                                )
                                Divider()
                            }
                        }
                    }
                }
            }

            if (showCreateDialog) {
                AlertDialog(
                    onDismissRequest = { showCreateDialog = false },
                    title = { Text("New Conversation") },
                    text = {
                        OutlinedTextField(
                            value = newConversationTitle,
                            onValueChange = { newConversationTitle = it },
                            label = { Text("Conversation Title") },
                            singleLine = true,
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newConversationTitle.isNotBlank()) {
                                    focusManager.clearFocus()
                                    showCreateDialog = false
                                    chatViewModel.createNewConversationAndSelect(newConversationTitle) {
                                        onSelectConversationAndNavigate(it, newConversationTitle)
                                    }
                                }
                            },
                            enabled = newConversationTitle.isNotBlank()
                        ) { Text("Create") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
                    }
                )
            }
        }
    )
}

@Composable
fun ConversationHistoryItem(
    conversation: Conversation,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { Text(conversation.title.ifBlank { "(Untitled Conversation)" }, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text("Last updated: ${conversation.updated_at}", style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete conversation",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    )
}