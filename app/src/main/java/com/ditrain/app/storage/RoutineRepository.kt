package com.ditrain.app.storage

import com.ditrain.app.model.Routine
import com.ditrain.app.util.AtomicWrite
import com.ditrain.app.util.JsonIo
import java.io.File

/**
 * One JSON file per routine, under [filesDir]/routines/. Loads are forgiving:
 * a corrupt file returns null and is left in place for manual recovery
 * (the design spec §7 calls for not auto-deleting user data on parse failure).
 */
class RoutineRepository(filesDir: File) {

    private val dir: File = File(filesDir, "routines")

    fun save(routine: Routine) {
        val target = File(dir, "${routine.id}.json")
        AtomicWrite.writeText(target, JsonIo.json.encodeToString(Routine.serializer(), routine))
    }

    fun load(id: String): Routine? {
        val file = File(dir, "$id.json")
        if (!file.exists()) return null
        return runCatching {
            JsonIo.json.decodeFromString(Routine.serializer(), file.readText())
        }.getOrNull()
    }

    fun list(): List<String> {
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    fun delete(id: String): Boolean {
        val file = File(dir, "$id.json")
        return file.exists() && file.delete()
    }
}
