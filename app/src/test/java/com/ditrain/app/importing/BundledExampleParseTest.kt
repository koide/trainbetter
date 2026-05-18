package com.ditrain.app.importing

import com.ditrain.app.storage.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Validates that every bundled example routine under `assets/example_routines/`
 * parses cleanly via [RoutineImporter] using the bundled catalog. Catches
 * authoring errors (missing exerciseIds, malformed JSON, invalid structure)
 * before they reach the user.
 *
 * Resolves asset files relative to the current working directory, which for
 * unit tests is the `app/` module root.
 */
class BundledExampleParseTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `all bundled example routines parse to Success`() {
        val catalog = ExerciseCatalog.fromAssets(
            File("src/main/assets/exercises.json").inputStream(),
            customsFile = File(tmp.root, "c.json"),
        )
        val examplesDir = File("src/main/assets/example_routines")
        assertTrue("missing assets/example_routines dir at ${examplesDir.absolutePath}", examplesDir.isDirectory)

        val examples = examplesDir.listFiles { f -> f.isFile && f.extension == "json" }
            ?.sortedBy { it.name }
            ?: emptyList()
        assertTrue("expected at least one bundled example, found none", examples.isNotEmpty())

        val failures = mutableListOf<String>()
        examples.forEach { file ->
            val result = RoutineImporter.parse(file.readText(), catalog)
            if (result !is ImportResult.Success) {
                failures += "${file.name}: $result"
            }
        }
        assertEquals(
            "Bundled example(s) failed to parse:\n" + failures.joinToString("\n"),
            emptyList<String>(),
            failures,
        )
    }
}
