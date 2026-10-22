package com.karas.preseteditor.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Operations on a SillyTavern chat-completion preset, kept as a raw [JsonObject] so that
 * every field the editor does not know about survives a round trip. Mirrors the desktop
 * editor: prompts live in `prompts`, their order and enabled state in `prompt_order[0].order`.
 */
object PresetJson {

    val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        isLenient = true
    }

    const val DEFAULT_CHARACTER_ID = 100001

    // Android uses ICU regexes, which need every literal "{" and "}" escaped, even inside [].
    private val VARIABLE_REGEX = Regex(
        """\{\{\s*(?:getvar|setvar|addvar|incvar|decvar|flushvar|getglobalvar|setglobalvar|addglobalvar|incglobalvar|decglobalvar|flushglobalvar)::\s*([^\}:]+)[^\}]*\}\}""",
        RegexOption.IGNORE_CASE
    )

    // ---- parsing ---------------------------------------------------------------

    /** Parses preset text. Throws [IllegalArgumentException] when it is not a preset. */
    fun parse(text: String): JsonObject {
        val element = try {
            json.parseToJsonElement(text.removePrefix("﻿"))
        } catch (error: Exception) {
            throw IllegalArgumentException("Файл не является JSON", error)
        }
        val obj = element as? JsonObject ?: throw IllegalArgumentException("В файле нет объекта пресета")
        if (obj["prompts"] !is JsonArray) throw IllegalArgumentException("В пресете нет массива prompts")
        return normalize(obj)
    }

    fun encode(preset: JsonObject): String = json.encodeToString(JsonObject.serializer(), preset)

    fun defaultPreset(): JsonObject = buildJsonObject {
        put("impersonation_prompt", "[Write your next reply from the point of view of {{user}}, using the chat history so far as a guideline for the writing style of {{user}}. Don't write as {{char}} or system. Don't describe actions of {{char}}.]")
        put("new_chat_prompt", "[Start a new Chat]")
        put("new_group_chat_prompt", "[Start a new group chat. Group members: {{group}}]")
        put("new_example_chat_prompt", "[Example Chat]")
        put("continue_nudge_prompt", "[Continue your last message without repeating its original content.]")
        put("scenario_format", "{{scenario}}")
        put("personality_format", "{{personality}}")
        put("group_nudge_prompt", "[Write the next reply only as {{char}}.]")
        put("wi_format", "{0}")
        put("prompts", JsonArray(emptyList()))
        put("prompt_order", JsonArray(listOf(orderContainer(emptyList()))))
    }

    /** Fields the desktop app stores in metadata; they never belong in an exported preset. */
    fun cleanForExport(preset: JsonObject): JsonObject = JsonObject(
        preset.filterKeys {
            it !in setOf("presetId", "metadata", "schemaVersion", "coverRelativePath", "favoritePrompts", "favoritePromptIds")
        }
    )

    // ---- reading -----------------------------------------------------------------

    fun prompts(preset: JsonObject): List<JsonObject> =
        (preset["prompts"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    private fun orderContainerOf(preset: JsonObject): JsonObject? =
        ((preset["prompt_order"] as? JsonArray)?.firstOrNull() as? JsonObject)

    fun orderEntries(preset: JsonObject): List<JsonObject> =
        (orderContainerOf(preset)?.get("order") as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    fun id(prompt: JsonObject): String = prompt.string("identifier")

    fun prompt(preset: JsonObject, id: String): JsonObject? = prompts(preset).firstOrNull { id(it) == id }

    fun orderedPrompts(preset: JsonObject): List<JsonObject> {
        val byId = LinkedHashMap<String, JsonObject>()
        prompts(preset).forEach { byId[id(it)] = it }
        val result = ArrayList<JsonObject>(byId.size)
        val used = HashSet<String>()
        orderEntries(preset).forEach { entry ->
            val entryId = entry.string("identifier")
            val prompt = byId[entryId]
            if (prompt != null && used.add(entryId)) result.add(prompt)
        }
        byId.values.forEach { if (used.add(id(it))) result.add(it) }
        return result
    }

    fun isEnabled(preset: JsonObject, prompt: JsonObject): Boolean {
        val promptId = id(prompt)
        val entry = orderEntries(preset).firstOrNull { it.string("identifier") == promptId }
        entry?.boolOrNull("enabled")?.let { return it }
        return prompt.boolOrNull("enabled") ?: true
    }

    fun stats(preset: JsonObject): PresetStats {
        val list = prompts(preset)
        val enabled = list.count { isEnabled(preset, it) }
        val variables = HashSet<String>()
        list.forEach { prompt ->
            VARIABLE_REGEX.findAll(prompt.string("content")).forEach { match ->
                val name = match.groupValues[1].trim()
                if (name.isNotEmpty()) variables.add(name.lowercase())
            }
        }
        return PresetStats(list.size, enabled, (list.size - enabled).coerceAtLeast(0), variables.size)
    }

    // ---- sections ------------------------------------------------------------------

    private val SECTION_EDGE = Regex("""^[\s=:#*._|+~<>\{\}\[\]()\\/\-–—─-╿]+|[\s=:#*._|+~<>\{\}\[\]()\\/\-–—─-╿]+$""")
    private val STRONG_PREFIX = Regex("""^(={2,}|#{2,}|[-_*~]{3,}|[─-╿]+)""")
    private val WRAPPED = Regex("""^(={2,}|#{2,}|[-_*~]{3,}|[─-╿]+).+?(={2,}|#{2,}|[-_*~]{3,}|[─-╿]+)$""")
    private val ICON_DASH = Regex("""^[^\w\s]{1,4}\s*[-–—─-╿]\s*\S""")

    /** Same heuristic as the desktop app: prompts named like "=== Style ===" start a section. */
    fun sectionInfo(prompt: JsonObject): SectionInfo? {
        val name = prompt.string("name").ifBlank { id(prompt) }.trim()
        if (name.isEmpty()) return null
        if (!STRONG_PREFIX.containsMatchIn(name) && !WRAPPED.matches(name) && !ICON_DASH.containsMatchIn(name)) return null
        val title = name.replace(SECTION_EDGE, "").trim().ifEmpty { name }
        val level = when {
            name.startsWith("===") || name.startsWith("━") || name.startsWith("┃") -> 1
            name.startsWith("==") -> 2
            else -> 3
        }
        return SectionInfo(title, level)
    }

    // ---- writing -------------------------------------------------------------------

    /** Makes identifiers unique, fills names and rebuilds `prompt_order` (desktop normalizePreset). */
    fun normalize(preset: JsonObject): JsonObject {
        val seen = HashSet<String>()
        val fixed = (preset["prompts"] as? JsonArray ?: JsonArray(emptyList())).mapIndexed { index, element ->
            val prompt = element as? JsonObject ?: JsonObject(emptyMap())
            var promptId = prompt.string("identifier").ifEmpty { "prompt-${index + 1}" }
            if (promptId in seen) promptId = "$promptId-${index + 1}"
            seen.add(promptId)
            prompt.with("identifier", JsonPrimitive(promptId))
                .let { if (it.string("name").isEmpty()) it.with("name", JsonPrimitive(promptId)) else it }
        }
        return sync(preset.with("prompts", JsonArray(fixed)))
    }

    /**
     * Port of the desktop `syncPromptOrder`: puts `prompts` in display order, writes one order
     * entry per prompt (keeping extra fields of old entries), mirrors `enabled` into both places
     * and keeps order entries that point at prompts this preset does not define.
     */
    fun sync(preset: JsonObject, orderedIds: List<String>? = null): JsonObject {
        val prompts = prompts(preset)
        val promptById = LinkedHashMap<String, JsonObject>().apply { prompts.forEach { put(id(it), it) } }
        val oldEntries = orderEntries(preset)
        val entryById = HashMap<String, JsonObject>().apply {
            oldEntries.forEach { entry -> entry.string("identifier").takeIf { it.isNotEmpty() }?.let { put(it, entry) } }
        }
        val ids = (orderedIds?.takeIf { it.isNotEmpty() } ?: orderedPrompts(preset).map(::id)).toMutableList()
        prompts.forEach { if (id(it) !in ids) ids.add(id(it)) }

        val newPrompts = ArrayList<JsonElement>()
        val newOrder = ArrayList<JsonElement>()
        val used = HashSet<String>()
        ids.forEach { promptId ->
            val prompt = promptById[promptId] ?: return@forEach
            if (!used.add(promptId)) return@forEach
            val oldEntry = entryById[promptId]
            val enabled = oldEntry?.boolOrNull("enabled") ?: prompt.boolOrNull("enabled") ?: true
            newPrompts.add(prompt.with("enabled", JsonPrimitive(enabled)))
            newOrder.add(
                (oldEntry ?: JsonObject(emptyMap()))
                    .with("identifier", JsonPrimitive(promptId))
                    .with("enabled", JsonPrimitive(enabled))
            )
        }
        oldEntries.forEach { entry ->
            val entryId = entry.string("identifier")
            if (entryId.isNotEmpty() && entryId !in promptById && newOrder.none { (it as JsonObject).string("identifier") == entryId }) {
                newOrder.add(entry)
            }
        }

        val container = (orderContainerOf(preset) ?: orderContainer(emptyList())).with("order", JsonArray(newOrder))
        val orderList = (preset["prompt_order"] as? JsonArray)?.toMutableList() ?: mutableListOf()
        if (orderList.isEmpty()) orderList.add(container) else orderList[0] = container
        return preset
            .with("prompts", JsonArray(newPrompts))
            .with("prompt_order", JsonArray(orderList))
    }

    fun setEnabled(preset: JsonObject, promptId: String, enabled: Boolean): JsonObject {
        val withPrompt = replacePrompt(preset, promptId) { it.with("enabled", JsonPrimitive(enabled)) }
        val entries = orderEntries(withPrompt).map { entry ->
            if (entry.string("identifier") == promptId) entry.with("enabled", JsonPrimitive(enabled)) else entry
        }
        return sync(withOrderEntries(withPrompt, entries))
    }

    fun updatePrompt(preset: JsonObject, promptId: String, transform: (JsonObject) -> JsonObject): JsonObject =
        replacePrompt(preset, promptId, transform)

    fun addPrompt(preset: JsonObject, afterId: String? = null, source: JsonObject? = null): Pair<JsonObject, String> {
        val base = source ?: buildJsonObject {
            put("name", "New Prompt")
            put("system_prompt", false)
            put("marker", false)
            put("content", "")
            put("role", "system")
            put("injection_position", 0)
            put("injection_depth", 4)
            put("injection_order", 100)
            put("forbid_overrides", false)
        }
        val newId = uniqueIdentifier(preset, base.string("identifier").ifEmpty { base.string("name").ifEmpty { "prompt" } })
        var prompt = base.with("identifier", JsonPrimitive(newId))
        if (prompt.string("name").isEmpty()) prompt = prompt.with("name", JsonPrimitive(newId))
        if (prompt.boolOrNull("enabled") == null) prompt = prompt.with("enabled", JsonPrimitive(true))

        val order = orderedPrompts(preset).map(::id).toMutableList()
        val insertAt = afterId?.let { order.indexOf(it) }?.takeIf { it >= 0 }?.plus(1) ?: order.size
        order.add(insertAt, newId)
        val withPrompt = preset.with("prompts", JsonArray(prompts(preset) + prompt))
        return sync(withPrompt, order) to newId
    }

    fun duplicatePrompt(preset: JsonObject, promptId: String): Pair<JsonObject, String>? {
        val source = prompt(preset, promptId) ?: return null
        val copy = source
            .with("name", JsonPrimitive("${source.string("name").ifEmpty { promptId }} (copy)"))
            .with("enabled", JsonPrimitive(isEnabled(preset, source)))
        return addPrompt(preset, promptId, copy)
    }

    fun deletePrompt(preset: JsonObject, promptId: String): JsonObject {
        val remaining = prompts(preset).filter { id(it) != promptId }
        val entries = orderEntries(preset).filter { it.string("identifier") != promptId }
        return sync(withOrderEntries(preset.with("prompts", JsonArray(remaining)), entries))
    }

    fun reorder(preset: JsonObject, orderedIds: List<String>): JsonObject = sync(preset, orderedIds)

    fun move(preset: JsonObject, promptId: String, toIndex: Int): JsonObject {
        val ids = orderedPrompts(preset).map(::id).toMutableList()
        if (!ids.remove(promptId)) return preset
        ids.add(toIndex.coerceIn(0, ids.size), promptId)
        return sync(preset, ids)
    }

    fun uniqueIdentifier(preset: JsonObject, base: String): String {
        val used = prompts(preset).map(::id).toHashSet()
        val clean = base.trim()
            .replace(Regex("""\s+"""), "-")
            .replace(Regex("""[^\w-]"""), "")
            .take(48)
            .ifEmpty { "prompt" }
        if (clean !in used) return clean
        var index = 2
        while ("$clean-$index" in used) index++
        return "$clean-$index"
    }

    // ---- helpers -------------------------------------------------------------------

    private fun orderContainer(order: List<JsonElement>): JsonObject = buildJsonObject {
        put("character_id", DEFAULT_CHARACTER_ID)
        put("order", JsonArray(order))
    }

    private fun replacePrompt(preset: JsonObject, promptId: String, transform: (JsonObject) -> JsonObject): JsonObject =
        preset.with("prompts", JsonArray(prompts(preset).map { if (id(it) == promptId) transform(it) else it }))

    private fun withOrderEntries(preset: JsonObject, entries: List<JsonObject>): JsonObject {
        val container = (orderContainerOf(preset) ?: orderContainer(emptyList())).with("order", JsonArray(entries))
        val orderList = (preset["prompt_order"] as? JsonArray)?.toMutableList() ?: mutableListOf()
        if (orderList.isEmpty()) orderList.add(container) else orderList[0] = container
        return preset.with("prompt_order", JsonArray(orderList))
    }
}

data class PresetStats(
    val promptCount: Int = 0,
    val enabledCount: Int = 0,
    val disabledCount: Int = 0,
    val variableCount: Int = 0
)

data class SectionInfo(val title: String, val level: Int)

// ---- JsonObject helpers used across the app ---------------------------------------

/** Returns a copy with [key] set, keeping the original key order. */
fun JsonObject.with(key: String, value: JsonElement): JsonObject {
    val map = LinkedHashMap<String, JsonElement>(this)
    map[key] = value
    return JsonObject(map)
}

fun JsonObject.with(key: String, value: String): JsonObject = with(key, JsonPrimitive(value))
fun JsonObject.with(key: String, value: Boolean): JsonObject = with(key, JsonPrimitive(value))
fun JsonObject.with(key: String, value: Number): JsonObject = with(key, JsonPrimitive(value))

fun JsonObject.string(key: String): String {
    val value = this[key] ?: return ""
    if (value is JsonNull) return ""
    return (value as? JsonPrimitive)?.contentOrNull ?: ""
}

fun JsonObject.boolOrNull(key: String): Boolean? {
    val value = this[key] as? JsonPrimitive ?: return null
    if (value.isString) return null
    return value.booleanOrNull
}

fun JsonObject.intOrNull(key: String): Int? {
    val value = this[key] as? JsonPrimitive ?: return null
    return value.intOrNull ?: value.doubleOrNull?.toInt() ?: value.contentOrNull?.trim()?.toIntOrNull()
}

fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.arrayOrEmpty(key: String): List<JsonElement> = (this[key] as? JsonArray)?.toList() ?: emptyList()
