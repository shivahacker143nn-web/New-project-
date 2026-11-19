package com.example.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.MemoryFactEntity
import com.example.ui.AishuViewModel
import com.example.ui.components.StatusPill
import com.example.ui.components.TerminalCard
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CardBorderDark
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmberGlow
import com.example.ui.theme.NeonScarlet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemorySettingsSection(viewModel: AishuViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val searchQuery by viewModel.historySearchQuery.collectAsStateWithLifecycle()
    val historyMessages by viewModel.filteredHistory.collectAsStateWithLifecycle()

    var newMemoryText by remember { mutableStateOf("") }
    var selectedMemoryIds by remember { mutableStateOf(setOf<Long>()) }
    var editingMemory by remember { mutableStateOf<MemoryFactEntity?>(null) }
    var editMemoryInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TerminalCard {
            Text(
                text = "MEMORY ENGINE CONTROLS",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Extract Explicit Memories", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("Remembers your name, preferences, instructions, and \"Remember that...\" facts.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Switch(
                    checked = settings.autoSaveMemory,
                    onCheckedChange = { viewModel.updateMemoryPreferences(autoSave = it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = CrimsonPrimary)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use Memory Context in Replies", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("Injects saved facts into online Gemini and offline replies.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Switch(
                    checked = settings.useMemoryContext,
                    onCheckedChange = { viewModel.updateMemoryPreferences(useContext = it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = CrimsonPrimary)
                )
            }
        }

        TerminalCard {
            Text("VIEW, EDIT & MANAGE MEMORIES (${memories.size})", style = MaterialTheme.typography.labelLarge, color = CrimsonPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newMemoryText,
                onValueChange = { newMemoryText = it },
                label = { Text("Enter fact, name, or preference to remember") },
                modifier = Modifier.fillMaxWidth().testTag("manual_memory_input")
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (newMemoryText.isNotBlank()) {
                            viewModel.addManualMemory(newMemoryText)
                            newMemoryText = ""
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("save_memory_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text("Save Memory")
                }
                OutlinedButton(
                    onClick = {
                        if (selectedMemoryIds.isNotEmpty()) {
                            viewModel.deleteSelectedMemories(selectedMemoryIds.toList())
                            selectedMemoryIds = emptySet()
                        } else {
                            viewModel.clearAllMemories()
                        }
                    },
                    enabled = memories.isNotEmpty(),
                    modifier = Modifier.height(48.dp).testTag("delete_memories_button"),
                    border = BorderStroke(1.dp, NeonScarlet)
                ) {
                    Text(
                        text = if (selectedMemoryIds.isNotEmpty()) "Delete (${selectedMemoryIds.size})" else "Clear All",
                        color = NeonScarlet
                    )
                }
            }
        }

        memories.forEach { mem ->
            val isChecked = selectedMemoryIds.contains(mem.id)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    when {
                        isChecked -> ElectricCyan
                        mem.isPinned -> CrimsonPrimary
                        else -> CardBorderDark
                    }
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            selectedMemoryIds = if (checked) {
                                selectedMemoryIds + mem.id
                            } else {
                                selectedMemoryIds - mem.id
                            }
                        },
                        colors = CheckboxDefaults.colors(checkedColor = ElectricCyan)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mem.category, style = MaterialTheme.typography.labelSmall, color = EmberGlow)
                        Text(mem.factText, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    }
                    IconButton(
                        onClick = {
                            editingMemory = mem
                            editMemoryInput = mem.factText
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit memory", tint = ElectricCyan)
                    }
                    IconButton(onClick = { viewModel.toggleMemoryPin(mem) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pin memory",
                            tint = if (mem.isPinned) CrimsonPrimary else TextMuted
                        )
                    }
                    IconButton(
                        onClick = {
                            selectedMemoryIds = selectedMemoryIds - mem.id
                            viewModel.deleteMemory(mem.id)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete memory", tint = NeonScarlet)
                    }
                }
            }
        }

        // Conversation History Management inside Memory Section
        TerminalCard {
            Text(
                text = "CONVERSATION HISTORY (${historyMessages.size})",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setHistorySearchQuery(it) },
                label = { Text("Search conversation history") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("history_search_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.clearAllHistory() },
                enabled = historyMessages.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("clear_history_button"),
                border = BorderStroke(1.dp, NeonScarlet)
            ) {
                Text("Clear Conversation History", color = NeonScarlet)
            }
        }

        historyMessages.take(20).forEach { msg ->
            val dateLabel = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, CardBorderDark)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${msg.role} • $dateLabel • ${msg.providerLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmberGlow
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(msg.content, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                    IconButton(onClick = { viewModel.deleteHistoryMessage(msg.id) }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete message", tint = NeonScarlet)
                    }
                }
            }
        }
    }

    editingMemory?.let { target ->
        AlertDialog(
            onDismissRequest = { editingMemory = null },
            title = { Text("Edit Memory") },
            text = {
                OutlinedTextField(
                    value = editMemoryInput,
                    onValueChange = { editMemoryInput = it },
                    label = { Text("Memory Fact") },
                    minLines = 2
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.editMemory(target.id, editMemoryInput)
                        editingMemory = null
                    }
                ) { Text("Save", color = CrimsonPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { editingMemory = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun PermissionsSettingsSection(viewModel: AishuViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val speechReady = remember { viewModel.speechManager.isSpeechRecognitionAvailable() }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        micGranted = it
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = it
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TerminalCard {
            Text("RUNTIME PERMISSION STATUS", style = MaterialTheme.typography.labelLarge, color = CrimsonPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "AI SHU never requests unnecessary phone call, contacts, SMS, or broad gallery/storage permissions.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Microphone & Audio Recording", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                StatusPill(if (micGranted) "GRANTED" else "DENIED", if (micGranted) CyberGreen else NeonScarlet)
            }
            if (!micGranted) {
                Spacer(modifier = Modifier.height(6.dp))
                Button(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Request Microphone Permission")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Camera (Vision Capture)", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                StatusPill(if (cameraGranted) "GRANTED" else "OPTIONAL", if (cameraGranted) CyberGreen else AmberAlert)
            }
            if (!cameraGranted) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow Camera Permission")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Notifications (Auto Mode)", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                StatusPill(if (notifGranted) "GRANTED" else "DENIED", if (notifGranted) CyberGreen else AmberAlert)
            }
            if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Request Notification Permission")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Photo Picker (Zero-Permission)", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                StatusPill("ACTIVE", CyberGreen)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("System SpeechRecognizer", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                StatusPill(if (speechReady) "AVAILABLE" else "UNAVAILABLE", if (speechReady) CyberGreen else AmberAlert)
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Open Android App Settings")
            }
        }
    }
}

@Composable
fun PrivacySecuritySettingsSection(viewModel: AishuViewModel) {
    var confirmWipe by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TerminalCard {
            Text("ENCRYPTION & CREDENTIAL SECURITY", style = MaterialTheme.typography.labelLarge, color = CrimsonPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            StatusPill(
                text = if (viewModel.isHardwareKeystoreActive()) "ANDROID KEYSTORE AES-256-GCM" else "ENCRYPTED LOCAL STORE",
                accentColor = CyberGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Google Gemini AI runs through Firebase AI Logic protected by Firebase App Check—no raw Gemini API key is exposed in the UI or APK source.\n• Sensitive local credentials and settings vaults use Android Keystore AES-256 encryption.\n• Zero-trust Firestore rules restrict synced profiles and permanent rules strictly to your authenticated UID.\n• Custom voice profiles keep audio recordings locally on your device and require explicit authorization confirmation.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        TerminalCard(borderColor = NeonScarlet.copy(alpha = 0.55f)) {
            Text("DATA CONTROL & WIPE", style = MaterialTheme.typography.labelLarge, color = NeonScarlet)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Permanently erase all conversation messages, saved personal memories, and encrypted local API keys from this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { confirmWipe = true },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("wipe_all_data_button"),
                border = BorderStroke(1.dp, NeonScarlet)
            ) {
                Text("Wipe Local History, Memories & Keys", color = NeonScarlet)
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Wipe All Local Data?") },
            text = { Text("This permanently deletes all conversation history, saved memories, and encrypted API keys from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.wipeAllPrivateData()
                        confirmWipe = false
                    }
                ) { Text("Wipe Everything", color = NeonScarlet) }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AboutAishuSection(viewModel: AishuViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TerminalCard {
            Text("ABOUT AI SHU", style = MaterialTheme.typography.labelLarge, color = CrimsonPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("AI SHU Personal AI Assistant v2.0", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Online Intelligence: Powered by Google Gemini (Firebase AI Logic + App Check) with Google Search Grounding and Gemini Vision (Image Q&A, OCR & Object Identification).\n• Bilingual English & Telugu: Automatic spoken/written language detection and natural replies.\n• Automatic Offline Switching: Detects connectivity automatically and uses local Room memories, conversation history, calculations, and downloaded offline models.\n• Custom Voice Studio: Records or imports audio samples locally, extracts vocal pitch and timbre characteristics, supports replacing/deleting samples, and calibrates built-in Text-to-Speech on-device.\n• Up to 30 Personal Rules: Persistent rules with conflict detection and protected priority safeguards.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        TerminalCard {
            Text("BUILD & SECURITY SUMMARY", style = MaterialTheme.typography.labelLarge, color = CrimsonPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            StatusPill(
                text = if (viewModel.isHardwareKeystoreActive()) "KEYSTORE HARDWARE VAULT READY" else "LOCAL ENCRYPTED STORAGE",
                accentColor = CyberGreen
            )
        }
    }
}
