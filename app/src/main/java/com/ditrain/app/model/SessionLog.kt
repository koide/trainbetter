package com.ditrain.app.model

import com.ditrain.app.util.InstantIso
import com.ditrain.app.util.LocalDateIso
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionLog(
    val id: String,
    val routineId: String?,
    val weekIndex: Int?,
    val sessionTemplateId: String?,
    val scheduledDate: LocalDateIso,
    val performedDate: LocalDateIso,
    val startedAt: InstantIso,
    val completedAt: InstantIso?,
    val executed: List<ExecutedExercise> = emptyList(),
    val cardioExecuted: List<CardioLog> = emptyList(),
    val notes: String? = null,
)

@Serializable
data class ExecutedExercise(
    val exerciseId: String,
    val substitutedFromId: String? = null,
    val skipped: Boolean = false,
    val sets: List<LoggedSet> = emptyList(),
)

@Serializable
sealed interface LoggedSet {
    val performedAt: InstantIso
    val notes: String?

    @Serializable @SerialName("straight")
    data class Straight(
        val weightKg: Double,
        val reps: Int,
        override val performedAt: InstantIso,
        val rpe: Double? = null,
        val rir: Int? = null,
        val restSec: Int? = null,
        val tempo: String? = null,
        override val notes: String? = null,
    ) : LoggedSet

    @Serializable @SerialName("myo_rep")
    data class MyoRep(
        val weightKg: Double,
        val activationReps: Int,
        override val performedAt: InstantIso,
        val activationRpe: Double? = null,
        val miniSets: List<MiniSet> = emptyList(),
        val restSec: Int? = null,
        val tempo: String? = null,
        override val notes: String? = null,
    ) : LoggedSet
}

@Serializable
data class MiniSet(val reps: Int, val rpe: Double? = null)

@Serializable
data class CardioLog(
    val activityKind: CardioKind,
    val durationMin: Int,
    val performedAt: InstantIso,
    val substitutedFromKind: CardioKind? = null,
    val description: String? = null,
    val avgBpm: Int? = null,
    val notes: String? = null,
    val skipped: Boolean = false,
)
