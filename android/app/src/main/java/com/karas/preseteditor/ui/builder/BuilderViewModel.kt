package com.karas.preseteditor.ui.builder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karas.preseteditor.data.AppServices
import com.karas.preseteditor.data.PresetJson
import com.karas.preseteditor.data.PresetMetadata
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File

data class BuilderState(
    val preset: JsonObject? = null,
    val metadata: PresetMetadata? = null,
    val coverFile: File? = null,
    val coverVersion: Long = 0,
    val canUndo: Boolean = false,
    val undoVersion: Int = 0,
    val error: String? = null
)

/** Holds one open preset. Every change is saved to the workspace shortly after it is made. */
class BuilderViewModel(application: Application, val presetId: String) : AndroidViewModel(application) {
    private val workspace = AppServices.workspace(application)

    private val _state = MutableStateFlow(BuilderState())
    val state: StateFlow<BuilderState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val undoStack = ArrayDeque<JsonObject>()
    private var lastUndoKey: String? = null
    private var lastUndoAt = 0L

    private var presetDirty = false
    private var metadataDirty = false
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val result = withContext(AppServices.workspaceDispatcher) {
                runCatching {
                    val preset = workspace.loadPreset(presetId)
                    val metadata = workspace.loadMetadata(presetId) ?: workspace.savePreset(presetId, preset)
                    Triple(preset, metadata, workspace.coverFile(metadata))
                }
            }
            result.onSuccess { (preset, metadata, cover) ->
                _state.update { it.copy(preset = preset, metadata = metadata, coverFile = cover) }
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Не удалось открыть пресет") }
            }
        }
    }

    /**
     * Applies a change to the preset. Changes with the same [undoKey] made within a couple of
     * seconds (typing in one field) share a single undo step.
     */
    fun edit(undoKey: String? = null, transform: (JsonObject) -> JsonObject) {
        val current = _state.value.preset ?: return
        val next = transform(current)
        if (next == current) return
        val now = System.currentTimeMillis()
        val coalesce = undoKey != null && undoKey == lastUndoKey && now - lastUndoAt < 2500
        if (!coalesce) {
            undoStack.addLast(current)
            if (undoStack.size > 60) undoStack.removeFirst()
        }
        lastUndoKey = undoKey
        lastUndoAt = now
        presetDirty = true
        _state.update { it.copy(preset = next, canUndo = undoStack.isNotEmpty()) }
        scheduleSave()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        lastUndoKey = null
        presetDirty = true
        _state.update { it.copy(preset = previous, canUndo = undoStack.isNotEmpty(), undoVersion = it.undoVersion + 1) }
        scheduleSave()
    }

    fun updateMetadata(transform: (PresetMetadata) -> PresetMetadata) {
        val current = _state.value.metadata ?: return
        metadataDirty = true
        _state.update { it.copy(metadata = transform(current)) }
        scheduleSave()
    }

    fun setCover(bytes: ByteArray, extension: String) {
        viewModelScope.launch {
            flush()
            val result = withContext(AppServices.workspaceDispatcher) {
                runCatching {
                    val metadata = workspace.setCover(presetId, bytes, extension)
                    metadata to workspace.coverFile(metadata)
                }
            }
            result.onSuccess { (metadata, file) ->
                _state.update { it.copy(metadata = metadata, coverFile = file, coverVersion = it.coverVersion + 1) }
            }.onFailure { _messages.send("Не удалось сохранить обложку") }
        }
    }

    fun removeCover() {
        viewModelScope.launch {
            flush()
            val metadata = withContext(AppServices.workspaceDispatcher) { workspace.removeCover(presetId) } ?: return@launch
            _state.update { it.copy(metadata = metadata, coverFile = null, coverVersion = it.coverVersion + 1) }
        }
    }

    fun export(onReady: (fileName: String, text: String) -> Unit) {
        viewModelScope.launch {
            flush()
            val result = withContext(AppServices.workspaceDispatcher) { runCatching { workspace.exportPreset(presetId) } }
            result.onSuccess { (name, text) -> onReady(name, text) }
                .onFailure { _messages.send("Не удалось подготовить экспорт") }
        }
    }

    fun message(text: String) {
        viewModelScope.launch { _messages.send(text) }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            flush()
        }
    }

    /** Writes pending changes right away (also called when the app goes to background). */
    fun flush() {
        val snapshot = _state.value
        val savePreset = presetDirty
        val saveMetadata = metadataDirty
        presetDirty = false
        metadataDirty = false
        if (!savePreset && !saveMetadata) return
        AppServices.ioScope.launch {
            runCatching {
                var metadata = snapshot.metadata
                if (saveMetadata && metadata != null) metadata = workspace.saveMetadata(metadata)
                if (savePreset && snapshot.preset != null) {
                    val saved = workspace.savePreset(presetId, snapshot.preset)
                    _state.update { state ->
                        state.copy(metadata = state.metadata?.copy(stats = saved.stats, presetRelativePath = saved.presetRelativePath))
                    }
                }
            }.onFailure {
                _messages.trySend("Не удалось сохранить изменения")
            }
        }
    }

    override fun onCleared() {
        saveJob?.cancel()
        flush()
        super.onCleared()
    }

    class Factory(private val application: Application, private val presetId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BuilderViewModel(application, presetId) as T
    }

    companion object {
        /** Prompt list as the user sees it, ready for the UI. */
        fun rows(preset: JsonObject): List<PromptRow> = PresetJson.orderedPrompts(preset).map { prompt ->
            PromptRow.from(preset, prompt)
        }
    }
}
