package com.karas.preseteditor.ui.common

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Reading and writing user files through the Storage Access Framework and the share sheet. */
object Files {

    fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index)?.let { return it }
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "preset.json"
    }

    suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("Не удалось открыть файл")
    }

    suspend fun readBytes(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Не удалось открыть файл")
    }

    suspend fun writeText(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("Не удалось записать файл")
    }

    fun extensionFor(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri)
        return mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: displayName(context, uri).substringAfterLast('.', "png")
    }

    /** Opens the system share sheet with the preset as a .json attachment. */
    suspend fun shareJson(context: Context, fileName: String, text: String) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            File(dir, fileName).apply { writeText(text) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Отправить пресет"))
    }

    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Отправить отчёт"))
    }
}

/** Loads a cover from disk off the main thread, downsampled to roughly [maxSizePx]. */
@Composable
fun CoverImage(file: File?, version: Long, maxSizePx: Int, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, file?.path, version, file?.lastModified()) {
        value = file?.let { withContext(Dispatchers.IO) { decodeSampled(it, maxSizePx) } }
    }
    bitmap?.let {
        Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    }
}

private fun decodeSampled(file: File, maxSizePx: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSizePx && bounds.outHeight / (sample * 2) >= maxSizePx) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
