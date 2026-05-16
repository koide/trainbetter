package com.ditrain.app.storage

import com.ditrain.app.model.Equipment
import com.ditrain.app.model.Exercise
import com.ditrain.app.model.MovementPattern
import com.ditrain.app.model.MuscleGroup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExerciseCatalogTest {

    @get:Rule val tmp = TemporaryFolder()

    private val bundled = listOf(
        Exercise(
            id = "back-squat", name = "Back Squat",
            pattern = MovementPattern.SQUAT,
            primaryMuscles = listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            equipment = listOf(Equipment.BARBELL),
            descriptionUrl = "https://example/back-squat",
        ),
        Exercise(
            id = "bench", name = "Bench Press",
            pattern = MovementPattern.HORIZONTAL_PUSH,
            primaryMuscles = listOf(MuscleGroup.CHEST),
            equipment = listOf(Equipment.BARBELL),
            descriptionUrl = "https://example/bench",
        ),
    )

    @Test fun `bundled-only load exposes both entries`() = runBlocking {
        val catalog = ExerciseCatalog.fromInMemory(bundled, customsFile = File(tmp.root, "custom.json"))
        assertEquals(2, catalog.visibleExercises().size)
        assertEquals(bundled[0], catalog.byId("back-squat"))
    }

    @Test fun `custom exercise is added and resolvable`() = runBlocking {
        val customs = File(tmp.root, "custom.json")
        val catalog = ExerciseCatalog.fromInMemory(bundled, customsFile = customs)
        val custom = Exercise(
            id = "low-bar-my-stance", name = "Low-bar (my stance)",
            pattern = MovementPattern.SQUAT,
            primaryMuscles = listOf(MuscleGroup.QUADS),
            equipment = listOf(Equipment.BARBELL),
            descriptionUrl = null, custom = true, parentId = "back-squat",
        )
        catalog.addCustom(custom)
        assertEquals(3, catalog.visibleExercises().size)
        assertEquals(custom, catalog.byId("low-bar-my-stance"))
        assertTrue(customs.exists())
    }

    @Test fun `soft-deleted custom is hidden by default but still resolves by id`() = runBlocking {
        val customs = File(tmp.root, "custom.json")
        val catalog = ExerciseCatalog.fromInMemory(bundled, customsFile = customs)
        val custom = Exercise(
            id = "my-curl", name = "My Curl",
            pattern = MovementPattern.ISOLATION,
            primaryMuscles = listOf(MuscleGroup.BICEPS),
            equipment = listOf(Equipment.DUMBBELL),
            descriptionUrl = null, custom = true,
        )
        catalog.addCustom(custom)
        catalog.softDelete("my-curl")

        assertEquals(2, catalog.visibleExercises().size)
        val stillResolvable = catalog.byId("my-curl")
        assertEquals(custom.copy(deleted = true), stillResolvable)
        assertTrue(stillResolvable!!.deleted)
    }

    @Test fun `soft-deleted custom included when includeDeleted=true`() = runBlocking {
        val customs = File(tmp.root, "custom.json")
        val catalog = ExerciseCatalog.fromInMemory(bundled, customsFile = customs)
        catalog.addCustom(Exercise(
            id = "x", name = "X",
            pattern = MovementPattern.ISOLATION,
            primaryMuscles = listOf(MuscleGroup.BICEPS),
            equipment = listOf(Equipment.DUMBBELL),
            descriptionUrl = null, custom = true,
        ))
        catalog.softDelete("x")
        assertEquals(2, catalog.visibleExercises().size)
        assertEquals(3, catalog.visibleExercises(includeDeleted = true).size)
    }

    @Test fun `restore clears deleted flag`() = runBlocking {
        val catalog = ExerciseCatalog.fromInMemory(bundled, customsFile = File(tmp.root, "c.json"))
        catalog.addCustom(Exercise(
            id = "x", name = "X",
            pattern = MovementPattern.ISOLATION,
            primaryMuscles = listOf(MuscleGroup.BICEPS),
            equipment = listOf(Equipment.DUMBBELL),
            descriptionUrl = null, custom = true,
        ))
        catalog.softDelete("x")
        catalog.restore("x")
        assertFalse(catalog.byId("x")!!.deleted)
    }

    @Test fun `hard-delete is refused when references exist`() = runBlocking {
        val catalog = ExerciseCatalog.fromInMemory(bundled, customsFile = File(tmp.root, "c.json"))
        catalog.addCustom(Exercise(
            id = "x", name = "X",
            pattern = MovementPattern.ISOLATION,
            primaryMuscles = listOf(MuscleGroup.BICEPS),
            equipment = listOf(Equipment.DUMBBELL),
            descriptionUrl = null, custom = true,
        ))
        val refused = catalog.hardDelete("x", isReferenced = { id -> id == "x" })
        assertFalse(refused)
        assertEquals(3, catalog.visibleExercises(includeDeleted = true).size)
    }

    @Test fun `hard-delete succeeds when no references`() = runBlocking {
        val catalog = ExerciseCatalog.fromInMemory(bundled, customsFile = File(tmp.root, "c.json"))
        catalog.addCustom(Exercise(
            id = "y", name = "Y",
            pattern = MovementPattern.ISOLATION,
            primaryMuscles = listOf(MuscleGroup.BICEPS),
            equipment = listOf(Equipment.DUMBBELL),
            descriptionUrl = null, custom = true,
        ))
        val ok = catalog.hardDelete("y", isReferenced = { _ -> false })
        assertTrue(ok)
        assertNull(catalog.byId("y"))
    }

    @Test fun `bundled exercise cannot be soft or hard deleted`() = runBlocking {
        val catalog = ExerciseCatalog.fromInMemory(bundled, customsFile = File(tmp.root, "c.json"))
        val softOk = catalog.softDelete("back-squat")
        assertFalse(softOk)
        val hardOk = catalog.hardDelete("back-squat", isReferenced = { false })
        assertFalse(hardOk)
        assertFalse(catalog.byId("back-squat")!!.deleted)
    }

    @Test fun `customs persisted between catalog instances`() = runBlocking {
        val customs = File(tmp.root, "custom.json")
        val cat1 = ExerciseCatalog.fromInMemory(bundled, customsFile = customs)
        cat1.addCustom(Exercise(
            id = "z", name = "Z",
            pattern = MovementPattern.ISOLATION,
            primaryMuscles = listOf(MuscleGroup.BICEPS),
            equipment = listOf(Equipment.DUMBBELL),
            descriptionUrl = null, custom = true,
        ))
        val cat2 = ExerciseCatalog.fromInMemory(bundled, customsFile = customs)
        assertEquals("Z", cat2.byId("z")?.name)
    }
}
