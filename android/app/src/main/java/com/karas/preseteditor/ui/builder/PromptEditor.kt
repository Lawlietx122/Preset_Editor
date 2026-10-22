@file:OptIn(ExperimentalMaterial3Api::class)

package com.karas.preseteditor.ui.builder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.karas.preseteditor.data.PresetJson
import com.karas.preseteditor.data.boolOrNull
import com.karas.preseteditor.data.intOrNull
import com.karas.preseteditor.data.string
import com.karas.preseteditor.data.with
import com.karas.preseteditor.ui.theme.ContentTextStyle
import kotlinx.serialization.json.JsonObject

private val ROLES = listOf("system", "user", "assistant")

/** Full-screen editor for one prompt. Every change goes straight into the preset. */
@Composable
fun PromptEditor(
    preset: JsonObject,
    promptId: String,
    canUndo: Boolean,
    undoVersion: Int,
    edit: (undoKey: String?, transform: (JsonObject) -> JsonObject) -> Unit,
    onUndo: () -> Unit,
    onOpenPrompt: (String) -> Unit,
    onClose: () -> Unit
) {
    val prompt = PresetJson.prompt(preset, promptId)
    BackHandler(onBack = onClose)
    if (prompt == null) {
        // Deleted or undone away: go back to the list.
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val enabled = PresetJson.isEnabled(preset, prompt)
    val update: (String, (JsonObject) -> JsonObject) -> Unit = { key, transform ->
        edit("$promptId:$key") { PresetJson.updatePrompt(it, promptId, transform) }
    }

    // Text fields keep their own state so the cursor never jumps while the preset re-renders;
    // an undo bumps undoVersion and reloads them from the restored preset.
    var name by remember(promptId, undoVersion) { mutableStateOf(prompt.string("name")) }
    var content by remember(promptId, undoVersion) { mutableStateOf(prompt.string("content")) }
    var depth by remember(promptId, undoVersion) { mutableStateOf((prompt.intOrNull("injection_depth") ?: 4).toString()) }
    var order by remember(promptId, undoVersion) { mutableStateOf((prompt.intOrNull("injection_order") ?: 100).toString()) }

    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var technicalOpen by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val role = prompt.string("role").ifBlank { "system" }
    val inChat = (prompt.intOrNull("injection_position") ?: 0) == 1
    val marker = prompt.boolOrNull("marker") == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(name.ifBlank { promptId }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "К списку") }
                },
                actions = {
                    IconButton(onClick = onUndo, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Отменить")
                    }
                    IconButton(onClick = {
                        var newId: String? = null
                        edit(null) { current ->
                            PresetJson.duplicatePrompt(current, promptId)?.let { (next, id) -> newId = id; next } ?: current
                        }
                        newId?.let(onOpenPrompt)
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Дублировать")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { value ->
                    name = value
                    update("name") { it.with("name", value.trim().ifEmpty { promptId }) }
                },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Роль", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ROLES.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = role == value,
                        onClick = { update("role") { it.with("role", value) } },
                        shape = SegmentedButtonDefaults.itemShape(index, ROLES.size)
                    ) { Text(value) }
                }
            }

            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                ListItem(
                    headlineContent = { Text("Включён") },
                    supportingContent = { Text(if (enabled) "Промпт попадёт в запрос" else "Промпт пропускается") },
                    trailingContent = {
                        Switch(checked = enabled, onCheckedChange = { value -> edit(null) { PresetJson.setEnabled(it, promptId, value) } })
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
            }

            if (marker) {
                Text(
                    "Это маркер: SillyTavern подставляет сюда свои данные (описание персонажа, лорбук и т. п.), текст у него не редактируется.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        "Текст · ${counter(content)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { fullscreen = true }) {
                        Icon(Icons.Outlined.OpenInFull, contentDescription = null)
                        Spacer(Modifier.padding(start = 6.dp))
                        Text("На весь экран")
                    }
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { value ->
                        content = value
                        update("content") { it.with("content", value) }
                    },
                    textStyle = ContentTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                    placeholder = { Text("Текст промпта. Макросы вроде {{char}} и {{user}} работают как в SillyTavern.") }
                )
            }

            ExpandableHeader("Дополнительно", advancedOpen) { advancedOpen = !advancedOpen }
            if (advancedOpen) {
                Text("Позиция вставки", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Относительно" to 0, "В чате" to 1).forEachIndexed { index, (label, value) ->
                        SegmentedButton(
                            selected = (if (inChat) 1 else 0) == value,
                            onClick = { update("position") { it.with("injection_position", value) } },
                            shape = SegmentedButtonDefaults.itemShape(index, 2)
                        ) { Text(label) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = depth,
                        onValueChange = { value ->
                            depth = value.filter { it.isDigit() || it == '-' }
                            depth.toIntOrNull()?.let { number -> update("depth") { it.with("injection_depth", number) } }
                        },
                        label = { Text("Глубина") },
                        enabled = inChat,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = order,
                        onValueChange = { value ->
                            order = value.filter { it.isDigit() || it == '-' }
                            order.toIntOrNull()?.let { number -> update("order") { it.with("injection_order", number) } }
                        },
                        label = { Text("Порядок") },
                        enabled = inChat,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    if (inChat) "Глубина считается от конца чата. При одинаковой глубине раньше идёт меньший порядок."
                    else "Глубина и порядок используются только для позиции «В чате».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    ListItem(
                        headlineContent = { Text("Маркер") },
                        supportingContent = { Text("Место для данных SillyTavern вместо своего текста") },
                        trailingContent = {
                            Switch(checked = marker, onCheckedChange = { value -> update("marker") { it.with("marker", value) } })
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    )
                }
                Text(
                    "Идентификатор: $promptId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ExpandableHeader("Технические поля", technicalOpen) { technicalOpen = !technicalOpen }
            if (technicalOpen) {
                Text(
                    "Только просмотр. Редкие поля сохраняются при экспорте как есть.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SelectionContainer {
                    Text(
                        PresetJson.encode(prompt.filterKeys { it != "content" }.let(::JsonObject)),
                        style = ContentTextStyle.copy(fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (fullscreen) {
        FullscreenContentEditor(
            title = name.ifBlank { promptId },
            value = content,
            onValueChange = { value ->
                content = value
                update("content") { it.with("content", value) }
            },
            onClose = { fullscreen = false }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить промпт?") },
            text = { Text("«${name.ifBlank { promptId }}» будет удалён из пресета. Это можно отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    edit(null) { PresetJson.deletePrompt(it, promptId) }
                    onClose()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } }
        )
    }
}

private fun counter(text: String): String {
    val words = text.trim().takeIf { it.isNotEmpty() }?.split(Regex("""\s+"""))?.size ?: 0
    return "${text.length} симв. · $words сл."
}

@Composable
private fun ExpandableHeader(title: String, open: Boolean, onToggle: () -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        }
    }
}

@Composable
private fun FullscreenContentEditor(title: String, value: String, onValueChange: (String) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(counter(value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Готово") }
                    },
                    actions = { TextButton(onClick = onClose) { Text("Готово") } }
                )
            }
        ) { padding ->
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = ContentTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier
                    .padding(padding)
                    .imePadding()
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
