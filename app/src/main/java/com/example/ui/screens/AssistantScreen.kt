package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.ConversationMessageEntity
import com.example.data.model.AppLanguage
import com.example.data.model.AssistantMode
import com.example.data.model.ConnectionStatus
import com.example.data.model.VoiceSessionState
import com.example.ui.AishuViewModel
import com.example.ui.components.AishuCoreVisualizer
import com.example.ui.components.CyberGridBackground
import com.example.ui.components.StatusPill
import com.example.ui.components.VoiceActivityWaveform
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CardBorderDark
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.DeepBloodRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmberGlow
import com.example.ui.theme.NeonScarlet
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PanelElevated
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AssistantScreen(
    viewModel: AishuViewModel,
    onOpenSettings: () -> Unit,
    onOpenApiSettings: () -> Unit = onOpenSettings
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val activeEmotion by viewModel.activeEmotion.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val isEffectiveOffline by viewModel.isEffectiveOffline.collectAsStateWithLifecycle()
    val isHandsFreeActive by viewModel.isHandsFreeActive.collectAsStateWithLifecycle()
    val isMicMuted by viewModel.isMicMuted.collectAsStateWithLifecycle()
    val wakeServiceRunning by viewModel.wakeWordServiceRunning.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.speechManager.rmsDb.collectAsStateWithLifecycle()
    val partialText by viewModel.speechManager.partialText.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachmentPath.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    var showAttachmentDialog by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importPhotoFromPicker(uri, attachToChat = true)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        if (success && file != null) {
            viewModel.onCameraPhotoCaptured(file, attachToChat = true)
        }
        pendingCameraFile = null
    }

    val launchCameraCapture = {
        val file = viewModel.createCameraPhotoFile()
        pendingCameraFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            viewModel.setAuthNotice(null)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onAppBackgrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onAssistantScreenLeft()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    CyberGridBackground {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            val isCompactHeight = maxHeight < 580.dp
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // Top Bar: Dynamic Personal Assistant Name, language switcher, and Settings icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = settings.personalAssistantName.uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${settings.speakingStyle.displayName.uppercase()} • ${settings.language.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmberGlow,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = TerminalSurface,
                        border = BorderStroke(1.dp, DeepBloodRed),
                        modifier = Modifier
                            .testTag("language_cycle_button")
                            .clickable {
                                val next = when (settings.language) {
                                    AppLanguage.BILINGUAL -> AppLanguage.ENGLISH
                                    AppLanguage.ENGLISH -> AppLanguage.TELUGU
                                    AppLanguage.TELUGU -> AppLanguage.BILINGUAL
                                }
                                viewModel.updateLanguage(next)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Switch Language",
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = settings.language.shortCode,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open Settings",
                            tint = TextPrimary
                        )
                    }
                }
            }

            // Auto-detected Telemetry Status Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = if (isEffectiveOffline) "AUTO: OFFLINE" else "AUTO: ONLINE",
                    accentColor = if (isEffectiveOffline) AmberAlert else CyberGreen
                )

                val apiColor = when (connectionStatus) {
                    is ConnectionStatus.Connected -> CyberGreen
                    is ConnectionStatus.Error -> NeonScarlet
                    is ConnectionStatus.Testing -> ElectricCyan
                    ConnectionStatus.NotConfigured -> TextMuted
                }
                val apiLabel = if (isEffectiveOffline) {
                    "LOCAL INTELLIGENCE"
                } else {
                    when (val st = connectionStatus) {
                        is ConnectionStatus.Connected -> "GEMINI ONLINE"
                        is ConnectionStatus.Error -> "GEMINI ERROR"
                        is ConnectionStatus.Testing -> "TESTING..."
                        ConnectionStatus.NotConfigured -> "API SETTINGS"
                    }
                }
                StatusPill(
                    text = apiLabel,
                    accentColor = apiColor,
                    modifier = Modifier
                        .testTag("assistant_api_status_pill")
                        .clickable { onOpenApiSettings() }
                )

                if (settings.useCustomVoice) {
                    StatusPill(text = "CUSTOM VOICE", accentColor = ElectricCyan)
                }
            }

            Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 8.dp))

            // Central Animated Neural Logo + Voice Activity Waveform
            AishuCoreVisualizer(
                sessionState = sessionState,
                rmsLevel = rmsLevel,
                size = if (isCompactHeight) 76.dp else 128.dp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val stateLabel = when {
                    isMicMuted -> "MIC MUTED"
                    sessionState == VoiceSessionState.IDLE -> "STANDBY — READY"
                    sessionState == VoiceSessionState.WAKE_WORD_STANDBY -> "AUTO WAKE STANDBY (\"${settings.wakeWord}\")"
                    sessionState == VoiceSessionState.LISTENING -> "LISTENING..."
                    sessionState == VoiceSessionState.PROCESSING -> "PROCESSING..."
                    else -> "SPEAKING..."
                }
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        isMicMuted -> AmberAlert
                        sessionState == VoiceSessionState.IDLE -> TextSecondary
                        else -> CrimsonPrimary
                    }
                )
                StatusPill(
                    text = "${activeEmotion.badgeCode} • ${activeEmotion.displayName.uppercase()}",
                    accentColor = EmberGlow
                )
            }

            if (!isCompactHeight || sessionState != VoiceSessionState.IDLE) {
                VoiceActivityWaveform(
                    sessionState = sessionState,
                    rmsLevel = rmsLevel,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Live partial speech transcript
            AnimatedVisibility(visible = partialText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CrimsonContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "▸ $partialText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // Status / Diagnostic Banner
            AnimatedVisibility(visible = !statusBanner.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PanelElevated,
                    border = BorderStroke(1.dp, AmberAlert.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusBanner.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AmberAlert,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearStatusBanner() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss notice",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Conversation Transcript
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                shape = RoundedCornerShape(16.dp),
                color = ObsidianBg.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, CardBorderDark)
            ) {
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "CONVERSATION TRANSCRIPT EMPTY",
                                style = MaterialTheme.typography.labelLarge,
                                color = EmberGlow
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap Speak to Talk for continuous English or తెలుగు conversation, attach a photo for Gemini Vision, or type below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            TranscriptBubble(
                                assistantName = settings.personalAssistantName,
                                message = msg,
                                onSaveMemory = { viewModel.bookmarkMessageAsMemory(msg) }
                            )
                        }
                    }
                }
            }

            // Pending Vision Image Attachment Preview
            AnimatedVisibility(visible = pendingAttachment != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = TerminalSurface,
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .padding(top = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AsyncImage(
                            model = File(pendingAttachment.orEmpty()),
                            contentDescription = "Attached photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Text(
                            text = "Photo attached for Gemini Vision",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearPendingAttachment() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove attachment", tint = NeonScarlet)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Text Command + Photo Vision Attachment Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = TerminalSurface,
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.size(48.dp)
                ) {
                    IconButton(
                        onClick = { showAttachmentDialog = true },
                        modifier = Modifier.testTag("attach_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Attach Photo or Camera",
                            tint = ElectricCyan
                        )
                    }
                }

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            text = if (settings.language == AppLanguage.TELUGU) {
                                "తెలుగు లేదా English లో టైప్ చేయండి..."
                            } else {
                                "Message ${settings.personalAssistantName} (EN / తెలుగు)..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("assistant_text_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonPrimary,
                        unfocusedBorderColor = CardBorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = TerminalSurface,
                        unfocusedContainerColor = TerminalSurface
                    )
                )

                Surface(
                    shape = CircleShape,
                    color = if (textInput.isNotBlank() || pendingAttachment != null) CrimsonPrimary else PanelElevated,
                    modifier = Modifier.size(48.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank() || pendingAttachment != null) {
                                viewModel.sendTextCommand(textInput, speakAloud = true)
                                textInput = ""
                            }
                        },
                        modifier = Modifier.testTag("send_text_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Command",
                            tint = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Primary Voice Controls: Replace Speak with single Stop button while Automatic Mode is active
            val isAutomaticSessionActive = settings.assistantMode == AssistantMode.AUTOMATIC &&
                (isHandsFreeActive ||
                    sessionState == VoiceSessionState.LISTENING ||
                    sessionState == VoiceSessionState.PROCESSING ||
                    sessionState == VoiceSessionState.SPEAKING)
            val isManualSessionActive = settings.assistantMode == AssistantMode.MANUAL &&
                (sessionState == VoiceSessionState.LISTENING ||
                    sessionState == VoiceSessionState.PROCESSING ||
                    sessionState == VoiceSessionState.SPEAKING)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isAutomaticSessionActive) {
                    Button(
                        onClick = {
                            viewModel.stopAllVoiceActivity()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("stop_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonScarlet,
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Automatic Conversation"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "STOP CONVERSATION",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (isMicMuted || sessionState == VoiceSessionState.IDLE || sessionState == VoiceSessionState.WAKE_WORD_STANDBY) {
                                viewModel.startVoiceSession()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("speak_to_talk_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isManualSessionActive) DeepBloodRed else CrimsonPrimary,
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Speak to Talk"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isMicMuted -> "RESUME MIC"
                                isManualSessionActive -> "LISTENING / SPEAKING"
                                else -> "SPEAK TO TALK"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.toggleMuteMic() },
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("mute_mic_button"),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.5.dp, if (isMicMuted) AmberAlert else CardBorderDark),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isMicMuted) CrimsonContainer else TerminalSurface,
                            contentColor = if (isMicMuted) AmberAlert else TextPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMicMuted) "Unmute Microphone" else "Mute Microphone"
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.stopAllVoiceActivity()
                        },
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("stop_button"),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.5.dp, NeonScarlet),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = TerminalSurface,
                            contentColor = NeonScarlet
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Assistant"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "STOP",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Mode Selector directly below the Speak button (Requirement #2)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("mode_selector_row"),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = settings.assistantMode == AssistantMode.MANUAL,
                    onClick = { viewModel.setAssistantModeAndWakeWord(AssistantMode.MANUAL, false) },
                    label = { Text("Manual Mode") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_mode_chip"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CrimsonContainer,
                        selectedLabelColor = TextPrimary
                    )
                )
                FilterChip(
                    selected = settings.assistantMode == AssistantMode.AUTOMATIC,
                    onClick = { viewModel.setAssistantModeAndWakeWord(AssistantMode.AUTOMATIC, true) },
                    label = {
                        Text(
                            if (wakeServiceRunning) "Auto (${settings.wakeWord})" else "Automatic Mode",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("automatic_mode_chip"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CrimsonContainer,
                        selectedLabelColor = ElectricCyan
                    )
                )
            }
            }
        }
    }

    if (showAttachmentDialog) {
        AlertDialog(
            onDismissRequest = { showAttachmentDialog = false },
            title = { Text("Attach Photo for AI Vision") },
            text = {
                Text("Choose a photo with the zero-permission Android Photo Picker or capture one with your camera.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAttachmentDialog = false
                        val camGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (camGranted) {
                            launchCameraCapture()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = CrimsonPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Camera", color = CrimsonPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAttachmentDialog = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = ElectricCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery", color = ElectricCyan)
                }
            }
        )
    }
}

@Composable
private fun TranscriptBubble(
    assistantName: String,
    message: ConversationMessageEntity,
    onSaveMemory: () -> Unit
) {
    val isUser = message.role == "USER"
    val timeText = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 14.dp
            ),
            color = if (isUser) CrimsonContainer else TerminalSurface,
            border = BorderStroke(
                1.dp,
                if (isUser) CrimsonPrimary.copy(alpha = 0.6f) else CardBorderDark
            ),
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) {
                            "YOU • $timeText"
                        } else {
                            "${assistantName.uppercase()} • ${message.emotionTag} • $timeText"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) EmberGlow else NeonScarlet
                    )
                    if (!isUser) {
                        IconButton(
                            onClick = onSaveMemory,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = "Save to Memory",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                if (!message.attachedImagePath.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AsyncImage(
                        model = File(message.attachedImagePath),
                        contentDescription = "User attached photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                if (!isUser) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message.providerLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.isOffline) AmberAlert else CyberGreen
                    )
                }
            }
        }
    }
}
