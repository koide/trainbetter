package com.ditrain.app.ui.dialog

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.model.Equipment
import com.ditrain.app.model.Exercise
import com.ditrain.app.model.MuscleGroup
import com.ditrain.app.ui.ViewStyling

/**
 * Read-only detail view for one Exercise: name, primary/secondary muscles as chips,
 * pattern + equipment, and an "Open description URL" action when [Exercise.descriptionUrl] is set.
 */
class ExerciseDetailDialogController(
    private val context: Context,
    private val dp: (Int) -> Int,
) {
    fun show(exercise: Exercise) {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))

            addView(TextView(context).apply {
                text = exercise.name
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })

            if (exercise.aliases.isNotEmpty()) {
                addView(TextView(context).apply {
                    text = "Aliases: " + exercise.aliases.joinToString(", ")
                    textSize = 13f
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(0, dp(4), 0, dp(4))
                })
            }

            addView(metaRow("Pattern", exercise.pattern.name.replace('_', ' ').lowercase().capitalizeFirst()))
            addView(metaRow("Equipment", exercise.equipment.joinToString(", ") { it.label() }))

            addView(label("Primary muscles"))
            addView(muscleChipRow(exercise.primaryMuscles, "#1D4ED8"))

            if (exercise.secondaryMuscles.isNotEmpty()) {
                addView(label("Secondary muscles"))
                addView(muscleChipRow(exercise.secondaryMuscles, "#475569"))
            }

            if (exercise.deleted) {
                addView(TextView(context).apply {
                    text = "This exercise is soft-deleted. It's hidden from pickers but still referenced by historical sessions."
                    textSize = 13f
                    setTextColor(Color.parseColor("#FCA5A5"))
                    setPadding(0, dp(10), 0, 0)
                })
            }
        }

        val builder = AlertDialog.Builder(context)
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton("Close", null)

        val url = exercise.descriptionUrl
        if (!url.isNullOrBlank()) {
            builder.setPositiveButton("Open description") { _, _ ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                // If no browser handles the intent, silently swallow.
            }
        }

        builder.show()
    }

    private fun label(text: String) = TextView(context).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.parseColor("#CBD5E1"))
        setPadding(0, dp(10), 0, dp(4))
    }

    private fun metaRow(label: String, value: String) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(4), 0, 0)
        addView(TextView(context).apply {
            text = "$label:"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(dp(110), WRAP_CONTENT)
        })
        addView(TextView(context).apply {
            text = value
            textSize = 13f
            setTextColor(Color.WHITE)
        })
    }

    private fun muscleChipRow(muscles: List<MuscleGroup>, fillColor: String): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
        }
        muscles.forEach { m ->
            row.addView(
                ViewStyling.chip(context, m.label(), fillColor, dp).apply {
                    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                        marginEnd = dp(6)
                        topMargin = dp(2)
                        bottomMargin = dp(2)
                    }
                }
            )
        }
        return row
    }
}

private fun String.capitalizeFirst(): String =
    if (isEmpty()) this else this[0].uppercaseChar() + substring(1)

private fun Equipment.label(): String = name.lowercase().replace('_', ' ')

private fun MuscleGroup.label(): String = name.lowercase().replace('_', ' ')
