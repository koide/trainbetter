# DiTrain v1 — Plan 2.5: Routine builder

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** User can build a routine through a multi-step wizard without touching JSON. Create-only in this milestone — editing existing routines is deferred to v3 (workaround: import → tweak JSON → save as new). The JSON import path stays available for power users and inter-device routine sharing.

**Architecture:** A chain of dialog controllers, each editing one level of the routine tree. Each editor receives an initial value (or null for new) and a `onSave: (T) -> Unit` callback. The orchestration lives in `MainActivity` — no `RoutineBuilderController` god-class needed; just a sequence of `openX → onSave → openY` lambdas. Final review reuses the existing `RoutinePreviewDialogController` in `Mode.IMPORT` (Save & activate vs. Save only buttons).

**Tech Stack:** No new dependencies. Reuses everything from Plan 2.

**Reference spec:** `docs/superpowers/specs/2026-05-15-ditrain-v1-design.md` — §4.2 Routine schema.

**Reference plan:** Plan 2 (`docs/superpowers/plans/2026-05-15-ditrain-v1-routines.md`) — established dialog patterns and the existing `RoutinePreviewDialogController`/`ExercisePickerDialogController` are reused.

---

## Pre-flight

- Working directory: `C:\Users\Usuario\Documents\TrainBetter`
- Branch: `main`
- Base commit at plan start: `36cae2e` (v0.2.0-routines + status bar fix + catalog expansion)
- Build env vars: `JAVA_HOME` + `ANDROID_HOME` per Plan 1 README.

---

## Conventions

- Bash tool for shell. Gradle env inline: `JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat ...`
- Commit messages: Conventional Commits, single line, **NO `Co-Authored-By:` or any other trailer**.
- No new unit tests in this plan — these are pure UI controllers. Acceptance is `assembleDebug` succeeding and manual smoke on device.

---

## File Structure (Plan 2.5)

Creates seven new files in a new `ui/dialog/builder/` package, plus small edits to two existing files:

```
TrainBetter/
└── app/src/main/java/com/ditrain/app/
    ├── MainActivity.kt                              # MODIFY: add openBuilder() flow
    └── ui/dialog/
        ├── MainMenuDialogController.kt              # MODIFY: add "Create routine" row
        └── builder/
            ├── RoutineMetaDialogController.kt        # CREATE
            ├── WeekEditorDialogController.kt         # CREATE
            ├── SessionEditorDialogController.kt      # CREATE
            ├── ExerciseBlockEditorDialogController.kt # CREATE
            ├── SetPrescriptionEditorDialogController.kt # CREATE
            └── CardioBlockEditorDialogController.kt   # CREATE
```

**Files deliberately NOT in this plan:**
- Edit-existing-routine flow (Plan 3+ if user revisits).
- Any tests — UI dialogs follow DiRead's manual-test-on-device convention.

---

## Task 1: `SetPrescriptionEditorDialogController` — leaf-level set editor

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/dialog/builder/SetPrescriptionEditorDialogController.kt`

The most fundamental editor: edit one `SetPrescription`. Two type variants (Straight, MyoRep), with all the prescription fields exposed.

- [ ] **Step 1: Implement** the file with exactly this content:

```kotlin
package com.ditrain.app.ui.dialog.builder

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.model.LoadTarget
import com.ditrain.app.model.RepsTarget
import com.ditrain.app.model.SetPrescription

/**
 * Edits one [SetPrescription]. The user picks Straight or Myo-rep, then fills in the
 * fields. Save → [onSave] fires with the assembled prescription.
 *
 * Reps target shape:
 *  - radio: Fixed / Range / AMRAP / AMRAP-min
 *  - one or two number inputs depending on the choice
 *
 * Load target shape:
 *  - radio: Absolute (kg) / %1RM / RPE / Relative-to-last (kg delta) / Open
 *  - one number input matching the choice (none for Open)
 */
