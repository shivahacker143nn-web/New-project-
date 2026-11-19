package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.GeminiAiService
import com.example.data.model.ConnectionStatus
import com.example.ui.AishuViewModel
import com.example.ui.components.StatusPill
import com.example.ui.components.TerminalCard
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonScarlet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ApiSettingsSection(viewModel: AishuViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val isOnline by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val ttsReady by viewModel.ttsEngine.isReady.collectAsStateWithLifecycle()
    val hasEnglishTts by viewModel.ttsEngine.hasEnglishVoice.collectAsStateWithLifecycle()
    val hasTeluguTts by viewModel.ttsEngine.hasTeluguVoice.collectAsStateWithLifecycle()
    val firebaseConfig = remember { viewModel.getMaskedFirebaseConfig() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Live Gemini & Firebase Connection Center
        TerminalCard {
            Text(
                text = "API MANAGEMENT CENTER",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    text = if (isOnline) "NETWORK ONLINE" else "NETWORK OFFLINE",
                    accentColor = if (isOnline) CyberGreen else AmberAlert
                )
                when (val st = connectionStatus) {
                    ConnectionStatus.NotConfigured -> StatusPill("OFFLINE STANDBY", AmberAlert)
                    ConnectionStatus.Testing -> StatusPill("TESTING GEMINI...", ElectricCyan)
                    is ConnectionStatus.Connected -> StatusPill(
                        text = if (st.latencyMs > 0) "GEMINI READY • ${st.latencyMs}ms" else "GEMINI CONFIGURED",
                        accentColor = CyberGreen
                    )
                    is ConnectionStatus.Error -> StatusPill("CONNECTION ERROR", NeonScarlet)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (connectionStatus is ConnectionStatus.Testing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = ElectricCyan,
                        strokeWidth = 2.dp
                    )
                    Text("Verifying live Google Gemini connection via Firebase AI Logic...", color = ElectricCyan)
                }
            } else if (connectionStatus is ConnectionStatus.Error) {
                Text(
                    text = (connectionStatus as ConnectionStatus.Error).message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonScarlet
                )
            } else {
                Text(
                    text = "AI SHU uses Google Gemini through Firebase AI Logic and App Check as its single primary AI brain. No raw API keys are exposed inside source files or required for voice, speech recognition, or text-to-speech.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // 2. Google Gemini API & Google Search Grounding
        TerminalCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "1. GOOGLE GEMINI AI & SEARCH",
                    style = MaterialTheme.typography.labelLarge,
                    color = CrimsonPrimary,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(text = "PRIMARY BRAIN", accentColor = CrimsonPrimary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            ConfigKeyValueRow("Active AI Engine", "Google Gemini (Firebase AI Logic)")
            Spacer(modifier = Modifier.height(6.dp))
            ConfigKeyValueRow("Model", GeminiAiService.DEFAULT_MODEL)
            Spacer(modifier = Modifier.height(6.dp))
            ConfigKeyValueRow("Credentials", "Managed via Firebase App Check")

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Google Search Grounding",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Grounds Gemini responses in live Google Search results when online.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = settings.googleSearchEnabled,
                    onCheckedChange = { viewModel.updateGoogleSearchEnabled(it) },
                    modifier = Modifier.testTag("gemini_search_switch"),
                    colors = SwitchDefaults.colors(checkedTrackColor = CrimsonPrimary)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { viewModel.testApiConnection() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("test_api_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ElectricCyan)
            ) {
                Text("Test Gemini Connection", color = ElectricCyan)
            }
        }

        // 3. Existing Firebase Configuration (Masked Safely)
        TerminalCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "2. FIREBASE CONFIGURATION",
                    style = MaterialTheme.typography.labelLarge,
                    color = CrimsonPrimary,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    text = if (firebaseConfig.isConfigured) "CONFIGURED" else "MISSING",
                    accentColor = if (firebaseConfig.isConfigured) CyberGreen else NeonScarlet
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ConfigKeyValueRow("Project ID", firebaseConfig.projectId)
            Spacer(modifier = Modifier.height(6.dp))
            ConfigKeyValueRow("Firebase App ID", firebaseConfig.maskedAppId)
            Spacer(modifier = Modifier.height(6.dp))
            ConfigKeyValueRow("Firebase API Key", firebaseConfig.maskedApiKeyStatus)
            Spacer(modifier = Modifier.height(6.dp))
            ConfigKeyValueRow("Security", firebaseConfig.appCheckMode)
            Spacer(modifier = Modifier.height(6.dp))
            StatusRow(
                label = "Firebase Auth Session",
                ready = currentUser != null,
                readyText = "SIGNED IN",
                standbyText = "LOCAL / SKIPPED"
            )
        }

        // 4. Built-In Device Speech, TTS & Voice Status (Zero API Keys Required)
        TerminalCard {
            Text(
                text = "3. SPEECH, TTS & CUSTOM VOICE SERVICES",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Voice recognition, speech synthesis, and Custom Voice profiles run using Android's built-in engines and local storage—no paid third-party keys needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            StatusRow(
                label = "Speech Recognition (STT)",
                ready = viewModel.speechManager.isSpeechRecognitionAvailable(),
                readyText = "READY",
                standbyText = "UNAVAILABLE"
            )
            Spacer(modifier = Modifier.height(6.dp))
            StatusRow(
                label = "Device Text-to-Speech (TTS)",
                ready = ttsReady,
                readyText = "READY",
                standbyText = "INITIALIZING"
            )
            Spacer(modifier = Modifier.height(6.dp))
            StatusRow(
                label = "English / Telugu TTS Packs",
                ready = hasEnglishTts || hasTeluguTts,
                readyText = buildString {
                    append(if (hasEnglishTts) "EN READY" else "EN DEFAULT")
                    append(" • ")
                    append(if (hasTeluguTts) "TE READY" else "TE MISSING")
                },
                standbyText = "CHECKING"
            )
            Spacer(modifier = Modifier.height(6.dp))
            StatusRow(
                label = "Custom Voice Synthesis",
                ready = true,
                readyText = "ON-DEVICE",
                standbyText = "OFF"
            )
        }
    }
}

@Composable
private fun ConfigKeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    ready: Boolean,
    readyText: String = "ACTIVE",
    standbyText: String = "STANDBY"
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        StatusPill(
            text = if (ready) readyText else standbyText,
            accentColor = if (ready) CyberGreen else AmberAlert
        )
    }
}
