package com.karas.preseteditor

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karas.preseteditor.ui.builder.BuilderScreen
import com.karas.preseteditor.ui.common.Files
import com.karas.preseteditor.ui.library.LibraryScreen
import com.karas.preseteditor.ui.library.LibraryViewModel
import com.karas.preseteditor.ui.theme.PresetEditorTheme
import kotlinx.coroutines.flow.MutableStateFlow

/** A preset handed to the app from outside: "Open with", "Share to", or shared text. */
sealed interface IncomingPreset {
    data class FromUri(val uri: Uri) : IncomingPreset
    data class FromText(val text: String) : IncomingPreset
}

class MainActivity : ComponentActivity() {
    private val incoming = MutableStateFlow<IncomingPreset?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            PresetEditorTheme {
                AppRoot(incoming)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { incoming.value = IncomingPreset.FromUri(it) }
            Intent.ACTION_SEND -> {
                val stream = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> incoming.value = IncomingPreset.FromUri(stream)
                    !text.isNullOrBlank() -> incoming.value = IncomingPreset.FromText(text)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(incoming: MutableStateFlow<IncomingPreset?>) {
    val context = LocalContext.current
    val libraryViewModel: LibraryViewModel = viewModel()
    var openPresetId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(incoming) {
        incoming.collect { item ->
            item ?: return@collect
            incoming.value = null
            when (item) {
                is IncomingPreset.FromUri -> runCatching { Files.readText(context, item.uri) }
                    .onSuccess { text -> libraryViewModel.import(Files.displayName(context, item.uri), text) { openPresetId = it } }
                    .onFailure { libraryViewModel.message("Не удалось прочитать файл") }
                is IncomingPreset.FromText -> libraryViewModel.import("shared-preset.json", item.text) { openPresetId = it }
            }
        }
    }

    val presetId = openPresetId
    if (presetId == null) {
        LibraryScreen(libraryViewModel, onOpen = { openPresetId = it })
    } else {
        BuilderScreen(presetId, onBack = { openPresetId = null })
    }
}
