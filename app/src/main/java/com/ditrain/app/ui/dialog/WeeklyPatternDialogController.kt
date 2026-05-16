package com.ditrain.app.ui.dialog

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.ui.ViewStyling
import java.time.LocalDate

/**
 * Asks the user which weekdays they train (Mon=0..Sun=6) and a start date. Returns
 * the choices via [onConfirm]. Cancel just dismisses without firing the callback.
 */
class WeeklyPatternDialogController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val onConfirm: (weeklyPattern: List<Int>, startDate: LocalDate) -> Unit,
) {

    fun show(prefilledPattern: List<Int>? = null, prefilledStart: LocalDate = LocalDate.now()) {
        val weekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val checkboxes = weekdayNames.mapIndexed { idx, name ->
            CheckBox(context).apply {
                text = name
                isChecked = prefilledPattern?.contains(idx) == true
                setTextColor(Color.WHITE)
            }
        }

        var startDate = prefilledStart
        val startDateView = TextView(context).apply {
            text = startDate.toString()
            textSize = 16f
            setTextColor(Color.parseColor("#93C5FD"))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = ViewStyling.roundedBackground("#1F2937", "#334155", dp(1), dp(8).toFloat())
            setOnClickListener {
                DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        startDate = LocalDate.of(y, m + 1, d)
                        text = startDate.toString()
                    },
                    startDate.year, startDate.monthValue - 1, startDate.dayOfMonth,
                ).show()
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            addView(TextView(context).apply {
                text = "Which days will you train?"
                textSize = 14f
                setTextColor(Color.parseColor("#CBD5E1"))
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                setPadding(0, dp(6), 0, dp(10))
                checkboxes.forEach { cb ->
                    addView(cb, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(4) })
                }
            })
            addView(TextView(context).apply {
                text = "Start on:"
                textSize = 14f
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, dp(4), 0, dp(4))
            })
            addView(startDateView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        AlertDialog.Builder(context)
            .setTitle("Schedule")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Activate") { _, _ ->
                val pattern = checkboxes.mapIndexedNotNull { idx, cb -> if (cb.isChecked) idx else null }
                if (pattern.isNotEmpty()) onConfirm(pattern, startDate)
            }
            .show()
    }
}
