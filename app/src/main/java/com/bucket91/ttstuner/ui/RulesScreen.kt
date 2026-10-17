package com.bucket91.ttstuner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bucket91.ttstuner.TtsTunerApplication
import com.bucket91.ttstuner.data.Rule
import com.bucket91.ttstuner.logic.RulesMd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RulesScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as TtsTunerApplication
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allRules by app.database.ruleDao().getAllFlow().collectAsState(initial = emptyList())
    val allCorrections by app.database.wordCorrectionDao().getAllFlow().collectAsState(initial = emptyList())

    var showMenu by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<Rule?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    // SAF file pickers
    val createDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val md = RulesMd.exportMarkdown(allRules, allCorrections)
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(md.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "rules.md exported successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(uri)?.use { isStream ->
                        isStream.bufferedReader().readText()
                    }.orEmpty()
                    val result = RulesMd.importMarkdown(text, allRules, allCorrections)
                    if (result.newRules.isNotEmpty()) app.database.ruleDao().insertAll(result.newRules)
                    if (result.newCorrections.isNotEmpty()) app.database.wordCorrectionDao().insertAll(result.newCorrections)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, result.summary, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingRule = Rule(text = "")
                    isAddingNew = true
                },
                modifier = Modifier.testTag("add_rule_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add rule")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("rules_screen")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pronunciation Rules (${allRules.count { it.enabled }} active)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("rules_overflow_menu_button")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Rules options")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export rules.md") },
                            onClick = {
                                showMenu = false
                                createDocLauncher.launch("rules.md")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import rules.md") },
                            onClick = {
                                showMenu = false
                                openDocLauncher.launch(arrayOf("*/*"))
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
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (allRules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No natural-language rules yet. Tap '+' to add rules for Gemini pronunciation tuning.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allRules, key = { it.id }) { rule ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editingRule = rule
                                    isAddingNew = false
                                }
                                .testTag("rule_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (rule.enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = rule.text,
                                    fontSize = 16.sp,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (rule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )

                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { checked ->
                                        scope.launch(Dispatchers.IO) {
                                            app.database.ruleDao().update(rule.copy(enabled = checked))
                                        }
                                    },
                                    modifier = Modifier.testTag("toggle_rule_enabled")
                                )

                                IconButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            app.database.ruleDao().delete(rule)
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Rule deleted",
                                                actionLabel = "Undo",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                app.database.ruleDao().insert(rule)
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("delete_rule_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete rule",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingRule != null) {
        val rule = editingRule!!
        var text by remember { mutableStateOf(rule.text) }
        var enabled by remember { mutableStateOf(rule.enabled) }

        AlertDialog(
            onDismissRequest = { editingRule = null },
            title = {
                Text(if (isAddingNew) "Add Natural Language Rule" else "Edit Rule")
            },
            text = {
                Column {
                    Text(
                        text = "Example: স should be written শ when it is pronounced \"sh\", for example in \"সঠিক\" write \"শঠিক\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Rule description") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_rule_text_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
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
                        val trimmed = text.trim()
                        if (trimmed.isEmpty()) return@TextButton
                        scope.launch(Dispatchers.IO) {
                            app.database.ruleDao().insert(
                                rule.copy(text = trimmed, enabled = enabled)
                            )
                            withContext(Dispatchers.Main) {
                                editingRule = null
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.testTag("dialog_save_rule_btn")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingRule = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
