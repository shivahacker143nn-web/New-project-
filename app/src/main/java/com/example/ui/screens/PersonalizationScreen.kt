package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.example.ui.AishuViewModel
import com.example.ui.components.CyberGridBackground
import com.example.ui.components.TerminalCard
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.EmberGlow
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PersonalizationScreen(
    viewModel: AishuViewModel,
    onComplete: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var userNameInput by remember(settings.userName, currentUser?.displayName) {
        mutableStateOf(
            settings.userName.ifBlank {
                currentUser?.displayName?.takeIf { !it.startsWith("+") }.orEmpty()
            }
        )
    }
    var assistantNameInput by remember(settings.personalAssistantName) {
        mutableStateOf(settings.personalAssistantName.ifBlank { "AI SHU" })
    }

    CyberGridBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "STEP 4 OF 4 • PERSONALIZATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmberGlow
                )
                Text(
                    text = "PERSONALIZE YOUR ASSISTANT",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CrimsonPrimary
                )
                Text(
                    text = "Tell AI SHU what to call you and what name your assistant should answer to. Both are remembered in encrypted memory and cloud sync.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                TerminalCard(borderColor = CrimsonPrimary.copy(alpha = 0.55f)) {
                    Text(
                        text = "What should I call your AI assistant?",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = assistantNameInput,
                        onValueChange = { assistantNameInput = it },
                        label = { Text("AI Assistant Name") },
                        placeholder = { Text("AI SHU") },
                        leadingIcon = {
                            Icon(Icons.Default.SmartToy, contentDescription = null, tint = CrimsonPrimary)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("personalize_assistant_name_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "What is your name?",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = userNameInput,
                        onValueChange = { userNameInput = it },
                        label = { Text("Your Name") },
                        placeholder = { Text("e.g. Kiran, Priya, Alex") },
                        leadingIcon = {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = CrimsonPrimary)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("personalize_user_name_input")
                    )
                }

                TerminalCard {
                    Text(
                        text = "You can update these names, preferred language (English / తెలుగు), speaking style, custom cloned voice, and up to 30 permanent rules anytime in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CyberGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.saveOnboardingPersonalization(
                        userName = userNameInput,
                        assistantName = assistantNameInput
                    )
                    onComplete()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(54.dp)
                    .testTag("personalization_finish_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonPrimary,
                    contentColor = TextPrimary
                )
            ) {
                Text("LAUNCH ${assistantNameInput.trim().ifEmpty { "AI SHU" }.uppercase()}", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.size(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Launch Assistant")
            }
        }
    }
}
