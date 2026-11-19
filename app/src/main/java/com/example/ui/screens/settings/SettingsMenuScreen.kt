package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ConnectionStatus
import com.example.data.model.VoiceOption
import com.example.ui.AishuViewModel
import com.example.ui.components.CyberGridBackground
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CardBorderDark
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.EmberGlow
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class SettingsSection(
    val id: String,
    val title: String,
    val icon: ImageVector
) {
    API_SETTINGS("api_settings", "API Settings", Icons.Default.Key),
    VOICE("voice", "Voice Settings", Icons.Default.RecordVoiceOver),
    ACCOUNT("account", "Account", Icons.Default.AccountCircle),
    RULES("rules", "Rules", Icons.Default.Gavel),
    CUSTOM("custom", "Custom Settings", Icons.Default.Tune),
    OFFLINE_MODE("offline_mode", "Offline Mode", Icons.Default.CloudOff),
    MEMORY("memory", "Memory", Icons.Default.Memory),
    EMOTIONS_STYLE("emotions_style", "Emotions and Speaking Style", Icons.Default.Mood),
    PHOTOS_CAMERA("photos_camera", "Photos and Camera", Icons.Default.PhotoCamera),
    PERMISSIONS("permissions", "Permissions", Icons.Default.Security),
    PRIVACY_SECURITY("privacy_security", "Privacy and Security", Icons.Default.Lock),
    ABOUT("about", "About AI SHU", Icons.Default.Info)
}

@Composable
fun SettingsMenuScreen(
    viewModel: AishuViewModel,
    onBack: () -> Unit,
    onSelectSection: (SettingsSection) -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val customVoices by viewModel.customVoices.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val ruleConflicts by viewModel.ruleConflicts.collectAsStateWithLifecycle()
    val savedPhotos by viewModel.savedPhotos.collectAsStateWithLifecycle()
    val isEffectiveOffline by viewModel.isEffectiveOffline.collectAsStateWithLifecycle()

    val sections = SettingsSection.entries

    CyberGridBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Assistant",
                            tint = TextPrimary
                        )
                    }
                    Column {
                        Text(
                            text = "SETTINGS",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${settings.personalAssistantName.uppercase()} • 12 MODULES",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmberGlow
                        )
                    }
                }

                StatusPill(
                    text = if (isEffectiveOffline) "AUTO: OFFLINE" else "AUTO: ONLINE",
                    accentColor = if (isEffectiveOffline) AmberAlert else CyberGreen
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(sections, key = { _, s -> s.id }) { index, section ->
                    val subtitle = when (section) {
                        SettingsSection.API_SETTINGS -> when (val cs = connectionStatus) {
                            is ConnectionStatus.Connected -> "Gemini AI (${cs.model}) • Firebase App Check"
                            is ConnectionStatus.Error -> "Gemini Error: ${cs.message.take(40)}"
                            ConnectionStatus.Testing -> "Testing Gemini AI connection..."
                            ConnectionStatus.NotConfigured -> "Gemini AI + Firebase Config • Tap to verify"
                        }

                        SettingsSection.ACCOUNT -> currentUser?.let {
                            "Signed in as ${it.displayName}"
                        } ?: "Local Session • Login, Recovery & Sync"

                        SettingsSection.RULES -> {
                            val conflictNote = if (ruleConflicts.isNotEmpty()) " • ${ruleConflicts.size} conflict(s)" else ""
                            "${rules.size}/30 rules (${rules.count { it.isEnabled }} enabled)$conflictNote"
                        }

                        SettingsSection.VOICE -> {
                            val v = VoiceOption.findById(settings.selectedVoiceId)
                            val activeVoice = if (settings.useCustomVoice) "Custom Voice Priority" else v.displayName
                            "$activeVoice • ${customVoices.size} profile(s) • ${settings.language.shortCode}"
                        }

                        SettingsSection.CUSTOM -> {
                            val userLabel = settings.userName.ifBlank { "Set your name" }
                            "$userLabel → ${settings.personalAssistantName} • Wake: ${settings.wakeWord}"
                        }

                        SettingsSection.OFFLINE_MODE -> {
                            val installedCount = viewModel.modelCatalog.count { viewModel.isLocalModelDownloaded(it) }
                            val statusText = if (isEffectiveOffline) "Offline Active (Auto)" else "Auto-Standby When Online"
                            "$statusText • $installedCount local model(s)"
                        }

                        SettingsSection.MEMORY -> "${memories.size} saved memories • ${messages.size} chat messages"

                        SettingsSection.EMOTIONS_STYLE -> "${settings.emotionStyle.displayName} • ${settings.speakingStyle.displayName}"

                        SettingsSection.PHOTOS_CAMERA -> "${savedPhotos.size} photo(s) • Vision Q&A, OCR & Objects"

                        SettingsSection.PERMISSIONS -> if (viewModel.speechManager.hasMicrophonePermission()) {
                            "Microphone Granted • Tap to manage"
                        } else {
                            "Microphone Needed • Optional controls"
                        }

                        SettingsSection.PRIVACY_SECURITY -> if (viewModel.isHardwareKeystoreActive()) {
                            "Android Keystore AES-256 Active"
                        } else {
                            "Encrypted Local Vault & Data Wipe"
                        }

                        SettingsSection.ABOUT -> "AI SHU v2.0 • Architecture & Policies"
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_item_${section.id}")
                            .clickable { onSelectSection(section) },
                        shape = RoundedCornerShape(14.dp),
                        color = TerminalSurface,
                        border = BorderStroke(1.dp, CardBorderDark)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = null,
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${index + 1}. ${section.title}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Open ${section.title}",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
