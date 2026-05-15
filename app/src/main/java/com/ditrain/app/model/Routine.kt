package com.ditrain.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class LoopMode { ONCE, REPEAT }

@Serializable
data class Routine(
    val id: String,
    val name: String,
    val loopMode: LoopMode,
    val weeks: List<Week>,
    val description: String? = null,
    val author: String? = null,
    val schemaVersion: Int = 1,
    /** Weekday indices the user trains, Mon=0..Sun=6. Null = prompt on activation. */
    val defaultWeeklyPattern: List<Int>? = null,
)

@Serializable
data class Week(
    val label: String,
    val sessions: List<SessionTemplate>,
)

@Serializable
data class SessionTemplate(
    val id: String,
    val name: String,
    val blocks: List<ExerciseBlock> = emptyList(),
    val cardioBlocks: List<CardioBlock> = emptyList(),
)

@Serializable
data class ExerciseBlock(
    val exerciseId: String,
    val sets: List<SetPrescription>,
    val notes: String? = null,
)

@Serializable
sealed interface SetPrescription {
    val rest: Int?
    val tempo: String?
    val notes: String?

    @Serializable @SerialName("straight")
    data class Straight(
        val reps: RepsTarget,
        val load: LoadTarget,
        val rpeTarget: Double? = null,
        override val rest: Int? = null,
        override val tempo: String? = null,
        override val notes: String? = null,
    ) : SetPrescription

    @Serializable @SerialName("myo_rep")
    data class MyoRep(
        val activationReps: RepsTarget,
        val load: LoadTarget,
        val miniSetTargetReps: Int,
        val miniSetCount: Int,
        val miniSetRestSec: Int = 15,
        val rpeStopThreshold: Double? = 10.0,
        override val rest: Int? = null,
        override val tempo: String? = null,
        override val notes: String? = null,
    ) : SetPrescription
}

@Serializable
sealed interface RepsTarget {
    @Serializable @SerialName("fixed")
    data class Fixed(val reps: Int) : RepsTarget

    @Serializable @SerialName("range")
    data class Range(val min: Int, val max: Int) : RepsTarget

    @Serializable @SerialName("amrap")
    data object Amrap : RepsTarget

    @Serializable @SerialName("amrap_min")
    data class AmrapMin(val min: Int) : RepsTarget
}

@Serializable
sealed interface LoadTarget {
    @Serializable @SerialName("absolute_kg")
    data class AbsoluteKg(val kg: Double) : LoadTarget

    @Serializable @SerialName("pct_1rm")
    data class PctOneRm(val pct: Double) : LoadTarget   // 0.80 = 80 %

    @Serializable @SerialName("rpe_target")
    data class RpeTarget(val rpe: Double) : LoadTarget

    @Serializable @SerialName("relative_to_last")
    data class RelativeToLast(val deltaKg: Double) : LoadTarget

    @Serializable @SerialName("open")
    data object Open : LoadTarget
}

enum class CardioKind {
    RUNNING, SWIMMING, CYCLING, ROWING,
    WALKING, ELLIPTICAL, HIKING, OTHER,
}

@Serializable
data class CardioBlock(
    val activityKind: CardioKind,
    val description: String? = null,
    val targetDurationMin: Int? = null,
    val targetAvgBpm: Int? = null,
    val notes: String? = null,
)