class SetPrescriptionEditorDialogController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val onSave: (SetPrescription) -> Unit,
) {

    fun show(initial: SetPrescription? = null) {
        val typeIsMyo = initial is SetPrescription.MyoRep

        // ── Type radio ──
        val typeStraightRb = RadioButton(context).apply { text = "Straight"; setTextColor(Color.WHITE); isChecked = !typeIsMyo; id = View.generateViewId() }
        val typeMyoRb = RadioButton(context).apply { text = "Myo-rep"; setTextColor(Color.WHITE); isChecked = typeIsMyo; id = View.generateViewId() }
        val typeGroup = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL; addView(typeStraightRb); addView(typeMyoRb) }

        // ── Reps target ──
        val repsModeFixed = RadioButton(context).apply { text = "Fixed"; setTextColor(Color.WHITE); id = View.generateViewId() }
        val repsModeRange = RadioButton(context).apply { text = "Range"; setTextColor(Color.WHITE); id = View.generateViewId() }
        val repsModeAmrap = RadioButton(context).apply { text = "AMRAP"; setTextColor(Color.WHITE); id = View.generateViewId() }
        val repsModeAmrapMin = RadioButton(context).apply { text = "AMRAP≥"; setTextColor(Color.WHITE); id = View.generateViewId() }
        val repsGroup = RadioGroup(context).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(repsModeFixed); addView(repsModeRange); addView(repsModeAmrap); addView(repsModeAmrapMin)
        }
        val repsA = numberInput("reps")
        val repsB = numberInput("max")

        fun selectRepsMode(rb: RadioButton) {
            repsModeFixed.isChecked = rb === repsModeFixed
            repsModeRange.isChecked = rb === repsModeRange
            repsModeAmrap.isChecked = rb === repsModeAmrap
            repsModeAmrapMin.isChecked = rb === repsModeAmrapMin
            repsA.visibility = if (rb === repsModeAmrap) View.GONE else View.VISIBLE
            repsB.visibility = if (rb === repsModeRange) View.VISIBLE else View.GONE
            repsA.hint = when (rb) {
                repsModeFixed -> "reps"; repsModeRange -> "min"; repsModeAmrapMin -> "min reps"; else -> "reps"
            }
        }
        listOf(repsModeFixed, repsModeRange, repsModeAmrap, repsModeAmrapMin).forEach { rb ->
            rb.setOnClickListener { selectRepsMode(rb) }
        }

        // ── Load target ──
        val loadKg = RadioButton(context).apply { text = "kg"; setTextColor(Color.WHITE); id = View.generateViewId() }
        val loadPct = RadioButton(context).apply { text = "%1RM"; setTextColor(Color.WHITE); id = View.generateViewId() }
        val loadRpe = RadioButton(context).apply { text = "RPE"; setTextColor(Color.WHITE); id = View.generateViewId() }
        val loadRel = RadioButton(context).apply { text = "+/− kg"; setTextColor(Color.WHITE); id = View.generateViewId() }
        val loadOpen = RadioButton(context).apply { text = "open"; setTextColor(Color.WHITE); id = View.generateViewId() }
        val loadGroup = RadioGroup(context).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(loadKg); addView(loadPct); addView(loadRpe); addView(loadRel); addView(loadOpen)
        }
        val loadValue = numberInput("value")

        fun selectLoadMode(rb: RadioButton) {
            loadKg.isChecked = rb === loadKg
            loadPct.isChecked = rb === loadPct
            loadRpe.isChecked = rb === loadRpe
            loadRel.isChecked = rb === loadRel
            loadOpen.isChecked = rb === loadOpen
            loadValue.visibility = if (rb === loadOpen) View.GONE else View.VISIBLE
            loadValue.hint = when (rb) {
                loadKg -> "kg"; loadPct -> "0.80 = 80%"; loadRpe -> "8.5"; loadRel -> "+2.5 kg"; else -> ""
            }
        }
        listOf(loadKg, loadPct, loadRpe, loadRel, loadOpen).forEach { rb ->
            rb.setOnClickListener { selectLoadMode(rb) }
        }

        // ── Optional fields ──
        val rpeTarget = numberInput("optional rpe target")
        val restSec = numberInput("rest sec")
        val tempo = EditText(context).apply { hint = "tempo (e.g. 3-1-1)"; setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#94A3B8")) }
        val notes = EditText(context).apply { hint = "notes"; setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#94A3B8")) }

        // ── Myo-rep-specific fields ──
        val miniSetTargetReps = numberInput("mini-set target reps (e.g. 5)")
        val miniSetCount = numberInput("mini-set count (e.g. 3)")
        val miniSetRestSec = numberInput("mini-set rest sec (default 15)")
        val rpeStopThreshold = numberInput("RPE stop threshold (default 10)")

        val myoFields = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Myo-rep cluster"))
            addView(miniSetTargetReps); addView(miniSetCount); addView(miniSetRestSec); addView(rpeStopThreshold)
            visibility = if (typeIsMyo) View.VISIBLE else View.GONE
        }
        typeStraightRb.setOnClickListener {
            typeStraightRb.isChecked = true; typeMyoRb.isChecked = false; myoFields.visibility = View.GONE
        }
        typeMyoRb.setOnClickListener {
            typeStraightRb.isChecked = false; typeMyoRb.isChecked = true; myoFields.visibility = View.VISIBLE
        }

        // ── Preload from `initial` ──
        when (initial) {
            null -> { selectRepsMode(repsModeFixed); selectLoadMode(loadOpen) }
            is SetPrescription.Straight -> {
                preloadReps(initial.reps, repsModeFixed, repsModeRange, repsModeAmrap, repsModeAmrapMin, repsA, repsB)
                selectRepsMode(currentRepsMode(initial.reps, repsModeFixed, repsModeRange, repsModeAmrap, repsModeAmrapMin))
                preloadLoad(initial.load, loadKg, loadPct, loadRpe, loadRel, loadOpen, loadValue)
                selectLoadMode(currentLoadMode(initial.load, loadKg, loadPct, loadRpe, loadRel, loadOpen))
                initial.rpeTarget?.let { rpeTarget.setText(it.toString()) }
                initial.rest?.let { restSec.setText(it.toString()) }
                tempo.setText(initial.tempo.orEmpty())
                notes.setText(initial.notes.orEmpty())
            }
            is SetPrescription.MyoRep -> {
                preloadReps(initial.activationReps, repsModeFixed, repsModeRange, repsModeAmrap, repsModeAmrapMin, repsA, repsB)
                selectRepsMode(currentRepsMode(initial.activationReps, repsModeFixed, repsModeRange, repsModeAmrap, repsModeAmrapMin))
                preloadLoad(initial.load, loadKg, loadPct, loadRpe, loadRel, loadOpen, loadValue)
                selectLoadMode(currentLoadMode(initial.load, loadKg, loadPct, loadRpe, loadRel, loadOpen))
                miniSetTargetReps.setText(initial.miniSetTargetReps.toString())
                miniSetCount.setText(initial.miniSetCount.toString())
                miniSetRestSec.setText(initial.miniSetRestSec.toString())
                initial.rpeStopThreshold?.let { rpeStopThreshold.setText(it.toString()) }
                initial.rest?.let { restSec.setText(it.toString()) }
                tempo.setText(initial.tempo.orEmpty())
                notes.setText(initial.notes.orEmpty())
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            addView(label("Type"))
            addView(typeGroup)
            addView(label("Reps target"))
            addView(repsGroup)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(repsA, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
                addView(repsB, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
            })
            addView(label("Load target"))
            addView(loadGroup)
            addView(loadValue)
            addView(myoFields)
            addView(label("Optional"))
            addView(rpeTarget); addView(restSec); addView(tempo); addView(notes)
        }

        AlertDialog.Builder(context)
            .setTitle(if (initial == null) "New set" else "Edit set")
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val reps = readReps(repsModeFixed, repsModeRange, repsModeAmrap, repsModeAmrapMin, repsA, repsB) ?: return@setPositiveButton
                val load = readLoad(loadKg, loadPct, loadRpe, loadRel, loadOpen, loadValue) ?: return@setPositiveButton
                val result: SetPrescription = if (typeMyoRb.isChecked) {
                    SetPrescription.MyoRep(
                        activationReps = reps,
                        load = load,
                        miniSetTargetReps = miniSetTargetReps.text.toString().toIntOrNull() ?: 5,
                        miniSetCount = miniSetCount.text.toString().toIntOrNull() ?: 3,
                        miniSetRestSec = miniSetRestSec.text.toString().toIntOrNull() ?: 15,
                        rpeStopThreshold = rpeStopThreshold.text.toString().toDoubleOrNull() ?: 10.0,
                        rest = restSec.text.toString().toIntOrNull(),
                        tempo = tempo.text.toString().ifBlank { null },
                        notes = notes.text.toString().ifBlank { null },
                    )
                } else {
                    SetPrescription.Straight(
                        reps = reps,
                        load = load,
                        rpeTarget = rpeTarget.text.toString().toDoubleOrNull(),
                        rest = restSec.text.toString().toIntOrNull(),
                        tempo = tempo.text.toString().ifBlank { null },
                        notes = notes.text.toString().ifBlank { null },
                    )
                }
                onSave(result)
            }
            .show()
    }

    private fun numberInput(hintText: String) = EditText(context).apply {
        hint = hintText
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#94A3B8"))
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    private fun label(s: String) = TextView(context).apply {
        text = s
        textSize = 12f
        setTextColor(Color.parseColor("#CBD5E1"))
        setPadding(0, dp(10), 0, dp(4))
    }

    private fun readReps(
        fixed: RadioButton, range: RadioButton, amrap: RadioButton, amrapMin: RadioButton,
        a: EditText, b: EditText,
    ): RepsTarget? = when {
        amrap.isChecked -> RepsTarget.Amrap
        fixed.isChecked -> a.text.toString().toIntOrNull()?.let { RepsTarget.Fixed(it) }
        range.isChecked -> {
            val min = a.text.toString().toIntOrNull(); val max = b.text.toString().toIntOrNull()
            if (min != null && max != null) RepsTarget.Range(min, max) else null
        }
        amrapMin.isChecked -> a.text.toString().toIntOrNull()?.let { RepsTarget.AmrapMin(it) }
        else -> null
    }

    private fun readLoad(
        kg: RadioButton, pct: RadioButton, rpe: RadioButton, rel: RadioButton, open: RadioButton,
        value: EditText,
    ): LoadTarget? = when {
        open.isChecked -> LoadTarget.Open
        kg.isChecked -> value.text.toString().toDoubleOrNull()?.let { LoadTarget.AbsoluteKg(it) }
        pct.isChecked -> value.text.toString().toDoubleOrNull()?.let { LoadTarget.PctOneRm(it) }
        rpe.isChecked -> value.text.toString().toDoubleOrNull()?.let { LoadTarget.RpeTarget(it) }
        rel.isChecked -> value.text.toString().toDoubleOrNull()?.let { LoadTarget.RelativeToLast(it) }
        else -> null
    }

    private fun preloadReps(r: RepsTarget, fixed: RadioButton, range: RadioButton, amrap: RadioButton, amrapMin: RadioButton, a: EditText, b: EditText) {
        when (r) {
            is RepsTarget.Fixed -> { a.setText(r.reps.toString()) }
            is RepsTarget.Range -> { a.setText(r.min.toString()); b.setText(r.max.toString()) }
            RepsTarget.Amrap -> { /* nothing to preload */ }
            is RepsTarget.AmrapMin -> { a.setText(r.min.toString()) }
        }
    }

    private fun currentRepsMode(r: RepsTarget, fixed: RadioButton, range: RadioButton, amrap: RadioButton, amrapMin: RadioButton): RadioButton =
        when (r) {
            is RepsTarget.Fixed -> fixed
            is RepsTarget.Range -> range
            RepsTarget.Amrap -> amrap
            is RepsTarget.AmrapMin -> amrapMin
        }

    private fun preloadLoad(l: LoadTarget, kg: RadioButton, pct: RadioButton, rpe: RadioButton, rel: RadioButton, open: RadioButton, value: EditText) {
        when (l) {
            is LoadTarget.AbsoluteKg -> value.setText(l.kg.toString())
            is LoadTarget.PctOneRm -> value.setText(l.pct.toString())
            is LoadTarget.RpeTarget -> value.setText(l.rpe.toString())
            is LoadTarget.RelativeToLast -> value.setText(l.deltaKg.toString())
            LoadTarget.Open -> { /* nothing */ }
        }
    }

    private fun currentLoadMode(l: LoadTarget, kg: RadioButton, pct: RadioButton, rpe: RadioButton, rel: RadioButton, open: RadioButton): RadioButton =
        when (l) {
            is LoadTarget.AbsoluteKg -> kg
            is LoadTarget.PctOneRm -> pct
            is LoadTarget.RpeTarget -> rpe
            is LoadTarget.RelativeToLast -> rel
            LoadTarget.Open -> open
        }
}
```

- [ ] **Step 2: Verify compile**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
  ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" \
  ./gradlew.bat :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/dialog/builder/SetPrescriptionEditorDialogController.kt
git commit -m "feat(builder): add SetPrescriptionEditorDialogController for set editing"
```

