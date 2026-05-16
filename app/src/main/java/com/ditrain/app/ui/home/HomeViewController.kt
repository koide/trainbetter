package com.ditrain.app.ui.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import com.ditrain.app.model.Routine
import com.ditrain.app.ui.ViewStyling

/**
 * Home view for Plan 2. Renders:
 *  - A title bar with the app name and an overflow "⋮" affordance.
 *  - A body that adapts to whether a routine is active: a "No routine yet"
 *    placeholder or a summary of the active routine.
 *
 * Plan 3 replaces the body with "today's session" + Start button. The menu
 * affordance stays.
 */
class HomeViewController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val onMenuClick: () -> Unit,
    private val onImportNow: () -> Unit,
) {

    fun buildView(activeRoutine: Routine?): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))

        addView(titleBar())
        addView(divider())

        if (activeRoutine == null) {
            addView(noRoutineCard())
        } else {
            addView(activeRoutineCard(activeRoutine))
        }
    }

    private fun titleBar() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            text = "DiTrain"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        addView(TextView(context).apply {
            text = "⋮"
            textSize = 28f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(4), dp(16), dp(4))
            isClickable = true
            isFocusable = true
            setOnClickListener { onMenuClick() }
        })
    }

    private fun divider() = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        }
        setBackgroundColor(Color.parseColor("#334155"))
    }

    private fun noRoutineCard() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(20).toFloat())
        setPadding(dp(16), dp(16), dp(16), dp(16))

        addView(TextView(context).apply {
            text = "Welcome to DiTrain"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        addView(TextView(context).apply {
            text = "Import a routine to get started. You can paste JSON, pick a file, or try a bundled example."
            textSize = 14f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(8), 0, dp(14))
        })
        addView(ViewStyling.actionButton(
            context, "Import a routine", "#2563EB", compact = false, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        ).apply {
            setOnClickListener { onImportNow() }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun activeRoutineCard(routine: Routine) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(20).toFloat())
        setPadding(dp(16), dp(16), dp(16), dp(16))

        addView(TextView(context).apply {
            text = "Active routine"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
        })
        addView(TextView(context).apply {
            text = routine.name
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(2), 0, 0)
        })
        addView(TextView(context).apply {
            text = "${routine.weeks.size} week${if (routine.weeks.size == 1) "" else "s"} · " +
                    (if (routine.loopMode.name == "REPEAT") "repeats indefinitely" else "runs once")
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(4), 0, dp(14))
        })
        addView(TextView(context).apply {
            text = "Session execution arrives in the next milestone. For now you can browse the routine via Menu → Routines."
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
        })
    }
}
