@file:OptIn(Beta::class)

package io.livekit.android.example.voiceassistant

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.github.ajalt.timberkt.Timber
import io.livekit.android.LiveKit
import io.livekit.android.annotations.Beta
import io.livekit.android.example.voiceassistant.ui.HistoryScreen
import io.livekit.android.example.voiceassistant.ui.NewChatScreen
import io.livekit.android.example.voiceassistant.ui.SettingsScreen
import io.livekit.android.example.voiceassistant.ui.theme.LiveKitVoiceAssistantExampleTheme
import io.livekit.android.example.voiceassistant.viewmodels.ChatViewModel
import io.livekit.android.example.voiceassistant.viewmodels.HistoryViewModel
import io.livekit.android.example.voiceassistant.viewmodels.SettingsViewModel
import io.livekit.android.util.LoggingLevel
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LiveKit.loggingLevel = LoggingLevel.DEBUG

        // Fetch user profile only if it hasn't been fetched yet.
        if (settingsViewModel.userProfile.value == null) {
            settingsViewModel.fetchUserProfile()
        }

        setContent {
            val userProfile by settingsViewModel.userProfile.collectAsState()
            val isVietnamese = userProfile?.preferences?.isVietnamese ?: false

            // 1. System-Level Update: This tells Android to change the locale for the whole app.
            // It will trigger an activity recreation.
            LaunchedEffect(isVietnamese) {
                val targetLanguageTag = if (isVietnamese) "vi" else "en"
                val targetLocaleList = LocaleListCompat.forLanguageTags(targetLanguageTag)
                if (AppCompatDelegate.getApplicationLocales() != targetLocaleList) {
                    AppCompatDelegate.setApplicationLocales(targetLocaleList)
                }
            }

            // 2. Compose-Level Update: This wrapper ensures that the Compose UI tree
            // explicitly uses a context with the correct locale for recomposition.
            val context = LocalContext.current
            val locale = if (isVietnamese) Locale("vi") else Locale("en")
            val localizedContext = remember(locale, context) {
                createLocalizedContext(context, locale)
            }

            // Provide the localized context to the entire Composable hierarchy.
            CompositionLocalProvider(LocalContext provides localizedContext) {
                LiveKitVoiceAssistantExampleTheme {
                    TopLevelApp(
                        modifier = Modifier.fillMaxSize(),
                        chatViewModel = chatViewModel,
                        settingsViewModel = settingsViewModel,
                        historyViewModel = historyViewModel
                    )
                }
            }
        }
    }

    private fun createLocalizedContext(context: Context, locale: Locale): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    @Composable
    fun TopLevelApp(
        modifier: Modifier = Modifier,
        chatViewModel: ChatViewModel,
        settingsViewModel: SettingsViewModel,
        historyViewModel: HistoryViewModel
    ) {
        Timber.i { "Load TopLevelApp" }
        Timber.i { "ViewModel instance in X: $chatViewModel" }
        val tabs = listOf(
            stringResource(R.string.tab_chat),
            stringResource(R.string.tab_history),
            stringResource(R.string.tab_settings)
        )
        val tabIcons = listOf(
            Pair(R.drawable.ic_chat, R.drawable.ic_chat_filled),
            Pair(R.drawable.ic_log, R.drawable.ic_log_filled),
            Pair(R.drawable.ic_settings, R.drawable.ic_settings_filled),
        )
        var selectedTabIndex by remember { mutableIntStateOf(0) }
        var activeConversationId by remember { mutableStateOf<String?>(null) }
        var activeConversationTitle by remember { mutableStateOf("New Chat") }
        var showTabBar by remember { mutableStateOf(true) }

        Column(
            modifier = modifier
                .fillMaxSize()
        ) {
            // Content area
            Column(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> NewChatScreen( // Chat Tab
                        activeConversationIdFromTopLevel = activeConversationId,
                        setActiveConversationIdFromTopLevel = { activeConversationId = it },
                        activeConversationTitleFromTopLevel = activeConversationTitle,
                        setActiveConversationTitleFromTopLevel = { activeConversationTitle = it },
                        setShowTabBar = { showTabBar = it },
                        chatViewModel = chatViewModel,
                        settingsViewModel = settingsViewModel
                    )

                    1 -> HistoryScreen( // History Tab
                        onSelectConversationAndNavigate = { conversationId, conversationTitle ->
                            Timber.d { "History: Conversation $conversationId selected. Navigating to Chat." }
                            activeConversationId = conversationId
                            activeConversationTitle = conversationTitle
                            selectedTabIndex = 0
                        },
                        chatViewModel = chatViewModel,
                        historyViewModel = historyViewModel
                    )

                    2 -> SettingsScreen(chatViewModel = chatViewModel, settingsViewModel = settingsViewModel)
                }
            }

            // Tab bar at the bottom
            if (showTabBar) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color(0xFF134D8B),
                        contentColor = Color.White,
                        divider = {}
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