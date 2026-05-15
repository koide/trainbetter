package com.ditrain.app.model

import com.ditrain.app.util.JsonIo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLogSerializationTest {

    private fun roundTrip(s: SessionLog): SessionLog {
        val raw = JsonIo.json.encodeToString(SessionLog.serializer(), s)
        return JsonIo.json.decodeFromString(SessionLog.serializer(), raw)
    }

    @Test fun `straight set log round-trips with rpe rir rest tempo`() {
        val s = SessionLog(
            id = "u1",
            routineId = "r1",
            weekIndex = 0,
            sessionTemplateId = "push-a",
            scheduledDate = "2026-05-14",
            performedDate = "2026-05-14",
            startedAt = "2026-05-14T16:00:00Z",
            completedAt = "2026-05-14T17:00:00Z",
            executed = listOf(
                ExecutedExercise("bench", sets = listOf(
                    LoggedSet.Straight(
                        weightKg = 100.0,
                        reps = 5,
                        rpe = 8.0,
                        rir = 2,
                        restSec = 180,
                        tempo = "3-1-1",
                        performedAt = "2026-05-14T16:05:00Z",
                        notes = "felt strong",
                    )
                ))
            ),
        )
        assertEquals(s, roundTrip(s))
    }

    @Test fun `myo-rep log with partial mini-sets round-trips`() {
        val s = SessionLog(
            id = "u2",
            routineId = null,
            weekIndex = null,
            sessionTemplateId = null,
            scheduledDate = "2026-05-14",
            performedDate = "2026-05-14",
            startedAt = "2026-05-14T16:00:00Z",
            completedAt = null,
            executed = listOf(
                ExecutedExercise("curl", sets = listOf(
                    LoggedSet.MyoRep(
                        weightKg = 15.0,
                        activationReps = 14,
                        activationRpe = 9.5,
                        miniSets = listOf(
                            MiniSet(reps = 5, rpe = 9.0),
                            MiniSet(reps = 5, rpe = 9.5),
                            MiniSet(reps = 4, rpe = 10.0),   // cluster aborted on rep 4
                        ),
                        performedAt = "2026-05-14T16:30:00Z",
                    )
                ))
            ),
        )
        assertEquals(s, roundTrip(s))
    }

    @Test fun `cardio-only log round-trips`() {
        val s = SessionLog(
            id = "u3",
            routineId = "r1",
            weekIndex = 0,
            sessionTemplateId = "easy-run",
            scheduledDate = "2026-05-15",
            performedDate = "2026-05-15",
            startedAt = "2026-05-15T07:00:00Z",
            completedAt = "2026-05-15T07:32:00Z",
            cardioExecuted = listOf(
                CardioLog(
                    activityKind = CardioKind.RUNNING,
                    durationMin = 32,
                    avgBpm = 142,
                    performedAt = "2026-05-15T07:32:00Z",
                )
            ),
        )
        val back = roundTrip(s)
        assertEquals(s, back)
        assertTrue(back.executed.isEmpty())
        assertTrue(back.cardioExecuted.isNotEmpty())
    }

    @Test fun `mixed session log round-trips`() {
        val s = SessionLog(
            id = "u4",
            routineId = "r1",
            weekIndex = 0,
            sessionTemplateId = "lift-plus-walk",
            scheduledDate = "2026-05-16",
            performedDate = "2026-05-16",
            startedAt = "2026-05-16T16:00:00Z",
            completedAt = "2026-05-16T17:30:00Z",
            executed = listOf(
                ExecutedExercise("squat", sets = listOf(
                    LoggedSet.Straight(120.0, reps = 5, rpe = 8.0,
                        performedAt = "2026-05-16T16:20:00Z")
                ))
            ),
            cardioExecuted = listOf(
                CardioLog(CardioKind.WALKING, durationMin = 15,
                    performedAt = "2026-05-16T17:30:00Z")
            ),
        )
        assertEquals(s, roundTrip(s))
    }

    @Test fun `skipped exercise round-trips with empty sets`() {
        val s = SessionLog(
            id = "u5", routineId = null, weekIndex = null, sessionTemplateId = null,
            scheduledDate = "2026-05-14", performedDate = "2026-05-14",
            startedAt = "2026-05-14T16:00:00Z", completedAt = null,
            executed = listOf(ExecutedExercise("ohp", skipped = true, sets = emptyList())),
        )
        val back = roundTrip(s)
        assertTrue(back.executed[0].skipped)
        assertEquals(emptyList<LoggedSet>(), back.executed[0].sets)
    }

    @Test fun `substituted exercise records original id`() {
        val s = SessionLog(
            id = "u6", routineId = null, weekIndex = null, sessionTemplateId = null,
            scheduledDate = "2026-05-14", performedDate = "2026-05-14",
            startedAt = "2026-05-14T16:00:00Z", completedAt = null,
            executed = listOf(
                ExecutedExercise(
                    exerciseId = "landmine-press",
                    substitutedFromId = "ohp",
                    sets = listOf(LoggedSet.Straight(40.0, reps = 8, rpe = 7.0,
                        performedAt = "2026-05-14T16:05:00Z"))
                )
            ),
        )
        val back = roundTrip(s)
        assertEquals("ohp", back.executed[0].substitutedFromId)
    }
}
