package com.karas.preseteditor.crash

import android.content.Context
import android.os.Build
import com.karas.preseteditor.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the stack trace of the last crash in a file, so the next launch can show it and the
 * user can send it without adb.
 */
object CrashLog {
    private const val FILE_NAME = "last-crash.txt"

    fun install(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                file.writeText(
                    "Preset Editor ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), " +
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "$time, thread ${thread.name}\n\n$trace"
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        File(context.filesDir, FILE_NAME).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
