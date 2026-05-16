package com.ditrain.app

import android.net.Uri
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ditrain.app.importing.ImportResult
import com.ditrain.app.importing.RoutineImporter
import com.ditrain.app.model.AppState
import com.ditrain.app.model.Routine
import com.ditrain.app.progression.ScheduleLayout
import com.ditrain.app.storage.AppStateRepository
import com.ditrain.app.storage.ExerciseCatalog
import com.ditrain.app.storage.RoutineRepository
import com.ditrain.app.ui.dialog.MainMenuDialogController
import com.ditrain.app.ui.dialog.RoutineImportDialogController
import com.ditrain.app.ui.dialog.RoutineListDialogController
import com.ditrain.app.ui.dialog.RoutinePreviewDialogController
import com.ditrain.app.ui.dialog.WeeklyPatternDialogController
import com.ditrain.app.ui.home.HomeViewController
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

class MainActivity : AppCompatActivity() {

    private val dp: (Int) -> Int = { dpToPx(it) }

    private lateinit var routineRepo: RoutineRepository
    private lateinit var appStateRepo: AppStateRepository
    private lateinit var catalog: ExerciseCatalog

    private var appState: AppState = AppState(null, emptyList(), com.ditrain.app.model.Settings())
    private var activeRoutine: Routine? = null

    private lateinit var rootContainer: FrameLayout
    private lateinit var home: HomeViewController

    private val pickRoutineFile: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val text = runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                toast("Couldn't read the selected file.")
                return@registerForActivityResult
            }
            handleImportText(text)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        routineRepo = RoutineRepository(filesDir)
        appStateRepo = AppStateRepository(filesDir)
        catalog = ExerciseCatalog.fromAssets(
            assets.open("exercises.json"),
            File(filesDir, "custom_exercises.json"),
        )

        home = HomeViewController(
            context = this,
            dp = dp,
            onMenuClick = { openMainMenu() },
            onImportNow = { openImport() },
        )

        rootContainer = FrameLayout(this)
        setContentView(rootContainer)

        renderHome()

        lifecycleScope.launch {
            appState = appStateRepo.load()
            activeRoutine = appState.activeRoutineId?.let { routineRepo.load(it) }
            renderHome()
        }
    }

    private fun renderHome() {
        rootContainer.removeAllViews()
        rootContainer.addView(home.buildView(activeRoutine))
    }

    // ───────────── Menu flow ─────────────

    private fun openMainMenu() {
        MainMenuDialogController(
            context = this,
            dp = dp,
            onOpenRoutines = { openRoutineList() },
            onImportRoutine = { openImport() },
        ).show()
    }

    private fun openRoutineList() = lifecycleScope.launch {
        val ids = routineRepo.list()
        val routines = ids.mapNotNull { routineRepo.load(it) }.sortedBy { it.name.lowercase() }
        RoutineListDialogController(
            context = this@MainActivity,
            dp = dp,
            activeRoutineId = appState.activeRoutineId,
            onView = { r -> openRoutinePreviewForView(r) },
            onActivate = { r -> startActivation(r) },
            onDelete = { r -> deleteRoutine(r) },
        ).show(routines)
    }

    private fun openRoutinePreviewForView(r: Routine) {
        RoutinePreviewDialogController(this, catalog, dp).show(
            routine = r,
            mode = RoutinePreviewDialogController.Mode.VIEW,
            onPrimary = { startActivation(r) },
            onSecondary = { /* close */ },
            primaryLabel = if (r.id == appState.activeRoutineId) "Re-activate" else "Activate",
            secondaryLabel = "Close",
        )
    }

    private fun deleteRoutine(r: Routine) = lifecycleScope.launch {
        val wasActive = r.id == appState.activeRoutineId
        routineRepo.delete(r.id)
        if (wasActive) {
            appState = appState.copy(activeRoutineId = null, scheduledSessions = emptyList())
            appStateRepo.save(appState)
            activeRoutine = null
            renderHome()
        }
        toast("Deleted \"${r.name}\".")
    }

    // ───────────── Import flow ─────────────

    private fun openImport() {
        RoutineImportDialogController(
            context = this,
            dp = dp,
            bundledExamples = listOf(
                RoutineImportDialogController.BundledExample(
                    assetPath = "example_routines/simple-ab-template.json",
                    displayName = "Simple A/B Template",
                    description = "Two full-body sessions that cycle Mon/Wed/Fri.",
                )
            ),
            loadBundledExample = { path ->
                assets.open(path).bufferedReader().use { it.readText() }
            },
            onParse = { json -> handleImportText(json) },
            onPickFile = {
                pickRoutineFile.launch(arrayOf("application/json", "*/*"))
            },
        ).show()
    }

    private fun handleImportText(json: String) {
        when (val result = RoutineImporter.parse(json, catalog)) {
            is ImportResult.Success -> previewAfterImport(result.routine)
            is ImportResult.ParseError -> showImportError("Couldn't parse JSON", result.message)
            is ImportResult.UnsupportedSchemaVersion -> showImportError(
                "Unsupported schema",
                "This routine was made by a newer (or older) DiTrain (schemaVersion=${result.schemaVersion}). Update the app.",
            )
            is ImportResult.InvalidStructure -> showImportError("Invalid routine structure", result.reason)
            is ImportResult.MissingExerciseIds -> showImportError(
                "Missing exercises",
                "These exercise ids aren't in the catalog yet:\n\n${result.missingIds.joinToString("\n") { "• $it" }}\n\nAdd them as custom exercises first, then retry.",
            )
        }
    }

    private fun showImportError(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun previewAfterImport(routine: Routine) {
        RoutinePreviewDialogController(this, catalog, dp).show(
            routine = routine,
            mode = RoutinePreviewDialogController.Mode.IMPORT,
            onPrimary = { saveImportedRoutine(routine, activate = true) },
            onSecondary = { saveImportedRoutine(routine, activate = false) },
            primaryLabel = "Save & activate",
            secondaryLabel = "Save only",
        )
    }

    private fun saveImportedRoutine(routine: Routine, activate: Boolean) = lifecycleScope.launch {
        routineRepo.save(routine)
        if (activate) {
            startActivation(routine)
        } else {
            toast("Saved \"${routine.name}\".")
        }
    }

    // ───────────── Activation flow ─────────────

    private fun startActivation(routine: Routine) {
        val pattern = routine.defaultWeeklyPattern
        if (pattern != null) {
            performActivation(routine, pattern, LocalDate.now())
        } else {
            WeeklyPatternDialogController(this, dp) { chosenPattern, startDate ->
                performActivation(routine, chosenPattern, startDate)
            }.show()
        }
    }

    private fun performActivation(
        routine: Routine,
        weeklyPattern: List<Int>,
        startDate: LocalDate,
    ) = lifecycleScope.launch {
        try {
            val scheduled = ScheduleLayout.lay(routine, weeklyPattern, startDate, futureWeeks = 8)
            appState = appState.copy(
                activeRoutineId = routine.id,
                scheduledSessions = scheduled,
            )
            appStateRepo.save(appState)
            activeRoutine = routine
            renderHome()
            toast("Activated \"${routine.name}\" with ${scheduled.size} scheduled sessions.")
        } catch (e: IllegalArgumentException) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Couldn't activate")
                .setMessage(e.message ?: "Unknown error")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // ───────────── Utilities ─────────────

    private fun dpToPx(dpValue: Int): Int =
        (dpValue * resources.displayMetrics.density).toInt()

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
