package com.ditrain.app.ui.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.model.LoopMode
import com.ditrain.app.model.Routine
import com.ditrain.app.ui.ViewStyling

/**
 * Lists saved routines with per-row View / Activate / Delete actions. The activate
 * button just invokes [onActivate(routine)] — the parent wires it into the
 * AppState + schedule-layout flow.
 */
class RoutineListDialogController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val activeRoutineId: String?,
    private val onView: (Routine) -> Unit,
    private val onActivate: (Routine) -> Unit,
    private val onDelete: (Routine) -> Unit,
) {

    fun show(routines: List<Routine>) {
        if (routines.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("Routines")
                .setMessage("No routines saved yet. Use \"Import routine\" from the menu to add one.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Routines")
            .setView(ScrollView(context).apply { addView(container) })
            .setNegativeButton("Close", null)
            .create()

        routines.forEach { r -> container.addView(row(r, dialog)) }
        dialog.show()
    }

    private fun row(r: Routine, dialog: AlertDialog): View {
        val isActive = r.id == activeRoutineId

        val viewBtn = ViewStyling.actionButton(
            context = context, label = "View",
            fillColor = "#2563EB", compact = true, dp = dp,
            roundedBackground = { fill, stroke, radius ->
                ViewStyling.roundedBackground(fill, stroke, dp(2), radius)
            },
        ).apply { setOnClickListener { onView(r) } }

        val activateBtn = ViewStyling.actionButton(
            context = context,
            label = if (isActive) "Active" else "Activate",
            fillColor = if (isActive) "#16A34A" else "#7C3AED",
            compact = true, dp = dp,
            roundedBackground = { fill, stroke, radius ->
                ViewStyling.roundedBackground(fill, stroke, dp(2), radius)
            },
        ).apply {
            isEnabled = !isActive
            setOnClickListener {
                dialog.dismiss()
                onActivate(r)
            }
        }

        val deleteBtn = ViewStyling.dangerButton(context, "Delete", compact = true, dp = dp).apply {
            setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle("Delete \"${r.name}\"?")
                    .setMessage(
                        if (isActive)
                            "This routine is currently active. Deleting it will clear all future scheduled sessions for it. Past session logs are kept in history."
                        else
                            "Past session logs that reference this routine remain in history."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        dialog.dismiss()
                        onDelete(r)
                    }
                    .show()
            }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(20).toFloat())
            setPadding(dp(14), dp(14), dp(14), dp(14))

            addView(TextView(context).apply {
                text = r.name
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = "${r.weeks.size} week${if (r.weeks.size == 1) "" else "s"} · " +
                       (if (r.loopMode == LoopMode.REPEAT) "repeats" else "runs once")
                textSize = 13f
                setTextColor(Color.parseColor("#94A3B8"))
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(viewBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
                addView(activateBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
                addView(deleteBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })

            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) }
        }
    }
}
