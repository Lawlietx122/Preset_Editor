@file:OptIn(ExperimentalMaterial3Api::class)

package com.karas.preseteditor.ui.builder

import android.app.Application
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karas.preseteditor.data.PresetJson
import com.karas.preseteditor.ui.common.Files
import com.karas.preseteditor.ui.common.formatPrompts
import kotlinx.coroutines.launch

@Composable
fun BuilderScreen(presetId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: BuilderViewModel = viewModel(
        key = "builder-$presetId",
        factory = BuilderViewModel.Factory(context.applicationContext as Application, presetId)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val text = pendingExport
        pendingExport = null
        if (uri == null || text == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { Files.writeText(context, uri, text) }
                .onSuccess { viewModel.message("Сохранено: ${Files.displayName(context, uri)}") }
                .onFailure { viewModel.message("Не удалось сохранить файл") }
        }
    }

    val preset = state.preset
    val metadata = state.metadata
    val close = {
        viewModel.flush()
        onBack()
    }
    val edit: (String?, (kotlinx.serialization.json.JsonObject) -> kotlinx.serialization.json.JsonObject) -> Unit =
        { key, transform -> viewModel.edit(key, transform) }

    val openId = editingId
    if (preset != null && openId != null) {
        PromptEditor(
            preset = preset,
            promptId = openId,
            canUndo = state.canUndo,
            undoVersion = state.undoVersion,
            edit = edit,
            onUndo = viewModel::undo,
            onOpenPrompt = { editingId = it },
            onClose = { editingId = null }
        )
        return
    }

    BackHandler(onBack = close)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(metadata?.title ?: "Пресет", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        metadata?.let {
                            Text(
                                formatPrompts(it.stats),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = close) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "К библиотеке") }
                },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Отменить")
                    }
                    IconButton(onClick = {
                        viewModel.export { name, text -> scope.launch { Files.shareJson(context, name, text) } }
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Поделиться JSON")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Ещё") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Сохранить JSON в файл") },
                                leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.export { name, text ->
                                        pendingExport = text
                                        exportLauncher.launch(name)
                                    }
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (tab == 0 && preset != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        var newId: String? = null
                        viewModel.edit(null) { current -> PresetJson.addPrompt(current).let { (next, id) -> newId = id; next } }
                        editingId = newId
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Промпт") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Промпты") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Инфо") })
            }
            when {
                state.error != null -> Text(
                    state.error ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp)
                )
                preset == null || metadata == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                tab == 0 -> PromptListTab(
                    preset = preset,
                    edit = edit,
                    onOpenPrompt = { editingId = it }
                )
                else -> InfoTab(
                    metadata = metadata,
                    coverFile = state.coverFile,
                    coverVersion = state.coverVersion,
                    onChange = viewModel::updateMetadata,
                    onCover = viewModel::setCover,
                    onRemoveCover = viewModel::removeCover,
                    onError = viewModel::message
                )
            }
        }
    }
}
