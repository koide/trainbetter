package com.ditrain.app.ui.session

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ditrain.app.model.ExecutedExercise
import com.ditrain.app.model.ExerciseBlock
import com.ditrain.app.model.LoggedSet
import com.ditrain.app.model.MiniSet
import com.ditrain.app.model.SetPrescription
import com.ditrain.app.model.WeightUnit
import com.ditrain.app.model.kgToLb
import com.ditrain.app.storage.ExerciseCatalog
import com.ditrain.app.ui.ViewStyling
import com.ditrain.app.util.InstantIso
import kotlin.math.roundToInt

/**
 * Renders one strength block: exercise name, prescription summary, list of
 * already-logged sets (compact), then the active SetEntryView, then the
 * "Add set" / "Skip exercise" actions.
 *
 * The active row is created for the next prescribed set (1-indexed). When the
 * prescribed set count has been logged, the active row becomes an "extra set"
 * row driven by the last prescription as a template; the user can stop by
 * tapping the next block.
 */
class StrengthBlockView(
    context: Context,
    private val catalog: ExerciseCatalog,
    private val dp: (Int) -> Int,
    private val unit: WeightUnit,
    private val nowIso: () -> InstantIso,
    private val resolveLastTopWeightKg: (exerciseId: String) -> Double?,
    private val resolveLatestE1rmKg: (exerciseId: String) -> Double?,
    private val onLogged: (LoggedSet) -> Unit,
    private val onSkip: () -> Unit,
) : ScrollView(context) {

    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
    }

    init {
        addView(column, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    fun bind(block: ExerciseBlock, executed: ExecutedExercise) {
        column.removeAllViews()

        val exercise = catalog.byId(block.exerciseId)
        val displayName = exercise?.name ?: "(unknown: ${block.exerciseId})"

        column.addView(TextView(context).apply {
            text = displayName
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })

        column.addView(TextView(context).apply {
            text = summarizePrescription(block)
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(4), 0, dp(4))
        })
        block.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            column.addView(TextView(context).apply {
                text = "notes: $notes"
                textSize = 13f
                setTextColor(Color.parseColor("#FDE68A"))
                setPadding(0, 0, 0, dp(8))
            })
        }

        if (executed.skipped) {
            column.addView(TextView(context).apply {
                text = "(skipped)"
                textSize = 16f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, dp(16), 0, dp(8))
            })
            return
        }

        executed.sets.forEachIndexed { idx, s ->
            column.addView(loggedSetRow(idx + 1, s))
        }

        val activeIdx = executed.sets.size
        val prescription = block.sets.getOrNull(activeIdx) ?: block.sets.lastOrNull()
        if (prescription != null) {
            val resolved = SessionPrescription.resolve(
                prescription = prescription,
                latestE1rmKg = resolveLatestE1rmKg(block.exerciseId),
                lastTopWeightKg = resolveLastTopWeightKg(block.exerciseId),
            )
            val entry = SetEntryView(context, dp, unit, nowIso)
            when (prescription) {
                is SetPrescription.Straight -> entry.renderStraight(
                    setNumber = activeIdx + 1,
                    prescription = prescription,
                    resolved = resolved,
                    onLog = { logged -> onLogged(logged) },
                )
                is SetPrescription.MyoRep -> renderMyoRepFlow(entry, prescription, resolved)
            }
            column.addView(entry, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
        }

        column.addView(skipButton(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
    }

    private fun renderMyoRepFlow(
        entry: SetEntryView,
        prescription: SetPrescription.MyoRep,
        resolved: SessionPrescription.Resolved,
    ) {
        var activationWeightKg = 0.0
        var activationReps = 0
        var activationRpe: Double? = null
        val miniSets = mutableListOf<MiniSet>()
        var finalized = false

        fun finalize() {
            // Guard: End-cluster tap can race with the last mini-set's Log click.
            if (finalized) return
            finalized = true
            onLogged(LoggedSet.MyoRep(
                weightKg = activationWeightKg,
                activationReps = activationReps,
                activationRpe = activationRpe,
                miniSets = miniSets.toList(),
                performedAt = nowIso(),
            ))
        }

        fun renderNextMini() {
            val nextIdx = miniSets.size + 1
            if (nextIdx > prescription.miniSetCount) {
                finalize()
                return
            }
            entry.renderMyoRepMiniSet(
                miniIndex = nextIdx,
                targetReps = prescription.miniSetTargetReps,
                onLogged = { mini ->
                    miniSets.add(mini)
                    if (miniSets.size >= prescription.miniSetCount) finalize() else renderNextMini()
                },
                onEndCluster = { finalize() },
            )
        }

        entry.renderMyoRepActivation(
            prescription = prescription,
            resolved = resolved,
            onActivationLogged = { wKg, r, rpe ->
                activationWeightKg = wKg
                activationReps = r
                activationRpe = rpe
                renderNextMini()
            },
        )
    }

    private fun loggedSetRow(setNumber: Int, s: LoggedSet): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        background = ViewStyling.roundedBackground("#0F172A", "#1F2937", dp(1), dp(14).toFloat())
        setPadding(dp(12), dp(8), dp(12), dp(8))
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
        layoutParams = lp

        val summary = when (s) {
            is LoggedSet.Straight -> "Set $setNumber · ${formatKg(s.weightKg)} × ${s.reps}" +
                    (s.rpe?.let { " @ ${trimZero(it)}" } ?: "")
            is LoggedSet.MyoRep -> {
                val miniSum = s.miniSets.joinToString("+") { it.reps.toString() }
                "Set $setNumber · myo ${formatKg(s.weightKg)} · act ${s.activationReps}" +
                    (if (miniSum.isNotEmpty()) " · mini $miniSum" else "")
            }
        }
        addView(TextView(context).apply {
            text = summary
            textSize = 14f
            setTextColor(Color.WHITE)
        })
    }

    private fun skipButton(): TextView = TextView(context).apply {
        text = "⤵ Skip exercise"
        textSize = 14f
        setTextColor(Color.parseColor("#FCA5A5"))
        setPadding(dp(10), dp(10), dp(10), dp(10))
        background = ViewStyling.roundedBackground("#111827", "#7F1D1D", dp(1), dp(999).toFloat())
        isClickable = true
        setOnClickListener { onSkip() }
    }

    private fun summarizePrescription(block: ExerciseBlock): String {
        val count = block.sets.size
        val first = block.sets.firstOrNull()
        return when (first) {
            null -> "no sets prescribed"
            is SetPrescription.Straight -> "${count}×${SessionPrescription.resolve(first, null, null).repsHint}" +
                (first.rest?.let { " · ${it}s rest" } ?: "")
            is SetPrescription.MyoRep -> "$count cluster(s) · myo-rep"
        }
    }

    private fun formatKg(kg: Double): String = when (unit) {
        WeightUnit.KG -> if (kg % 1.0 == 0.0) "${kg.toInt()} kg" else "$kg kg"
        WeightUnit.LB -> "${kg.kgToLb().roundToInt()} lb"
    }

    private fun trimZero(d: Double): String =
        if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
}
