package com.karas.preseteditor.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

enum class PresetStatus(val key: String, val label: String) {
    Draft("Draft", "Черновик"),
    Ready("Ready", "Готов"),
    Template("Template", "Шаблон"),
    Archived("Archived", "Архив");

    companion object {
        fun from(value: String): PresetStatus = entries.firstOrNull { it.key == value } ?: Draft
    }
}

/**
 * Library metadata, stored next to the preset like the desktop app does
 * (`Metadata/<id>.meta.json`, schema version 3). Fields the mobile app does not edit yet
 * (favorites, cover crop, series) are kept in [extra] and written back untouched.
 */
data class PresetMetadata(
    val presetId: String,
    val sourceFileName: String = "",
    val displayName: String = "",
    val author: String = "",
    val authorLink: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val status: PresetStatus = PresetStatus.Draft,
    val presetRelativePath: String = "",
    val coverRelativePath: String = "",
    val stats: PresetStats = PresetStats(),
    val createdAt: String = now(),
    val updatedAt: String = now(),
    val extra: JsonObject = JsonObject(emptyMap())
) {
    val title: String
        get() = displayName.ifBlank { displayNameFromSource(sourceFileName).ifBlank { "Без названия" } }

    fun toJson(): JsonObject {
        val known = buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("presetId", presetId)
            put("sourceFileName", sourceFileName)
            put("displayName", displayName)
            put("author", author)
            put("description", description)
            put("tags", JsonArray(tags.map { JsonPrimitive(it) }))
            put("status", status.key)
            put("authorLink", authorLink)
            put("presetRelativePath", presetRelativePath)
            put("coverRelativePath", coverRelativePath)
            put("stats", buildJsonObject {
                put("promptCount", stats.promptCount)
                put("enabledCount", stats.enabledCount)
                put("disabledCount", stats.disabledCount)
                put("variableCount", stats.variableCount)
            })
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }
        val merged = LinkedHashMap<String, JsonElement>()
        merged.putAll(known)
        extra.forEach { (key, value) -> if (key !in merged) merged[key] = value }
        if ("favoritePrompts" !in merged) merged["favoritePrompts"] = JsonArray(emptyList())
        if ("favoritePromptIds" !in merged) merged["favoritePromptIds"] = JsonArray(emptyList())
        if ("seriesId" !in merged) merged["seriesId"] = JsonNull
        return JsonObject(merged)
    }

    companion object {
        const val SCHEMA_VERSION = 3

        private val KNOWN_KEYS = setOf(
            "schemaVersion", "presetId", "sourceFileName", "displayName", "author", "description", "tags",
            "status", "authorLink", "presetRelativePath", "coverRelativePath", "stats", "createdAt", "updatedAt"
        )

        fun now(): String = Instant.now().toString()

        fun fromJson(presetId: String, value: JsonObject): PresetMetadata {
            val stats = value.objectOrNull("stats")
            return PresetMetadata(
                presetId = presetId,
                sourceFileName = value.string("sourceFileName"),
                displayName = value.string("displayName"),
                author = value.string("author"),
                authorLink = value.string("authorLink"),
                description = value.string("description"),
                tags = value.arrayOrEmpty("tags")
                    .mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
                    .filter { it.isNotEmpty() },
                status = PresetStatus.from(value.string("status")),
                presetRelativePath = value.string("presetRelativePath"),
                coverRelativePath = value.string("coverRelativePath"),
                stats = PresetStats(
                    promptCount = stats?.intOrNull("promptCount") ?: 0,
                    enabledCount = stats?.intOrNull("enabledCount") ?: 0,
                    disabledCount = stats?.intOrNull("disabledCount") ?: 0,
                    variableCount = stats?.intOrNull("variableCount") ?: 0
                ),
                createdAt = value.string("createdAt").ifEmpty { now() },
                updatedAt = value.string("updatedAt").ifEmpty { now() },
                extra = JsonObject(value.filterKeys { it !in KNOWN_KEYS })
            )
        }

        /** "my-cool_preset.json" -> "my cool preset", as in the desktop app. */
        fun displayNameFromSource(sourceFileName: String): String {
            val base = sourceFileName.substringAfterLast('/').substringAfterLast('\\')
            val withoutExt = if (base.contains('.')) base.substringBeforeLast('.') else base
            return withoutExt.replace(Regex("[-_]+"), " ").trim()
        }
    }
}
