package com.example.ui.screens.settings

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CustomVoiceProfileEntity
import com.example.data.model.AppLanguage
import com.example.data.model.SpeakingStyle
import com.example.data.model.VoiceOption
import com.example.ui.RecordingPhase
import com.example.ui.VoiceSettingsViewModel
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
import com.example.ui.theme.PanelElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceSettingsSection(voiceViewModel: VoiceSettingsViewModel) {
    val context = LocalContext.current
    val settings by voiceViewModel.settings.collectAsStateWithLifecycle()
    val ttsReady by voiceViewModel.ttsReady.collectAsStateWithLifecycle()
    val ttsSpeaking by voiceViewModel.ttsSpeaking.collectAsStateWithLifecycle()
    val hasEnglish by voiceViewModel.hasEnglishVoice.collectAsStateWithLifecycle()
    val hasTelugu by voiceViewModel.hasTeluguVoice.collectAsStateWithLifecycle()
    val voiceCount by voiceViewModel.engineVoiceCount.collectAsStateWithLifecycle()
    val activeSynthesisStatus by voiceViewModel.activeSynthesisStatus.collectAsStateWithLifecycle()

    val profiles by voiceViewModel.customVoices.collectAsStateWithLifecycle()
    val stagedDraft by voiceViewModel.stagedDraft.collectAsStateWithLifecycle()
    val replacingProfileId by voiceViewModel.replacingProfileId.collectAsStateWithLifecycle()
    val recordingProgress by voiceViewModel.recordingProgress.collectAsStateWithLifecycle()
    val playingPath by voiceViewModel.playingFilePath.collectAsStateWithLifecycle()
    val statusMessage by voiceViewModel.statusMessage.collectAsStateWithLifecycle()

    val isRecording = recordingProgress.isRecording
    val isAnalyzingAudio = recordingProgress.phase == RecordingPhase.ANALYZING
    val durationMs = recordingProgress.elapsedMs
    val amplitude = recordingProgress.amplitude
    val waveformSamples = recordingProgress.waveformSamples

    var newProfileName by remember { mutableStateOf("My Personal Voice") }
    var consentConfirmed by remember { mutableStateOf(false) }
    var testPhrase by remember { mutableStateOf("") }
    var renamingProfile by remember { mutableStateOf<CustomVoiceProfileEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var tuningProfile by remember { mutableStateOf<CustomVoiceProfileEntity?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceViewModel.startRecording(newProfileName)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            voiceViewModel.importAudioUri(uri, newProfileName)
        }
    }

    val activeCustomProfile = remember(profiles, settings.useCustomVoice) {
        if (settings.useCustomVoice) {
            profiles.firstOrNull { it.isSelected } ?: profiles.firstOrNull()
        } else null
    }
    val replacingTarget = remember(profiles, replacingProfileId) {
        replacingProfileId?.let { id -> profiles.firstOrNull { it.id == id } }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AnimatedVisibility(visible = !statusMessage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PanelElevated,
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { voiceViewModel.clearStatusMessage() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss notice", tint = TextSecondary)
                    }
                }
            }
        }

        // 1. Built-In TTS Engine Status & Interactive Synthesis Tester
        TerminalCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TEXT-TO-SPEECH & VOICE ENGINE",
                    style = MaterialTheme.typography.labelLarge,
                    color = CrimsonPrimary
                )
                if (ttsSpeaking) {
                    TextButton(onClick = { voiceViewModel.stopSynthesis() }) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop TTS", tint = NeonScarlet, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("STOP AUDIO", color = NeonScarlet)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill(
                    text = if (ttsReady) "TTS ENGINE ONLINE" else "TTS INITIALIZING",
                    accentColor = if (ttsReady) CyberGreen else AmberAlert
                )
                StatusPill(
                    text = if (hasEnglish) "ENGLISH READY" else "ENGLISH FALLBACK",
                    accentColor = if (hasEnglish) CyberGreen else AmberAlert
                )
                StatusPill(
                    text = if (hasTelugu) "TELUGU READY" else "TELUGU MISSING",
                    accentColor = if (hasTelugu) CyberGreen else AmberAlert
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(activeSynthesisStatus, style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
            if (settings.useCustomVoice) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        text = "ACTIVE: ${activeCustomProfile?.name?.uppercase() ?: "CUSTOM VOICE"}",
                        accentColor = ElectricCyan
                    )
                    TextButton(onClick = { voiceViewModel.disableCustomVoicePriority() }) {
                        Text("Use Built-In Voice", color = AmberAlert)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = testPhrase,
                onValueChange = { testPhrase = it },
                label = { Text("Test TTS Synthesis Phrase (Optional)") },
                placeholder = { Text("Enter English or తెలుగు sentence to synthesize...", color = TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("tts_test_phrase_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (ttsSpeaking) {
                        voiceViewModel.stopSynthesis()
                    } else if (activeCustomProfile != null) {
                        voiceViewModel.synthesizeCustomProfileSample(activeCustomProfile, testPhrase)
                    } else {
                        voiceViewModel.previewBuiltInVoice(VoiceOption.findById(settings.selectedVoiceId), testPhrase)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("synthesize_test_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (ttsSpeaking) NeonScarlet else CrimsonPrimary
                )
            ) {
                Icon(
                    imageVector = if (ttsSpeaking) Icons.Default.Stop else Icons.Default.RecordVoiceOver,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    when {
                        ttsSpeaking -> "Stop Synthesized Speech"
                        activeCustomProfile != null -> "Synthesize with ${activeCustomProfile.name}"
                        else -> "Synthesize with Selected Voice"
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Language Selection", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = settings.language == lang,
                        onClick = { voiceViewModel.updateLanguage(lang) },
                        label = { Text(lang.displayName) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CrimsonContainer)
                    )
                }
            }
        }

        // 2. Purely On-Device Custom Voice Studio
        TerminalCard(borderColor = ElectricCyan.copy(alpha = 0.55f)) {
            Text(
                text = "CUSTOM VOICE STUDIO (ON-DEVICE)",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            StatusPill(
                text = "ON-DEVICE ACOUSTIC CALIBRATION • NO API KEY REQUIRED",
                accentColor = CyberGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Record your voice or import an audio file (MP3, WAV, M4A, OGG, max 10 MB). AI SHU extracts vocal pitch (~Hz), warmth, cadence, and timbre strictly on your device to calibrate Android Text-to-Speech. Recordings never leave your phone; true zero-shot neural waveform replication would require bundling an offline neural TTS model.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            if (replacingTarget != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PanelElevated,
                    border = BorderStroke(1.dp, ElectricCyan)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Replacing sample for \"${replacingTarget.name}\" — record or import a new audio file.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { voiceViewModel.cancelReplacingProfile() },
                            modifier = Modifier.testTag("cancel_replace_voice_button")
                        ) {
                            Text("Cancel", color = NeonScarlet)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = newProfileName,
                onValueChange = { newProfileName = it },
                label = { Text("Custom Voice Profile Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("custom_voice_name_input")
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { consentConfirmed = !consentConfirmed },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = consentConfirmed,
                    onCheckedChange = { consentConfirmed = it },
                    colors = CheckboxDefaults.colors(checkedColor = CrimsonPrimary),
                    modifier = Modifier.testTag("voice_consent_checkbox")
                )
                Text(
                    text = "I confirm this voice belongs to me or I have explicit authorization to calibrate and use it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }

            if (isRecording) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECORDING: ${(durationMs / 1000f).let { "%.1f".format(it) }}s / ${(recordingProgress.maxDurationMs / 1000)}s",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonScarlet
                    )
                    TextButton(onClick = { voiceViewModel.cancelActiveRecording() }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
                recordingProgress.activeFilePath?.let { path ->
                    Text(
                        text = "Writing local file: ${File(path).name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                RecordingWaveformBar(samples = waveformSamples)
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { recordingProgress.progressFraction.coerceIn(0.02f, 1f) },
                    modifier = Modifier.fillMaxWidth().testTag("recording_progress_bar"),
                    color = CrimsonPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { amplitude.coerceIn(0.05f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = ElectricCyan
                )
            }

            if (isAnalyzingAudio) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ElectricCyan, strokeWidth = 2.dp)
                    Text("Extracting vocal pitch, warmth, and cadence characteristics...", color = ElectricCyan)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (isRecording) {
                            voiceViewModel.stopAndStageRecording()
                        } else if (voiceViewModel.hasMicrophonePermission()) {
                            voiceViewModel.startRecording(newProfileName)
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("record_custom_voice_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) NeonScarlet else CrimsonPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(if (isRecording) "Stop Recording" else "Record My Voice")
                }

                OutlinedButton(
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("upload_custom_voice_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Import Audio")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Local Vault: ${recordingProgress.storedSampleCount} file(s) • ${(recordingProgress.totalLocalStorageBytes / 1024L)} KB",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                TextButton(onClick = { voiceViewModel.pruneOrphanedLocalSamples() }) {
                    Text("Clean Unused Files", color = ElectricCyan)
                }
            }

            stagedDraft?.let { draft ->
                Spacer(modifier = Modifier.height(12.dp))
                StagedVoiceDraftCard(
                    draft = draft,
                    isPlayingDraft = playingPath == draft.filePath,
                    replacingTargetName = replacingTarget?.name,
                    onDiscard = { voiceViewModel.discardStagedVoice() },
                    onUpdateCharacteristics = { pitch, warmth, expressiveness, genderHint ->
                        voiceViewModel.updateStagedCharacteristics(
                            pitchMultiplier = pitch,
                            warmth = warmth,
                            expressiveness = expressiveness,
                            genderHint = genderHint
                        )
                    },
                    onTogglePreviewRaw = { voiceViewModel.togglePreviewStagedRawAudio() },
                    onTestTts = { voiceViewModel.previewStagedSynthesizedSpeech(testPhrase) },
                    onCommit = {
                        val effectiveConsent = consentConfirmed || (replacingTarget?.consentConfirmed == true)
                        voiceViewModel.commitStagedVoice(newProfileName, effectiveConsent)
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "SAVED CUSTOM VOICE PROFILES (${profiles.size})",
                style = MaterialTheme.typography.labelLarge,
                color = EmberGlow
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (profiles.isEmpty()) {
                Text(
                    text = "No custom voice profiles saved yet. Record or import an audio sample above, inspect/tune its characteristics, and save.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    profiles.forEach { profile ->
                        SavedCustomVoiceProfileCard(
                            profile = profile,
                            isActiveAssistant = settings.useCustomVoice && profile.isSelected,
                            isPlaying = playingPath == profile.filePath,
                            localFileReady = voiceViewModel.verifyLocalSampleExists(profile),
                            isBeingReplaced = replacingProfileId == profile.id,
                            onSynthesizeTts = { voiceViewModel.synthesizeCustomProfileSample(profile, testPhrase) },
                            onToggleRawPlayback = { voiceViewModel.toggleRawSamplePlayback(profile) },
                            onTune = { tuningProfile = profile },
                            onRename = {
                                renamingProfile = profile
                                renameInput = profile.name
                            },
                            onReplaceSample = { voiceViewModel.beginReplacingProfile(profile) },
                            onSelectAndPrioritize = { voiceViewModel.selectCustomVoiceProfile(profile.id) },
                            onDelete = { voiceViewModel.deleteCustomVoice(profile) }
                        )
                    }
                }
            }
        }

        // 3. Global Voice Tuning & 7 Speaking Styles
        TerminalCard {
            Text(
                text = "GLOBAL SPEED, PITCH, VOLUME & 7 SPEAKING STYLES",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text("Voice Speed: ${"%.2f".format(settings.speechSpeed)}x", color = TextPrimary)
            Slider(
                value = settings.speechSpeed,
                onValueChange = { voiceViewModel.updateGlobalVoiceTuning(speed = it) },
                valueRange = 0.6f..1.8f,
                colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary)
            )

            Text("Voice Pitch: ${"%.2f".format(settings.speechPitch)}x", color = TextPrimary)
            Slider(
                value = settings.speechPitch,
                onValueChange = { voiceViewModel.updateGlobalVoiceTuning(pitch = it) },
                valueRange = 0.6f..1.6f,
                colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary)
            )

            Text("Voice Volume: ${(settings.speechVolume * 100).toInt()}%", color = TextPrimary)
            Slider(
                value = settings.speechVolume,
                onValueChange = { voiceViewModel.updateGlobalVoiceTuning(volume = it) },
                valueRange = 0.1f..1.0f,
                colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary)
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text("Speaking Style (Custom Voice takes top priority when selected)", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SpeakingStyle.entries.forEach { style ->
                    FilterChip(
                        selected = settings.speakingStyle == style,
                        onClick = { voiceViewModel.updateGlobalVoiceTuning(speakingStyle = style) },
                        label = { Text(style.displayName) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CrimsonContainer)
                    )
                }
            }
        }

        // 4. Built-in Assistant Voices
        TerminalCard {
            Text(
                text = "BUILT-IN ASSISTANT VOICES (10 PROFILES)",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceOption.ALL_VOICES.forEach { option ->
                    val selected = !settings.useCustomVoice && settings.selectedVoiceId == option.id
                    val engineSummary = remember(option.id, settings.language, voiceCount) {
                        voiceViewModel.ttsEngine.getVoiceAvailabilitySummary(option, settings.language)
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_option_${option.id}")
                            .clickable { voiceViewModel.selectBuiltInVoice(option.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) CrimsonContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selected) CrimsonPrimary else CardBorderDark)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { voiceViewModel.selectBuiltInVoice(option.id) },
                                colors = RadioButtonDefaults.colors(selectedColor = CrimsonPrimary)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${option.displayName} — ${option.subtitle}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = engineSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            IconButton(
                                onClick = { voiceViewModel.previewBuiltInVoice(option, testPhrase) },
                                modifier = Modifier.size(48.dp).testTag("preview_voice_${option.id}")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Preview Voice", tint = NeonScarlet)
                            }
                        }
                    }
                }
            }
        }

        // 5. Required Voice Rules Card at the Bottom of Voice Settings
        VoiceRulesCard()
    }

    tuningProfile?.let { target ->
        VoiceProfileCharacteristicsDialog(
            profile = target,
            onDismiss = { tuningProfile = null },
            onTestSynthesis = { previewProfile ->
                voiceViewModel.synthesizeCustomProfileSample(previewProfile, testPhrase)
            },
            onSaveCharacteristics = { pitch, rate, warmth, expressiveness, similarityBoost, genderHint ->
                voiceViewModel.updateSavedProfileCharacteristics(
                    profileId = target.id,
                    pitchMultiplier = pitch,
                    rateMultiplier = rate,
                    warmth = warmth,
                    expressiveness = expressiveness,
                    similarityBoost = similarityBoost,
                    baseGenderHint = genderHint
                )
                tuningProfile = null
            }
        )
    }

    renamingProfile?.let { target ->
        AlertDialog(
            onDismissRequest = { renamingProfile = null },
            title = { Text("Rename Voice Profile") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("Profile Name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        voiceViewModel.renameCustomVoice(target.id, renameInput)
                        renamingProfile = null
                    }
                ) { Text("Save", color = CrimsonPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { renamingProfile = null }) { Text("Cancel") }
            }
        )
    }
}
