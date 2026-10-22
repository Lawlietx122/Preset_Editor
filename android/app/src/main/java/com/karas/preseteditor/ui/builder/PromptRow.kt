package com.karas.preseteditor.ui.builder

import com.karas.preseteditor.data.PresetJson
import com.karas.preseteditor.data.SectionInfo
import com.karas.preseteditor.data.boolOrNull
import com.karas.preseteditor.data.intOrNull
import com.karas.preseteditor.data.string
import kotlinx.serialization.json.JsonObject

/** What one line of the prompt list needs to know. */
data class PromptRow(
    val id: String,
    val name: String,
    val role: String,
    val enabled: Boolean,
    val inChat: Boolean,
    val depth: Int,
    val chars: Int,
    val marker: Boolean,
    val section: SectionInfo?,
    val content: String
) {
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return id.lowercase().contains(q) || name.lowercase().contains(q) ||
            role.lowercase().contains(q) || content.lowercase().contains(q)
    }

    /** "system · в чате, глубина 4 · 78 симв." */
    val summary: String
        get() = buildList {
            add(role)
            add(if (inChat) "в чате, глубина $depth" else "относительно")
            if (marker) add("маркер") else add("$chars симв.")
        }.joinToString(" · ")

    companion object {
        fun from(preset: JsonObject, prompt: JsonObject): PromptRow {
            val content = prompt.string("content")
            return PromptRow(
                id = PresetJson.id(prompt),
                name = prompt.string("name").ifBlank { PresetJson.id(prompt) },
                role = prompt.string("role").ifBlank { "system" },
                enabled = PresetJson.isEnabled(preset, prompt),
                inChat = (prompt.intOrNull("injection_position") ?: 0) == 1,
                depth = prompt.intOrNull("injection_depth") ?: 4,
                chars = content.length,
                marker = prompt.boolOrNull("marker") == true,
                section = PresetJson.sectionInfo(prompt),
                content = content
            )
        }
    }
}
