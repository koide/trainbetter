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

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (initial == null) "New exercise block" else "Edit exercise block")
            .setView(ScrollView(context).apply { addView(container) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val exId = exerciseId
                if (exId == null) {
                    AlertDialog.Builder(context).setTitle("Pick an exercise first")
                        .setPositiveButton("OK", null).show()
                    return@setOnClickListener
                }
                if (sets.isEmpty()) {
                    AlertDialog.Builder(context).setTitle("Add at least one set")
                        .setPositiveButton("OK", null).show()
                    return@setOnClickListener
                }
                onSave(ExerciseBlock(
                    exerciseId = exId,
                    sets = sets.toList(),
                    notes = notesEdit.text.toString().ifBlank { null },
                ))
                dialog.dismiss()
            }
        }
        dialog.show()
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
