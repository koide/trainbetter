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
import com.ditrain.app.ui.ViewStyling

/**
 * The "⋮" overflow menu shown from Home. Each row is a labeled action that opens
 * a sub-dialog. Plan 6 adds Settings / Backup / Glossary / About; Plan 2 ships
 * only Routines + Import routine.
 */
class MainMenuDialogController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val onOpenRoutines: () -> Unit,
    private val onImportRoutine: () -> Unit,
    private val onBrowseExercises: () -> Unit,
) {
    fun show() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Menu")
            .setView(ScrollView(context).apply { addView(container) })
            .setNegativeButton("Close", null)
            .create()

        container.addView(row("Routines", "Browse, activate, or delete saved routines") {
            dialog.dismiss()
            onOpenRoutines()
        })
        container.addView(row("Import routine", "Paste JSON, pick a file, or load a bundled example") {
            dialog.dismiss()
            onImportRoutine()
        })
        container.addView(row("Browse exercises", "See the bundled exercise catalog and their IDs") {
            dialog.dismiss()
            onBrowseExercises()
        })

        dialog.show()
    }

    private fun row(title: String, subtitle: String, onClick: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(20).toFloat())
            setPadding(dp(14), dp(14), dp(14), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            addView(TextView(context).apply {
                text = title
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = subtitle
                textSize = 13f
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, dp(4), 0, 0)
            })

            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        }
}
