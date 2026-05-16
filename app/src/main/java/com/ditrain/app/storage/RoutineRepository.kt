package com.ditrain.app.storage

import com.ditrain.app.model.Routine
import com.ditrain.app.util.AtomicWrite
import com.ditrain.app.util.JsonIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One JSON file per routine, under [filesDir]/routines/. Loads are forgiving:
 * a corrupt file returns null and is left in place for manual recovery
 * (the design spec §7 calls for not auto-deleting user data on parse failure).
 */
class RoutineRepository(filesDir: File) {

    private val dir: File = File(filesDir, "routines")

    suspend fun save(routine: Routine) = withContext(Dispatchers.IO) {
        val target = File(dir, "${routine.id}.json")
        AtomicWrite.writeText(target, JsonIo.json.encodeToString(Routine.serializer(), routine))
    }

    suspend fun load(id: String): Routine? = withContext(Dispatchers.IO) {
        val file = File(dir, "$id.json")
        if (!file.exists()) return@withContext null
        runCatching {
            JsonIo.json.decodeFromString(Routine.serializer(), file.readText())
        }.getOrNull()
    }

    suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        if (!dir.exists()) return@withContext emptyList()
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(dir, "$id.json")
        file.exists() && file.delete()
    }
}
