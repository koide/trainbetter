package com.ditrain.app.storage

import com.ditrain.app.model.AppState
import com.ditrain.app.model.EffortMode
import com.ditrain.app.model.ScheduledSession
import com.ditrain.app.model.Settings
import com.ditrain.app.model.ThemeMode
import com.ditrain.app.model.WeightUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppStateRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `load on fresh dir returns defaults`() = runBlocking {
        val s = AppStateRepository(tmp.root).load()
        assertEquals(null, s.activeRoutineId)
        assertEquals(emptyList<ScheduledSession>(), s.scheduledSessions)
        assertEquals(WeightUnit.KG, s.settings.weightUnit)
        assertEquals(EffortMode.RPE, s.settings.effortMode)
    }

    @Test fun `save then load roundtrips`() = runBlocking {
        val repo = AppStateRepository(tmp.root)
        val s = AppState(
            activeRoutineId = "abc",
            scheduledSessions = listOf(
                ScheduledSession(date = "2026-05-14", routineId = "abc",
                    weekIndex = 0, sessionTemplateId = "push-a")
            ),
            settings = Settings(
                weightUnit = WeightUnit.LB,
                theme = ThemeMode.DARK,
                restTimerHaptic = false,
            ),
        )
        repo.save(s)
        assertEquals(s, repo.load())
    }

    @Test fun `state file lands at filesDir slash state json`() = runBlocking {
        val repo = AppStateRepository(tmp.root)
        repo.save(AppState(null, emptyList(), Settings()))
        assertTrue(tmp.root.resolve("state.json").exists())
    }

    @Test fun `corrupt state json falls back to defaults`() = runBlocking {
        tmp.root.resolve("state.json").writeText("not json")
        val s = AppStateRepository(tmp.root).load()
        assertEquals(null, s.activeRoutineId)
    }
}
