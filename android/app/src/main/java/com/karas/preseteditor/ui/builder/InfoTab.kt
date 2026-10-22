package com.karas.preseteditor.ui.builder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karas.preseteditor.data.PresetMetadata
import com.karas.preseteditor.data.PresetStats
import com.karas.preseteditor.data.PresetStatus
import com.karas.preseteditor.ui.common.CoverImage
import com.karas.preseteditor.ui.common.Files
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoTab(
    metadata: PresetMetadata,
    coverFile: File?,
    coverVersion: Long,
    onChange: ((PresetMetadata) -> PresetMetadata) -> Unit,
    onCover: (ByteArray, String) -> Unit,
    onRemoveCover: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { Files.readBytes(context, uri) to Files.extensionFor(context, uri) }
                .onSuccess { (bytes, ext) -> onCover(bytes, ext) }
                .onFailure { onError("Не удалось открыть изображение") }
        }
    }
    val launchPicker = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    // Local copies keep typing smooth; they are written to metadata on every change.
    var name by remember(metadata.presetId) { mutableStateOf(metadata.displayName) }
    var author by remember(metadata.presetId) { mutableStateOf(metadata.author) }
    var link by remember(metadata.presetId) { mutableStateOf(metadata.authorLink) }
    var tags by remember(metadata.presetId) { mutableStateOf(metadata.tags.joinToString(", ")) }
    var description by remember(metadata.presetId) { mutableStateOf(metadata.description) }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = launchPicker),
            contentAlignment = Alignment.Center
        ) {
            if (coverFile != null) {
                CoverImage(coverFile, coverVersion, 1200, Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Добавить обложку", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (coverFile != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = launchPicker) { Text("Сменить обложку") }
                TextButton(onClick = onRemoveCover) { Text("Убрать") }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { value -> name = value; onChange { it.copy(displayName = value) } },
            label = { Text("Название пресета") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Статус", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PresetStatus.entries.forEach { status ->
                FilterChip(
                    selected = metadata.status == status,
                    onClick = { onChange { it.copy(status = status) } },
                    label = { Text(status.label) }
                )
            }
        }

        OutlinedTextField(
            value = author,
            onValueChange = { value -> author = value; onChange { it.copy(author = value) } },
            label = { Text("Автор") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = link,
            onValueChange = { value -> link = value; onChange { it.copy(authorLink = value) } },
            label = { Text("Ссылка на автора") },
            placeholder = { Text("https://…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = tags,
            onValueChange = { value ->
                tags = value
                onChange { it.copy(tags = value.split(',').map(String::trim).filter(String::isNotEmpty)) }
            },
            label = { Text("Теги через запятую") },
            placeholder = { Text("roleplay, style, system") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = description,
            onValueChange = { value -> description = value; onChange { it.copy(description = value) } },
            label = { Text("Описание") },
            placeholder = { Text("Для чего этот пресет…") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        StatsCard(metadata.stats, metadata.sourceFileName)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatsCard(stats: PresetStats, sourceFileName: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Сводка", style = MaterialTheme.typography.titleMedium)
            StatLine("Промптов", stats.promptCount.toString())
            StatLine("Включено", stats.enabledCount.toString())
            StatLine("Выключено", stats.disabledCount.toString())
            StatLine("Переменных", stats.variableCount.toString())
            if (sourceFileName.isNotBlank()) StatLine("Исходный файл", sourceFileName)
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