---

## Task 2: `CardioBlockEditorDialogController` — leaf-level cardio block editor

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/dialog/builder/CardioBlockEditorDialogController.kt`

- [ ] **Step 1: Implement** with exactly this content:

```kotlin
package com.ditrain.app.ui.dialog.builder

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.model.CardioBlock
import com.ditrain.app.model.CardioKind

/**
 * Edits one [CardioBlock]. Picker for activity kind, optional description (required for
 * OTHER), optional target duration and avg BPM, optional notes.
 */
class CardioBlockEditorDialogController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val onSave: (CardioBlock) -> Unit,
) {

    fun show(initial: CardioBlock? = null) {
        val kindGroup = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
        val kindButtons: List<Pair<CardioKind, RadioButton>> = CardioKind.entries.map { kind ->
            kind to RadioButton(context).apply {
                text = kind.name.lowercase().replaceFirstChar { it.uppercase() }
                setTextColor(Color.WHITE)
                isChecked = initial?.activityKind == kind || (initial == null && kind == CardioKind.RUNNING)
                id = View.generateViewId()
            }.also { kindGroup.addView(it) }
        }

        val description = EditText(context).apply {
            hint = "description (required for OTHER)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
            setText(initial?.description.orEmpty())
        }
        val targetDuration = EditText(context).apply {
            hint = "target duration (min)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
            initial?.targetDurationMin?.let { setText(it.toString()) }
        }
        val targetBpm = EditText(context).apply {
            hint = "target avg BPM"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
            initial?.targetAvgBpm?.let { setText(it.toString()) }
        }
        val notes = EditText(context).apply {
            hint = "notes"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
            setText(initial?.notes.orEmpty())
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            addView(label("Activity"))
            addView(kindGroup)
            addView(label("Description"))
            addView(description)
            addView(label("Targets (optional)"))
            addView(targetDuration); addView(targetBpm)
            addView(label("Notes"))
            addView(notes)
        }

        AlertDialog.Builder(context)
            .setTitle(if (initial == null) "New cardio block" else "Edit cardio block")
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val chosenKind = kindButtons.firstOrNull { it.second.isChecked }?.first ?: CardioKind.RUNNING
                val desc = description.text.toString().ifBlank { null }
                if (chosenKind == CardioKind.OTHER && desc.isNullOrBlank()) {
                    AlertDialog.Builder(context)
                        .setTitle("Description required")
                        .setMessage("Cardio kind OTHER requires a description.")
                        .setPositiveButton("OK", null).show()
                    return@setPositiveButton
                }
                onSave(CardioBlock(
                    activityKind = chosenKind,
                    description = desc,
                    targetDurationMin = targetDuration.text.toString().toIntOrNull(),
                    targetAvgBpm = targetBpm.text.toString().toIntOrNull(),
                    notes = notes.text.toString().ifBlank { null },
                ))
            }
            .show()
    }

    private fun label(s: String) = TextView(context).apply {
        text = s
        textSize = 12f
        setTextColor(Color.parseColor("#CBD5E1"))
        setPadding(0, dp(10), 0, dp(4))
    }
}
```

- [ ] **Step 2: Compile**: `./gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/dialog/builder/CardioBlockEditorDialogController.kt
git commit -m "feat(builder): add CardioBlockEditorDialogController"
```

---

## Task 3: `ExerciseBlockEditorDialogController` — builds one strength block

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/dialog/builder/ExerciseBlockEditorDialogController.kt`

