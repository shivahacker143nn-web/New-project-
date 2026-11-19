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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppLanguage
import com.example.data.model.AssistantMode
import com.example.data.model.ResponseLength
import com.example.ui.AishuViewModel
import com.example.ui.components.StatusPill
import com.example.ui.components.TerminalCard
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmberGlow
import com.example.ui.theme.NeonScarlet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AccountSettingsSection(
    viewModel: AishuViewModel,
    onLoggedOut: () -> Unit
) {
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    val isOnline by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    var displayNameInput by remember(user?.displayName) {
        mutableStateOf(user?.displayName.orEmpty())
    }
    var recoveryEmailInput by remember(user?.email) {
        mutableStateOf(user?.email.orEmpty())
    }
    var confirmLogout by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TerminalCard {
            Text(
                text = "USER ACCOUNT & CLOUD IDENTITY",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (user != null) {
                StatusPill(
                    text = "SIGNED IN • ${user?.providerId?.uppercase()}",
                    accentColor = CyberGreen
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Name: ${user?.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                if (!user?.email.isNullOrBlank()) {
                    Text(
                        text = "Email: ${user?.email}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                if (!user?.phoneNumber.isNullOrBlank()) {
                    Text(
                        text = "Phone: ${user?.phoneNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Text(
                    text = "UID: ${user?.uid}",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmberGlow
                )

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = displayNameInput,
                    onValueChange = { displayNameInput = it },
                    label = { Text("Update Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.updateUserDisplayName(displayNameInput) },
                        enabled = displayNameInput.isNotBlank() && isOnline,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                    ) {
                        Text("Save Name")
                    }
                    OutlinedButton(
                        onClick = { viewModel.syncCloudProfileAndRules() },
                        enabled = isOnline,
                        modifier = Modifier.weight(1f).height(48.dp),
                        border = BorderStroke(1.dp, ElectricCyan)
                    ) {
                        Text("Sync Cloud", color = ElectricCyan)
                    }
                }
            } else {
                StatusPill(text = "LOCAL SESSION", accentColor = AmberAlert)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You are using AI SHU in a local device session. Sign in via Google or Mobile OTP to sync your profile and permanent rules to Cloud Firestore.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        TerminalCard {
            Text(
                text = "ACCOUNT RECOVERY",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = recoveryEmailInput,
                onValueChange = { recoveryEmailInput = it },
                label = { Text("Account Recovery Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.sendPasswordResetEmail(recoveryEmailInput) },
                enabled = isOnline && recoveryEmailInput.contains("@"),
                modifier = Modifier.fillMaxWidth().height(46.dp),
                border = BorderStroke(1.dp, ElectricCyan)
            ) {
                Text("Send Password Reset Link", color = ElectricCyan)
            }
        }

        TerminalCard {
            Text(
                text = "SESSION ACTIONS",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { confirmLogout = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("logout_button"),
                border = BorderStroke(1.dp, NeonScarlet)
            ) {
                Text(
                    text = if (user != null) "Logout & Return to Login" else "Open Login Screen",
                    color = NeonScarlet
                )
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sign Out?") },
            text = { Text("You will return to the AI SHU login screen. Encrypted local API keys and offline memories stay stored safely on this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        viewModel.logout(onLoggedOut)
                    }
                ) { Text("Confirm Logout", color = NeonScarlet) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomSettingsSection(viewModel: AishuViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.wakeWordServiceRunning.collectAsStateWithLifecycle()

    var userNameInput by remember(settings.userName) {
        mutableStateOf(settings.userName)
    }
    var assistantNameInput by remember(settings.personalAssistantName) {
        mutableStateOf(settings.personalAssistantName)
    }
    var notesInput by remember(settings.personalizationNotes) {
        mutableStateOf(settings.personalizationNotes)
    }
    var wakeInput by remember(settings.wakeWord) { mutableStateOf(settings.wakeWord) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TerminalCard {
            Text(
                text = "PERSONAL IDENTITY & ASSISTANT NAME",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = userNameInput,
                onValueChange = { userNameInput = it },
                label = { Text("Your Name") },
                placeholder = { Text("What should AI SHU call you?") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_user_name_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = assistantNameInput,
                onValueChange = { assistantNameInput = it },
                label = { Text("AI Assistant Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_assistant_name_input")
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text("Response Length", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ResponseLength.entries.forEach { len ->
                    FilterChip(
                        selected = settings.responseLength == len,
                        onClick = { viewModel.updateCustomSettings(responseLength = len) },
                        label = { Text(len.displayName) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CrimsonContainer)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                label = { Text("Personalization Instructions (e.g. Call me Boss, prefer bullet steps)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    viewModel.updateCustomSettings(
                        userName = userNameInput,
                        assistantName = assistantNameInput,
                        personalizationNotes = notesInput
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_custom_settings_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
            ) {
                Text("Save Custom Preferences")
            }
        }

        TerminalCard {
            Text(
                text = "LANGUAGE PREFERENCES",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = settings.language == lang,
                        onClick = { viewModel.updateLanguage(lang) },
                        label = { Text("${lang.displayName} (${lang.speechLocaleTag})") },
                        modifier = Modifier.fillMaxWidth().testTag("lang_chip_${lang.name.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CrimsonContainer)
                    )
                }
            }
        }

        TerminalCard {
            Text(
                text = "WAKE WORD & HANDS-FREE MODE",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = settings.assistantMode == AssistantMode.MANUAL,
                    onClick = { viewModel.setAssistantModeAndWakeWord(AssistantMode.MANUAL, false) },
                    label = { Text("Manual Mode") },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CrimsonContainer)
                )
                FilterChip(
                    selected = settings.assistantMode == AssistantMode.AUTOMATIC,
                    onClick = { viewModel.setAssistantModeAndWakeWord(AssistantMode.AUTOMATIC, true) },
                    label = { Text("Automatic Mode") },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CrimsonContainer)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = wakeInput,
                onValueChange = { wakeInput = it },
                label = { Text("Custom Wake Word (e.g. Hey AISHU, Hey Siri)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("wake_word_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Wake-Word Sensitivity: ${settings.wakeWordSensitivity}/10", color = TextPrimary)
            Slider(
                value = settings.wakeWordSensitivity.toFloat(),
                onValueChange = { viewModel.updateWakeWordConfig(sensitivity = it.toInt()) },
                valueRange = 1f..10f,
                steps = 8,
                colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { viewModel.updateWakeWordConfig(wakeWord = wakeInput) },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("save_wake_word_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text("Save Wake Word")
                }
                if (serviceRunning) {
                    OutlinedButton(
                        onClick = { viewModel.stopWakeWordListeningImmediately() },
                        modifier = Modifier.height(48.dp),
                        border = BorderStroke(1.dp, NeonScarlet)
                    ) {
                        Text("Stop Auto Mic", color = NeonScarlet)
                    }
                }
            }
        }
    }
}
