package com.ditrain.app.storage

import com.ditrain.app.model.AppState
import com.ditrain.app.model.Settings
import com.ditrain.app.util.AtomicWrite
import com.ditrain.app.util.JsonIo
import java.io.File

class AppStateRepository(filesDir: File) {
    private val file = File(filesDir, "state.json")

    fun load(): AppState {
        if (!file.exists()) return defaultState()
        return runCatching {
            JsonIo.json.decodeFromString(AppState.serializer(), file.readText())
        }.getOrElse { defaultState() }
    }

    fun save(state: AppState) {
        AtomicWrite.writeText(file, JsonIo.json.encodeToString(AppState.serializer(), state))
    }

    private fun defaultState() = AppState(
        activeRoutineId = null,
        scheduledSessions = emptyList(),
        settings = Settings(),
    )
}
