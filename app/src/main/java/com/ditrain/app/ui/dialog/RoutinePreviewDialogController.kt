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
import com.ditrain.app.model.CardioBlock
import com.ditrain.app.model.CardioKind
import com.ditrain.app.model.ExerciseBlock
import com.ditrain.app.model.LoadTarget
import com.ditrain.app.model.LoopMode
import com.ditrain.app.model.RepsTarget
import com.ditrain.app.model.Routine
import com.ditrain.app.model.SetPrescription
import com.ditrain.app.storage.ExerciseCatalog
import com.ditrain.app.ui.ViewStyling

/**
 * Read-only structural view of a routine. Used by the import flow (where buttons are
 * "Save"/"Save & activate") and by the routine list (where buttons are "Activate"/"Close").
 *
 * Renders week → session → exercise block → set prescription rows.
 */
class RoutinePreviewDialogController(
    private val context: Context,
    private val catalog: ExerciseCatalog,
    private val dp: (Int) -> Int,
) {

    enum class Mode { IMPORT, VIEW }

    fun show(
        routine: Routine,
        mode: Mode,
        onPrimary: () -> Unit,
        onSecondary: () -> Unit,
        primaryLabel: String,
        secondaryLabel: String,
    ) {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))

            addView(TextView(context).apply {
                text = routine.name
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            routine.description?.let {
                addView(TextView(context).apply {
                    text = it
                    textSize = 13f
                    setTextColor(Color.parseColor("#CBD5E1"))
                    setPadding(0, dp(4), 0, dp(4))
                })
            }
            addView(metaLine("Type", when (routine.loopMode) {
                LoopMode.ONCE -> "Mesocycle (${routine.weeks.size} weeks, runs once)"
                LoopMode.REPEAT -> "Template (${routine.weeks.size} week${if (routine.weeks.size == 1) "" else "s"}, repeats)"
            }))
            routine.author?.let { addView(metaLine("Author", it)) }
            routine.defaultWeeklyPattern?.let { pattern ->
                addView(metaLine("Default weekdays", pattern.joinToString(", ") { weekdayName(it) }))
            }

            routine.weeks.forEachIndexed { wIdx, week ->
                addView(sectionHeader("Week ${wIdx + 1}: ${week.label}"))
                week.sessions.forEach { session ->
                    addView(sessionCard(session.name, session.blocks, session.cardioBlocks))
                }
            }
        }

        AlertDialog.Builder(context)
            .setTitle(if (mode == Mode.IMPORT) "Preview import" else "Routine")
            .setView(ScrollView(context).apply { addView(content) })
            .setNeutralButton(secondaryLabel) { _, _ -> onSecondary() }
            .setPositiveButton(primaryLabel) { _, _ -> onPrimary() }
            .show()
    }

    private fun metaLine(label: String, value: String) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(4), 0, 0)
        addView(TextView(context).apply {
            text = "$label:"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(dp(120), WRAP_CONTENT)
        })
        addView(TextView(context).apply {
            text = value
            textSize = 13f
            setTextColor(Color.WHITE)
        })
    }

    private fun sectionHeader(text: String) = TextView(context).apply {
        this.text = text
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#93C5FD"))
        setPadding(0, dp(14), 0, dp(6))
    }

    private fun sessionCard(
        sessionName: String,
        blocks: List<ExerciseBlock>,
        cardioBlocks: List<CardioBlock>,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(16).toFloat())
        setPadding(dp(12), dp(10), dp(12), dp(10))

        addView(TextView(context).apply {
            text = sessionName
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })

        blocks.forEach { b ->
            val exercise = catalog.byId(b.exerciseId)
            val exName = exercise?.name ?: "(unknown: ${b.exerciseId})"
            addView(TextView(context).apply {
                text = "$exName  —  ${formatSets(b.sets)}"
                textSize = 13f
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, dp(4), 0, 0)
            })
        }
        cardioBlocks.forEach { c ->
            val kindLabel = if (c.activityKind == CardioKind.OTHER && !c.description.isNullOrBlank())
                "Cardio: ${c.description}" else "Cardio: ${c.activityKind.name.lowercase()}"
            val mins = c.targetDurationMin?.let { " · ${it} min" } ?: ""
            val bpm = c.targetAvgBpm?.let { " · target ${it} bpm" } ?: ""
            addView(TextView(context).apply {
                text = "$kindLabel$mins$bpm"
                textSize = 13f
                setTextColor(Color.parseColor("#FDE68A"))
                setPadding(0, dp(4), 0, 0)
            })
        }

        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        }
    }

    private fun formatSets(sets: List<SetPrescription>): String {
        val first = sets.firstOrNull() ?: return "no sets"
        val sameAcross = sets.all { it::class == first::class && it == first }
        return if (sameAcross) "${sets.size} × ${formatOne(first)}"
        else sets.joinToString("; ") { formatOne(it) }
    }

    private fun formatOne(s: SetPrescription): String = when (s) {
        is SetPrescription.Straight -> "${formatReps(s.reps)} @ ${formatLoad(s.load)}" +
                (s.rpeTarget?.let { " @ RPE $it" } ?: "")
        is SetPrescription.MyoRep -> "myo: ${formatReps(s.activationReps)} act + ${s.miniSetCount}×${s.miniSetTargetReps}"
    }

    private fun formatReps(r: RepsTarget): String = when (r) {
        is RepsTarget.Fixed -> "${r.reps} reps"
        is RepsTarget.Range -> "${r.min}-${r.max} reps"
        RepsTarget.Amrap -> "AMRAP"
        is RepsTarget.AmrapMin -> "AMRAP ≥${r.min}"
    }

    private fun formatLoad(l: LoadTarget): String = when (l) {
        is LoadTarget.AbsoluteKg -> "${l.kg} kg"
        is LoadTarget.PctOneRm -> "${(l.pct * 100).toInt()}% 1RM"
        is LoadTarget.RpeTarget -> "RPE ${l.rpe}"
        is LoadTarget.RelativeToLast -> "last + ${l.deltaKg} kg"
        LoadTarget.Open -> "open load"
    }

    private fun weekdayName(idx: Int): String = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[idx]
}
