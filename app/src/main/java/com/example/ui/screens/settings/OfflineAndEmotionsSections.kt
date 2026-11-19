package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.OfflineModelCatalogItem
import com.example.data.model.EmotionStyle
import com.example.data.model.SpeakingStyle
import com.example.ui.AishuViewModel
import com.example.ui.components.StatusPill
import com.example.ui.components.TerminalCard
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CardBorderDark
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmberGlow
import com.example.ui.theme.NeonScarlet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun OfflineModeSection(viewModel: AishuViewModel) {
    val isOnline by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    val downloadState by viewModel.modelDownloadState.collectAsStateWithLifecycle()
    var pendingDownloadItem by remember { mutableStateOf<OfflineModelCatalogItem?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TerminalCard(borderColor = if (isOnline) CyberGreen.copy(alpha = 0.5f) else AmberAlert.copy(alpha = 0.7f)) {
            Text(
                text = "AUTOMATIC OFFLINE INTELLIGENCE ENGINE",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            StatusPill(
                text = if (isOnline) "ONLINE DETECTED • AUTO SWITCH READY" else "OFFLINE ACTIVE (AUTO-DETECTED)",
                accentColor = if (isOnline) CyberGreen else AmberAlert
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AI SHU detects internet connectivity automatically with zero manual Online/Offline switches. When offline, replies use local Room memories, conversation history, time/date, calculations, device TTS/STT, and offline model storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        TerminalCard {
            Text(
                text = "HONEST OFFLINE BOUNDARIES",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "• Available Offline: Saved personal memories, recent conversation recall, basic math, local clock/date, and device speech recognition & calibrated TTS.\n• Unavailable Offline: Live Google Search, cloud Gemini intelligence, and Gemini Vision image analysis — AI SHU will clearly tell you instead of guessing.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        TerminalCard(borderColor = ElectricCyan.copy(alpha = 0.45f)) {
            Text(
                text = "DOWNLOADABLE OFFLINE MODELS & STORAGE",
                style = MaterialTheme.typography.labelLarge,
                color = ElectricCyan
            )
            Spacer(modifier = Modifier.height(6.dp))
            val freeMb = viewModel.localModelManager.getAvailableStorageBytes() / (1024 * 1024)
            StatusPill("FREE STORAGE: $freeMb MB", CyberGreen)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = downloadState.errorMessage ?: downloadState.statusMessage,
                style = MaterialTheme.typography.labelSmall,
                color = if (downloadState.errorMessage != null) NeonScarlet else EmberGlow
            )
            if (downloadState.activeModelId != null) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { downloadState.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = ElectricCyan
                )
                TextButton(onClick = { viewModel.cancelLocalModelDownload() }) {
                    Text("Cancel Download", color = NeonScarlet)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                viewModel.modelCatalog.forEach { item ->
                    val installed = viewModel.isLocalModelDownloaded(item)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (installed) CyberGreen.copy(alpha = 0.5f) else CardBorderDark)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(
                                    text = "${item.sizeLabel} • Needs ${item.minRamLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            if (installed) {
                                OutlinedButton(
                                    onClick = { viewModel.deleteLocalModel(item) },
                                    border = BorderStroke(1.dp, NeonScarlet)
                                ) {
                                    Text("Delete", color = NeonScarlet)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { pendingDownloadItem = item },
                                    enabled = isOnline && downloadState.activeModelId == null,
                                    border = BorderStroke(1.dp, ElectricCyan)
                                ) {
                                    Text("Download", color = ElectricCyan)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDownloadItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDownloadItem = null },
            title = { Text("Confirm Offline Model Download") },
            text = {
                Text("Download ${item.name} (${item.sizeLabel})? Requires ${item.minRamLabel} and free disk space. Wi-Fi is recommended.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.downloadLocalModelWithConsent(item)
                        pendingDownloadItem = null
                    }
                ) { Text("Download Now", color = ElectricCyan) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDownloadItem = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmotionsAndStyleSection(viewModel: AishuViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TerminalCard {
            Text(
                text = "EMOTIONS & CONVERSATIONAL TONE",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Select how AI SHU shapes its wording and emotional responsiveness (Happy, Sad/Gentle, Calm, Excited, Serious, Friendly, Empathetic, and more). AI SHU adapts tone thoughtfully without ever claiming real biological feelings.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EmotionStyle.entries.forEach { style ->
                    FilterChip(
                        selected = settings.emotionStyle == style,
                        onClick = { viewModel.updateEmotionStyle(style) },
                        label = { Text("${style.displayName} (${style.badgeCode})") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CrimsonContainer),
                        modifier = Modifier.testTag("emotion_chip_${style.name.lowercase()}")
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text("Context-Adaptive Emotion", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("Automatically shifts tone smoothly based on your message context.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Switch(
                    checked = settings.autoAdaptEmotion,
                    onCheckedChange = { viewModel.toggleAutoAdaptEmotion(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = CrimsonPrimary)
                )
            }
        }

        TerminalCard {
            Text(
                text = "SPEAKING STYLE ENGINE (7 STYLES)",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Choose how replies sound when spoken aloud. If Custom Voice is active, your replicated custom voice takes priority over generic voice styles.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpeakingStyle.entries.forEach { style ->
                    FilterChip(
                        selected = settings.speakingStyle == style,
                        onClick = { viewModel.updateVoiceTuning(speakingStyle = style) },
                        label = { Text(style.displayName) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CrimsonContainer),
                        modifier = Modifier.testTag("speaking_style_chip_${style.name.lowercase()}")
                    )
                }
            }
        }
    }
}
