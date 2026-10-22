@file:OptIn(ExperimentalMaterial3Api::class)

package com.karas.preseteditor.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karas.preseteditor.data.LibraryItem
import com.karas.preseteditor.data.PresetStatus
import com.karas.preseteditor.ui.common.CoverImage
import com.karas.preseteditor.ui.common.Files
import com.karas.preseteditor.ui.common.StatusBadge
import com.karas.preseteditor.ui.common.formatPrompts
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onOpen: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pendingExport by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmDelete by remember { mutableStateOf<LibraryItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { Files.readText(context, uri) }
                .onSuccess { text -> viewModel.import(Files.displayName(context, uri), text, onOpen) }
                .onFailure { viewModel.message("Не удалось прочитать файл") }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val export = pendingExport
        pendingExport = null
        if (uri == null || export == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { Files.writeText(context, uri, export.second) }
                .onSuccess { viewModel.message("Сохранено: ${Files.displayName(context, uri)}") }
                .onFailure { viewModel.message("Не удалось сохранить файл") }
        }
    }
    val openImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Пресеты") },
                actions = {
                    IconButton(onClick = openImport) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = "Импорт JSON")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.create(onOpen) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Новый пресет") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.items.isNotEmpty()) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Поиск по названию, тегам, автору") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Очистить")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(selected = state.status == null, onClick = { viewModel.setStatus(null) }, label = { Text("Все") })
                    }
                    items(PresetStatus.entries) { status ->
                        FilterChip(
                            selected = state.status == status,
                            onClick = { viewModel.setStatus(if (state.status == status) null else status) },
                            label = { Text(status.label) }
                        )
                    }
                }
            }

            when {
                !state.loaded -> Unit
                state.items.isEmpty() -> EmptyLibrary(onCreate = { viewModel.create(onOpen) }, onImport = openImport)
                state.visibleItems.isEmpty() -> Text(
                    "Ничего не найдено",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.visibleItems, key = { it.presetId }) { item ->
                        LibraryRow(
                            item = item,
                            onOpen = { if (item.presetExists) onOpen(item.presetId) else viewModel.message("Файл пресета не найден") },
                            onExport = {
                                viewModel.export(item.presetId) { name, text ->
                                    pendingExport = name to text
                                    exportLauncher.launch(name)
                                }
                            },
                            onShare = {
                                viewModel.export(item.presetId) { name, text -> scope.launch { Files.shareJson(context, name, text) } }
                            },
                            onDuplicate = { viewModel.duplicate(item.presetId) },
                            onDelete = { confirmDelete = item }
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Удалить пресет?") },
            text = { Text("«${item.metadata.title}» исчезнет из библиотеки. Копия JSON останется в папке Backups.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item.presetId)
                    confirmDelete = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun LibraryRow(
    item: LibraryItem,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val meta = item.metadata
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (item.coverFile != null) {
                    CoverImage(item.coverFile, 0, 256, Modifier.fillMaxSize())
                } else {
                    Icon(
                        Icons.AutoMirrored.Outlined.Article,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    meta.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (item.presetExists) formatPrompts(meta.stats) else "Файл пресета не найден",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(meta.status)
                    if (meta.tags.isNotEmpty()) {
                        Text(
                            meta.tags.joinToString(", "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Действия")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Сохранить JSON в файл") },
                        leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                        enabled = item.presetExists,
                        onClick = { menuOpen = false; onExport() }
                    )
                    DropdownMenuItem(
                        text = { Text("Поделиться") },
                        leadingIcon = { Icon(Icons.Outlined.Share, null) },
                        enabled = item.presetExists,
                        onClick = { menuOpen = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text("Дублировать") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                        enabled = item.presetExists,
                        onClick = { menuOpen = false; onDuplicate() }
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

@Composable
private fun EmptyLibrary(onCreate: () -> Unit, onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Article,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("Пока нет пресетов", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Импортируйте JSON из SillyTavern или с компьютера, либо начните с пустого пресета.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Outlined.FileUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Импортировать JSON")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Создать пустой пресет")
        }
    }
}