Manages the chosen exercise + list of sets for one `ExerciseBlock`. Uses `ExercisePickerDialogController` for exercise selection and `SetPrescriptionEditorDialogController` for editing each set. Adds "Duplicate" so the user can quickly say "4×5" by entering one set then duplicating × 3.

- [ ] **Step 1: Implement**

```kotlin
package com.ditrain.app.ui.dialog.builder

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.model.ExerciseBlock
import com.ditrain.app.model.LoadTarget
import com.ditrain.app.model.RepsTarget
import com.ditrain.app.model.SetPrescription
import com.ditrain.app.storage.ExerciseCatalog
import com.ditrain.app.ui.ViewStyling
import com.ditrain.app.ui.dialog.ExerciseDetailDialogController
import com.ditrain.app.ui.dialog.ExercisePickerDialogController

class ExerciseBlockEditorDialogController(
    private val context: Context,
    private val catalog: ExerciseCatalog,
    private val dp: (Int) -> Int,
    private val onSave: (ExerciseBlock) -> Unit,
) {

    fun show(initial: ExerciseBlock? = null) {
        var exerciseId: String? = initial?.exerciseId
        val sets: MutableList<SetPrescription> = initial?.sets?.toMutableList() ?: mutableListOf()
        var notesValue: String = initial?.notes.orEmpty()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        val exerciseRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val exerciseLabel = TextView(context).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            text = exerciseId?.let { catalog.byId(it)?.name } ?: "(pick an exercise)"
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val pickExerciseBtn = ViewStyling.actionButton(
            context, "Pick…", "#2563EB", compact = true, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        ).apply {
            setOnClickListener {
                val detail = ExerciseDetailDialogController(context, dp)
                ExercisePickerDialogController(
                    context = context, catalog = catalog, dp = dp,
                    onPicked = { ex ->
                        exerciseId = ex.id
                        exerciseLabel.text = ex.name
                    },
                    onDetail = { ex -> detail.show(ex) },
                ).show()
            }
        }
        exerciseRow.addView(exerciseLabel)
        exerciseRow.addView(pickExerciseBtn)

        val setListContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val notesEdit = EditText(context).apply {
            hint = "block notes (optional)"
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#94A3B8"))
            setText(notesValue)
        }

        val addSetBtn = ViewStyling.actionButton(
            context, "Add set", "#7C3AED", compact = true, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        )

        fun renderSets() {
            setListContainer.removeAllViews()
            if (sets.isEmpty()) {
                setListContainer.addView(TextView(context).apply {
                    text = "No sets yet — tap Add set."
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(0, dp(8), 0, dp(8))
                })
                return
            }
            sets.forEachIndexed { idx, s -> setListContainer.addView(setRow(idx, s, sets, ::renderSets)) }
        }

        addSetBtn.setOnClickListener {
            SetPrescriptionEditorDialogController(context, dp) { newSet ->
                sets.add(newSet); renderSets()
            }.show()
        }

        container.addView(label("Exercise"))
        container.addView(exerciseRow)
        container.addView(label("Sets"))
        container.addView(setListContainer)
        container.addView(addSetBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        container.addView(label("Notes (optional)"))
        container.addView(notesEdit)

        renderSets()

        AlertDialog.Builder(context)
            .setTitle(if (initial == null) "New exercise block" else "Edit exercise block")
            .setView(ScrollView(context).apply { addView(container) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val exId = exerciseId
                if (exId == null) {
                    AlertDialog.Builder(context).setTitle("Pick an exercise first")
                        .setPositiveButton("OK", null).show()
                    return@setPositiveButton
                }
                if (sets.isEmpty()) {
                    AlertDialog.Builder(context).setTitle("Add at least one set")
                        .setPositiveButton("OK", null).show()
                    return@setPositiveButton
                }
                onSave(ExerciseBlock(
                    exerciseId = exId,
                    sets = sets.toList(),
                    notes = notesEdit.text.toString().ifBlank { null },
                ))
            }
            .show()
    }

    private fun setRow(idx: Int, set: SetPrescription, sets: MutableList<SetPrescription>, refresh: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(12).toFloat())
            setPadding(dp(10), dp(6), dp(10), dp(6))

            addView(TextView(context).apply {
                text = "Set ${idx + 1}: ${describe(set)}"
                textSize = 13f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(linkBtn("Edit") {
                SetPrescriptionEditorDialogController(context, dp) { updated ->
                    sets[idx] = updated; refresh()
                }.show(initial = set)
            })
            addView(linkBtn("Dup") {
                sets.add(idx + 1, set); refresh()
            })
            addView(linkBtn("Del") {
                sets.removeAt(idx); refresh()
            })

            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) }
        }

    private fun linkBtn(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 13f
        setTextColor(Color.parseColor("#60A5FA"))
        setPadding(dp(8), dp(6), dp(8), dp(6))
        setOnClickListener { onClick() }
    }

    private fun label(s: String) = TextView(context).apply {
        text = s; textSize = 12f
        setTextColor(Color.parseColor("#CBD5E1"))
        setPadding(0, dp(10), 0, dp(4))
    }

    private fun describe(s: SetPrescription): String = when (s) {
        is SetPrescription.Straight -> "${formatReps(s.reps)} @ ${formatLoad(s.load)}" + (s.rpeTarget?.let { " @ RPE $it" } ?: "")
        is SetPrescription.MyoRep -> "myo ${formatReps(s.activationReps)} act + ${s.miniSetCount}×${s.miniSetTargetReps}"
    }

    private fun formatReps(r: RepsTarget): String = when (r) {
        is RepsTarget.Fixed -> "${r.reps}"
        is RepsTarget.Range -> "${r.min}-${r.max}"
        RepsTarget.Amrap -> "AMRAP"
        is RepsTarget.AmrapMin -> "AMRAP≥${r.min}"
    }

    private fun formatLoad(l: LoadTarget): String = when (l) {
        is LoadTarget.AbsoluteKg -> "${l.kg} kg"
        is LoadTarget.PctOneRm -> "${(l.pct * 100).toInt()}%"
        is LoadTarget.RpeTarget -> "RPE ${l.rpe}"
        is LoadTarget.RelativeToLast -> "last+${l.deltaKg}"
        LoadTarget.Open -> "open"
    }
}
```

