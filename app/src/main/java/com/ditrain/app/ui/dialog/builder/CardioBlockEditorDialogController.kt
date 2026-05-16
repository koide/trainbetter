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
import com.ditrain.app.model.CardioBlock
import com.ditrain.app.model.CardioKind

/**
 * Edits one [CardioBlock]. Picker for activity kind, optional description (required for
 * OTHER), optional target duration and avg BPM, optional notes.
 */
class CardioBlockEditorDialogController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val onSave: (CardioBlock) -> Unit,
) {

    fun show(initial: CardioBlock? = null) {
        val kindGroup = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
        val kindButtons: List<Pair<CardioKind, RadioButton>> = CardioKind.entries.map { kind ->
            kind to RadioButton(context).apply {
                text = kind.name.lowercase().replaceFirstChar { it.uppercase() }
                setTextColor(Color.WHITE)
                isChecked = initial?.activityKind == kind || (initial == null && kind == CardioKind.RUNNING)
                id = View.generateViewId()
            }.also { kindGroup.addView(it) }
        }

        val description = EditText(context).apply {
            hint = "description (required for OTHER)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
            setText(initial?.description.orEmpty())
        }
        val targetDuration = EditText(context).apply {
            hint = "target duration (min)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
            initial?.targetDurationMin?.let { setText(it.toString()) }
        }
        val targetBpm = EditText(context).apply {
            hint = "target avg BPM"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
            initial?.targetAvgBpm?.let { setText(it.toString()) }
        }
        val notes = EditText(context).apply {
            hint = "notes"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
            setText(initial?.notes.orEmpty())
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            addView(label("Activity"))
            addView(kindGroup)
            addView(label("Description"))
            addView(description)
            addView(label("Targets (optional)"))
            addView(targetDuration); addView(targetBpm)
            addView(label("Notes"))
            addView(notes)
        }

        AlertDialog.Builder(context)
            .setTitle(if (initial == null) "New cardio block" else "Edit cardio block")
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val chosenKind = kindButtons.firstOrNull { it.second.isChecked }?.first ?: CardioKind.RUNNING
                val desc = description.text.toString().ifBlank { null }
                if (chosenKind == CardioKind.OTHER && desc.isNullOrBlank()) {
                    AlertDialog.Builder(context)
                        .setTitle("Description required")
                        .setMessage("Cardio kind OTHER requires a description.")
                        .setPositiveButton("OK", null).show()
                    return@setPositiveButton
                }
                onSave(CardioBlock(
                    activityKind = chosenKind,
                    description = desc,
                    targetDurationMin = targetDuration.text.toString().toIntOrNull(),
                    targetAvgBpm = targetBpm.text.toString().toIntOrNull(),
                    notes = notes.text.toString().ifBlank { null },
                ))
            }
            .show()
    }

    private fun label(s: String) = TextView(context).apply {
        text = s
        textSize = 12f
        setTextColor(Color.parseColor("#CBD5E1"))
        setPadding(0, dp(10), 0, dp(4))
    }
}
