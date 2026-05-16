package com.ditrain.app.storage

import com.ditrain.app.model.AppState
import com.ditrain.app.model.Settings
import com.ditrain.app.util.AtomicWrite
import com.ditrain.app.util.JsonIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AppStateRepository(filesDir: File) {
    private val file = File(filesDir, "state.json")

    suspend fun load(): AppState = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext defaultState()
        runCatching {
            JsonIo.json.decodeFromString(AppState.serializer(), file.readText())
        }.getOrElse { defaultState() }
    }

    suspend fun save(state: AppState) = withContext(Dispatchers.IO) {
        AtomicWrite.writeText(file, JsonIo.json.encodeToString(AppState.serializer(), state))
    }

    private fun defaultState() = AppState(
        activeRoutineId = null,
        scheduledSessions = emptyList(),
        settings = Settings(),
    )
}