- [ ] **Step 2: Compile**: `./gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/dialog/builder/ExerciseBlockEditorDialogController.kt
git commit -m "feat(builder): add ExerciseBlockEditorDialogController for strength blocks"
```

---

## Task 4: `SessionEditorDialogController` — builds one session

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/dialog/builder/SessionEditorDialogController.kt`

Edits one `SessionTemplate` — name + a list of strength blocks (via #3) + a list of cardio blocks (via #2). On Save, returns a SessionTemplate.

- [ ] **Step 1: Implement**

```kotlin
package com.ditrain.app.ui.dialog.builder

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.model.CardioBlock
import com.ditrain.app.model.ExerciseBlock
import com.ditrain.app.model.SessionTemplate
import com.ditrain.app.storage.ExerciseCatalog
import com.ditrain.app.ui.ViewStyling
import java.util.UUID

class SessionEditorDialogController(
    private val context: Context,
    private val catalog: ExerciseCatalog,
    private val dp: (Int) -> Int,
    private val onSave: (SessionTemplate) -> Unit,
) {

    fun show(initial: SessionTemplate? = null, suggestedName: String = "Day A") {
        val sessionId = initial?.id ?: UUID.randomUUID().toString().take(8)
        val nameEdit = EditText(context).apply {
            hint = "session name (e.g. Push A)"
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#94A3B8"))
            setText(initial?.name ?: suggestedName)
        }
        val blocks: MutableList<ExerciseBlock> = initial?.blocks?.toMutableList() ?: mutableListOf()
        val cardioBlocks: MutableList<CardioBlock> = initial?.cardioBlocks?.toMutableList() ?: mutableListOf()

        val blockList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val cardioList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        fun renderBlocks() {
            blockList.removeAllViews()
            if (blocks.isEmpty()) {
                blockList.addView(emptyLine("No strength blocks yet."))
            } else {
                blocks.forEachIndexed { idx, b -> blockList.addView(blockRow(idx, b, blocks, ::renderBlocks)) }
            }
        }
        fun renderCardio() {
            cardioList.removeAllViews()
            if (cardioBlocks.isEmpty()) {
                cardioList.addView(emptyLine("No cardio blocks yet."))
            } else {
                cardioBlocks.forEachIndexed { idx, c -> cardioList.addView(cardioRow(idx, c, cardioBlocks, ::renderCardio)) }
            }
        }

        val addStrengthBtn = ViewStyling.actionButton(
            context, "Add strength block", "#7C3AED", compact = true, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        ).apply {
            setOnClickListener {
                ExerciseBlockEditorDialogController(context, catalog, dp) { newBlock ->
                    blocks.add(newBlock); renderBlocks()
                }.show()
            }
        }
        val addCardioBtn = ViewStyling.actionButton(
            context, "Add cardio block", "#0EA5E9", compact = true, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        ).apply {
            setOnClickListener {
                CardioBlockEditorDialogController(context, dp) { newCardio ->
                    cardioBlocks.add(newCardio); renderCardio()
                }.show()
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            addView(label("Session name"))
            addView(nameEdit)
            addView(label("Strength blocks"))
            addView(blockList); addView(addStrengthBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(label("Cardio blocks"))
            addView(cardioList); addView(addCardioBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        }

        renderBlocks(); renderCardio()

        AlertDialog.Builder(context)
            .setTitle(if (initial == null) "New session" else "Edit session")
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = nameEdit.text.toString().trim().ifEmpty { suggestedName }
                if (blocks.isEmpty() && cardioBlocks.isEmpty()) {
                    AlertDialog.Builder(context).setTitle("Add at least one block")
                        .setMessage("A session needs at least one strength or cardio block.")
                        .setPositiveButton("OK", null).show()
                    return@setPositiveButton
                }
                onSave(SessionTemplate(
                    id = sessionId,
                    name = name,
                    blocks = blocks.toList(),
                    cardioBlocks = cardioBlocks.toList(),
                ))
            }
            .show()
    }

    private fun blockRow(idx: Int, block: ExerciseBlock, list: MutableList<ExerciseBlock>, refresh: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(12).toFloat())
            setPadding(dp(10), dp(6), dp(10), dp(6))

            val exName = catalog.byId(block.exerciseId)?.name ?: "(unknown: ${block.exerciseId})"
            addView(TextView(context).apply {
                text = "$exName · ${block.sets.size} sets"
                textSize = 13f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(linkBtn("Edit") {
                ExerciseBlockEditorDialogController(context, catalog, dp) { updated ->
                    list[idx] = updated; refresh()
                }.show(initial = block)
            })
            addView(linkBtn("Del") { list.removeAt(idx); refresh() })

            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) }
        }

    private fun cardioRow(idx: Int, block: CardioBlock, list: MutableList<CardioBlock>, refresh: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(12).toFloat())
            setPadding(dp(10), dp(6), dp(10), dp(6))

            val name = block.description?.takeIf { it.isNotBlank() } ?: block.activityKind.name.lowercase()
            addView(TextView(context).apply {
                text = "Cardio: $name" + (block.targetDurationMin?.let { " · ${it} min" } ?: "")
                textSize = 13f
                setTextColor(Color.parseColor("#FDE68A"))
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(linkBtn("Edit") {
                CardioBlockEditorDialogController(context, dp) { updated ->
                    list[idx] = updated; refresh()
                }.show(initial = block)
            })
            addView(linkBtn("Del") { list.removeAt(idx); refresh() })

            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) }
        }

    private fun emptyLine(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(Color.parseColor("#94A3B8"))
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun linkBtn(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 13f
        setTextColor(Color.parseColor("#60A5FA"))
        setPadding(dp(8), dp(6), dp(8), dp(6))
        setOnClickListener { onClick() }
    }

    private fun label(s: String) = TextView(context).apply {
        text = s; textSize = 12f
        setTextColor(Color.parseColor("#CBD5E1"))
        setPadding(0, dp(10), 0, dp(4))
    }
}
```

- [ ] **Step 2: Compile**: `./gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/dialog/builder/SessionEditorDialogController.kt
git commit -m "feat(builder): add SessionEditorDialogController"
```

---

## Task 5: `WeekEditorDialogController` — builds one week

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/dialog/builder/WeekEditorDialogController.kt`

Edits one `Week` — label + session list. Includes a "Copy sessions from previous week" affordance when `previousWeek` is non-null (skipped for week 1). On Next, fires callback with the resulting Week.

- [ ] **Step 1: Implement**

```kotlin
package com.ditrain.app.ui.dialog.builder

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.model.SessionTemplate
import com.ditrain.app.model.Week
import com.ditrain.app.storage.ExerciseCatalog
import com.ditrain.app.ui.ViewStyling
import java.util.UUID

class WeekEditorDialogController(
    private val context: Context,
    private val catalog: ExerciseCatalog,
    private val dp: (Int) -> Int,
    private val onSave: (Week) -> Unit,
) {

    fun show(
        weekIndex: Int,
        totalWeeks: Int,
        initial: Week? = null,
        previousWeek: Week? = null,
    ) {
        val labelEdit = EditText(context).apply {
            hint = "week label (e.g. Week 1, Heavy, Deload)"
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#94A3B8"))
            setText(initial?.label ?: "Week ${weekIndex + 1}")
        }
        val sessions: MutableList<SessionTemplate> = initial?.sessions?.toMutableList() ?: mutableListOf()
        val sessionList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        fun renderSessions() {
            sessionList.removeAllViews()
            if (sessions.isEmpty()) {
                sessionList.addView(TextView(context).apply {
                    text = "No sessions yet."
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(0, dp(8), 0, dp(8))
                })
            } else {
                sessions.forEachIndexed { idx, s -> sessionList.addView(sessionRow(idx, s, sessions, ::renderSessions)) }
            }
        }

        val addBtn = ViewStyling.actionButton(
            context, "Add session", "#7C3AED", compact = true, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        ).apply {
            setOnClickListener {
                val nextLetter = ('A' + sessions.size).coerceAtMost('Z')
                SessionEditorDialogController(context, catalog, dp) { newSession ->
                    sessions.add(newSession); renderSessions()
                }.show(suggestedName = "Day $nextLetter")
            }
        }

        val copyFromPrev: View? = previousWeek?.takeIf { it.sessions.isNotEmpty() }?.let { prev ->
            ViewStyling.actionButton(
                context, "Copy ${prev.sessions.size} sessions from previous week", "#475569", compact = true, dp = dp,
                roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
            ).apply {
                setOnClickListener {
                    // Generate fresh session ids so they're unique within the routine.
                    sessions.clear()
                    prev.sessions.forEach { s ->
                        sessions.add(s.copy(id = UUID.randomUUID().toString().take(8)))
                    }
                    renderSessions()
                }
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            addView(TextView(context).apply {
                text = "Week ${weekIndex + 1} of $totalWeeks"
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
            })
            addView(label("Week label"))
            addView(labelEdit)
            addView(label("Sessions"))
            addView(sessionList)
            addView(addBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
            if (copyFromPrev != null) {
                addView(copyFromPrev, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
            }
        }

        renderSessions()

        AlertDialog.Builder(context)
            .setTitle("Build week ${weekIndex + 1}")
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (weekIndex + 1 < totalWeeks) "Next ▸" else "Review ▸") { _, _ ->
                if (sessions.isEmpty()) {
                    AlertDialog.Builder(context).setTitle("Add at least one session")
                        .setMessage("Every week needs at least one session.")
                        .setPositiveButton("OK", null).show()
                    return@setPositiveButton
                }
                val finalLabel = labelEdit.text.toString().trim().ifEmpty { "Week ${weekIndex + 1}" }
                onSave(Week(label = finalLabel, sessions = sessions.toList()))
            }
            .show()
    }

    private fun sessionRow(idx: Int, session: SessionTemplate, list: MutableList<SessionTemplate>, refresh: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(12).toFloat())
            setPadding(dp(10), dp(6), dp(10), dp(6))

            val blockSummary = session.blocks.size.let { b ->
                val c = session.cardioBlocks.size
                buildString {
                    if (b > 0) append("$b strength")
                    if (b > 0 && c > 0) append(" · ")
                    if (c > 0) append("$c cardio")
                }
            }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = session.name; textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                })
                addView(TextView(context).apply {
                    text = blockSummary
                    textSize = 12f
                    setTextColor(Color.parseColor("#94A3B8"))
                })
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(linkBtn("Edit") {
                SessionEditorDialogController(context, catalog, dp) { updated ->
                    list[idx] = updated; refresh()
                }.show(initial = session)
            })
            addView(linkBtn("Del") { list.removeAt(idx); refresh() })

            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) }
        }

    private fun linkBtn(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 13f
        setTextColor(Color.parseColor("#60A5FA"))
        setPadding(dp(8), dp(6), dp(8), dp(6))
        setOnClickListener { onClick() }
    }

    private fun label(s: String) = TextView(context).apply {
        text = s; textSize = 12f
        setTextColor(Color.parseColor("#CBD5E1"))
        setPadding(0, dp(10), 0, dp(4))
    }
}
```

- [ ] **Step 2: Compile**: `./gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/dialog/builder/WeekEditorDialogController.kt
git commit -m "feat(builder): add WeekEditorDialogController with copy-from-previous"
```

---

## Task 6: `RoutineMetaDialogController` — step 1 of the wizard

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/dialog/builder/RoutineMetaDialogController.kt`

Edits routine top-level metadata: name, optional description, loopMode (Once/Repeat), week count. On Next, fires callback with a `RoutineMeta` data class that the MainActivity flow uses to seed the week editor.

- [ ] **Step 1: Implement**

```kotlin
package com.ditrain.app.ui.dialog.builder

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.model.LoopMode

class RoutineMetaDialogController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val onNext: (RoutineMeta) -> Unit,
) {

    data class RoutineMeta(
        val name: String,
        val description: String?,
        val loopMode: LoopMode,
        val weekCount: Int,
    )

    fun show() {
        val nameEdit = EditText(context).apply {
            hint = "routine name (e.g. My Hypertrophy Block)"
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#94A3B8"))
        }
        val descEdit = EditText(context).apply {
            hint = "description (optional)"
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#94A3B8"))
        }
        val onceRb = RadioButton(context).apply {
            text = "Runs once (mesocycle)"; setTextColor(Color.WHITE); id = View.generateViewId()
        }
        val repeatRb = RadioButton(context).apply {
            text = "Repeats indefinitely (template)"; setTextColor(Color.WHITE); isChecked = true; id = View.generateViewId()
        }
        val loopGroup = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL; addView(onceRb); addView(repeatRb) }
        val weekCountEdit = EditText(context).apply {
            hint = "number of weeks (1..52)"
            setText("1")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#94A3B8"))
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            addView(label("Name"))
            addView(nameEdit)
            addView(label("Description"))
            addView(descEdit)
            addView(label("Type"))
            addView(loopGroup)
            addView(label("Number of weeks"))
            addView(weekCountEdit)
            addView(TextView(context).apply {
                text = "Tip: Templates loop the same week(s) forever. Mesocycles run once and end."
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, dp(8), 0, 0)
            })
        }

        AlertDialog.Builder(context)
            .setTitle("New routine — step 1 of 2")
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Next ▸") { _, _ ->
                val name = nameEdit.text.toString().trim()
                if (name.isEmpty()) {
                    AlertDialog.Builder(context).setTitle("Name required")
                        .setPositiveButton("OK", null).show()
                    return@setPositiveButton
                }
                val weeks = weekCountEdit.text.toString().toIntOrNull()?.coerceIn(1, 52) ?: 1
                onNext(RoutineMeta(
                    name = name,
                    description = descEdit.text.toString().ifBlank { null },
                    loopMode = if (onceRb.isChecked) LoopMode.ONCE else LoopMode.REPEAT,
                    weekCount = weeks,
                ))
            }
            .show()
    }

    private fun label(s: String) = TextView(context).apply {
        text = s; textSize = 12f
        setTextColor(Color.parseColor("#CBD5E1"))
        setPadding(0, dp(10), 0, dp(4))
    }
}
```

- [ ] **Step 2: Compile**: `./gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/dialog/builder/RoutineMetaDialogController.kt
git commit -m "feat(builder): add RoutineMetaDialogController as wizard step 1"
```

---

## Task 7: Wire the builder flow in `MainActivity` + `MainMenu`

**Files:**
- Modify: `app/src/main/java/com/ditrain/app/ui/dialog/MainMenuDialogController.kt` (add a new "Create routine" row)
- Modify: `app/src/main/java/com/ditrain/app/MainActivity.kt` (add `openBuilder()` flow + wire menu row to it)

- [ ] **Step 1: Modify `MainMenuDialogController.kt`**

Replace the file's content with exactly:

```kotlin
package com.ditrain.app.ui.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.ui.ViewStyling

