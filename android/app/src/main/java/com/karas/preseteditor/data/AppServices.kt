package com.karas.preseteditor.data

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Process-wide singletons. The app is small enough not to need a DI framework. */
object AppServices {
    @Volatile
    private var workspace: Workspace? = null

    /**
     * All workspace file access runs on this one-at-a-time dispatcher, so a save queued when a
     * screen closes always lands before the next screen reads the library.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val workspaceDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    /** Outlives screens, so pending saves still finish after a ViewModel is cleared. */
    val ioScope = CoroutineScope(SupervisorJob() + workspaceDispatcher)

    fun workspace(context: Context): Workspace =
        workspace ?: synchronized(this) {
            workspace ?: Workspace(File(context.applicationContext.filesDir, Workspace.FOLDER_NAME)).also { workspace = it }
        }
}
