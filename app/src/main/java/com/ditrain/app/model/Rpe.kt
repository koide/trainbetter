package com.ditrain.app.model

/** UI preference for which effort scale to expose: RPE on a 1–10 scale, or RIR on a 0–5 scale. */
enum class EffortMode { RPE, RIR }

object Effort {
    /** Returns RIR (0..5) for an RPE in [1.0, 10.0]; returns null if RPE is out of bounds. */
    fun rpeToRir(rpe: Double): Int? {
        if (rpe < 1.0 || rpe > 10.0) return null
        val rir = (10.0 - rpe).toInt()        // truncates 8.5→1.5→1, 9.5→0.5→0
        return rir.coerceIn(0, 9)
    }

    /** Inverse of [rpeToRir] for integer RIR values: RPE = 10 − RIR. */
    fun rirToRpe(rir: Int): Double = (10 - rir).toDouble()
}
