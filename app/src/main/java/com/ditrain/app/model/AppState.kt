package com.ditrain.app.model

import com.ditrain.app.util.LocalDateIso
import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AppState(
    val activeRoutineId: String?,
    val scheduledSessions: List<ScheduledSession>,
    val settings: Settings,
    val schemaVersion: Int = 1,
)

@Serializable
data class ScheduledSession(
    val date: LocalDateIso,
    val routineId: String,
    val weekIndex: Int,
    val sessionTemplateId: String,
    val sessionLogId: String? = null,
)

@Serializable
data class Settings(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val effortMode: EffortMode = EffortMode.RPE,
    val barWeightKg: Double = 20.0,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val restTimerHaptic: Boolean = true,
    val showDeletedExercises: Boolean = false,
)
