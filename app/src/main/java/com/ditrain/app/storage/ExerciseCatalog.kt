package com.ditrain.app.storage

import com.ditrain.app.model.Exercise
import com.ditrain.app.util.AtomicWrite
import com.ditrain.app.util.JsonIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.InputStream

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

    suspend fun addCustom(ex: Exercise): Exercise = withContext(Dispatchers.IO) {
        require(ex.custom) { "addCustom requires Exercise.custom == true (id=${ex.id})" }
        customs[ex.id] = ex
        persistCustoms()
        ex
    }

    suspend fun softDelete(id: String): Boolean = withContext(Dispatchers.IO) {
        val existing = customs[id] ?: return@withContext false
        customs[id] = existing.copy(deleted = true)
        persistCustoms()
        true
    }

    suspend fun restore(id: String): Boolean = withContext(Dispatchers.IO) {
        val existing = customs[id] ?: return@withContext false
        customs[id] = existing.copy(deleted = false)
        persistCustoms()
        true
    }

    suspend fun hardDelete(id: String, isReferenced: (String) -> Boolean): Boolean = withContext(Dispatchers.IO) {
        if (id !in customs) return@withContext false
        if (isReferenced(id)) return@withContext false
        customs.remove(id)
        persistCustoms()
        true
    }

    private fun persistCustoms() {
        val list = customs.values.toList()
        AtomicWrite.writeText(
            customsFile,
            JsonIo.json.encodeToString(ListSerializer(Exercise.serializer()), list),
        )
    }

    companion object {
        fun fromAssets(bundledStream: InputStream, customsFile: File): ExerciseCatalog {
            val parsed = bundledStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val list = JsonIo.json.decodeFromString(ListSerializer(Exercise.serializer()), parsed)
            return ExerciseCatalog(list.associateBy { it.id }, customsFile)
        }

        fun fromInMemory(bundled: List<Exercise>, customsFile: File): ExerciseCatalog =
            ExerciseCatalog(bundled.associateBy { it.id }, customsFile)
    }
}
