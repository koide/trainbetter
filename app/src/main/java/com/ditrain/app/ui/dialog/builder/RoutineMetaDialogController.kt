package com.ditrain.app.ui.dialog.builder

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.model.LoopMode

class RoutineMetaDialogController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val onNext: (RoutineMeta) -> Unit,
) {

    data class RoutineMeta(
        val name: String,
        val description: String?,
        val loopMode: LoopMode,
        val weekCount: Int,
    )

    fun show(initial: RoutineMeta? = null) {
        val nameEdit = EditText(context).apply {
            hint = "routine name (e.g. My Hypertrophy Block)"
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#94A3B8"))
            if (initial != null) setText(initial.name)
        }
        val descEdit = EditText(context).apply {
            hint = "description (optional)"
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#94A3B8"))
            if (initial != null) setText(initial.description.orEmpty())
        }
        val isOnce = initial?.loopMode == LoopMode.ONCE
        val onceRb = RadioButton(context).apply {
            text = "Runs once (mesocycle)"; setTextColor(Color.WHITE); id = View.generateViewId()
            isChecked = isOnce
        }
        val repeatRb = RadioButton(context).apply {
            text = "Repeats indefinitely (template)"; setTextColor(Color.WHITE); id = View.generateViewId()
            isChecked = !isOnce
        }
        val loopGroup = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL; addView(onceRb); addView(repeatRb) }
        val weekCountEdit = EditText(context).apply {
            hint = "number of weeks (1..52)"
            setText((initial?.weekCount ?: 1).toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#94A3B8"))
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            addView(label("Name"))
            addView(nameEdit)
            addView(label("Description"))
            addView(descEdit)
            addView(label("Type"))
            addView(loopGroup)
            addView(label("Number of weeks"))
            addView(weekCountEdit)
            addView(TextView(context).apply {
                text = "Tip: Templates loop the same week(s) forever. Mesocycles run once and end."
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, dp(8), 0, 0)
            })
        }

        AlertDialog.Builder(context)
            .setTitle(if (initial == null) "New routine — step 1 of 2" else "Edit routine — step 1 of 2")
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Next ▸") { _, _ ->
                val name = nameEdit.text.toString().trim()
                if (name.isEmpty()) {
                    AlertDialog.Builder(context).setTitle("Name required")
                        .setPositiveButton("OK", null).show()
                    return@setPositiveButton
                }
                val weeks = weekCountEdit.text.toString().toIntOrNull()?.coerceIn(1, 52) ?: 1
                onNext(RoutineMeta(
                    name = name,
                    description = descEdit.text.toString().ifBlank { null },
                    loopMode = if (onceRb.isChecked) LoopMode.ONCE else LoopMode.REPEAT,
                    weekCount = weeks,
                ))
            }
            .show()
    }

    private fun label(s: String) = TextView(context).apply {
        text = s; textSize = 12f
        setTextColor(Color.parseColor("#CBD5E1"))
        setPadding(0, dp(10), 0, dp(4))
    }
}
