package com.ditrain.app.importing

import com.ditrain.app.model.Equipment
import com.ditrain.app.model.Exercise
import com.ditrain.app.model.MovementPattern
import com.ditrain.app.model.MuscleGroup
import com.ditrain.app.storage.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RoutineImporterTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun catalog(extraIds: List<String> = emptyList()): ExerciseCatalog {
        val base = listOf(
            ex("squat", MovementPattern.SQUAT),
            ex("bench", MovementPattern.HORIZONTAL_PUSH),
            ex("row", MovementPattern.HORIZONTAL_PULL),
        ) + extraIds.map { ex(it, MovementPattern.ISOLATION) }
        return ExerciseCatalog.fromInMemory(base, customsFile = File(tmp.root, "c.json"))
    }

    private fun ex(id: String, pattern: MovementPattern) = Exercise(
        id = id, name = id, pattern = pattern,
        primaryMuscles = listOf(MuscleGroup.QUADS),
        equipment = listOf(Equipment.BARBELL),
        descriptionUrl = null,
    )

    private val minimalValid = """
        {
          "id": "r1",
          "name": "R1",
          "loopMode": "REPEAT",
          "schemaVersion": 1,
          "weeks": [
            {
              "label": "Week 1",
              "sessions": [
                {
                  "id": "d1",
                  "name": "Day 1",
                  "blocks": [
                    {
                      "exerciseId": "squat",
                      "sets": [
                        { "type": "straight", "reps": {"type":"fixed","reps":5}, "load": {"type":"open"} }
                      ]
                    }
                  ],
                  "cardioBlocks": []
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test fun `valid JSON returns Success with parsed routine`() {
        val r = RoutineImporter.parse(minimalValid, catalog())
        assertTrue(r is ImportResult.Success)
        assertEquals("r1", (r as ImportResult.Success).routine.id)
    }

    @Test fun `malformed JSON returns ParseError`() {
        val r = RoutineImporter.parse("{ bad", catalog())
        assertTrue("expected ParseError, got $r", r is ImportResult.ParseError)
    }

    @Test fun `wrong schemaVersion returns UnsupportedSchemaVersion`() {
        val r = RoutineImporter.parse(
            minimalValid.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99"),
            catalog(),
        )
        assertEquals(ImportResult.UnsupportedSchemaVersion(99), r)
    }

    @Test fun `missing exerciseId returns MissingExerciseIds with that id`() {
        val r = RoutineImporter.parse(
            minimalValid.replace("\"exerciseId\": \"squat\"", "\"exerciseId\": \"nonexistent\""),
            catalog(),
        )
        assertTrue(r is ImportResult.MissingExerciseIds)
        assertEquals(listOf("nonexistent"), (r as ImportResult.MissingExerciseIds).missingIds)
    }

    @Test fun `multiple missing exercise ids are all reported`() {
        val custom = """
            {
              "id": "r2", "name": "R2", "loopMode": "REPEAT", "schemaVersion": 1,
              "weeks": [{
                "label": "W1",
                "sessions": [{
                  "id": "d1", "name": "D1",
                  "blocks": [
                    {"exerciseId": "unknown-a", "sets": [{"type":"straight","reps":{"type":"fixed","reps":5},"load":{"type":"open"}}]},
                    {"exerciseId": "unknown-b", "sets": [{"type":"straight","reps":{"type":"fixed","reps":5},"load":{"type":"open"}}]}
                  ],
                  "cardioBlocks": []
                }]
              }]
            }
        """.trimIndent()
        val r = RoutineImporter.parse(custom, catalog())
        assertTrue(r is ImportResult.MissingExerciseIds)
        assertEquals(setOf("unknown-a", "unknown-b"), (r as ImportResult.MissingExerciseIds).missingIds.toSet())
    }

    @Test fun `empty weeks returns InvalidStructure`() {
        val emptyWeeks = """
            {
              "id": "r3", "name": "R3", "loopMode": "REPEAT", "schemaVersion": 1,
              "weeks": []
            }
        """.trimIndent()
        val r = RoutineImporter.parse(emptyWeeks, catalog())
        assertTrue("expected InvalidStructure, got $r", r is ImportResult.InvalidStructure)
    }

    @Test fun `session with no blocks and no cardio is invalid`() {
        val noBlocks = """
            {
              "id": "r3", "name": "R3", "loopMode": "REPEAT", "schemaVersion": 1,
              "weeks": [{
                "label": "W1",
                "sessions": [{"id": "d1", "name": "D1", "blocks": [], "cardioBlocks": []}]
              }]
            }
        """.trimIndent()
        val r = RoutineImporter.parse(noBlocks, catalog())
        assertTrue(r is ImportResult.InvalidStructure)
    }

    @Test fun `cardio-only session is valid (no strength blocks needed)`() {
        val cardioOnly = """
            {
              "id": "r4", "name": "R4", "loopMode": "REPEAT", "schemaVersion": 1,
              "weeks": [{
                "label": "W1",
                "sessions": [{
                  "id": "d1", "name": "D1",
                  "blocks": [],
                  "cardioBlocks": [{"activityKind": "RUNNING", "targetDurationMin": 30}]
                }]
              }]
            }
        """.trimIndent()
        val r = RoutineImporter.parse(cardioOnly, catalog())
        assertTrue("expected Success, got $r", r is ImportResult.Success)
    }

    @Test fun `cardio block of kind OTHER without description is invalid`() {
        val badCardio = """
            {
              "id": "r5", "name": "R5", "loopMode": "REPEAT", "schemaVersion": 1,
              "weeks": [{
                "label": "W1",
                "sessions": [{
                  "id": "d1", "name": "D1",
                  "blocks": [],
                  "cardioBlocks": [{"activityKind": "OTHER", "targetDurationMin": 25}]
                }]
              }]
            }
        """.trimIndent()
        val r = RoutineImporter.parse(badCardio, catalog())
        assertTrue("expected InvalidStructure, got $r", r is ImportResult.InvalidStructure)
    }

    @Test fun `cardio OTHER with description is valid`() {
        val okCardio = """
            {
              "id": "r6", "name": "R6", "loopMode": "REPEAT", "schemaVersion": 1,
              "weeks": [{
                "label": "W1",
                "sessions": [{
                  "id": "d1", "name": "D1",
                  "blocks": [],
                  "cardioBlocks": [{"activityKind": "OTHER", "description": "Stair-master intervals", "targetDurationMin": 25}]
                }]
              }]
            }
        """.trimIndent()
        val r = RoutineImporter.parse(okCardio, catalog())
        assertTrue("expected Success, got $r", r is ImportResult.Success)
    }
}
