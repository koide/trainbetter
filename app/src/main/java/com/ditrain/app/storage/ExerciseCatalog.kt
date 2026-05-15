package com.ditrain.app.storage

import com.ditrain.app.model.Exercise
import com.ditrain.app.util.AtomicWrite
import com.ditrain.app.util.JsonIo
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.InputStream

/**
 * In-memory catalog of all exercises. Composed of:
 *  - immutable bundled entries (parsed from `assets/exercises.json`)
 *  - mutable custom entries (persisted to a JSON file under filesDir)
 *
 * Soft delete only applies to customs. Bundled entries cannot be deleted.
 */
class ExerciseCatalog private constructor(
    private val bundled: Map<String, Exercise>,
    private val customsFile: File,
) {

    private val customs = mutableMapOf<String, Exercise>()

    init {
        if (customsFile.exists()) {
            runCatching {
                JsonIo.json.decodeFromString(ListSerializer(Exercise.serializer()), customsFile.readText())
            }.getOrNull()?.forEach { customs[it.id] = it }
        }
    }

    fun byId(id: String): Exercise? = customs[id] ?: bundled[id]

    fun visibleExercises(includeDeleted: Boolean = false): List<Exercise> {
        val all = bundled.values.toList() + customs.values
        return if (includeDeleted) all else all.filterNot { it.deleted }
    }

    fun addCustom(ex: Exercise): Exercise {
        require(ex.custom) { "addCustom requires Exercise.custom == true (id=${ex.id})" }
        customs[ex.id] = ex
        persistCustoms()
        return ex
    }

    /** Returns true if the exercise was soft-deleted; false if id is unknown or bundled. */
    fun softDelete(id: String): Boolean {
        val existing = customs[id] ?: return false
        customs[id] = existing.copy(deleted = true)
        persistCustoms()
        return true
    }

    fun restore(id: String): Boolean {
        val existing = customs[id] ?: return false
        customs[id] = existing.copy(deleted = false)
        persistCustoms()
        return true
    }

    /**
     * Permanently removes a custom exercise. Refuses when [isReferenced] returns true.
     * Returns true on success, false if id is unknown/bundled or still referenced.
     */
    fun hardDelete(id: String, isReferenced: (String) -> Boolean): Boolean {
        if (id !in customs) return false
        if (isReferenced(id)) return false
        customs.remove(id)
        persistCustoms()
        return true
    }

    private fun persistCustoms() {
        val list = customs.values.toList()
        AtomicWrite.writeText(
            customsFile,
            JsonIo.json.encodeToString(ListSerializer(Exercise.serializer()), list),
        )
    }

    companion object {
        /** Load bundled entries from an InputStream (e.g., AssetManager.open("exercises.json")). */
        fun fromAssets(bundledStream: InputStream, customsFile: File): ExerciseCatalog {
            val parsed = bundledStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val list = JsonIo.json.decodeFromString(ListSerializer(Exercise.serializer()), parsed)
            return ExerciseCatalog(list.associateBy { it.id }, customsFile)
        }

        /** Test seam: build a catalog from an already-decoded list. */
        fun fromInMemory(bundled: List<Exercise>, customsFile: File): ExerciseCatalog =
            ExerciseCatalog(bundled.associateBy { it.id }, customsFile)
    }
}
