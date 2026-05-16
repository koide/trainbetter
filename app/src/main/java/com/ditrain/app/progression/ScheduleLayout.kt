package com.ditrain.app.progression

import com.ditrain.app.model.LoopMode
import com.ditrain.app.model.Routine
import com.ditrain.app.model.ScheduledSession
import com.ditrain.app.util.iso
import java.time.LocalDate

/**
 * Lays out [ScheduledSession]s for a freshly-activated routine, given:
 *  - the user's chosen [weeklyPattern] (Mon=0..Sun=6),
 *  - a [startDate] to begin scheduling from (sessions roll forward from this date),
 *  - [futureWeeks] — how many weeks to schedule ahead for REPEAT routines (ignored for ONCE).
 *
 * For [LoopMode.ONCE], emits exactly `weeks.size × sessionsPerWeek` entries.
 * For [LoopMode.REPEAT], emits `futureWeeks × sessionsPerWeek` entries, cycling through the
 * routine's weeks as needed.
 *
 * Throws [IllegalArgumentException] if any week has more sessions than the weekly pattern can hold.
 */
object ScheduleLayout {

    fun lay(
        routine: Routine,
        weeklyPattern: List<Int>,
        startDate: LocalDate,
        futureWeeks: Int = 8,
    ): List<ScheduledSession> {
        require(weeklyPattern.isNotEmpty()) { "weeklyPattern must be non-empty" }
        require(weeklyPattern.all { it in 0..6 }) { "weeklyPattern entries must be 0..6 (Mon..Sun)" }

        // Validate every week of the routine fits the pattern.
        routine.weeks.forEachIndexed { idx, week ->
            require(week.sessions.size <= weeklyPattern.size) {
                "Routine prescribes ${week.sessions.size} sessions/week for week ${idx + 1} (${week.label}), " +
                "but the user picked only ${weeklyPattern.size} training days."
            }
        }

        val sortedPattern = weeklyPattern.sorted()
        val totalWeeksToSchedule = when (routine.loopMode) {
            LoopMode.ONCE -> routine.weeks.size
            LoopMode.REPEAT -> futureWeeks
        }

        val out = mutableListOf<ScheduledSession>()
        var cursorMonday = mondayOnOrBefore(startDate)

        for (i in 0 until totalWeeksToSchedule) {
            val routineWeekIndex = i % routine.weeks.size
            val week = routine.weeks[routineWeekIndex]
            week.sessions.forEachIndexed { sessionIdx, session ->
                val weekday = sortedPattern[sessionIdx]
                val date = cursorMonday.plusDays(weekday.toLong())
                if (date < startDate) return@forEachIndexed   // skip past dates in the first week
                out.add(ScheduledSession(
                    date = date.iso(),
                    routineId = routine.id,
                    weekIndex = routineWeekIndex,
                    sessionTemplateId = session.id,
                ))
            }
            cursorMonday = cursorMonday.plusWeeks(1)
        }

        return out
    }

    private fun mondayOnOrBefore(date: LocalDate): LocalDate {
        val daysFromMonday = (date.dayOfWeek.value + 6) % 7   // Mon=1..Sun=7 → Mon=0..Sun=6
        return date.minusDays(daysFromMonday.toLong())
    }
}
