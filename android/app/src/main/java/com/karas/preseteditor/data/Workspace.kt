package com.karas.preseteditor.data

import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.UUID

data class LibraryItem(
    val presetId: String,
    val metadata: PresetMetadata,
    val coverFile: File?,
    val presetExists: Boolean
)

/**
 * File storage with the same layout as the desktop "Preset Editor Workspace":
 * `Presets/<id>.json`, `Metadata/<id>.meta.json`, `Assets/Covers/<id>.<ext>`, `Backups/`.
 * A desktop workspace folder copied into [root] is picked up as is.
 */
class Workspace(val root: File) {

    private val presetsDir get() = File(root, "Presets")
    private val metadataDir get() = File(root, "Metadata")
    private val coversDir get() = File(root, "Assets/Covers")
    private val backupsDir get() = File(root, "Backups")

    init {
        listOf(presetsDir, metadataDir, coversDir, File(root, "Series"), backupsDir).forEach { it.mkdirs() }
    }

    // ---- library ---------------------------------------------------------------------

    @Synchronized
    fun list(): List<LibraryItem> {
        val items = LinkedHashMap<String, LibraryItem>()
        metadataDir.listFiles().orEmpty().forEach { file ->
            val presetId = file.name.removeSuffix(".meta.json").takeIf { file.name.endsWith(".meta.json") } ?: return@forEach
            if (!isPresetId(presetId)) return@forEach
            var metadata = loadMetadata(presetId) ?: return@forEach
            val presetFile = presetFile(presetId, metadata)
            val exists = presetFile.isFile
            if (exists) {
                val preset = runCatching { PresetJson.parse(presetFile.readText()) }.getOrNull()
                if (preset != null) {
                    val stats = PresetJson.stats(preset)
                    if (stats != metadata.stats || metadata.presetRelativePath.isEmpty()) {
                        metadata = metadata.copy(stats = stats, presetRelativePath = relativePresetPath(presetId))
                        writeMetadata(metadata)
                    }
                }
            }
            items[presetId] = LibraryItem(presetId, metadata, coverFile(metadata), exists)
        }
        presetsDir.listFiles().orEmpty().forEach { file ->
            val presetId = file.name.removeSuffix(".json")
            if (!file.name.endsWith(".json") || !isPresetId(presetId) || presetId in items) return@forEach
            val preset = runCatching { PresetJson.parse(file.readText()) }.getOrNull() ?: return@forEach
            val metadata = PresetMetadata(
                presetId = presetId,
                sourceFileName = file.name,
                displayName = PresetMetadata.displayNameFromSource(file.name),
                presetRelativePath = relativePresetPath(presetId),
                stats = PresetJson.stats(preset)
            )
            writeMetadata(metadata)
            items[presetId] = LibraryItem(presetId, metadata, null, true)
        }
        return items.values.sortedByDescending { it.metadata.updatedAt }
    }

    // ---- presets ---------------------------------------------------------------------

    @Synchronized
    fun loadPreset(presetId: String): JsonObject {
        val metadata = loadMetadata(presetId)
        return PresetJson.parse(presetFile(presetId, metadata).readText())
    }

    /** Writes the preset and refreshes stats/updatedAt in its metadata. */
    @Synchronized
    fun savePreset(presetId: String, preset: JsonObject): PresetMetadata {
        val clean = PresetJson.cleanForExport(preset)
        writeAtomically(File(presetsDir, "$presetId.json"), PresetJson.encode(clean))
        val existing = loadMetadata(presetId)
        val metadata = (existing ?: PresetMetadata(presetId = presetId, sourceFileName = "$presetId.json")).copy(
            presetRelativePath = relativePresetPath(presetId),
            stats = PresetJson.stats(clean),
            updatedAt = PresetMetadata.now()
        )
        writeMetadata(metadata)
        return metadata
    }

    @Synchronized
    fun createPreset(): String {
        val presetId = newId()
        val now = PresetMetadata.now()
        val preset = PresetJson.defaultPreset()
        writeAtomically(File(presetsDir, "$presetId.json"), PresetJson.encode(preset))
        writeMetadata(
            PresetMetadata(
                presetId = presetId,
                sourceFileName = "$presetId.json",
                displayName = "Новый пресет",
                presetRelativePath = relativePresetPath(presetId),
                stats = PresetJson.stats(preset),
                createdAt = now,
                updatedAt = now
            )
        )
        return presetId
    }

    /** Imports preset text; throws [IllegalArgumentException] with a readable message when invalid. */
    @Synchronized
    fun importPreset(fileName: String, text: String): String {
        val preset = PresetJson.cleanForExport(PresetJson.parse(text))
        val presetId = newId()
        val sourceFileName = safeJsonFileName(fileName)
        val now = PresetMetadata.now()
        writeAtomically(File(presetsDir, "$presetId.json"), PresetJson.encode(preset))
        writeMetadata(
            PresetMetadata(
                presetId = presetId,
                sourceFileName = sourceFileName,
                displayName = PresetMetadata.displayNameFromSource(sourceFileName),
                presetRelativePath = relativePresetPath(presetId),
                stats = PresetJson.stats(preset),
                createdAt = now,
                updatedAt = now
            )
        )
        return presetId
    }

