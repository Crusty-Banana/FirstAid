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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.livekit.android.example.voiceassistant.R
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
        floatingActionButton = {
            if (isLoggedIn) {
                FloatingActionButton(onClick = {
                    historyViewModel.clearError() // Clear previous errors before showing dialog
                    newConversationTitle = "" // Reset title
                    showCreateDialog = true
                }) {
                    Icon(Icons.Filled.Add, stringResource(id = R.string.create_new_conversation))
                }
            }
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                    .padding(horizontal = 16.dp), // Adjusted padding
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isLoggedIn) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(id = R.string.login_to_view_history), style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(id = R.string.conversation_history), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        Button(onClick = { historyViewModel.fetchConversations() }, enabled = !isLoading) {
                            Text(if (isLoading && conversations.isEmpty()) stringResource(id = R.string.loading) else stringResource(id = R.string.refresh))
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
                            Text(stringResource(id = R.string.no_conversation_history), style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
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
                    title = { Text(stringResource(id = R.string.new_conversation)) },
                    text = {
                        OutlinedTextField(
                            value = newConversationTitle,
                            onValueChange = { newConversationTitle = it },
                            label = { Text(stringResource(id = R.string.conversation_title)) },
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
                        ) { Text(stringResource(id = R.string.create)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(id = R.string.cancel)) }
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
        headlineContent = { Text(conversation.title.ifBlank { stringResource(id = R.string.untitled_conversation) }, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(stringResource(id = R.string.last_updated, conversation.updated_at), style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(id = R.string.delete_conversation),
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