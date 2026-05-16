package com.ditrain.app.storage

import com.ditrain.app.model.ExecutedExercise
import com.ditrain.app.model.LoggedSet
import com.ditrain.app.model.SessionLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionLogRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun log(id: String, date: String, weightKg: Double = 100.0): SessionLog =
        SessionLog(
            id = id,
            routineId = null,
            weekIndex = null,
            sessionTemplateId = null,
            scheduledDate = date,
            performedDate = date,
            startedAt = "${date}T16:00:00Z",
            completedAt = "${date}T17:00:00Z",
            executed = listOf(ExecutedExercise("bench", sets = listOf(
                LoggedSet.Straight(weightKg, reps = 5, performedAt = "${date}T16:05:00Z")
            )))
        )

    private fun repo(rolloverBytes: Long = 1L shl 20) = SessionLogRepository(tmp.root, rolloverBytes)

    @Test fun `append writes to sessions json sorted by performedDate`() = runBlocking {
        val repo = repo()
        repo.append(log("a", "2026-05-02"))
        repo.append(log("b", "2026-05-01"))    // earlier — should sort first
        val all = repo.loadAll()
        assertEquals(listOf("b", "a"), all.map { it.id })
    }

    @Test fun `creates logs subdir on first write`() = runBlocking {
        repo().append(log("only", "2026-05-01"))
        assertTrue(File(tmp.root, "logs").exists())
        assertTrue(File(tmp.root, "logs/sessions.json").exists())
    }

    @Test fun `update existing id replaces in place`() = runBlocking {
        val repo = repo()
        repo.append(log("a", "2026-05-01", weightKg = 80.0))
        val updated = log("a", "2026-05-01", weightKg = 100.0)
        repo.upsert(updated)
        val loaded = repo.loadAll().single()
        assertEquals(100.0, (loaded.executed[0].sets[0] as LoggedSet.Straight).weightKg, 1e-9)
    }

    @Test fun `delete by id removes the entry`() = runBlocking {
        val repo = repo()
        repo.append(log("a", "2026-05-01"))
        repo.append(log("b", "2026-05-02"))
        assertTrue(repo.delete("a"))
        assertEquals(listOf("b"), repo.loadAll().map { it.id })
    }

    @Test fun `rollover archives the live file when bytes exceed threshold`() = runBlocking {
        // Tiny threshold forces a rollover after the first big write
        val repo = repo(rolloverBytes = 200L)
        repo.append(log("a", "2026-05-01"))     // first append, fits
        repo.append(log("b", "2026-05-10"))     // pushes past 200 B → triggers rollover

        // The live file should now contain exactly the most-recent post-rollover content
        val live = File(tmp.root, "logs/sessions.json")
        val archiveDir = File(tmp.root, "logs/archive")
        assertTrue(archiveDir.exists())
        val archives = archiveDir.listFiles()?.map { it.name } ?: emptyList()
        assertEquals(1, archives.size)
        assertTrue(archives.single().startsWith("sessions-2026-05-01_2026-05-10"))

        // live file still exists; loadAll covers live + archive seamlessly
        assertTrue(live.exists())
        val all = repo.loadAll()
        assertEquals(setOf("a", "b"), all.map { it.id }.toSet())
    }

    @Test fun `load returns merged chronological list across live and archive`() = runBlocking {
        val repo = repo(rolloverBytes = 200L)
        repo.append(log("old", "2026-01-01"))
        repo.append(log("mid", "2026-03-01"))   // forces rollover; old + mid archived
        repo.append(log("new", "2026-06-01"))   // lives in live file

        val all = repo.loadAll()
        assertEquals(listOf("old", "mid", "new"), all.map { it.id })
    }

    @Test fun `archive filename encodes correct first and last performedDate`() = runBlocking {
        val repo = repo(rolloverBytes = 200L)
        repo.append(log("a", "2026-02-15"))
        repo.append(log("b", "2026-04-01"))     // triggers rollover containing both
        val archives = File(tmp.root, "logs/archive").listFiles()!!
        assertEquals(1, archives.size)
        assertEquals("sessions-2026-02-15_2026-04-01.json", archives[0].name)
    }

    @Test fun `corrupt live file is renamed and a fresh one created`() = runBlocking {
        val live = File(tmp.root, "logs/sessions.json")
        live.parentFile.mkdirs()
        live.writeText("{ this is broken")
        val repo = repo()
        val all = repo.loadAll()
        assertEquals(emptyList<SessionLog>(), all)
        // corrupt file was renamed, not deleted
        assertNull(File(tmp.root, "logs").listFiles()?.firstOrNull { it.name == "sessions.json" && it.readText().contains("broken") })
        assertNotNull(File(tmp.root, "logs").listFiles()?.firstOrNull { it.name.startsWith("sessions.corrupt.") })
    }

    @Test fun `corrupt archive is skipped and other data still loads`() = runBlocking {
        val repo = repo()
        repo.append(log("live", "2026-06-01"))
        // Forge a bogus archive
        val archiveDir = File(tmp.root, "logs/archive").apply { mkdirs() }
        File(archiveDir, "sessions-2026-01-01_2026-02-01.json").writeText("not json")
        val all = repo.loadAll()
        // The live entry still loads even though the archive is unreadable
        assertEquals(listOf("live"), all.map { it.id })
    }

    @Test fun `loadByDateRange opens only overlapping archives`() = runBlocking {
        val repo = repo(rolloverBytes = 200L)
        repo.append(log("oldest", "2025-01-01"))
        repo.append(log("middle", "2025-06-01"))   // triggers rollover with both
        repo.append(log("recent", "2026-03-01"))
        val ids = repo.loadByDateRange(from = "2026-01-01", to = "2026-12-31").map { it.id }
        assertEquals(listOf("recent"), ids)
    }
}
