package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.CustomVoiceProfileEntity
import com.example.data.model.VoiceGenderHint
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun VoiceProfileCharacteristicsDialog(
    profile: CustomVoiceProfileEntity,
    onDismiss: () -> Unit,
    onTestSynthesis: (CustomVoiceProfileEntity) -> Unit,
    onSaveCharacteristics: (
        pitchMultiplier: Float,
        rateMultiplier: Float,
        warmth: Float,
        expressiveness: Float,
        similarityBoost: Float,
        genderHint: VoiceGenderHint
    ) -> Unit
) {
    var pitch by remember(profile.id) { mutableFloatStateOf(profile.pitchMultiplier) }
    var rate by remember(profile.id) { mutableFloatStateOf(profile.rateMultiplier) }
    var warmth by remember(profile.id) { mutableFloatStateOf(profile.warmth) }
    var expressiveness by remember(profile.id) { mutableFloatStateOf(profile.expressiveness) }
    var similarityBoost by remember(profile.id) { mutableFloatStateOf(profile.similarityBoost) }
    var genderHint by remember(profile.id) {
        mutableStateOf(
            runCatching { VoiceGenderHint.valueOf(profile.baseGenderHint) }
                .getOrDefault(VoiceGenderHint.NEUTRAL)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tune \"${profile.name}\" Characteristics") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (profile.estimatedPitchHz > 0f) {
                    Text(
                        text = "Detected Vocal Fundamental: ~${profile.estimatedPitchHz.toInt()} Hz • Clarity ${(profile.clarityScore * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                } else {
                    Text(
                        text = "Adjust acoustic characteristics synthesized by the TTS engine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("Base Vocal Timbre", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VoiceGenderHint.entries.forEach { hint ->
                        FilterChip(
                            selected = genderHint == hint,
                            onClick = { genderHint = hint },
                            label = { Text(hint.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CrimsonContainer)
                        )
                    }
                }

                Text("Pitch Calibration: ${"%.2f".format(pitch)}x", color = TextPrimary)
                Slider(
                    value = pitch,
                    onValueChange = { pitch = it },
                    valueRange = 0.65f..1.55f,
                    colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary)
                )

                Text("Cadence / Speed: ${"%.2f".format(rate)}x", color = TextPrimary)
                Slider(
                    value = rate,
                    onValueChange = { rate = it },
                    valueRange = 0.70f..1.50f,
                    colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary)
                )

                Text("Spectral Warmth: ${(warmth * 100).toInt()}%", color = TextPrimary)
                Slider(
                    value = warmth,
                    onValueChange = { warmth = it },
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                )

                Text("Expressiveness & Inflection: ${(expressiveness * 100).toInt()}%", color = TextPrimary)
                Slider(
                    value = expressiveness,
                    onValueChange = { expressiveness = it },
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                )

                Text("Timbre Match Strength: ${(similarityBoost * 100).toInt()}%", color = TextPrimary)
                Slider(
                    value = similarityBoost,
                    onValueChange = { similarityBoost = it },
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary)
                )

                TextButton(
                    onClick = {
                        onTestSynthesis(
                            profile.copy(
                                pitchMultiplier = pitch,
                                rateMultiplier = rate,
                                warmth = warmth,
                                expressiveness = expressiveness,
                                similarityBoost = similarityBoost,
                                baseGenderHint = genderHint.name
                            )
                        )
                    }
                ) {
                    Text("Test Synthesized Output", color = ElectricCyan)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSaveCharacteristics(pitch, rate, warmth, expressiveness, similarityBoost, genderHint)
                }
            ) {
                Text("Save Characteristics", color = CrimsonPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
