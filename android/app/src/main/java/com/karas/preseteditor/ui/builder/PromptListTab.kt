package com.karas.preseteditor.ui.builder

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowDown
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karas.preseteditor.data.PresetJson
import kotlinx.serialization.json.JsonObject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PromptListTab(
    preset: JsonObject,
    edit: (undoKey: String?, transform: (JsonObject) -> JsonObject) -> Unit,
    onOpenPrompt: (String) -> Unit
) {
    val allRows = remember(preset) { BuilderViewModel.rows(preset) }
    var query by rememberSaveable { mutableStateOf("") }
    var dragging by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf(allRows) }
    var confirmDelete by remember { mutableStateOf<PromptRow?>(null) }
    LaunchedEffect(allRows) { if (!dragging) rows = allRows }

    val searching = query.isNotBlank()
    val visible = if (searching) allRows.filter { it.matches(query) } else rows
    val enabledCount = allRows.count { it.enabled }

    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        rows = rows.toMutableList().apply { add(to.index, removeAt(from.index)) }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            placeholder = { Text("Поиск по имени и тексту") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Очистить") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp)
        )
        Text(
            if (searching) "Найдено: ${visible.size}. Перетаскивание недоступно во время поиска."
            else "${allRows.size} всего · $enabledCount вкл. · ${allRows.size - enabledCount} выкл. Тяните за ⋮⋮, чтобы переставить.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        if (allRows.isEmpty()) {
            Text(
                "В пресете пока нет промптов. Нажмите «Промпт», чтобы добавить первый.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(visible, key = { it.id }) { row ->
                ReorderableItem(reorderState, key = row.id, enabled = !searching) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "drag")
                    PromptRowItem(
                        row = row,
                        elevation = elevation.value,
                        dragHandle = {
                            IconButton(
                                onClick = {},
                                enabled = !searching,
                                modifier = Modifier.draggableHandle(
                                    enabled = !searching,
                                    onDragStarted = {
                                        dragging = true
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        dragging = false
                                        val ids = rows.map { it.id }
                                        edit(null) { PresetJson.reorder(it, ids) }
                                    }
                                )
                            ) {
                                Icon(Icons.Outlined.DragIndicator, contentDescription = "Перетащить")
                            }
                        },
                        onOpen = { onOpenPrompt(row.id) },
                        onToggle = { enabled -> edit(null) { PresetJson.setEnabled(it, row.id, enabled) } },
                        onDuplicate = {
                            edit(null) { PresetJson.duplicatePrompt(it, row.id)?.first ?: it }
                        },
                        onMoveTop = { edit(null) { PresetJson.move(it, row.id, 0) } },
                        onMoveBottom = { edit(null) { PresetJson.move(it, row.id, Int.MAX_VALUE) } },
                        onDelete = { confirmDelete = row }
                    )
                }
            }
        }
    }

    confirmDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Удалить промпт?") },
            text = { Text("«${row.name}» будет удалён из пресета. Это можно отменить кнопкой «Отменить» вверху.") },
            confirmButton = {
                TextButton(onClick = {
                    edit(null) { PresetJson.deletePrompt(it, row.id) }
                    confirmDelete = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun PromptRowItem(
    row: PromptRow,
    elevation: Float,
    dragHandle: @Composable () -> Unit,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onMoveTop: () -> Unit,
    onMoveBottom: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isSection = row.section != null
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSection) colors.secondaryContainer else colors.surfaceContainerLow,
        shadowElevation = elevation.dp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen)
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(start = 0.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dragHandle()
            Column(Modifier.weight(1f).alpha(if (row.enabled) 1f else 0.6f)) {
                Text(
                    row.section?.title ?: row.name,
                    style = if (isSection) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSection) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSection) colors.onSecondaryContainer else colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isSection) "Раздел · ${row.summary}" else row.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(4.dp))
            Switch(checked = row.enabled, onCheckedChange = onToggle)
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Действия с промптом")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Дублировать") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                        onClick = { menuOpen = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text("В начало") },
                        leadingIcon = { Icon(Icons.Outlined.KeyboardDoubleArrowUp, null) },
                        onClick = { menuOpen = false; onMoveTop() }
                    )
                    DropdownMenuItem(
                        text = { Text("В конец") },
                        leadingIcon = { Icon(Icons.Outlined.KeyboardDoubleArrowDown, null) },
                        onClick = { menuOpen = false; onMoveBottom() }
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}