    @Synchronized
    fun duplicatePreset(presetId: String): String {
        val source = loadMetadata(presetId)
        val preset = loadPreset(presetId)
        val copyId = newId()
        val now = PresetMetadata.now()
        writeAtomically(File(presetsDir, "$copyId.json"), PresetJson.encode(preset))
        val cover = source?.let { coverFile(it) }?.let { file ->
            val target = File(coversDir, "$copyId.${file.extension}")
            file.copyTo(target, overwrite = true)
            "Assets/Covers/${target.name}"
        } ?: ""
        writeMetadata(
            (source ?: PresetMetadata(presetId = copyId)).copy(
                presetId = copyId,
                displayName = "${source?.title ?: "Пресет"} (копия)",
                presetRelativePath = relativePresetPath(copyId),
                coverRelativePath = cover,
                stats = PresetJson.stats(preset),
                createdAt = now,
                updatedAt = now,
                extra = JsonObject((source?.extra ?: JsonObject(emptyMap())).filterKeys { it != "seriesId" })
            )
        )
        return copyId
    }

    /** Removes the preset, its metadata and cover. A JSON backup is kept in Backups/. */
    @Synchronized
    fun deletePreset(presetId: String) {
        val metadata = loadMetadata(presetId)
        val file = presetFile(presetId, metadata)
        if (file.isFile) {
            val name = safeJsonFileName(metadata?.title ?: presetId).removeSuffix(".json")
            file.copyTo(File(backupsDir, "${name}_deleted_${System.currentTimeMillis()}.json"), overwrite = true)
            file.delete()
        }
        metadata?.let { coverFile(it) }?.delete()
        File(metadataDir, "$presetId.meta.json").delete()
    }

    /** File name and pretty-printed JSON ready to be written outside the app. */
    @Synchronized
    fun exportPreset(presetId: String): Pair<String, String> {
        val metadata = loadMetadata(presetId)
        val preset = PresetJson.cleanForExport(loadPreset(presetId))
        val name = metadata?.displayName?.takeIf { it.isNotBlank() }
            ?: metadata?.sourceFileName?.takeIf { it.isNotBlank() && !isPresetId(it.removeSuffix(".json")) }
            ?: "preset"
        return safeJsonFileName(name) to PresetJson.encode(preset)
    }

    // ---- metadata & covers -------------------------------------------------------------

    @Synchronized
    fun loadMetadata(presetId: String): PresetMetadata? {
        val file = File(metadataDir, "$presetId.meta.json")
        if (!file.isFile) return null
        return runCatching {
            val obj = PresetJson.json.parseToJsonElement(file.readText()) as JsonObject
            PresetMetadata.fromJson(presetId, obj)
        }.getOrNull()
    }

    @Synchronized
    fun saveMetadata(metadata: PresetMetadata): PresetMetadata {
        val updated = metadata.copy(updatedAt = PresetMetadata.now())
        writeMetadata(updated)
        return updated
    }

    @Synchronized
    fun setCover(presetId: String, bytes: ByteArray, extension: String): PresetMetadata {
        val ext = extension.lowercase().trimStart('.').takeIf { it in setOf("png", "jpg", "jpeg", "webp", "gif") } ?: "png"
        coversDir.listFiles().orEmpty().filter { it.name.startsWith("$presetId.") }.forEach { it.delete() }
        val target = File(coversDir, "$presetId.$ext")
        target.writeBytes(bytes)
        val metadata = (loadMetadata(presetId) ?: PresetMetadata(presetId = presetId))
            .copy(coverRelativePath = "Assets/Covers/${target.name}")
        return saveMetadata(metadata)
    }

    @Synchronized
    fun removeCover(presetId: String): PresetMetadata? {
        val metadata = loadMetadata(presetId) ?: return null
        coverFile(metadata)?.delete()
        return saveMetadata(metadata.copy(coverRelativePath = ""))
    }

    fun coverFile(metadata: PresetMetadata): File? {
        val relative = metadata.coverRelativePath.replace('\\', '/')
        if (!relative.startsWith("Assets/Covers/") || relative.contains("..")) return null
        return File(root, relative).takeIf { it.isFile }
    }

    // ---- internals ---------------------------------------------------------------------

    private fun writeMetadata(metadata: PresetMetadata) {
        writeAtomically(
            File(metadataDir, "${metadata.presetId}.meta.json"),
            PresetJson.json.encodeToString(JsonObject.serializer(), metadata.toJson())
        )
    }

    private fun presetFile(presetId: String, metadata: PresetMetadata?): File {
        val relative = metadata?.presetRelativePath?.replace('\\', '/')
        if (!relative.isNullOrBlank() && !relative.contains("..")) {
            val file = File(root, relative)
            if (file.isFile) return file
        }
        return File(presetsDir, "$presetId.json")
    }

    private fun relativePresetPath(presetId: String) = "Presets/$presetId.json"

    private fun writeAtomically(target: File, text: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.writeText(text)
            temp.delete()
        }
    }

    companion object {
        const val FOLDER_NAME = "Preset Editor Workspace"
        private val ID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

        fun isPresetId(value: String) = ID_REGEX.matches(value)

        fun newId(): String = UUID.randomUUID().toString()

        fun safeJsonFileName(name: String): String {
            var clean = name.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("""[<>:"/\\|?*\u0000-\u001F]"""), "_")
                .trim()
            if (clean.isEmpty()) clean = "preset.json"
            if (!clean.lowercase().endsWith(".json")) clean += ".json"
            return clean
        }
    }
}
