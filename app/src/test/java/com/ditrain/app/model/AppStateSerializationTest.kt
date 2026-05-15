package com.ditrain.app.model

import com.ditrain.app.util.JsonIo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateSerializationTest {

    @Test fun `default app state round-trips`() {
        val s = AppState(activeRoutineId = null, scheduledSessions = emptyList(),
            settings = Settings())
        val raw = JsonIo.json.encodeToString(AppState.serializer(), s)
        val back = JsonIo.json.decodeFromString(AppState.serializer(), raw)
        assertEquals(s, back)
        assertEquals(WeightUnit.KG, back.settings.weightUnit)
        assertEquals(EffortMode.RPE, back.settings.effortMode)
        assertEquals(20.0, back.settings.barWeightKg, 1e-9)
        assertEquals(ThemeMode.SYSTEM, back.settings.theme)
        assertTrue(back.settings.restTimerHaptic)
        assertEquals(false, back.settings.showDeletedExercises)
    }

    @Test fun `scheduled session round-trips with linked log`() {
        val sch = ScheduledSession(
            date = "2026-05-14",
            routineId = "r1",
            weekIndex = 0,
            sessionTemplateId = "push-a",
            sessionLogId = "log-uuid-123",
        )
        val raw = JsonIo.json.encodeToString(ScheduledSession.serializer(), sch)
        val back = JsonIo.json.decodeFromString(ScheduledSession.serializer(), raw)
        assertEquals(sch, back)
    }

    @Test fun `non-default settings round-trip`() {
        val s = Settings(
            weightUnit = WeightUnit.LB,
            effortMode = EffortMode.RIR,
            barWeightKg = 15.0,
            theme = ThemeMode.DARK,
            restTimerHaptic = false,
            showDeletedExercises = true,
        )
        val raw = JsonIo.json.encodeToString(Settings.serializer(), s)
        val back = JsonIo.json.decodeFromString(Settings.serializer(), raw)
        assertEquals(s, back)
    }
}
