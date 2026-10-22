package com.karas.preseteditor.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karas.preseteditor.data.AppServices
import com.karas.preseteditor.data.LibraryItem
import com.karas.preseteditor.data.PresetStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryState(
    val items: List<LibraryItem> = emptyList(),
    val loaded: Boolean = false,
    val query: String = "",
    val status: PresetStatus? = null
) {
    val visibleItems: List<LibraryItem>
        get() {
            val q = query.trim().lowercase()
            return items.filter { item ->
                val meta = item.metadata
                (status == null || meta.status == status) &&
                    (q.isEmpty() || listOf(meta.title, meta.author, meta.description, meta.sourceFileName)
                        .plus(meta.tags)
                        .any { it.lowercase().contains(q) })
            }
        }
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val workspace = AppServices.workspace(application)

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val items = withContext(AppServices.workspaceDispatcher) { workspace.list() }
            _state.update { it.copy(items = items, loaded = true) }
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setStatus(status: PresetStatus?) = _state.update { it.copy(status = status) }

    fun create(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = withContext(AppServices.workspaceDispatcher) { workspace.createPreset() }
            refresh()
            onCreated(id)
        }
    }

    fun import(fileName: String, text: String, onImported: (String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(AppServices.workspaceDispatcher) { runCatching { workspace.importPreset(fileName, text) } }
            result.onSuccess { id ->
                refresh()
                _messages.send("Пресет импортирован")
                onImported(id)
            }.onFailure { error ->
                _messages.send("Не удалось импортировать: ${error.message ?: "неизвестная ошибка"}")
            }
        }
    }

    fun duplicate(presetId: String) {
        viewModelScope.launch {
            val result = withContext(AppServices.workspaceDispatcher) { runCatching { workspace.duplicatePreset(presetId) } }
            refresh()
            _messages.send(if (result.isSuccess) "Копия создана" else "Не удалось создать копию")
        }
    }

    fun delete(presetId: String) {
        viewModelScope.launch {
            withContext(AppServices.workspaceDispatcher) { runCatching { workspace.deletePreset(presetId) } }
            refresh()
            _messages.send("Пресет удалён, копия JSON сохранена в Backups")
        }
    }

    fun export(presetId: String, onReady: (fileName: String, text: String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(AppServices.workspaceDispatcher) { runCatching { workspace.exportPreset(presetId) } }
            result.onSuccess { (name, text) -> onReady(name, text) }
                .onFailure { _messages.send("Не удалось подготовить экспорт") }
        }
    }

    fun message(text: String) {
        viewModelScope.launch { _messages.send(text) }
    }
}