/**
 * The "⋮" overflow menu shown from Home. Each row is a labeled action that opens
 * a sub-dialog. Plan 6 adds Settings / Backup / Glossary / About; Plan 2 ships
 * Routines + Import routine + Browse exercises; Plan 2.5 adds Create routine.
 */
class MainMenuDialogController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val onOpenRoutines: () -> Unit,
    private val onImportRoutine: () -> Unit,
    private val onBrowseExercises: () -> Unit,
    private val onCreateRoutine: () -> Unit,
) {
    fun show() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Menu")
            .setView(ScrollView(context).apply { addView(container) })
            .setNegativeButton("Close", null)
            .create()

        container.addView(row("Create routine", "Build a routine step-by-step through dialogs") {
            dialog.dismiss(); onCreateRoutine()
        })
        container.addView(row("Routines", "Browse, activate, or delete saved routines") {
            dialog.dismiss(); onOpenRoutines()
        })
        container.addView(row("Import routine", "Paste JSON, pick a file, or load a bundled example") {
            dialog.dismiss(); onImportRoutine()
        })
        container.addView(row("Browse exercises", "See the bundled exercise catalog and their IDs") {
            dialog.dismiss(); onBrowseExercises()
        })

        dialog.show()
    }

    private fun row(title: String, subtitle: String, onClick: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(20).toFloat())
            setPadding(dp(14), dp(14), dp(14), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            addView(TextView(context).apply {
                text = title
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = subtitle
                textSize = 13f
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, dp(4), 0, 0)
            })

            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        }
}
```

- [ ] **Step 2: Modify `MainActivity.kt`** — add the new menu wiring and the `openBuilder()` flow.

(a) Update the `openMainMenu()` function to pass `onCreateRoutine`:

In the current `openMainMenu()` body, change the `MainMenuDialogController(...)` call to include the new parameter:

```kotlin
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
```

(b) Add the new `openBuilder()` method anywhere in the class (suggested location: after `openExerciseBrowser()`):

```kotlin
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
        ) { week ->
            val updatedWeeks = draft.weeks.toMutableList().also { it[weekIndex] = week }
            val updated = draft.copy(weeks = updatedWeeks)
            if (weekIndex + 1 < updated.weeks.size) editWeek(updated, weekIndex + 1)
            else builderReview(updated)
        }.show(
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
        )
    }

    private fun saveBuiltRoutine(routine: com.ditrain.app.model.Routine, activate: Boolean) {
        lifecycleScope.launch {
            routineRepo.save(routine)
            if (activate) startActivation(routine)
            else toast("Saved \"${routine.name}\".")
        }
    }
