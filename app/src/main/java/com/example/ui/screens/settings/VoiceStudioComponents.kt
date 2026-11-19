package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.CustomVoiceProfileEntity
import com.example.data.model.VoiceGenderHint
import com.example.ui.StagedVoiceDraft
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StagedVoiceDraftCard(
    draft: StagedVoiceDraft,
    isPlayingDraft: Boolean,
    replacingTargetName: String?,
    onDiscard: () -> Unit,
    onUpdateCharacteristics: (pitch: Float?, warmth: Float?, expressiveness: Float?, genderHint: VoiceGenderHint?) -> Unit,
    onTogglePreviewRaw: () -> Unit,
    onTestTts: () -> Unit,
    onCommit: () -> Unit
) {
    val ap = draft.acousticProfile
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CrimsonContainer.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, ElectricCyan)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (replacingTargetName != null) {
                        "REPLACE SAMPLE FOR \"$replacingTargetName\" • ${(draft.durationMs / 1000f).let { "%.1f".format(it) }}s"
                    } else {
                        "STAGED ${draft.sourceType} • ${draft.formatLabel} • ${(draft.durationMs / 1000f).let { "%.1f".format(it) }}s"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDiscard, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Discard draft", tint = NeonScarlet)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill("~${ap.estimatedPitchHz.toInt()} Hz", ElectricCyan)
                StatusPill("TIMBRE: ${ap.detectedGenderHint.name}", CyberGreen)
                StatusPill("CLARITY: ${(ap.clarityScore * 100).toInt()}%", EmberGlow)
            }

            Text("Synthesized Pitch: ${"%.2f".format(ap.pitchMultiplier)}x", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            Slider(
                value = ap.pitchMultiplier,
                onValueChange = { onUpdateCharacteristics(it, null, null, null) },
                valueRange = 0.65f..1.55f,
                colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
            )

            Text(
                "Synthesized Warmth: ${(ap.warmth * 100).toInt()}% • Expressiveness: ${(ap.expressiveness * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slider(
                    value = ap.warmth,
                    onValueChange = { onUpdateCharacteristics(null, it, null, null) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary)
                )
                Slider(
                    value = ap.expressiveness,
                    onValueChange = { onUpdateCharacteristics(null, null, it, null) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VoiceGenderHint.entries.forEach { hint ->
                    FilterChip(
                        selected = ap.detectedGenderHint == hint,
                        onClick = { onUpdateCharacteristics(null, null, null, hint) },
                        label = { Text(hint.name) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CrimsonPrimary)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onTogglePreviewRaw,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("preview_staged_voice_button"),
                    border = BorderStroke(1.dp, ElectricCyan)
                ) {
                    Icon(
                        imageVector = if (isPlayingDraft) Icons.Default.Stop else Icons.Default.AudioFile,
                        contentDescription = "Play raw audio",
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(if (isPlayingDraft) "Stop" else "Raw Audio", color = ElectricCyan)
                }
                OutlinedButton(
                    onClick = onTestTts,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("synthesize_staged_voice_button"),
                    border = BorderStroke(1.dp, CyberGreen)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Test TTS synthesis",
                        tint = CyberGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Test TTS", color = CyberGreen)
                }
            }

            Button(
                onClick = onCommit,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_staged_voice_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
            ) {
                Text(
                    if (replacingTargetName != null) "Replace Voice Sample" else "Save Custom Voice Profile"
                )
            }
        }
    }
}

@Composable
fun SavedCustomVoiceProfileCard(
    profile: CustomVoiceProfileEntity,
    isActiveAssistant: Boolean,
    isPlaying: Boolean,
    localFileReady: Boolean,
    isBeingReplaced: Boolean,
    onSynthesizeTts: () -> Unit,
    onToggleRawPlayback: () -> Unit,
    onTune: () -> Unit,
    onRename: () -> Unit,
    onReplaceSample: () -> Unit,
    onSelectAndPrioritize: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActiveAssistant) CrimsonContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isBeingReplaced -> ElectricCyan
                isActiveAssistant -> CrimsonPrimary
                else -> CardBorderDark
            }
        ),
        modifier = Modifier.testTag("saved_voice_card_${profile.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        text = "${profile.sourceType} (${profile.formatLabel}) • ${(profile.durationMs / 1000f).let { "%.1f".format(it) }}s • ${if (localFileReady) "ON-DEVICE READY" else "LOCAL FILE MISSING"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (localFileReady) ElectricCyan else AmberAlert
                    )
                    Text(
                        text = buildString {
                            append("Pitch ${"%.2f".format(profile.pitchMultiplier)}x")
                            append(" • Rate ${"%.2f".format(profile.rateMultiplier)}x")
                            append(" • Warmth ${(profile.warmth * 100).toInt()}%")
                            if (profile.estimatedPitchHz > 0f) {
                                append(" • ~${profile.estimatedPitchHz.toInt()} Hz")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Row {
                    IconButton(
                        onClick = onSynthesizeTts,
                        modifier = Modifier.size(48.dp).testTag("synthesize_profile_${profile.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Synthesize TTS Preview",
                            tint = CyberGreen
                        )
                    }
                    IconButton(
                        onClick = onToggleRawPlayback,
                        modifier = Modifier.size(48.dp).testTag("play_raw_profile_${profile.id}")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Play Raw Sample",
                            tint = ElectricCyan
                        )
                    }
                    IconButton(
                        onClick = onTune,
                        modifier = Modifier.size(48.dp).testTag("tune_profile_${profile.id}")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Tune Characteristics", tint = EmberGlow)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.size(48.dp).testTag("rename_profile_${profile.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename Voice Profile", tint = TextSecondary)
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp).testTag("delete_profile_${profile.id}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Custom Voice", tint = NeonScarlet)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSelectAndPrioritize,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("select_profile_${profile.id}"),
                    border = BorderStroke(1.dp, if (isActiveAssistant) CyberGreen else CardBorderDark)
                ) {
                    if (isActiveAssistant) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyberGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                    Text(
                        text = if (isActiveAssistant) "Active Voice" else "Select & Prioritize",
                        color = if (isActiveAssistant) CyberGreen else TextPrimary
                    )
                }

                OutlinedButton(
                    onClick = onReplaceSample,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("replace_profile_${profile.id}"),
                    border = BorderStroke(1.dp, if (isBeingReplaced) ElectricCyan else CrimsonPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = if (isBeingReplaced) ElectricCyan else CrimsonPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = if (isBeingReplaced) "Replacing..." else "Replace",
                        color = if (isBeingReplaced) ElectricCyan else CrimsonPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceRulesCard() {
    TerminalCard(
        borderColor = CrimsonPrimary.copy(alpha = 0.65f),
        modifier = Modifier.testTag("voice_rules_card")
    ) {
        Text(
            text = "VOICE RULES & PROTECTIONS",
            style = MaterialTheme.typography.labelLarge,
            color = CrimsonPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "1. Selected Voice Priority: AI SHU always uses your selected custom or built-in voice for assistant speech output.\n" +
                "2. Characteristic Integrity: Saved vocal pitch, warmth, cadence, and timbre characteristics are preserved without unexpected modification or distortion.\n" +
                "3. Full User Control: You can replace a saved custom voice audio sample or permanently delete any voice profile at any time.\n" +
                "4. Explicit Authorization Required: Never clone, replicate, or use another person's voice without explicit user authorization.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
fun RecordingWaveformBar(samples: List<Float>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        val barCount = 36
        val slotWidth = size.width / barCount
        val barWidth = (slotWidth * 0.55f).coerceAtLeast(3f)
        for (i in 0 until barCount) {
            val level = samples.getOrElse(i) { 0.08f }.coerceIn(0.06f, 1f)
            val barHeight = size.height * level
            val x = i * slotWidth + (slotWidth - barWidth) / 2f
            val y = (size.height - barHeight) / 2f
            drawRoundRect(
                color = if (level > 0.6f) NeonScarlet else ElectricCyan,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}
