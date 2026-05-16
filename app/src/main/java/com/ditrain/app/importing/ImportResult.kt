package com.ditrain.app.importing

import com.ditrain.app.model.Routine

/**
 * Outcome of [RoutineImporter.parse]. Either a valid [Success] holding the parsed
 * Routine, or a categorized failure the import dialog can render to the user.
 */
sealed interface ImportResult {

    data class Success(val routine: Routine) : ImportResult

    /** JSON failed to parse (bad syntax). */
    data class ParseError(val message: String) : ImportResult

    /** JSON parsed but contained unsupported schemaVersion. */
    data class UnsupportedSchemaVersion(val schemaVersion: Int) : ImportResult

    /** JSON parsed but routine references exerciseIds not present in the catalog. */
    data class MissingExerciseIds(val missingIds: List<String>) : ImportResult

    /** Structural validation failed (empty weeks, sessions with no blocks, OTHER cardio missing description). */
    data class InvalidStructure(val reason: String) : ImportResult
}
