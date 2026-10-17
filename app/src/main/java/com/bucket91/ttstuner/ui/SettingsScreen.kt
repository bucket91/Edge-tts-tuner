package com.bucket91.ttstuner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bucket91.ttstuner.TtsTunerApplication
import com.bucket91.ttstuner.data.HistoryItem
import com.bucket91.ttstuner.logic.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onReopenHistory: (HistoryItem) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as TtsTunerApplication
    val scope = rememberCoroutineScope()

    val settings by app.settingsStore.settingsFlow.collectAsState(initial = com.bucket91.ttstuner.data.AppSettings())
    val historyItems by app.database.historyDao().getNewest50Flow().collectAsState(initial = emptyList())
    val allRules by app.database.ruleDao().getAllFlow().collectAsState(initial = emptyList())
    val allCorrections by app.database.wordCorrectionDao().getAllFlow().collectAsState(initial = emptyList())

    // Gemini API key state
    var keyInput by remember { mutableStateOf("") }
    var isEditingKey by remember { mutableStateOf(false) }
    var isTestingKey by remember { mutableStateOf(false) }
    var keyTestResult by remember { mutableStateOf<String?>(null) }
    var keyTestIsSuccess by remember { mutableStateOf(false) }

    // Model, Voice, Rate
    var modelInput by remember(settings.geminiModel) { mutableStateOf(settings.geminiModel) }
    var voiceInput by remember(settings.voice) { mutableStateOf(settings.voice) }
    var rateInput by remember(settings.rate) { mutableStateOf(settings.rate) }
    var voiceDropdownExpanded by remember { mutableStateOf(false) }

    val standardVoices = listOf(
        "bn-BD-PradeepNeural",
        "bn-BD-NabanitaNeural",
        "bn-IN-BashkarNeural",
        "bn-IN-TanishaaNeural"
    )

    // Audio test states
    var isRunningAudioTest by remember { mutableStateOf(false) }
    var audioTestLog by remember { mutableStateOf("") }
    var audioTestSuccess by remember { mutableStateOf<Boolean?>(null) }
    val isPlaying by app.audioPlayer.isPlaying.collectAsState()

    // Backup SAF launchers
    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val currentSettings = app.settingsStore.getSettings()
                    val backupJson = BackupManager.createBackupJson(currentSettings, allRules, allCorrections)
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(backupJson.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup exported successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val jsonStr = context.contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader().readText()
                    }.orEmpty()
                    val result = BackupManager.parseBackupJson(jsonStr, allRules, allCorrections)

                    app.settingsStore.restoreSettings(result.restoredSettings)
                    if (result.newRules.isNotEmpty()) app.database.ruleDao().insertAll(result.newRules)
                    if (result.newCorrections.isNotEmpty()) app.database.wordCorrectionDao().insertAll(result.newCorrections)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, result.summary, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup import failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val runAudioTestAction: () -> Unit = {
        isRunningAudioTest = true
        audioTestSuccess = null
        val logBuffer = StringBuilder()

        scope.launch(Dispatchers.IO) {
            val testSentence = "নমস্কার, এটি একটি পরীক্ষা। সঠিক উত্তর হলো চার।"
            val testOutFile = File(context.cacheDir, "audio_test_${System.currentTimeMillis()}.mp3")

            val result = app.edgeTtsClient.synthesize(
                text = testSentence,
                voice = settings.voice,
                rate = settings.rate,
                outFile = testOutFile
            ) { logLine ->
                logBuffer.appendLine(logLine)
                scope.launch(Dispatchers.Main) {
                    audioTestLog = logBuffer.toString()
                }
            }

            withContext(Dispatchers.Main) {
                isRunningAudioTest = false
                if (result.isSuccess) {
                    audioTestSuccess = true
                    app.audioPlayer.playFile(testOutFile)
                } else {
                    audioTestSuccess = false
                    val ex = result.exceptionOrNull()
                    logBuffer.appendLine("FAILED: ${ex?.message}")
                    audioTestLog = logBuffer.toString()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_screen")
    ) {
        // --- 1. Gemini API Key & Model ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Gemini Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                val maskedKey = remember(isEditingKey) { app.secretStore.getMaskedKey() }

                if (!isEditingKey && maskedKey.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gemini API Key",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = maskedKey,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("masked_api_key_display")
                            )
                        }
                        TextButton(onClick = { isEditingKey = true }) {
                            Text("Change")
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("Enter Google AI Studio Gemini API key") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_key_input"),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (isEditingKey) {
                            TextButton(onClick = {
                                isEditingKey = false
                                keyInput = ""
                            }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Button(
                            onClick = {
                                if (keyInput.isNotBlank()) {
                                    app.secretStore.saveApiKey(keyInput)
                                    keyInput = ""
                                    isEditingKey = false
                                    Toast.makeText(context, "API Key saved securely", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("save_api_key_button")
                        ) {
                            Text("Save")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            isTestingKey = true
                            keyTestResult = null
                            scope.launch(Dispatchers.IO) {
                                val currentKey = app.secretStore.getApiKey()
                                val res = app.geminiClient.testConnection(currentKey, modelInput)
                                withContext(Dispatchers.Main) {
                                    isTestingKey = false
                                    if (res.isSuccess) {
                                        keyTestIsSuccess = true
                                        keyTestResult = res.getOrNull()
                                    } else {
                                        keyTestIsSuccess = false
                                        keyTestResult = res.exceptionOrNull()?.message ?: "Test failed"
                                    }
                                }
                            }
                        },
                        enabled = !isTestingKey && app.secretStore.hasKey(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_gemini_key_button")
                    ) {
                        if (isTestingKey) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Test Key")
                        }
                    }
                }

                if (keyTestResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = if (keyTestIsSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (keyTestIsSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (keyTestIsSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = keyTestResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (keyTestIsSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Gemini Model field
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = {
                        modelInput = it
                        scope.launch(Dispatchers.IO) {
                            app.settingsStore.updateGeminiModel(it)
                        }
                    },
                    label = { Text("Gemini Model Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_model_input"),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- 2. Voice & Audio Settings ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Edge-TTS Voice & Rate",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                ExposedDropdownMenuBox(
                    expanded = voiceDropdownExpanded,
                    onExpandedChange = { voiceDropdownExpanded = !voiceDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = voiceInput,
                        onValueChange = {
                            voiceInput = it
                            scope.launch(Dispatchers.IO) { app.settingsStore.updateVoice(it) }
                        },
                        label = { Text("TTS Voice") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                            .fillMaxWidth()
                            .testTag("voice_input")
                    )

                    ExposedDropdownMenu(
                        expanded = voiceDropdownExpanded,
                        onDismissRequest = { voiceDropdownExpanded = false }
                    ) {
                        standardVoices.forEach { voiceOption ->
                            DropdownMenuItem(
                                text = { Text(voiceOption) },
                                onClick = {
                                    voiceInput = voiceOption
                                    voiceDropdownExpanded = false
                                    scope.launch(Dispatchers.IO) { app.settingsStore.updateVoice(voiceOption) }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = rateInput,
                    onValueChange = {
                        rateInput = it
                        scope.launch(Dispatchers.IO) { app.settingsStore.updateRate(it) }
                    },
                    label = { Text("Speech Rate (e.g. +4%, -5%, +0%)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rate_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use Device Voice by Default", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Uses on-device Android TextToSpeech if Edge-TTS is disabled or unavailable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.useFallbackTts,
                        onCheckedChange = { checked ->
                            scope.launch(Dispatchers.IO) {
                                app.settingsStore.updateUseFallbackTts(checked)
                            }
                        },
                        modifier = Modifier.testTag("fallback_tts_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- Audio test Button (A8 diagnostics) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = runAudioTestAction,
                        enabled = !isRunningAudioTest,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("audio_test_button")
                    ) {
                        if (isRunningAudioTest) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing...")
                        } else {
                            Icon(Icons.Default.VolumeUp, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Audio test")
                        }
                    }

                    if (isPlaying) {
                        OutlinedButton(
                            onClick = { app.audioPlayer.stop() }
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Text("Stop")
                        }
                    }
                }

                if (audioTestLog.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (audioTestSuccess == true) "Audio Test Log (SUCCESS)" else if (audioTestSuccess == false) "Audio Test Log (FAILED)" else "Audio Test Log",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (audioTestSuccess == true) Color(0xFF16A34A) else if (audioTestSuccess == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("TTS Audio Test Log", audioTestLog))
                                        Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("copy_audio_log_button")
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy log",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            SelectionContainer {
                                Text(
                                    text = audioTestLog,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    modifier = Modifier.testTag("audio_test_log_output")
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- 3. Backup and Restore ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Backup & Restore",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Exports rules, word corrections and settings (API key excluded for security)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val defaultName = "bangla_tts_backup_${System.currentTimeMillis()}.json"
                            createBackupLauncher.launch(defaultName)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_backup_button")
                    ) {
                        Text("Export backup")
                    }

                    OutlinedButton(
                        onClick = {
                            openBackupLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_backup_button")
                    ) {
                        Text("Import backup")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- 4. History (Newest 50, tap to reopen in Tune, clear all) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "History (${historyItems.size}/50)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (historyItems.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    app.database.historyDao().clearAll()
                                }
                            },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Text("Clear all", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (historyItems.isEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No tuning history yet. Sessions tuned will automatically appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    historyItems.forEach { item ->
                        val dateStr = remember(item.createdAt) {
                            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                            sdf.format(Date(item.createdAt))
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onReopenHistory(item) }
                                .testTag("history_item")
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = dateStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Tap to reopen in Tune",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.input,
                                    fontSize = 16.sp,
                                    maxLines = 2,
                                    fontWeight = FontWeight.Medium
                                )
                                if (item.final.isNotEmpty() && item.final != item.input) {
                                    Text(
                                        text = "→ ${item.final}",
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
