@file:OptIn(Beta::class)

package io.livekit.android.example.voiceassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.ajalt.timberkt.Timber
import io.livekit.android.LiveKit
import io.livekit.android.annotations.Beta
import io.livekit.android.example.voiceassistant.ui.HistoryScreen
import io.livekit.android.example.voiceassistant.ui.NewChatScreen
import io.livekit.android.example.voiceassistant.ui.SettingsScreen
import io.livekit.android.example.voiceassistant.ui.theme.LiveKitVoiceAssistantExampleTheme
import io.livekit.android.example.voiceassistant.viewmodels.ChatViewModel
import io.livekit.android.util.LoggingLevel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LiveKit.loggingLevel = LoggingLevel.DEBUG

        val chatViewModel = ChatViewModel()
        requireNeededPermissions {
            setContent {
                LiveKitVoiceAssistantExampleTheme {
                    TopLevelApp(
                        modifier = Modifier.fillMaxSize(),
                        chatViewModel = chatViewModel
                    )
                }
            }
        }
    }

    @Composable
    fun TopLevelApp(
        modifier: Modifier = Modifier,
        chatViewModel: ChatViewModel
    ) {
        Timber.i {"Load TopLevelApp"}
        Timber.i {"ViewModel instance in X: $chatViewModel"}
        val tabs = listOf("Chat", "History", "Settings")
        val tabIcons = listOf(
//            Pair(R.drawable.ic_home, R.drawable.ic_home_filled),
            Pair(R.drawable.ic_chat, R.drawable.ic_chat_filled),
            Pair(R.drawable.ic_log, R.drawable.ic_log_filled),
            Pair(R.drawable.ic_settings, R.drawable.ic_settings_filled),
        )
        var selectedTabIndex by remember { mutableIntStateOf(0) }
        var activeConversationId by remember { mutableStateOf<String?>(null) }
        var showTabBar by remember {mutableStateOf(true)}

        Box(
            modifier = modifier
                .fillMaxSize()
        ) {
            // Content area
            Column(modifier = Modifier.fillMaxSize()) {
                when (selectedTabIndex) {
                    0 -> NewChatScreen( // Chat Tab
                        activeConversationIdFromTopLevel = activeConversationId,
                        setActiveConversationIdFromTopLevel = { convId ->
                            activeConversationId = convId
                            // If a new conversation is started from ChatScreen itself,
                            // it's already on the ChatScreen. No tab switch needed here.
                        },
                        setShowTabBar = { show -> showTabBar = show },
                        chatViewModel = chatViewModel
                    )
                    1 -> HistoryScreen( // History Tab
                        onSelectConversationAndNavigate = { conversationId ->
                            Timber.d { "History: Conversation $conversationId selected. Navigating to Chat." }
                            activeConversationId = conversationId
                            selectedTabIndex = 0
                        },
                        chatViewModel = chatViewModel
                    )
                    2 -> SettingsScreen(chatViewModel = chatViewModel)
                }
            }

            // Tab bar at the bottom
            if (showTabBar) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color(0xFF134D8B),
                        contentColor = Color.White,
                        divider = {},
                        modifier = Modifier.height(50.dp)
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                content = {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(vertical = 4.dp) // adjust vertical padding
                                    ) {
                                        Icon(
                                            painter = painterResource(id = if (selectedTabIndex == index) tabIcons[index].second else tabIcons[index].first),
                                            contentDescription = null,
                                            modifier = Modifier.size(26.dp)
                                        )
                                        Spacer(modifier = Modifier.height(1.dp)) // reduce space between icon and text
                                        Text(text = title, fontSize = 10.sp)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}