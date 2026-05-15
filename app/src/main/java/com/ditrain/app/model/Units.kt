package com.ditrain.app.model

enum class WeightUnit(val label: String) {
    KG("kg"),
    LB("lb"),
    ;

    /**
     * Converts an internal kg value to a display string in this unit, rounded for input UX:
     *  - KG → nearest 0.5 kg
     *  - LB → nearest 1 lb
     *
     * Internal math elsewhere never goes through this rounding.
     */
    fun formatForInput(weightKg: Double): String = when (this) {
        KG -> {
            val halves = Math.round(weightKg * 2.0).toDouble() / 2.0
            if (halves % 1.0 == 0.0) halves.toInt().toString() else halves.toString()
        }
        LB -> {
            val pounds = Math.round(weightKg.kgToLb()).toDouble()
            pounds.toInt().toString()
        }
    }
}

private const val KG_PER_LB = 0.45359237
private const val LB_PER_KG = 1.0 / KG_PER_LB    // 2.20462262184…

fun Double.kgToLb(): Double = this * LB_PER_KG
fun Double.lbToKg(): Double = this * KG_PER_LB