```

- [ ] **Step 3: Build the APK**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
  ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" \
  ./gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/dialog/MainMenuDialogController.kt \
        app/src/main/java/com/ditrain/app/MainActivity.kt
git commit -m "feat(app): wire builder flow from MainMenu through wizard to save"
```

---

## Task 8: Full verification + milestone tag

- [ ] **Step 1: Run all unit tests**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
  ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" \
  ./gradlew.bat :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, **86 tests passing** (no new tests added in Plan 2.5).

- [ ] **Step 2: Build the bundle**

```bash
./gradlew.bat bundleDebug
```

- [ ] **Step 3: Manual smoke test on a device**

Walk through:
- Tap ⋮ → MainMenu → "Create routine" → metadata dialog appears.
- Enter "Manual Test Routine", pick Repeats, set 1 week, tap Next.
- Week 1 editor appears. Tap "Add session" → SessionEditor.
- In SessionEditor: name "Day A". Tap "Add strength block".
- In ExerciseBlockEditor: tap "Pick…" → pick `barbell-back-squat`. Tap "Add set". Set Straight / Fixed 5 / kg 80 / save. Tap "Dup" twice → 3 identical sets. Save the block.
- Back in SessionEditor: tap "Add cardio block" → pick WALKING, 10 min. Save.
- Save the session.
- Back in WeekEditor: tap Review.
- Preview dialog shows the routine. Tap "View JSON ▸" → see the generated JSON.
- Tap "Save & activate" → schedule dialog → pick Mon → Activate.
- Home shows the new routine active.

