package com.example.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.AssistantRuleEntity
import com.example.data.repository.AishuRepository
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@Composable
fun RulesSettingsSection(viewModel: AishuViewModel) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val conflicts by viewModel.ruleConflicts.collectAsStateWithLifecycle()
    var newTitle by remember { mutableStateOf("") }
    var newInstruction by remember { mutableStateOf("") }
    var newProtected by remember { mutableStateOf(true) }
    var editingRule by remember { mutableStateOf<AssistantRuleEntity?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editInstruction by remember { mutableStateOf("") }
    val atRuleLimit = rules.size >= AishuRepository.MAX_RULES

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TerminalCard(borderColor = CrimsonPrimary.copy(alpha = 0.5f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PERSONAL RULES ENGINE",
                    style = MaterialTheme.typography.labelLarge,
                    color = CrimsonPrimary
                )
                StatusPill(
                    text = "${rules.size} / ${AishuRepository.MAX_RULES} RULES",
                    accentColor = if (atRuleLimit) AmberAlert else CyberGreen
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Store up to ${AishuRepository.MAX_RULES} persistent rules. System safety and legal rules always remain highest priority. Conversation prompts cannot silently override protected rules.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        if (conflicts.isNotEmpty()) {
            TerminalCard(borderColor = AmberAlert) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AmberAlert)
                    Text("RULE CONFLICT DETECTED", style = MaterialTheme.typography.labelLarge, color = AmberAlert)
                }
                Spacer(modifier = Modifier.height(6.dp))
                conflicts.forEach { conflict ->
                    Text("• $conflict", style = MaterialTheme.typography.bodyMedium, color = AmberAlert)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        TerminalCard {
            Text(
                text = if (atRuleLimit) "RULE LIMIT REACHED (${AishuRepository.MAX_RULES}/${AishuRepository.MAX_RULES})" else "ADD RULE #${rules.size + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                label = { Text("Rule Name") },
                singleLine = true,
                enabled = !atRuleLimit,
                modifier = Modifier.fillMaxWidth().testTag("rule_title_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newInstruction,
                onValueChange = { newInstruction = it },
                label = { Text("Rule Description / Instruction") },
                minLines = 2,
                enabled = !atRuleLimit,
                modifier = Modifier.fillMaxWidth().testTag("rule_instruction_input")
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = newProtected,
                    onCheckedChange = { newProtected = it },
                    colors = CheckboxDefaults.colors(checkedColor = CrimsonPrimary)
                )
                Text("Lock as Protected Rule", color = TextPrimary)
            }
            Button(
                onClick = {
                    if (newInstruction.isNotBlank()) {
                        viewModel.addRule(newTitle, newInstruction, newProtected)
                        newTitle = ""
                        newInstruction = ""
                    }
                },
                enabled = !atRuleLimit && newInstruction.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_rule_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Rule")
            }
        }

        rules.forEachIndexed { index, rule ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, if (rule.isEnabled) CrimsonPrimary.copy(alpha = 0.5f) else CardBorderDark)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Rule #${index + 1} • ${rule.title}",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = if (rule.isProtected) "PROTECTED RULE" else "PERSONAL RULE",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (rule.isProtected) EmberGlow else ElectricCyan
                            )
                        }
                        Switch(
                            checked = rule.isEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateRule(rule.copy(isEnabled = enabled))
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = CrimsonPrimary)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(rule.instruction, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { viewModel.updateRule(rule.copy(isProtected = !rule.isProtected)) }
                        ) {
                            Icon(
                                imageVector = if (rule.isProtected) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Toggle Rule Protection",
                                tint = if (rule.isProtected) EmberGlow else TextMuted
                            )
                        }
                        IconButton(
                            onClick = {
                                editingRule = rule
                                editTitle = rule.title
                                editInstruction = rule.instruction
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Rule", tint = ElectricCyan)
                        }
                        IconButton(onClick = { viewModel.deleteRule(rule) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Rule", tint = NeonScarlet)
                        }
                    }
                }
            }
        }
    }

    editingRule?.let { target ->
        AlertDialog(
            onDismissRequest = { editingRule = null },
            title = { Text("Edit Rule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Rule Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editInstruction,
                        onValueChange = { editInstruction = it },
                        label = { Text("Rule Description") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateRule(
                            target.copy(
                                title = editTitle.trim().ifEmpty { target.title },
                                instruction = editInstruction.trim().ifEmpty { target.instruction }
                            )
                        )
                        editingRule = null
                    }
                ) { Text("Save", color = CrimsonPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { editingRule = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun PhotosAndCameraSection(viewModel: AishuViewModel) {
    val context = LocalContext.current
    val photos by viewModel.savedPhotos.collectAsStateWithLifecycle()
    val isOnline by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    val analyzingId by viewModel.isAnalyzingPhotoId.collectAsStateWithLifecycle()

    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var visionPrompt by remember { mutableStateOf("Describe this photo clearly and answer any details visible.") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importPhotoFromPicker(uri, attachToChat = false)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        if (success && file != null) {
            viewModel.onCameraPhotoCaptured(file, attachToChat = false)
        }
        pendingCameraFile = null
    }

    val launchCamera = {
        val file = viewModel.createCameraPhotoFile()
        pendingCameraFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TerminalCard {
            Text(
                text = "PHOTOS, CAMERA & GEMINI VISION",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            StatusPill(
                text = if (isOnline) "GEMINI VISION ONLINE" else "OFFLINE (VISION NEEDS INTERNET)",
                accentColor = if (isOnline) CyberGreen else AmberAlert
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Open Camera to take a photo or select from Gallery (zero-permission Photo Picker). Run general Image Analysis, ask custom questions about the image, extract readable text (OCR), or identify objects.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) launchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("camera_capture_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Camera")
                }

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("photo_picker_button"),
                    border = BorderStroke(1.dp, ElectricCyan)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = ElectricCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery Pick", color = ElectricCyan)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = visionPrompt,
                onValueChange = { visionPrompt = it },
                label = { Text("Ask a question about your uploaded photo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("vision_question_input")
            )
        }

        if (photos.isEmpty()) {
            TerminalCard {
                Text(
                    text = "No photos uploaded yet. Capture a photo or pick from gallery above to run Image Analysis, Text Recognition (OCR), or Object Recognition.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        } else {
            photos.forEach { photo ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, CardBorderDark)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        AsyncImage(
                            model = File(photo.filePath),
                            contentDescription = photo.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.analyzeSavedPhoto(photo, visionPrompt, speakAloud = true) },
                                enabled = isOnline && analyzingId != photo.id,
                                modifier = Modifier.weight(1f).height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                            ) {
                                if (analyzingId == photo.id) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TextPrimary, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ask & Analyze")
                                }
                            }
                            IconButton(onClick = { viewModel.deleteSavedPhoto(photo) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Photo", tint = NeonScarlet)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.analyzeSavedPhoto(
                                        photo,
                                        "Extract and transcribe all visible text (OCR) accurately from this image.",
                                        speakAloud = false
                                    )
                                },
                                enabled = isOnline && analyzingId != photo.id,
                                modifier = Modifier.weight(1f).height(44.dp),
                                border = BorderStroke(1.dp, ElectricCyan)
                            ) {
                                Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Recognize Text", color = ElectricCyan)
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.analyzeSavedPhoto(
                                        photo,
                                        "Identify and list all distinct objects, items, and scene elements in this image.",
                                        speakAloud = false
                                    )
                                },
                                enabled = isOnline && analyzingId != photo.id,
                                modifier = Modifier.weight(1f).height(44.dp),
                                border = BorderStroke(1.dp, EmberGlow)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = EmberGlow, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Identify Objects", color = EmberGlow)
                            }
                        }
                        if (photo.lastAnalysis.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CrimsonContainer.copy(alpha = 0.45f)
                            ) {
                                Text(
                                    text = photo.lastAnalysis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
