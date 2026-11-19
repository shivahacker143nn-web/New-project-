package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.AishuViewModel
import com.example.ui.components.CyberGridBackground
import com.example.ui.theme.EmberGlow
import com.example.ui.theme.TextPrimary

@Composable
fun SettingsDetailScreen(
    section: SettingsSection,
    viewModel: AishuViewModel,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit
) {
    CyberGridBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("settings_detail_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Settings",
                        tint = TextPrimary
                    )
                }
                Column {
                    Text(
                        text = section.title.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "AI SHU CONFIGURATION MODULE",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmberGlow
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                when (section) {
                    SettingsSection.ACCOUNT -> AccountSettingsSection(viewModel, onLoggedOut)
                    SettingsSection.API_SETTINGS -> ApiSettingsSection(viewModel)
                    SettingsSection.RULES -> RulesSettingsSection(viewModel)
                    SettingsSection.VOICE -> {
                        val voiceViewModel: com.example.ui.VoiceSettingsViewModel =
                            androidx.lifecycle.viewmodel.compose.viewModel()
                        VoiceSettingsSection(voiceViewModel = voiceViewModel)
                    }
                    SettingsSection.CUSTOM -> CustomSettingsSection(viewModel)
                    SettingsSection.OFFLINE_MODE -> OfflineModeSection(viewModel)
                    SettingsSection.MEMORY -> MemorySettingsSection(viewModel)
                    SettingsSection.EMOTIONS_STYLE -> EmotionsAndStyleSection(viewModel)
                    SettingsSection.PHOTOS_CAMERA -> PhotosAndCameraSection(viewModel)
                    SettingsSection.PERMISSIONS -> PermissionsSettingsSection(viewModel)
                    SettingsSection.PRIVACY_SECURITY -> PrivacySecuritySettingsSection(viewModel)
                    SettingsSection.ABOUT -> AboutAishuSection(viewModel)
                }
            }
        }
    }
}
