package com.ditrain.app.ui.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.model.Exercise
import com.ditrain.app.storage.ExerciseCatalog
import com.ditrain.app.ui.ViewStyling

/**
 * Searchable list of catalog exercises. Tap a row → [onPicked] fires and the dialog closes.
 * Soft-deleted entries are hidden unless [includeDeleted] is true.
 */
class ExercisePickerDialogController(
    private val context: Context,
    private val catalog: ExerciseCatalog,
    private val dp: (Int) -> Int,
    private val onPicked: (Exercise) -> Unit,
    private val onDetail: ((Exercise) -> Unit)? = null,
    private val includeDeleted: Boolean = false,
    private val title: String = "Pick an exercise",
) {
    fun show() {
        val search = EditText(context).apply {
            hint = "Search by name…"
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
        }

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val all = catalog.visibleExercises(includeDeleted = includeDeleted)
            .sortedBy { it.name.lowercase() }

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(8))
                addView(search)
                addView(ScrollView(context).apply { addView(list) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(400)).apply { topMargin = dp(8) })
            })
            .setNegativeButton("Cancel", null)
            .create()

        fun render(filter: String) {
            list.removeAllViews()
            val needle = filter.trim().lowercase()
            val filtered = if (needle.isEmpty()) all else all.filter { ex ->
                ex.name.lowercase().contains(needle) || ex.aliases.any { it.lowercase().contains(needle) }
            }
            if (filtered.isEmpty()) {
                list.addView(TextView(context).apply {
                    text = "No matches"
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(0, dp(12), 0, dp(12))
                    gravity = Gravity.CENTER
                })
            } else {
                filtered.forEach { ex -> list.addView(row(ex, dialog)) }
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { render(s?.toString().orEmpty()) }
        })

        render("")
        dialog.show()
    }

    private fun row(ex: Exercise, dialog: AlertDialog): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(16).toFloat())
            setPadding(dp(14), dp(10), dp(14), dp(10))

            val textLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = ex.name
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                })
                addView(TextView(context).apply {
                    text = ex.pattern.name.lowercase().replace('_', ' ') +
                           " · " + ex.equipment.joinToString(", ") { it.name.lowercase() }
                    textSize = 12f
                    setTextColor(Color.parseColor("#94A3B8"))
                })
            }
            addView(textLayout, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

            if (onDetail != null) {
                addView(TextView(context).apply {
                    text = "Info"
                    setTextColor(Color.parseColor("#60A5FA"))
                    textSize = 14f
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setOnClickListener { onDetail.invoke(ex) }
                })
            }

            setOnClickListener {
                onPicked(ex)
                dialog.dismiss()
            }

            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
}
