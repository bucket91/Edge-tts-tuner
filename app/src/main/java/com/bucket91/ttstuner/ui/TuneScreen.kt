package com.bucket91.ttstuner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bucket91.ttstuner.TtsTunerApplication
import com.bucket91.ttstuner.audio.AudioCache
import com.bucket91.ttstuner.data.HistoryItem
import com.bucket91.ttstuner.data.Rule
import com.bucket91.ttstuner.data.WordCorrection
import com.bucket91.ttstuner.logic.CandidateWordPair
import com.bucket91.ttstuner.logic.DiffSegment
import com.bucket91.ttstuner.logic.Pipeline
import com.bucket91.ttstuner.logic.PipelineOutput
import com.bucket91.ttstuner.logic.WordDiff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TuneScreen(
    initialInputText: String = "",
    onClearInitialInput: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as TtsTunerApplication
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf(initialInputText) }
    if (initialInputText.isNotEmpty() && inputText != initialInputText) {
        inputText = initialInputText
        onClearInitialInput()
    }

    var progressText by remember { mutableStateOf("") }
    var isTuning by remember { mutableStateOf(false) }
    var pipelineOutput by remember { mutableStateOf<PipelineOutput?>(null) }
    var editableFinalText by remember { mutableStateOf("") }
    var preEditText by remember { mutableStateOf("") }

    // Audio states
    val isPlaying by app.audioPlayer.isPlaying.collectAsState()
    var isSynthesizing by remember { mutableStateOf(false) }
    var lastAudioFile by remember { mutableStateOf<File?>(null) }
    var audioError by remember { mutableStateOf<String?>(null) }
    var audioErrorLog by remember { mutableStateOf<String?>(null) }
    var showAudioDetails by remember { mutableStateOf(false) }

    // Bottom sheet for diff segment tap
    var selectedSegmentForLogging by remember { mutableStateOf<DiffSegment?>(null) }

    // Bottom sheet for "Log my edits"
    var showLogEditsSheet by remember { mutableStateOf(false) }
    val candidateWordPairs = remember { mutableStateListOf<CandidateWordPair>() }

    // File picker for "Save mp3"
    val saveMp3Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/mpeg")
    ) { uri: Uri? ->
        if (uri != null && lastAudioFile != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outStream ->
                        FileInputStream(lastAudioFile!!).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Audio saved successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val runTuneAction: () -> Unit = {
        if (inputText.isBlank()) {
            Toast.makeText(context, "Please enter some Bangla text first", Toast.LENGTH_SHORT).show()
        } else {
            isTuning = true
            progressText = "Starting pipeline..."
            audioError = null
            audioErrorLog = null

            scope.launch(Dispatchers.IO) {
                try {
                    val corrections = app.database.wordCorrectionDao().getAll()
                    val rules = app.database.ruleDao().getAll()
                    val settings = app.settingsStore.getSettings()
                    val apiKey = app.secretStore.getApiKey()

                    val output = Pipeline.run(
                        input = inputText,
                        corrections = corrections,
                        rules = rules,
                        apiKey = apiKey,
                        model = settings.geminiModel,
                        geminiClient = app.geminiClient,
                        onProgress = { step ->
                            progressText = step
                        }
                    )

                    withContext(Dispatchers.Main) {
                        pipelineOutput = output
                        editableFinalText = output.finalText
                        preEditText = output.finalText
                        isTuning = false
                        progressText = ""
                    }

                    // Save to history (keep newest 50)
                    app.database.historyDao().insert(
                        HistoryItem(
                            input = output.input,
                            stage1 = output.stage1ReplacedText,
                            stage2 = output.stage2ResultText ?: output.stage2Notice.orEmpty(),
                            final = output.finalText
                        )
                    )
                    app.database.historyDao().trimTo50()

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isTuning = false
                        progressText = "Error: ${e.message}"
                        Toast.makeText(context, "Tune failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val playAudioAction: (forceFallback: Boolean) -> Unit = { forceFallback ->
        val textToSpeak = editableFinalText.trim().ifEmpty { inputText.trim() }
        if (textToSpeak.isBlank()) {
            Toast.makeText(context, "No text to speak", Toast.LENGTH_SHORT).show()
        } else {
            isSynthesizing = true
            audioError = null
            audioErrorLog = null

            scope.launch(Dispatchers.IO) {
                val settings = app.settingsStore.getSettings()
                val voice = settings.voice
                val rate = settings.rate
                val useFallback = forceFallback || settings.useFallbackTts

                val cacheDir = context.cacheDir
                val audioFile = AudioCache.getCacheFile(cacheDir, voice, rate, textToSpeak, useFallback)
                val logLines = mutableListOf<String>()

                if (AudioCache.isCacheValid(audioFile)) {
                    withContext(Dispatchers.Main) {
                        lastAudioFile = audioFile
                        isSynthesizing = false
                        app.audioPlayer.playFile(audioFile)
                    }
                } else {
                    val client = if (useFallback) app.fallbackTtsClient else app.edgeTtsClient
                    val result = client.synthesize(textToSpeak, voice, rate, audioFile) { line ->
                        logLines.add(line)
                    }

                    withContext(Dispatchers.Main) {
                        isSynthesizing = false
                        if (result.isSuccess) {
                            AudioCache.pruneCache(cacheDir, 50)
                            lastAudioFile = audioFile
                            app.audioPlayer.playFile(audioFile)
                        } else {
                            val ex = result.exceptionOrNull()
                            audioError = ex?.message ?: "Audio synthesis failed"
                            audioErrorLog = logLines.takeLast(20).joinToString("\n")
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("tune_screen")
    ) {
        // --- 1. Input Box ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bangla Input",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val pasted = clip.getItemAt(0).text?.toString().orEmpty()
                                    if (pasted.isNotEmpty()) {
                                        inputText = pasted
                                    }
                                }
                            },
                            modifier = Modifier.testTag("paste_button")
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste text")
                        }
                        if (inputText.isNotEmpty()) {
                            IconButton(
                                onClick = { inputText = "" },
                                modifier = Modifier.testTag("clear_button")
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear text")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_text_field"),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                    placeholder = { Text("Paste or type Bangla paragraph or sentence here...", fontSize = 16.sp) },
                    minLines = 4,
                    maxLines = 10
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${inputText.length} characters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- Tune Button & Progress Line ---
        Button(
            onClick = runTuneAction,
            enabled = !isTuning && inputText.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("tune_button")
        ) {
            if (isTuning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(Icons.Default.Tune, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Tune", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (progressText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = progressText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3 Result Cards (A7) ---
        if (pipelineOutput != null) {
            val output = pipelineOutput!!

            // Card 1: 1. Word corrections (highlight replaced words)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "1. Word corrections",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val annotatedWordText = buildAnnotatedString {
                        if (output.stage1Replacements.isEmpty()) {
                            append(output.input)
                        } else {
                            // Show replaced text with replaced words highlighted
                            append(output.stage1ReplacedText)
                        }
                    }

                    Text(
                        text = annotatedWordText,
                        fontSize = 18.sp,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("stage1_text")
                    )

                    if (output.stage1Replacements.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Applied ${output.stage1Replacements.size} word corrections: " +
                                    output.stage1Replacements.joinToString(", ") { "${it.base} → ${it.preferred}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Card 2: 2. Gemini rules (shows text or skipped notice)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "2. Gemini rules",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (output.stage2Notice != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = output.stage2Notice,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    } else if (output.stage2ResultText != null) {
                        Text(
                            text = output.stage2ResultText,
                            fontSize = 18.sp,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("stage2_text")
                        )
                    }
                }
            }

            // Card 3: Final text (editable box)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Final text",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = editableFinalText,
                        onValueChange = { editableFinalText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("final_text_input"),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                        minLines = 3,
                        maxLines = 10
                    )
                }
            }

            // --- Diff View (tappable segments) ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Diff (tap any change to save as correction or rule)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (seg in output.diffSegments) {
                            when (seg) {
                                is DiffSegment.Equal -> {
                                    Text(
                                        text = seg.text + " ",
                                        fontSize = 18.sp
                                    )
                                }
                                is DiffSegment.Replace -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .clickable { selectedSegmentForLogging = seg }
                                            .testTag("diff_replace_segment")
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                                            Text(
                                                text = seg.oldText,
                                                fontSize = 18.sp,
                                                color = Color(0xFFEF4444),
                                                textDecoration = TextDecoration.LineThrough
                                            )
                                            Text(text = " → ", fontSize = 18.sp)
                                            Text(
                                                text = seg.newText,
                                                fontSize = 18.sp,
                                                color = Color(0xFF16A34A),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                is DiffSegment.Delete -> {
                                    Surface(
                                        color = Color(0xFFFEE2E2),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .clickable { selectedSegmentForLogging = seg }
                                            .testTag("diff_delete_segment")
                                    ) {
                                        Text(
                                            text = seg.oldText,
                                            fontSize = 18.sp,
                                            color = Color(0xFFEF4444),
                                            textDecoration = TextDecoration.LineThrough,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                is DiffSegment.Insert -> {
                                    Surface(
                                        color = Color(0xFFDCFCE7),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .clickable { selectedSegmentForLogging = seg }
                                            .testTag("diff_insert_segment")
                                    ) {
                                        Text(
                                            text = seg.newText,
                                            fontSize = 18.sp,
                                            color = Color(0xFF16A34A),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Controls: Speak, Stop, Save mp3, Log my edits (A7) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Audio & Edits Controls",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { playAudioAction(false) },
                        enabled = !isSynthesizing,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("speak_button")
                    ) {
                        if (isSynthesizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPlaying) "Playing" else "Speak")
                    }

                    OutlinedButton(
                        onClick = { app.audioPlayer.stop() },
                        enabled = isPlaying,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stop_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val defaultName = "bangla_tts_${System.currentTimeMillis()}.mp3"
                            saveMp3Launcher.launch(defaultName)
                        },
                        enabled = lastAudioFile != null && lastAudioFile!!.exists(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_mp3_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save mp3")
                    }

                    OutlinedButton(
                        onClick = {
                            // Diff pre-edit against user's editable final text
                            candidateWordPairs.clear()
                            val extracted = WordDiff.extractCandidateWordPairs(preEditText, editableFinalText)
                            candidateWordPairs.addAll(extracted)
                            showLogEditsSheet = true
                        },
                        enabled = pipelineOutput != null,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("log_edits_button")
                    ) {
                        Text("Log my edits")
                    }
                }

                // --- Audio Failure Message & Device Voice Fallback (A8) ---
                if (audioError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = audioError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )

                            if (audioErrorLog != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showAudioDetails = !showAudioDetails },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Details",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        if (showAudioDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("TTS Log", audioErrorLog))
                                            Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy log",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = showAudioDetails) {
                                    SelectionContainer {
                                        Text(
                                            text = audioErrorLog!!,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { playAudioAction(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("fallback_voice_button")
                            ) {
                                Text("Use device voice instead")
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Bottom Sheet: Logging Correction from Tapped Diff Segment (A5) ---
    if (selectedSegmentForLogging != null) {
        val seg = selectedSegmentForLogging!!
        val defaultOld = when (seg) {
            is DiffSegment.Replace -> seg.oldText
            is DiffSegment.Delete -> seg.oldText
            else -> ""
        }
        val defaultNew = when (seg) {
            is DiffSegment.Replace -> seg.newText
            is DiffSegment.Insert -> seg.newText
            else -> ""
        }

        var baseText by remember { mutableStateOf(defaultOld) }
        var preferredText by remember { mutableStateOf(defaultNew) }
        var wholeWord by remember {
            mutableStateOf(
                if (seg is DiffSegment.Replace) seg.oldTokens.size == seg.newTokens.size else true
            )
        }
        var noteText by remember { mutableStateOf("") }
        var isRuleMode by remember { mutableStateOf(false) }
        var ruleText by remember {
            mutableStateOf("\"$defaultOld\" should be written \"$defaultNew\" when pronounced this way")
        }

        ModalBottomSheet(
            onDismissRequest = { selectedSegmentForLogging = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = if (isRuleMode) "Save as Natural Language Rule" else "Save as Word Correction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (!isRuleMode) {
                    OutlinedTextField(
                        value = baseText,
                        onValueChange = { baseText = it },
                        label = { Text("Base word (original)") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = preferredText,
                        onValueChange = { preferredText = it },
                        label = { Text("Preferred respelling") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Whole word only", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = wholeWord,
                            onCheckedChange = { wholeWord = it }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Optional note") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (baseText.isBlank()) return@Button
                                scope.launch(Dispatchers.IO) {
                                    app.database.wordCorrectionDao().insert(
                                        WordCorrection(
                                            base = baseText.trim(),
                                            preferred = preferredText.trim(),
                                            wholeWord = wholeWord,
                                            note = noteText.trim(),
                                            enabled = true
                                        )
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Saved. Applies from the next run", Toast.LENGTH_SHORT).show()
                                        selectedSegmentForLogging = null
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_word_correction_btn")
                        ) {
                            Text("Save word correction")
                        }

                        OutlinedButton(
                            onClick = { isRuleMode = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Switch to Rule")
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = ruleText,
                        onValueChange = { ruleText = it },
                        label = { Text("Rule instruction") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (ruleText.isBlank()) return@Button
                                scope.launch(Dispatchers.IO) {
                                    app.database.ruleDao().insert(
                                        Rule(
                                            text = ruleText.trim(),
                                            enabled = true
                                        )
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Saved. Applies from the next run", Toast.LENGTH_SHORT).show()
                                        selectedSegmentForLogging = null
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_rule_btn")
                        ) {
                            Text("Save rule")
                        }

                        OutlinedButton(
                            onClick = { isRuleMode = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Switch to Word")
                        }
                    }
                }
            }
        }
    }

    // --- Bottom Sheet: "Log my edits" (A5) ---
    if (showLogEditsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLogEditsSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Log My Edits as Word Corrections",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (candidateWordPairs.isEmpty()) {
                    Text("No word changes detected between the pre-edit and edited text.")
                } else {
                    Text(
                        text = "Select changed word pairs to save into your dictionary:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    candidateWordPairs.forEachIndexed { index, pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    candidateWordPairs[index] = pair.copy(isSelected = !pair.isSelected)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = pair.isSelected,
                                onCheckedChange = { checked ->
                                    candidateWordPairs[index] = pair.copy(isSelected = checked)
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${pair.base} → ${pair.preferred}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val selected = candidateWordPairs.filter { it.isSelected && it.base.isNotBlank() }
                            if (selected.isNotEmpty()) {
                                scope.launch(Dispatchers.IO) {
                                    val newEntries = selected.map {
                                        WordCorrection(
                                            base = it.base,
                                            preferred = it.preferred,
                                            wholeWord = it.wholeWord,
                                            enabled = true
                                        )
                                    }
                                    app.database.wordCorrectionDao().insertAll(newEntries)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Saved. Applies from the next run", Toast.LENGTH_SHORT).show()
                                        showLogEditsSheet = false
                                    }
                                }
                            } else {
                                showLogEditsSheet = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_selected_corrections_btn")
                    ) {
                        Text("Save selected as word corrections")
                    }
                }
            }
        }
    }
}
