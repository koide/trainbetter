package com.ditrain.app.model

import kotlinx.serialization.Serializable

enum class MovementPattern {
    SQUAT, HINGE,
    HORIZONTAL_PUSH, VERTICAL_PUSH,
    HORIZONTAL_PULL, VERTICAL_PULL,
    LUNGE, CARRY, CORE, ISOLATION,
}

enum class MuscleGroup {
    QUADS, HAMSTRINGS, GLUTES,
    CHEST, UPPER_BACK, LATS,
    FRONT_DELT, SIDE_DELT, REAR_DELT,
    BICEPS, TRICEPS, FOREARMS,
    ABS, OBLIQUES, LOWER_BACK,
    CALVES, TRAPS, NECK,
    ADDUCTORS, ABDUCTORS,
}

enum class Equipment {
    BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT, BAND, OTHER,
}

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val pattern: MovementPattern,
    val primaryMuscles: List<MuscleGroup>,
    val equipment: List<Equipment>,
    val descriptionUrl: String?,
    val aliases: List<String> = emptyList(),
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val custom: Boolean = false,
    val parentId: String? = null,
    val deleted: Boolean = false,
)
