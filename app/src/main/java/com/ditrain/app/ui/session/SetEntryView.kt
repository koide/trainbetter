package com.ditrain.app.ui.session

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.ditrain.app.model.LoggedSet
import com.ditrain.app.model.MiniSet
import com.ditrain.app.model.SetPrescription
import com.ditrain.app.model.WeightUnit
import com.ditrain.app.model.lbToKg
import com.ditrain.app.ui.ViewStyling
import com.ditrain.app.util.InstantIso

/**
 * Active-set input row. Two layouts depending on the prescription type:
 *  - Straight: weight | reps | rpe | (note) | Log
 *  - Myo-rep activation: same as straight; on Log, the row transitions to a sequence
 *    of mini-set rows (reps | rpe | Log mini · End cluster).
 *
 * The view stays fully reusable: the parent rebuilds it once per "next set to log,"
 * passing a fresh prescription resolution.
 *
 * Internal weights are kg. When the display unit is LB, the field shows lb and the
 * onLog conversion happens here.
 */
class SetEntryView(
    context: Context,
    private val dp: (Int) -> Int,
    private val unit: WeightUnit,
    private val nowIso: () -> InstantIso,
) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        background = ViewStyling.roundedBackground("#111827", "#3B82F6", dp(2), dp(20).toFloat())
        setPadding(dp(14), dp(14), dp(14), dp(14))
        layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
    }

    fun renderStraight(
        setNumber: Int,
        prescription: SetPrescription.Straight,
        resolved: SessionPrescription.Resolved,
        onLog: (LoggedSet.Straight) -> Unit,
    ) {
        removeAllViews()

        addView(headerText("Set #$setNumber: " + resolved.repsHint +
                (resolved.weightHint?.let { " · $it" } ?: "")))

        val weightInput = numberInput(hint = unit.label).apply {
            resolved.suggestedWeightKg?.let { kg ->
                setText(unit.formatForInput(kg))
            }
        }
        val repsInput = numberInput(hint = "reps").apply {
            // Pre-fill reps when prescription is a single fixed number
            if (resolved.repsHint.toIntOrNull() != null) setText(resolved.repsHint)
        }
        val rpeInput = numberInput(hint = "RPE").apply {
            prescription.rpeTarget?.let { setText(trimZero(it)) }
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val noteInput = EditText(context).apply {
            hint = "note (optional)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
        }

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(weightInput, LayoutParams(0, WRAP_CONTENT, 1.3f).apply { marginEnd = dp(4) })
            addView(repsInput, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
            addView(rpeInput, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        }
        addView(row)
        addView(noteInput, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        val logBtn = ViewStyling.actionButton(
            context, "Log set", "#2563EB", compact = false, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        )
        logBtn.setOnClickListener {
            val parsedKg = parseWeightToKg(weightInput.text.toString()) ?: return@setOnClickListener toast("Weight required")
            val parsedReps = repsInput.text.toString().toIntOrNull() ?: return@setOnClickListener toast("Reps required")
            if (parsedReps <= 0) return@setOnClickListener toast("Reps must be positive")
            val rpe = rpeInput.text.toString().toDoubleOrNull()
            onLog(LoggedSet.Straight(
                weightKg = parsedKg,
                reps = parsedReps,
                performedAt = nowIso(),
                rpe = rpe,
                notes = noteInput.text.toString().ifBlank { null },
            ))
        }
        addView(logBtn, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
    }

    /**
     * Renders the myo-rep activation row (first stage). When activation is logged,
     * call [renderMyoRepMiniSet] with each subsequent mini-set, then [renderMyoRepClosing]
     * to attach the accumulated mini-sets onto a single [LoggedSet.MyoRep].
     */
    fun renderMyoRepActivation(
        prescription: SetPrescription.MyoRep,
        resolved: SessionPrescription.Resolved,
        onActivationLogged: (weightKg: Double, activationReps: Int, activationRpe: Double?) -> Unit,
    ) {
        removeAllViews()

        addView(headerText("Myo-rep activation: " + resolved.repsHint +
                (resolved.weightHint?.let { " · $it" } ?: "")))

        val weightInput = numberInput(hint = unit.label).apply {
            resolved.suggestedWeightKg?.let { setText(unit.formatForInput(it)) }
        }
        val repsInput = numberInput(hint = "reps").apply {
            if (resolved.repsHint.toIntOrNull() != null) setText(resolved.repsHint)
        }
        val rpeInput = numberInput(hint = "RPE")

        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(weightInput, LayoutParams(0, WRAP_CONTENT, 1.3f).apply { marginEnd = dp(4) })
            addView(repsInput, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
            addView(rpeInput, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        })

        val logBtn = ViewStyling.actionButton(
            context, "Log activation", "#2563EB", compact = false, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        )
        logBtn.setOnClickListener {
            val parsedKg = parseWeightToKg(weightInput.text.toString()) ?: return@setOnClickListener toast("Weight required")
            val parsedReps = repsInput.text.toString().toIntOrNull() ?: return@setOnClickListener toast("Reps required")
            if (parsedReps <= 0) return@setOnClickListener toast("Reps must be positive")
            val rpe = rpeInput.text.toString().toDoubleOrNull()
            onActivationLogged(parsedKg, parsedReps, rpe)
        }
        addView(logBtn, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
    }

    fun renderMyoRepMiniSet(
        miniIndex: Int,
        targetReps: Int,
        onLogged: (MiniSet) -> Unit,
        onEndCluster: () -> Unit,
    ) {
        removeAllViews()

        addView(headerText("Mini-set #$miniIndex (target $targetReps reps)"))

        val repsInput = numberInput(hint = "reps").apply { setText(targetReps.toString()) }
        val rpeInput = numberInput(hint = "RPE")

        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(repsInput, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
            addView(rpeInput, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        })

        val logBtn = ViewStyling.actionButton(
            context, "Log mini", "#2563EB", compact = false, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        )
        logBtn.setOnClickListener {
            val parsedReps = repsInput.text.toString().toIntOrNull() ?: return@setOnClickListener toast("Reps required")
            if (parsedReps <= 0) return@setOnClickListener toast("Reps must be positive")
            onLogged(MiniSet(reps = parsedReps, rpe = rpeInput.text.toString().toDoubleOrNull()))
        }
        val endBtn = ViewStyling.actionButton(
            context, "End cluster", "#475569", compact = false, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        )
        endBtn.setOnClickListener { onEndCluster() }

        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(logBtn, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
            addView(endBtn, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        }, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
    }

    private fun headerText(text: String) = TextView(context).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.parseColor("#CBD5E1"))
        setPadding(0, 0, 0, dp(8))
    }

    private fun numberInput(hint: String): EditText = EditText(context).apply {
        this.hint = hint
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#94A3B8"))
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    private fun parseWeightToKg(text: String): Double? {
        val d = text.toDoubleOrNull() ?: return null
        if (d <= 0.0) return null
        return when (unit) {
            WeightUnit.KG -> d
            WeightUnit.LB -> d.lbToKg()
        }
    }

    private fun trimZero(d: Double): String =
        if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

    private fun toast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
