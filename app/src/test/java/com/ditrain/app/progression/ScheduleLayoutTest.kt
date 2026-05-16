package com.ditrain.app.progression

import com.ditrain.app.model.ExerciseBlock
import com.ditrain.app.model.LoadTarget
import com.ditrain.app.model.LoopMode
import com.ditrain.app.model.RepsTarget
import com.ditrain.app.model.Routine
import com.ditrain.app.model.SessionTemplate
import com.ditrain.app.model.SetPrescription
import com.ditrain.app.model.Week
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ScheduleLayoutTest {

    private fun straightBlock() = ExerciseBlock("squat", sets = listOf(
        SetPrescription.Straight(RepsTarget.Fixed(5), LoadTarget.Open)
    ))

    private fun routine(
        loopMode: LoopMode,
        weeks: Int,
        sessionsPerWeek: Int,
    ): Routine {
        val sessions = (1..sessionsPerWeek).map { idx ->
            SessionTemplate("s$idx", "Session $idx", blocks = listOf(straightBlock()))
        }
        val weekList = (1..weeks).map { Week("Week $it", sessions) }
        return Routine(
            id = "r", name = "R", loopMode = loopMode,
            weeks = weekList,
        )
    }

    @Test fun `ONCE mesocycle produces exactly weeks times sessionsPerWeek entries`() {
        val r = routine(LoopMode.ONCE, weeks = 4, sessionsPerWeek = 3)
        // Mon=0, Wed=2, Fri=4 — 3 sessions/week
        val start = LocalDate.of(2026, 5, 18)   // a Monday
        val out = ScheduleLayout.lay(r, weeklyPattern = listOf(0, 2, 4), startDate = start, futureWeeks = 8)
        assertEquals(12, out.size)
    }

    @Test fun `ONCE first sessions land on chosen weekdays in order`() {
        val r = routine(LoopMode.ONCE, weeks = 1, sessionsPerWeek = 3)
        val start = LocalDate.of(2026, 5, 18)   // Monday
        val out = ScheduleLayout.lay(r, weeklyPattern = listOf(0, 2, 4), startDate = start, futureWeeks = 8)
        assertEquals(3, out.size)
        assertEquals("2026-05-18", out[0].date)   // Mon
        assertEquals("2026-05-20", out[1].date)   // Wed
        assertEquals("2026-05-22", out[2].date)   // Fri
        assertEquals("s1", out[0].sessionTemplateId)
        assertEquals("s2", out[1].sessionTemplateId)
        assertEquals("s3", out[2].sessionTemplateId)
    }

    @Test fun `REPEAT lays out futureWeeks worth of sessions`() {
        val r = routine(LoopMode.REPEAT, weeks = 1, sessionsPerWeek = 3)
        val start = LocalDate.of(2026, 5, 18)
        val out = ScheduleLayout.lay(r, weeklyPattern = listOf(0, 2, 4), startDate = start, futureWeeks = 8)
        assertEquals(8 * 3, out.size)
    }

    @Test fun `REPEAT cycles through weeks when routine has multiple weeks`() {
        val r = routine(LoopMode.REPEAT, weeks = 2, sessionsPerWeek = 3)
        val start = LocalDate.of(2026, 5, 18)
        val out = ScheduleLayout.lay(r, weeklyPattern = listOf(0, 2, 4), startDate = start, futureWeeks = 4)
        assertEquals(12, out.size)
        assertEquals(0, out[0].weekIndex)
        assertEquals(0, out[2].weekIndex)
        assertEquals(1, out[3].weekIndex)
        assertEquals(1, out[5].weekIndex)
        assertEquals(0, out[6].weekIndex)     // cycle back to week 0 after week 1
        assertEquals(1, out[9].weekIndex)
    }

    @Test fun `start on a non-pattern weekday advances to first matching weekday`() {
        val r = routine(LoopMode.ONCE, weeks = 1, sessionsPerWeek = 1)
        val start = LocalDate.of(2026, 5, 19)   // Tuesday
        val out = ScheduleLayout.lay(r, weeklyPattern = listOf(2), startDate = start, futureWeeks = 8)
        assertEquals(1, out.size)
        assertEquals("2026-05-20", out[0].date)   // first Wednesday on/after Tue 19th
    }

    @Test fun `throws when sessionsPerWeek exceeds weekly pattern size`() {
        val r = routine(LoopMode.ONCE, weeks = 1, sessionsPerWeek = 4)
        try {
            ScheduleLayout.lay(r, weeklyPattern = listOf(0, 2, 4), startDate = LocalDate.now(), futureWeeks = 8)
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message!!.contains("4 sessions/week"))
        }
    }

    @Test fun `routineId weekIndex sessionTemplateId are set correctly`() {
        val r = routine(LoopMode.ONCE, weeks = 1, sessionsPerWeek = 2)
        val out = ScheduleLayout.lay(r, weeklyPattern = listOf(0, 2), startDate = LocalDate.of(2026, 5, 18), futureWeeks = 8)
        assertEquals("r", out[0].routineId)
        assertEquals(0, out[0].weekIndex)
        assertEquals("s1", out[0].sessionTemplateId)
        assertEquals("s2", out[1].sessionTemplateId)
    }
}
