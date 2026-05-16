package com.ditrain.app.importing

import com.ditrain.app.model.CardioKind
import com.ditrain.app.model.Routine
import com.ditrain.app.storage.ExerciseCatalog
import com.ditrain.app.util.JsonIo
import kotlinx.serialization.SerializationException

/**
 * Parses a routine JSON string into a [Routine] and validates it against the catalog.
 * Pure JVM, no Android imports — fully testable as a unit.
 *
 * Validation order:
 *   1. JSON deserialization (kotlinx-serialization). Failure → [ImportResult.ParseError].
 *   2. schemaVersion == [SUPPORTED_SCHEMA_VERSION]. Otherwise → [ImportResult.UnsupportedSchemaVersion].
 *   3. Structural: every week has ≥1 session; every session has ≥1 strength block OR ≥1 cardio block.
 *      Cardio blocks of kind OTHER require a non-blank description.
 *      Failures → [ImportResult.InvalidStructure].
 *   4. Catalog: every `ExerciseBlock.exerciseId` resolves in the catalog (including soft-deleted entries).
 *      Failures → [ImportResult.MissingExerciseIds] with the full set of unknown ids.
 *
 * On success: [ImportResult.Success] with the parsed Routine.
 */
object RoutineImporter {

    const val SUPPORTED_SCHEMA_VERSION: Int = 1

    fun parse(json: String, catalog: ExerciseCatalog): ImportResult {
        val routine: Routine = try {
            JsonIo.json.decodeFromString(Routine.serializer(), json)
        } catch (e: SerializationException) {
            return ImportResult.ParseError(e.message ?: "couldn't parse JSON")
        } catch (e: IllegalArgumentException) {
            return ImportResult.ParseError(e.message ?: "couldn't parse JSON")
        }

        if (routine.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return ImportResult.UnsupportedSchemaVersion(routine.schemaVersion)
        }

        validateStructure(routine)?.let { return it }
        validateCatalog(routine, catalog)?.let { return it }

        return ImportResult.Success(routine)
    }

    private fun validateStructure(routine: Routine): ImportResult.InvalidStructure? {
        if (routine.weeks.isEmpty()) {
            return ImportResult.InvalidStructure("routine has no weeks")
        }
        routine.weeks.forEachIndexed { wIdx, week ->
            if (week.sessions.isEmpty()) {
                return ImportResult.InvalidStructure("week ${wIdx + 1} (${week.label}) has no sessions")
            }
            week.sessions.forEach { session ->
                if (session.blocks.isEmpty() && session.cardioBlocks.isEmpty()) {
                    return ImportResult.InvalidStructure(
                        "session ${session.id} (${session.name}) has neither strength blocks nor cardio blocks"
                    )
                }
                session.cardioBlocks.forEach { cb ->
                    if (cb.activityKind == CardioKind.OTHER && cb.description.isNullOrBlank()) {
                        return ImportResult.InvalidStructure(
                            "session ${session.id}: cardio block of kind OTHER requires a description"
                        )
                    }
                }
            }
        }
        return null
    }

    private fun validateCatalog(routine: Routine, catalog: ExerciseCatalog): ImportResult.MissingExerciseIds? {
        val missing = linkedSetOf<String>()
        for (week in routine.weeks) {
            for (session in week.sessions) {
                for (block in session.blocks) {
                    // catalog.byId resolves deleted entries too — references to soft-deleted
                    // customs are intentionally allowed (spec §6.1).
                    if (catalog.byId(block.exerciseId) == null) {
                        missing.add(block.exerciseId)
                    }
                }
            }
        }
        return if (missing.isEmpty()) null else ImportResult.MissingExerciseIds(missing.toList())
    }
}
