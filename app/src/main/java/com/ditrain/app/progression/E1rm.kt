package com.ditrain.app.progression

/**
 * Estimated 1-rep max (e1RM) from a sub-maximal set.
 *
 * Primary formula: Epley with RPE adjustment when RPE is in [1.0, 10.0]:
 *   weightKg * (1 + reps / 30) * (1 + 0.0333 * (10 - rpe))
 *
 * Fallback: plain Epley when RPE is null or out of range:
 *   weightKg * (1 + reps / 30)
 *
 * To signal "no RPE recorded" pass `null` rather than `0.0` — RPE values outside
 * [1.0, 10.0], including `0.0`, are treated as absent and fall back to plain Epley.
 *
 * Returns null if [weightKg] <= 0 or [reps] < 1 (no estimate is meaningful).
 */
object E1rm {

    fun estimate(weightKg: Double, reps: Int, rpe: Double?): Double? {
        if (weightKg <= 0.0 || reps < 1) return null
        val plain = weightKg * (1.0 + reps.toDouble() / 30.0)
        val rpeAdj = if (rpe != null && rpe in 1.0..10.0) {
            1.0 + 0.0333 * (10.0 - rpe)
        } else 1.0
        return plain * rpeAdj
    }
}
