package com.bucket91.ttstuner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bucket91.ttstuner.TtsTunerApplication
import com.bucket91.ttstuner.data.WordCorrection
import com.bucket91.ttstuner.logic.PronunciationJson
import com.bucket91.ttstuner.logic.RulesMd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

@Composable
fun WordsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as TtsTunerApplication
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allCorrections by app.database.wordCorrectionDao().getAllFlow().collectAsState(initial = emptyList())
    val allRules by app.database.ruleDao().getAllFlow().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    // Dialog state for adding or editing
    var editingCorrection by remember { mutableStateOf<WordCorrection?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    // SAF file pickers
    var exportTypeToSave by remember { mutableStateOf<String?>(null) } // "rules.md" or "pronunciation.json"
    val createDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        if (uri != null && exportTypeToSave != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val content = if (exportTypeToSave == "rules.md") {
                        RulesMd.exportMarkdown(allRules, allCorrections)
                    } else {
                        PronunciationJson.exportJson(allCorrections)
                    }
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Exported successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    var importTypeToOpen by remember { mutableStateOf<String?>(null) }
    val openDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && importTypeToOpen != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(uri)?.use { isStream ->
                        isStream.bufferedReader().readText()
                    }.orEmpty()

                    if (importTypeToOpen == "rules.md") {
                        val result = RulesMd.importMarkdown(text, allRules, allCorrections)
                        if (result.newRules.isNotEmpty()) app.database.ruleDao().insertAll(result.newRules)
                        if (result.newCorrections.isNotEmpty()) app.database.wordCorrectionDao().insertAll(result.newCorrections)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, result.summary, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val result = PronunciationJson.importJson(text, allCorrections)
                        if (result.newCorrections.isNotEmpty()) app.database.wordCorrectionDao().insertAll(result.newCorrections)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, result.summary, Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val filteredCorrections = remember(allCorrections, searchQuery) {
        if (searchQuery.isBlank()) {
            allCorrections
        } else {
            val q = searchQuery.trim().lowercase()
            allCorrections.filter {
                it.base.lowercase().contains(q) ||
                        it.preferred.lowercase().contains(q) ||
                        it.note.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingCorrection = WordCorrection(base = "", preferred = "")
                    isAddingNew = true
                },
                modifier = Modifier.testTag("add_word_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add word correction")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("words_screen")
        ) {
            // Search and Overflow row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search word corrections...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("words_search_input"),
                    singleLine = true
                )

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("words_overflow_menu_button")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Import/Export options")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export rules.md") },
                            onClick = {
                                showMenu = false
                                exportTypeToSave = "rules.md"
                                createDocLauncher.launch("rules.md")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import rules.md") },
                            onClick = {
                                showMenu = false
                                importTypeToOpen = "rules.md"
                                openDocLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export pronunciation.json") },
                            onClick = {
                                showMenu = false
                                exportTypeToSave = "pronunciation.json"
                                createDocLauncher.launch("pronunciation.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import pronunciation.json") },
                            onClick = {
                                showMenu = false
                                importTypeToOpen = "pronunciation.json"
                                openDocLauncher.launch(arrayOf("application/json", "*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy rules.md to Clipboard") },
                            onClick = {
                                showMenu = false
                                val md = RulesMd.exportMarkdown(allRules, allCorrections)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("rules.md", md))
                                Toast.makeText(context, "Copied rules.md", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy pronunciation.json to Clipboard") },
                            onClick = {
                                showMenu = false
                                val json = PronunciationJson.exportJson(allCorrections)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("pronunciation.json", json))
                                Toast.makeText(context, "Copied pronunciation.json", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share pronunciation.json") },
                            onClick = {
                                showMenu = false
                                val json = PronunciationJson.exportJson(allCorrections)
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, json)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share pronunciation.json"))
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredCorrections.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No matching word corrections found" else "No word corrections yet. Tap '+' to add one.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCorrections, key = { it.id }) { item ->
                        WordCorrectionCard(
                            item = item,
                            onClick = {
                                editingCorrection = item
                                isAddingNew = false
                            },
                            onToggleEnabled = { enabled ->
                                scope.launch(Dispatchers.IO) {
                                    app.database.wordCorrectionDao().update(item.copy(enabled = enabled))
                                }
                            },
                            onDelete = {
                                scope.launch(Dispatchers.IO) {
                                    app.database.wordCorrectionDao().delete(item)
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Deleted '${item.base}'",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        app.database.wordCorrectionDao().insert(item)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Dialog with Duplicate check (A3)
    if (editingCorrection != null) {
        val corr = editingCorrection!!
        var base by remember { mutableStateOf(corr.base) }
        var preferred by remember { mutableStateOf(corr.preferred) }
        var note by remember { mutableStateOf(corr.note) }
        var wholeWord by remember { mutableStateOf(corr.wholeWord) }
        var enabled by remember { mutableStateOf(corr.enabled) }

        AlertDialog(
            onDismissRequest = { editingCorrection = null },
            title = {
                Text(if (isAddingNew) "Add Word Correction" else "Edit Word Correction")
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = base,
                        onValueChange = { base = it },
                        label = { Text("Base word (in original text)") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_base_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = preferred,
                        onValueChange = { preferred = it },
                        label = { Text("Preferred respelling (spoken)") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_preferred_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enabled", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmedBase = base.trim()
                        val trimmedPref = preferred.trim()
                        if (trimmedBase.isEmpty()) return@TextButton

                        scope.launch(Dispatchers.IO) {
                            val normBase = Normalizer.normalize(trimmedBase, Normalizer.Form.NFC)

                            // Duplicate check (A3): adding a base that already exists opens existing entry for editing
                            if (isAddingNew) {
                                val existing = allCorrections.firstOrNull {
                                    Normalizer.normalize(it.base, Normalizer.Form.NFC) == normBase
                                }
                                if (existing != null) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Base already exists. Opening existing entry for edit.", Toast.LENGTH_SHORT).show()
                                        editingCorrection = existing
                                        isAddingNew = false
                                    }
                                    return@launch
                                }
                            }

                            val toSave = corr.copy(
                                base = trimmedBase,
                                preferred = trimmedPref,
                                note = note.trim(),
                                wholeWord = wholeWord,
                                enabled = enabled
                            )
                            app.database.wordCorrectionDao().insert(toSave)
                            withContext(Dispatchers.Main) {
                                editingCorrection = null
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.testTag("dialog_save_btn")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCorrection = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun WordCorrectionCard(
    item: WordCorrection,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("word_correction_card"),
        colors = CardDefaults.cardColors(
            containerColor = if (item.enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.base,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = " → ",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item.preferred,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = if (item.wholeWord) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (item.wholeWord) "Whole word" else "Substring",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    if (!item.enabled) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Disabled",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (item.note.isNotBlank()) {
                        Text(
                            text = "(${item.note})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Switch(
                checked = item.enabled,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.testTag("toggle_word_enabled")
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_word_btn")
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}