If anything fails on device, fix before tagging.

- [ ] **Step 4: Tag the milestone**

```bash
git tag -a v0.3.0-builder -m "Plan 2.5: Routine builder wizard milestone complete"
```

---

## Plan-2.5 self-review

**Spec coverage:**
- §4.2 Routine schema — every field reachable from the wizard: name/description/loopMode/weeks (RoutineMeta), week.label/sessions (WeekEditor), session.id/name/blocks/cardioBlocks (SessionEditor), ExerciseBlock.exerciseId/sets/notes (ExerciseBlockEditor), all SetPrescription variants and load/reps targets (SetPrescriptionEditor), all CardioBlock fields (CardioBlockEditor).
- §6.1 Import flow — unchanged; the JSON import path still works in parallel.

**Out of scope (correctly deferred):**
- Editing existing routines.
- Re-laying-out an active routine's schedule after a builder edit.
- Saving partial drafts (cancel = abandon).
- Drag-reorder of sessions/blocks/sets (only delete + re-add for v1).
- "Browse routines I've built" — covered by the existing Routines dialog.

**Placeholder scan:** none.

**Type consistency:** every controller's `onSave: (T) -> Unit` callback signature matches what the caller passes (`SetPrescription`, `CardioBlock`, `ExerciseBlock`, `SessionTemplate`, `Week`, `RoutineMeta`).

**File-size sanity:**
- SetPrescriptionEditor ~260 lines (the chunkiest, given 9 radio buttons across reps + load + type)
- ExerciseBlockEditor ~165 lines
- SessionEditor ~150 lines
- WeekEditor ~130 lines
- RoutineMetaEditor ~80 lines
- CardioBlockEditor ~90 lines
- MainActivity grows by ~50 lines for the builder flow

All well within the focused-file standard.
