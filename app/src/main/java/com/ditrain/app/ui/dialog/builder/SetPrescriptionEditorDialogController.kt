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
