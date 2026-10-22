package com.karas.preseteditor.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.karas.preseteditor.data.PresetStatus
import com.karas.preseteditor.data.PresetStats

fun ruPlural(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

fun formatPrompts(stats: PresetStats): String =
    "${stats.promptCount} ${ruPlural(stats.promptCount, "промпт", "промпта", "промптов")} · ${stats.enabledCount} вкл."

@Composable
fun StatusBadge(status: PresetStatus) {
    val colors = MaterialTheme.colorScheme
    val (background, foreground) = when (status) {
        PresetStatus.Ready -> colors.primaryContainer to colors.onPrimaryContainer
        PresetStatus.Template -> colors.secondaryContainer to colors.onSecondaryContainer
        PresetStatus.Archived -> colors.surfaceVariant to colors.onSurfaceVariant
        PresetStatus.Draft -> Color.Transparent to colors.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = background,
        contentColor = foreground,
        border = if (status == PresetStatus.Draft) androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant) else null
    ) {
        Text(status.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}
