package com.ditrain.app.ui.session

import com.ditrain.app.model.ExecutedExercise
import com.ditrain.app.model.LoggedSet
import com.ditrain.app.model.MiniSet
import com.ditrain.app.model.SessionLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateTest {

    private fun emptyLog(numBlocks: Int): SessionLog = SessionLog(
        id = "s1",
        routineId = "r1",
        weekIndex = 0,
        sessionTemplateId = "push-a",
        scheduledDate = "2026-06-02",
        performedDate = "2026-06-02",
        startedAt = "2026-06-02T10:00:00Z",
        completedAt = null,
        executed = List(numBlocks) { ExecutedExercise(exerciseId = "ex-$it", sets = emptyList()) },
        cardioExecuted = emptyList(),
    )

    @Test
    fun `appendStraightSet adds a logged set to the current strength block`() {
        val state = SessionState(emptyLog(numBlocks = 2))
        state.cursor = 0

        val newSet = LoggedSet.Straight(
            weightKg = 100.0,
            reps = 5,
            performedAt = "2026-06-02T10:05:00Z",
            rpe = 8.0,
        )
        state.appendSetToCurrentBlock(newSet)

        assertEquals(1, state.log.executed[0].sets.size)
        assertEquals(newSet, state.log.executed[0].sets[0])
        assertEquals(0, state.log.executed[1].sets.size)
    }

    @Test
    fun `appendMyoRepSet adds a myo-rep set to the current block`() {
        val state = SessionState(emptyLog(numBlocks = 1))
        state.cursor = 0
        val myo = LoggedSet.MyoRep(
            weightKg = 60.0,
            activationReps = 15,
            performedAt = "2026-06-02T10:10:00Z",
            miniSets = listOf(MiniSet(reps = 5), MiniSet(reps = 4)),
        )
        state.appendSetToCurrentBlock(myo)
        assertEquals(1, state.log.executed[0].sets.size)
        assertEquals(myo, state.log.executed[0].sets[0])
    }

    @Test
    fun `skipCurrentBlock marks the executed exercise as skipped and clears sets`() {
        val state = SessionState(emptyLog(numBlocks = 2))
        state.cursor = 1
        // Pre-populate with a set to verify it gets cleared
        state.appendSetToCurrentBlock(LoggedSet.Straight(
            weightKg = 50.0, reps = 10, performedAt = "2026-06-02T10:15:00Z"))
        state.skipCurrentBlock()
        assertTrue(state.log.executed[1].skipped)
        assertTrue(state.log.executed[1].sets.isEmpty())
        // Other blocks untouched
        assertFalse(state.log.executed[0].skipped)
    }

    @Test
    fun `previousLoggedStraightSet returns the most recent straight set in the current block`() {
        val state = SessionState(emptyLog(numBlocks = 1))
        state.cursor = 0
        val s1 = LoggedSet.Straight(weightKg = 60.0, reps = 8, performedAt = "2026-06-02T10:01:00Z")
        val s2 = LoggedSet.Straight(weightKg = 65.0, reps = 6, performedAt = "2026-06-02T10:04:00Z")
        state.appendSetToCurrentBlock(s1)
        state.appendSetToCurrentBlock(s2)
        assertEquals(s2, state.previousLoggedSetInCurrentBlock())
    }

    @Test
    fun `isStrengthBlockComplete is true when sets meet prescribed count`() {
        val state = SessionState(emptyLog(numBlocks = 1))
        state.cursor = 0
        assertFalse(state.isStrengthBlockComplete(prescribedSetCount = 3))
        state.appendSetToCurrentBlock(LoggedSet.Straight(60.0, 8, "2026-06-02T10:01:00Z"))
        state.appendSetToCurrentBlock(LoggedSet.Straight(60.0, 8, "2026-06-02T10:04:00Z"))
        assertFalse(state.isStrengthBlockComplete(prescribedSetCount = 3))
        state.appendSetToCurrentBlock(LoggedSet.Straight(60.0, 8, "2026-06-02T10:07:00Z"))
        assertTrue(state.isStrengthBlockComplete(prescribedSetCount = 3))
    }

    @Test
    fun `isStrengthBlockComplete also returns true for skipped blocks`() {
        val state = SessionState(emptyLog(numBlocks = 1))
        state.cursor = 0
        state.skipCurrentBlock()
        assertTrue(state.isStrengthBlockComplete(prescribedSetCount = 3))
    }

    @Test
    fun `markCompleted stamps completedAt and is idempotent in-memory`() {
        val state = SessionState(emptyLog(numBlocks = 1))
        state.markCompleted("2026-06-02T11:00:00Z")
        assertEquals("2026-06-02T11:00:00Z", state.log.completedAt)
        // No exception on re-mark (caller's responsibility)
        state.markCompleted("2026-06-02T11:05:00Z")
        assertEquals("2026-06-02T11:05:00Z", state.log.completedAt)
    }
}
