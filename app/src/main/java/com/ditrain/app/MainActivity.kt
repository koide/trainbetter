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
    private lateinit var sessionLogRepo: com.ditrain.app.storage.SessionLogRepository
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
        sessionLogRepo = com.ditrain.app.storage.SessionLogRepository(filesDir)
        catalog = ExerciseCatalog.fromAssets(
            assets.open("exercises.json"),
            File(filesDir, "custom_exercises.json"),
        )

        home = HomeViewController(
            context = this,
            dp = dp,
            onMenuClick = { openMainMenu() },
            onImportNow = { openImport() },
            onStartOrResumeSession = { openSession() },
        )

        rootContainer = FrameLayout(this)
        setContentView(rootContainer)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }

        renderHome()

        lifecycleScope.launch {
            appState = appStateRepo.load()
            activeRoutine = appState.activeRoutineId?.let { routineRepo.load(it) }
            renderHome()
        }
    }

    private fun renderHome() {
        rootContainer.removeAllViews()
        val today = computeTodayCard()
        rootContainer.addView(home.buildView(activeRoutine, today))
    }

    private fun computeTodayCard(): com.ditrain.app.ui.home.HomeViewController.TodayCard? {
        val routine = activeRoutine ?: return null
        val todayIso = java.time.LocalDate.now().toString()
        val candidate = appState.scheduledSessions
            .filter { it.date <= todayIso }
            .minByOrNull { it.date } ?: return null

        val week = routine.weeks.getOrNull(candidate.weekIndex) ?: return null
        val template = week.sessions.firstOrNull { it.id == candidate.sessionTemplateId } ?: return null
        val resuming = candidate.sessionLogId != null
        return com.ditrain.app.ui.home.HomeViewController.TodayCard(
            sessionTemplateName = template.name,
            weekLabel = week.label,
            scheduledDate = candidate.date,
            resuming = resuming,
        )
    }

    // ───────────── Menu flow ─────────────

    private fun openMainMenu() {
        MainMenuDialogController(
            context = this,
            dp = dp,
            onOpenRoutines = { openRoutineList() },
            onImportRoutine = { openImport() },
            onBrowseExercises = { openExerciseBrowser() },
            onCreateRoutine = { openBuilder() },
        ).show()
    }

    private fun openExerciseBrowser() {
        val detail = com.ditrain.app.ui.dialog.ExerciseDetailDialogController(this, dp)
        com.ditrain.app.ui.dialog.ExercisePickerDialogController(
            context = this,
            catalog = catalog,
            dp = dp,
            onPicked = { ex ->
                // No "pick" action in browse mode — show details instead.
                detail.show(ex)
            },
            onDetail = { ex -> detail.show(ex) },
            title = "Exercise catalog",
        ).show()
    }

    private fun openBuilder() {
        com.ditrain.app.ui.dialog.builder.RoutineMetaDialogController(
            context = this, dp = dp,
        ) { meta ->
            val draft = com.ditrain.app.model.Routine(
                id = java.util.UUID.randomUUID().toString().take(8),
                name = meta.name,
                description = meta.description,
                loopMode = meta.loopMode,
                weeks = List(meta.weekCount) { i ->
                    com.ditrain.app.model.Week(label = "Week ${i + 1}", sessions = emptyList())
                },
            )
            editWeek(draft, weekIndex = 0)
        }.show()
    }

    private fun editWeek(draft: com.ditrain.app.model.Routine, weekIndex: Int) {
        val prev: com.ditrain.app.model.Week? = if (weekIndex > 0) draft.weeks[weekIndex - 1] else null
        com.ditrain.app.ui.dialog.builder.WeekEditorDialogController(
            context = this, catalog = catalog, dp = dp,
            onSave = { week ->
                val updatedWeeks = draft.weeks.toMutableList().also { it[weekIndex] = week }
                val updated = draft.copy(weeks = updatedWeeks)
                if (weekIndex + 1 < updated.weeks.size) editWeek(updated, weekIndex + 1)
                else builderReview(updated)
            },
            onApplyToAllRemaining = { week ->
                // Propagate this week's content to every remaining slot, regenerating
                // session ids so each week has unique session identifiers.
                val updatedWeeks = draft.weeks.toMutableList()
                for (i in weekIndex until draft.weeks.size) {
                    val regeneratedSessions = week.sessions.map { s ->
                        s.copy(id = java.util.UUID.randomUUID().toString().take(8))
                    }
                    updatedWeeks[i] = com.ditrain.app.model.Week(
                        label = if (i == weekIndex) week.label else "Week ${i + 1}",
                        sessions = regeneratedSessions,
                    )
                }
                builderReview(draft.copy(weeks = updatedWeeks))
            },
        ).show(
            weekIndex = weekIndex,
            totalWeeks = draft.weeks.size,
            initial = draft.weeks[weekIndex].takeIf { it.sessions.isNotEmpty() },
            previousWeek = prev,
        )
    }

    private fun builderReview(draft: com.ditrain.app.model.Routine) {
        RoutinePreviewDialogController(this, catalog, dp).show(
            routine = draft,
            mode = RoutinePreviewDialogController.Mode.IMPORT,
            onPrimary = { saveBuiltRoutine(draft, activate = true) },
            onSecondary = { saveBuiltRoutine(draft, activate = false) },
            primaryLabel = "Save & activate",
            secondaryLabel = "Save only",
            titleOverride = "Review and save",
            introText = "Review your routine below, then tap Save & activate (recommended) or Save only.",
        )
    }

    private fun saveBuiltRoutine(routine: com.ditrain.app.model.Routine, activate: Boolean) {
        lifecycleScope.launch {
            val wasActive = routine.id == appState.activeRoutineId
            routineRepo.save(routine)
            if (activate) {
                startActivation(routine)
            } else if (wasActive) {
                // The routine was edited but the user chose "Save only" — keep the
                // existing schedule. The user can re-activate later to re-lay out
                // the schedule (since session ids may have changed).
                activeRoutine = routine
                renderHome()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Saved")
                    .setMessage("\"${routine.name}\" updated. Your existing schedule still references this routine; if session content changed, re-activate to re-layout the schedule from today.")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                toast("Saved \"${routine.name}\".")
            }
        }
    }

    private fun openEditor(existing: com.ditrain.app.model.Routine) {
        com.ditrain.app.ui.dialog.builder.RoutineMetaDialogController(
            context = this, dp = dp,
        ) { meta ->
            // Migrate the existing weeks list to the new week count: keep up to N
            // existing weeks, pad with empty weeks if increasing.
            val adjustedWeeks = adjustWeekCount(existing.weeks, meta.weekCount)
            val draft = existing.copy(
                name = meta.name,
                description = meta.description,
                loopMode = meta.loopMode,
                weeks = adjustedWeeks,
            )
            editWeek(draft, weekIndex = 0)
        }.show(initial = com.ditrain.app.ui.dialog.builder.RoutineMetaDialogController.RoutineMeta(
            name = existing.name,
            description = existing.description,
            loopMode = existing.loopMode,
            weekCount = existing.weeks.size,
        ))
    }

    private fun adjustWeekCount(
        existing: List<com.ditrain.app.model.Week>,
        target: Int,
    ): List<com.ditrain.app.model.Week> = when {
        target == existing.size -> existing
        target < existing.size -> existing.take(target)
        else -> existing + List(target - existing.size) { i ->
            com.ditrain.app.model.Week(label = "Week ${existing.size + i + 1}", sessions = emptyList())
        }
    }

    private fun openRoutineList() = lifecycleScope.launch {
        val ids = routineRepo.list()
        val routines = ids.mapNotNull { routineRepo.load(it) }.sortedBy { it.name.lowercase() }
        RoutineListDialogController(
            context = this@MainActivity,
            dp = dp,
            activeRoutineId = appState.activeRoutineId,
            onView = { r -> openRoutinePreviewForView(r) },
            onEdit = { r -> openEditor(r) },
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

    private fun deleteRoutine(r: Routine) {
        lifecycleScope.launch {
            val wasActive = r.id == appState.activeRoutineId
            routineRepo.delete(r.id)
            if (wasActive) {
                appState = appState.copy(activeRoutineId = null, scheduledSessions = emptyList())
                appStateRepo.save(appState)
                activeRoutine = null
                renderHome()
            }
            toast("Deleted \"${r.name}\".")
            // Re-open the list so the user sees the deletion reflected. The
            // RoutineList dialog dismissed itself in the Delete handler so we
            // need to bring it back with the updated state.
            openRoutineList()
        }
    }

    // ───────────── Import flow ─────────────

    private fun openImport() {
        RoutineImportDialogController(
            context = this,
            dp = dp,
            bundledExamples = listOf(
                RoutineImportDialogController.BundledExample(
                    assetPath = "example_routines/fullbody-3x.json",
                    displayName = "Full-body 3x/week",
                    description = "3-day full-body, alternating squat / deadlift / front-squat focus. Mon/Wed/Fri.",
                ),
                RoutineImportDialogController.BundledExample(
                    assetPath = "example_routines/upper-lower-4x.json",
                    displayName = "Upper/Lower 4x/week",
                    description = "Classic upper-lower split with heavy and volume days. Mon/Tue/Thu/Fri.",
                ),
                RoutineImportDialogController.BundledExample(
                    assetPath = "example_routines/ppl-6x.json",
                    displayName = "Push/Pull/Legs 6x/week",
                    description = "Six-day push-pull-legs with A and B variation days. Mon–Sat.",
                ),
                RoutineImportDialogController.BundledExample(
                    assetPath = "example_routines/fullbody-cardio-2x2.json",
                    displayName = "Full-body + Cardio (2+2)",
                    description = "2 full-body days + 2 cardio days. Mon (strength) / Tue (cardio) / Thu / Fri.",
                ),
                RoutineImportDialogController.BundledExample(
                    assetPath = "example_routines/simple-ab-template.json",
                    displayName = "Simple A/B Template",
                    description = "Two-day alternating full-body. Minimal starting point.",
                ),
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

    // ───────────── Session execution flow ─────────────

    private var sessionController: com.ditrain.app.ui.session.SessionViewController? = null

    private fun openSession() = lifecycleScope.launch {
        sessionHistoryCache = null
        val routine = activeRoutine ?: return@launch
        val todayIso = java.time.LocalDate.now().toString()
        val scheduled = appState.scheduledSessions
            .filter { it.date <= todayIso }
            .minByOrNull { it.date } ?: return@launch
        val week = routine.weeks.getOrNull(scheduled.weekIndex) ?: return@launch
        val template = week.sessions.firstOrNull { it.id == scheduled.sessionTemplateId } ?: return@launch

        val existingLogId = scheduled.sessionLogId
        val wasNewSession = existingLogId == null
        val initialLog = if (existingLogId != null) {
            sessionLogRepo.loadAll().firstOrNull { it.id == existingLogId }
                ?: buildFreshSessionLog(routine, scheduled, template)
        } else {
            buildFreshSessionLog(routine, scheduled, template)
        }

        if (existingLogId == null) {
            val updatedScheduled = appState.scheduledSessions.map {
                if (it === scheduled) it.copy(sessionLogId = initialLog.id) else it
            }
            appState = appState.copy(scheduledSessions = updatedScheduled)
            appStateRepo.save(appState)
            sessionLogRepo.upsert(initialLog)
        }

        val state = com.ditrain.app.ui.session.SessionState(initialLog)
        val controller = com.ditrain.app.ui.session.SessionViewController(
            context = this@MainActivity,
            catalog = catalog,
            dp = dp,
            settings = appState.settings,
            nowIso = { java.time.Instant.now().toString() },
            resolveLastTopWeightKg = { exerciseId -> lastTopWeightKg(exerciseId) },
            resolveLatestE1rmKg = { exerciseId -> latestE1rmKg(exerciseId) },
            onMutated = { lifecycleScope.launch { sessionLogRepo.upsert(state.log) } },
            onAbortSaveInProgress = { closeSession() },
            onAbortDiscard = { lifecycleScope.launch { discardSession(initialLog.id, clearSchedule = wasNewSession) } },
            onFinish = { lifecycleScope.launch { finishSession(state) } },
        )
        sessionController = controller
        rootContainer.removeAllViews()
        rootContainer.addView(controller.buildView(routine, template, state))
    }

    private fun buildFreshSessionLog(
        routine: com.ditrain.app.model.Routine,
        scheduled: com.ditrain.app.model.ScheduledSession,
        template: com.ditrain.app.model.SessionTemplate,
    ): com.ditrain.app.model.SessionLog {
        val nowIso = java.time.Instant.now().toString()
        return com.ditrain.app.model.SessionLog(
            id = java.util.UUID.randomUUID().toString(),
            routineId = routine.id,
            weekIndex = scheduled.weekIndex,
            sessionTemplateId = template.id,
            scheduledDate = scheduled.date,
            performedDate = java.time.LocalDate.now().toString(),
            startedAt = nowIso,
            completedAt = null,
            executed = template.blocks.map {
                com.ditrain.app.model.ExecutedExercise(exerciseId = it.exerciseId)
            },
            cardioExecuted = emptyList(),
        )
    }

    private fun closeSession() {
        sessionController = null
        renderHome()
    }

    private suspend fun discardSession(sessionId: String, clearSchedule: Boolean) {
        sessionLogRepo.delete(sessionId)
        if (clearSchedule) {
            val updated = appState.scheduledSessions.map {
                if (it.sessionLogId == sessionId) it.copy(sessionLogId = null) else it
            }
            appState = appState.copy(scheduledSessions = updated)
            appStateRepo.save(appState)
        }
        closeSession()
    }

    private suspend fun finishSession(state: com.ditrain.app.ui.session.SessionState) {
        state.markCompleted(java.time.Instant.now().toString())
        sessionLogRepo.upsert(state.log)
        val finishedLogId = state.log.id
        val updated = appState.scheduledSessions.filterNot { it.sessionLogId == finishedLogId }
        appState = appState.copy(scheduledSessions = updated)
        appStateRepo.save(appState)
        closeSession()
        toast("Session finished. ${state.log.executed.sumOf { it.sets.size }} sets logged.")
    }

    private var sessionHistoryCache: List<com.ditrain.app.model.SessionLog>? = null

    private fun lastTopWeightKg(exerciseId: String): Double? {
        val logs = sessionHistoryCacheOrLoad()
        return logs
            .sortedByDescending { it.performedDate }
            .flatMap { it.executed }
            .firstOrNull { it.exerciseId == exerciseId && !it.skipped && it.sets.isNotEmpty() }
            ?.sets
            ?.maxOfOrNull { s ->
                when (s) {
                    is com.ditrain.app.model.LoggedSet.Straight -> s.weightKg
                    is com.ditrain.app.model.LoggedSet.MyoRep -> s.weightKg
                }
            }
    }

    private fun latestE1rmKg(exerciseId: String): Double? {
        val logs = sessionHistoryCacheOrLoad()
        val candidates = logs
            .flatMap { it.executed }
            .filter { it.exerciseId == exerciseId && !it.skipped }
            .flatMap { it.sets }
            .mapNotNull { s ->
                when (s) {
                    is com.ditrain.app.model.LoggedSet.Straight ->
                        com.ditrain.app.progression.E1rm.estimate(s.weightKg, s.reps, s.rpe)
                    is com.ditrain.app.model.LoggedSet.MyoRep ->
                        com.ditrain.app.progression.E1rm.estimate(s.weightKg, s.activationReps, s.activationRpe)
                }
            }
        return candidates.maxOrNull()
    }

    private fun sessionHistoryCacheOrLoad(): List<com.ditrain.app.model.SessionLog> {
        sessionHistoryCache?.let { return it }
        val logs = kotlinx.coroutines.runBlocking { sessionLogRepo.loadAll() }
        sessionHistoryCache = logs
        return logs
    }

    // ───────────── Utilities ─────────────

    private fun dpToPx(dpValue: Int): Int =
        (dpValue * resources.displayMetrics.density).toInt()

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
