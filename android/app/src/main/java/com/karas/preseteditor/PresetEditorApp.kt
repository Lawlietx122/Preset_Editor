package com.karas.preseteditor

import android.app.Application
import com.karas.preseteditor.crash.CrashLog

class PresetEditorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
