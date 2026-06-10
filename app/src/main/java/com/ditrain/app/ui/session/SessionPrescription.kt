package com.ditrain.app.ui.session

import com.ditrain.app.model.LoadTarget
import com.ditrain.app.model.RepsTarget
import com.ditrain.app.model.SetPrescription

/**
 * Pure resolver: turns a [SetPrescription] + prior history into an initial suggestion
 * for the active set row's weight and reps fields, plus textual hints to show when
 * a value cannot be computed.
 *
 * Internal units are kg. UI is responsible for converting for display.
 */
object SessionPrescription {

    data class Resolved(
        val suggestedWeightKg: Double?,   // null = leave field blank
        val weightHint: String?,          // shown next to the field when there is no weight
        val repsHint: String,             // always populated; shown next to reps field
    )

    fun resolve(
        prescription: SetPrescription,
        latestE1rmKg: Double?,
        lastTopWeightKg: Double?,
    ): Resolved {
        val repsTarget = when (prescription) {
            is SetPrescription.Straight -> prescription.reps
            is SetPrescription.MyoRep -> prescription.activationReps
        }
        // Both subtypes carry a .load field but it isn't lifted onto the sealed interface.
        val load = when (prescription) {
            is SetPrescription.Straight -> prescription.load
            is SetPrescription.MyoRep -> prescription.load
        }

        val (weightKg, weightHint) = when (load) {
            is LoadTarget.AbsoluteKg -> load.kg to null
            is LoadTarget.PctOneRm -> {
                if (latestE1rmKg != null) (latestE1rmKg * load.pct) to null
                else null to "${(load.pct * 100).toInt()}% (no prior e1RM yet)"
            }
            is LoadTarget.RpeTarget -> null to "target @ RPE ${trimZero(load.rpe)}"
            is LoadTarget.RelativeToLast -> {
                if (lastTopWeightKg != null) (lastTopWeightKg + load.deltaKg) to null
                else null to "${formatDelta(load.deltaKg)} vs last (no prior)"
            }
            LoadTarget.Open -> null to null
        }

        val repsHint = when (repsTarget) {
            is RepsTarget.Fixed -> repsTarget.reps.toString()
            is RepsTarget.Range -> "${repsTarget.min}-${repsTarget.max}"
            RepsTarget.Amrap -> "AMRAP"
            is RepsTarget.AmrapMin -> "AMRAP, min ${repsTarget.min}"
        }

        return Resolved(weightKg, weightHint, repsHint)
    }

    private fun trimZero(d: Double): String =
        if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

    // Keeps the trailing ".0" so the unit reads naturally: "+5.0 kg", not "+5 kg".
    private fun formatDelta(d: Double): String {
        val sign = if (d >= 0) "+" else ""
        return "$sign$d kg"
    }
}
