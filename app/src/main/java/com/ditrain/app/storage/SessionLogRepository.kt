package com.ditrain.app.storage

import com.ditrain.app.model.SessionLog
import com.ditrain.app.util.AtomicWrite
import com.ditrain.app.util.JsonIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * Sessions persist to a single JSON array at filesDir/logs/sessions.json, sorted by
 * performedDate ascending. When sessions.json crosses [rolloverBytes], it's atomically
 * archived into logs/archive/sessions-{firstDate}_{lastDate}.json and a fresh empty
 * file replaces it.
 *
 * Reads are forgiving: a corrupt live file is renamed to sessions.corrupt.{ts}.json
 * and a fresh empty live file is created. Corrupt archive files are skipped (not
 * renamed — they may still be recoverable manually).
 */
class SessionLogRepository(
    filesDir: File,
    val rolloverBytes: Long = DEFAULT_ROLLOVER_BYTES,
) {
    private val logsDir = File(filesDir, "logs")
    private val liveFile = File(logsDir, "sessions.json")
    private val archiveDir = File(logsDir, "archive")

    private val listSerializer = ListSerializer(SessionLog.serializer())

    suspend fun append(log: SessionLog) = withContext(Dispatchers.IO) {
        val current = readLive().toMutableList()
        current.add(log)
        writeLive(current)
        maybeRollover()
    }

    suspend fun upsert(log: SessionLog) = withContext(Dispatchers.IO) {
        val current = readLive().toMutableList()
        val idx = current.indexOfFirst { it.id == log.id }
        if (idx >= 0) current[idx] = log else current.add(log)
        writeLive(current)
        maybeRollover()
    }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        val current = readLive().toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) writeLive(current)
        removed
    }

    suspend fun loadAll(): List<SessionLog> = withContext(Dispatchers.IO) {
        val all = mutableListOf<SessionLog>()
        all.addAll(readAllArchives())
        all.addAll(readLive())
        all.sortedBy { it.performedDate }
    }

    /** Returns logs whose performedDate is within [from..to] inclusive. */
    suspend fun loadByDateRange(from: String, to: String): List<SessionLog> = withContext(Dispatchers.IO) {
        val all = mutableListOf<SessionLog>()
        archiveFiles().forEach { f ->
            val (firstDate, lastDate) = decodeArchiveRange(f.name) ?: return@forEach
            if (lastDate < from || firstDate > to) return@forEach
            runCatching { JsonIo.json.decodeFromString(listSerializer, f.readText()) }
                .getOrNull()?.let { all.addAll(it.filter { l -> l.performedDate in from..to }) }
        }
        all.addAll(readLive().filter { it.performedDate in from..to })
        all.sortedBy { it.performedDate }
    }

    private fun readLive(): List<SessionLog> {
        if (!liveFile.exists()) return emptyList()
        val raw = liveFile.readText()
        return runCatching { JsonIo.json.decodeFromString(listSerializer, raw) }
            .getOrElse {
                quarantineLiveFile(raw)
                emptyList()
            }
    }

    private fun quarantineLiveFile(raw: String) {
        val ts = System.currentTimeMillis()
        val renamed = File(logsDir, "sessions.corrupt.${ts}.json")
        AtomicWrite.writeText(renamed, raw)
        if (!liveFile.delete()) {
            liveFile.renameTo(File(logsDir, "sessions.unremovable.${ts}.json"))
        }
    }

    private fun writeLive(list: List<SessionLog>) {
        val sorted = list.sortedBy { it.performedDate }
        AtomicWrite.writeText(liveFile, JsonIo.json.encodeToString(listSerializer, sorted))
    }

    private fun maybeRollover() {
        if (!liveFile.exists() || liveFile.length() <= rolloverBytes) return
        val live = readLive()
        if (live.size < 2) return
        val firstDate = live.first().performedDate
        val lastDate = live.last().performedDate
        val archiveFile = File(archiveDir, "sessions-${firstDate}_${lastDate}.json")
        AtomicWrite.writeText(archiveFile, JsonIo.json.encodeToString(listSerializer, live))
        AtomicWrite.writeText(liveFile, JsonIo.json.encodeToString(listSerializer, emptyList()))
    }

    private fun archiveFiles(): List<File> =
        archiveDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedBy { it.name } ?: emptyList()

    private fun readAllArchives(): List<SessionLog> =
        archiveFiles().flatMap { f ->
            runCatching { JsonIo.json.decodeFromString(listSerializer, f.readText()) }
                .getOrDefault(emptyList())
        }

    private fun decodeArchiveRange(name: String): Pair<String, String>? {
        val core = name.removePrefix("sessions-").removeSuffix(".json")
        val parts = core.split("_")
        return if (parts.size == 2) parts[0] to parts[1] else null
    }

    companion object {
        const val DEFAULT_ROLLOVER_BYTES: Long = 1L shl 20
    }
}
