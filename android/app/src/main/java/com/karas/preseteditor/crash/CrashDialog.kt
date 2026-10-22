package com.karas.preseteditor.crash

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karas.preseteditor.ui.common.Files
import com.karas.preseteditor.ui.theme.ContentTextStyle

/** Shown on launch when the previous run crashed. */
@Composable
fun CrashReportDialog() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var report by remember { mutableStateOf(CrashLog.read(context)) }
    val text = report ?: return
    val dismiss = {
        CrashLog.clear(context)
        report = null
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Приложение вылетало") },
        text = {
            SelectionContainer(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Отправьте этот текст разработчику, он покажет, где ошибка.\n\n$text",
                    style = ContentTextStyle.copy(fontSize = 12.sp, lineHeight = 16.sp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { Files.shareText(context, text) }) { Text("Поделиться") }
        },
        dismissButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(text))
                dismiss()
            }) { Text("Скопировать") }
        }
    )
}
