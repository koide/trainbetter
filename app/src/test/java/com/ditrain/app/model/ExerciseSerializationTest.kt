package com.ditrain.app.model

import com.ditrain.app.util.JsonIo
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSerializationTest {

    @Test fun `bundled exercise round-trips`() {
        val ex = Exercise(
            id = "barbell-back-squat",
            name = "Barbell Back Squat",
            aliases = listOf("Back Squat", "Squat"),
            pattern = MovementPattern.SQUAT,
            primaryMuscles = listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            secondaryMuscles = listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.LOWER_BACK),
            equipment = listOf(Equipment.BARBELL),
            descriptionUrl = "https://exrx.net/WeightExercises/Quadriceps/BBSquat",
            custom = false,
            parentId = null,
            deleted = false,
        )
        val raw = JsonIo.json.encodeToString(Exercise.serializer(), ex)
        val back = JsonIo.json.decodeFromString(Exercise.serializer(), raw)
        assertEquals(ex, back)
    }

    @Test fun `custom forked exercise round-trips with parentId`() {
        val ex = Exercise(
            id = "low-bar-squat-my-stance",
            name = "Low-bar squat (my stance)",
            pattern = MovementPattern.SQUAT,
            primaryMuscles = listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            equipment = listOf(Equipment.BARBELL),
            descriptionUrl = null,
            custom = true,
            parentId = "barbell-back-squat",
        )
        val back = JsonIo.json.decodeFromString(
            Exercise.serializer(),
            JsonIo.json.encodeToString(Exercise.serializer(), ex)
        )
        assertEquals(ex, back)
        assertTrue(back.custom)
        assertEquals("barbell-back-squat", back.parentId)
    }

    @Test fun `soft-deleted flag round-trips`() {
        val ex = Exercise(
            id = "weird-machine",
            name = "Weird Machine",
            pattern = MovementPattern.ISOLATION,
            primaryMuscles = listOf(MuscleGroup.BICEPS),
            equipment = listOf(Equipment.MACHINE),
            descriptionUrl = null,
            custom = true,
            deleted = true,
        )
        val back = JsonIo.json.decodeFromString(
            Exercise.serializer(),
            JsonIo.json.encodeToString(Exercise.serializer(), ex)
        )
        assertTrue(back.deleted)
        assertEquals(ex, back)
    }

    @Test fun `unknown future field is ignored`() {
        val raw = """{"id":"x","name":"X","pattern":"ISOLATION","primaryMuscles":["BICEPS"],"equipment":["MACHINE"],"descriptionUrl":null,"future_field":"ok"}"""
        val back = JsonIo.json.decodeFromString(Exercise.serializer(), raw)
        assertEquals("X", back.name)
        assertFalse(back.custom)
        assertFalse(back.deleted)
    }

    @Test fun `list of exercises round-trips`() {
        val list = listOf(
            Exercise("a", "A", pattern = MovementPattern.HINGE,
                primaryMuscles = listOf(MuscleGroup.HAMSTRINGS),
                equipment = listOf(Equipment.BARBELL), descriptionUrl = null),
            Exercise("b", "B", pattern = MovementPattern.CARRY,
                primaryMuscles = listOf(MuscleGroup.FOREARMS),
                equipment = listOf(Equipment.DUMBBELL), descriptionUrl = null),
        )
        val serializer = ListSerializer(Exercise.serializer())
        val back = JsonIo.json.decodeFromString(serializer,
            JsonIo.json.encodeToString(serializer, list))
        assertEquals(list, back)
    }
}
