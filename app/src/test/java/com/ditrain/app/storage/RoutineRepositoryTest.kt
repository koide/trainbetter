package com.ditrain.app.storage

import com.ditrain.app.model.ExerciseBlock
import com.ditrain.app.model.LoadTarget
import com.ditrain.app.model.LoopMode
import com.ditrain.app.model.RepsTarget
import com.ditrain.app.model.Routine
import com.ditrain.app.model.SessionTemplate
import com.ditrain.app.model.SetPrescription
import com.ditrain.app.model.Week
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RoutineRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun sampleRoutine(id: String = "r1") = Routine(
        id = id, name = "R-$id", loopMode = LoopMode.REPEAT,
        weeks = listOf(Week("Week 1", listOf(
            SessionTemplate("a", "Push A", blocks = listOf(
                ExerciseBlock(
                    "bench",
                    sets = listOf(SetPrescription.Straight(
                        reps = RepsTarget.Fixed(5),
                        load = LoadTarget.AbsoluteKg(60.0),
                    ))
                )
            ))
        )))
    )

    private fun repo() = RoutineRepository(tmp.root)

    @Test fun `save then load equals`() = runBlocking {
        val repo = repo()
        val r = sampleRoutine()
        repo.save(r)
        assertEquals(r, repo.load("r1"))
    }

    @Test fun `save creates routines subdir`() = runBlocking {
        repo().save(sampleRoutine())
        assertTrue(tmp.root.resolve("routines").exists())
        assertTrue(tmp.root.resolve("routines/r1.json").exists())
    }

    @Test fun `load missing returns null`() = runBlocking {
        assertNull(repo().load("nope"))
    }

    @Test fun `overwrite same id replaces content`() = runBlocking {
        val repo = repo()
        repo.save(sampleRoutine())
        val updated = sampleRoutine().copy(name = "renamed")
        repo.save(updated)
        assertEquals("renamed", repo.load("r1")?.name)
    }

    @Test fun `list returns saved ids only`() = runBlocking {
        val repo = repo()
        repo.save(sampleRoutine("a"))
        repo.save(sampleRoutine("b"))
        assertEquals(setOf("a", "b"), repo.list().toSet())
    }

    @Test fun `delete removes the file`() = runBlocking {
        val repo = repo()
        repo.save(sampleRoutine("d"))
        assertTrue(repo.delete("d"))
        assertNull(repo.load("d"))
        assertFalse(tmp.root.resolve("routines/d.json").exists())
    }

    @Test fun `delete missing returns false`() = runBlocking {
        assertFalse(repo().delete("never"))
    }

    @Test fun `corrupt file load returns null and is not deleted`() = runBlocking {
        val repo = repo()
        val routinesDir = tmp.root.resolve("routines")
        routinesDir.mkdirs()
        val corrupt = routinesDir.resolve("bad.json")
        corrupt.writeText("{ this is not valid")
        assertNull(repo.load("bad"))
        assertTrue(corrupt.exists())
    }
}
