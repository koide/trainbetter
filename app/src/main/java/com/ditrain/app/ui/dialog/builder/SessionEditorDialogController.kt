package com.ditrain.app.ui.dialog.builder

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.model.CardioBlock
import com.ditrain.app.model.ExerciseBlock
import com.ditrain.app.model.SessionTemplate
import com.ditrain.app.storage.ExerciseCatalog
import com.ditrain.app.ui.ViewStyling
import java.util.UUID

class SessionEditorDialogController(
    private val context: Context,
    private val catalog: ExerciseCatalog,
    private val dp: (Int) -> Int,
    private val onSave: (SessionTemplate) -> Unit,
) {

    fun show(initial: SessionTemplate? = null, suggestedName: String = "Day A") {
        val sessionId = initial?.id ?: UUID.randomUUID().toString().take(8)
        val nameEdit = EditText(context).apply {
            hint = "session name (e.g. Push A)"
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#94A3B8"))
            setText(initial?.name ?: suggestedName)
        }
        val blocks: MutableList<ExerciseBlock> = initial?.blocks?.toMutableList() ?: mutableListOf()
        val cardioBlocks: MutableList<CardioBlock> = initial?.cardioBlocks?.toMutableList() ?: mutableListOf()

        val blockList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val cardioList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        fun renderBlocks() {
            blockList.removeAllViews()
            if (blocks.isEmpty()) {
                blockList.addView(emptyLine("No strength blocks yet."))
            } else {
                blocks.forEachIndexed { idx, b -> blockList.addView(blockRow(idx, b, blocks, ::renderBlocks)) }
            }
        }
        fun renderCardio() {
            cardioList.removeAllViews()
            if (cardioBlocks.isEmpty()) {
                cardioList.addView(emptyLine("No cardio blocks yet."))
            } else {
                cardioBlocks.forEachIndexed { idx, c -> cardioList.addView(cardioRow(idx, c, cardioBlocks, ::renderCardio)) }
            }
        }

        val addStrengthBtn = ViewStyling.actionButton(
            context, "Add strength block", "#7C3AED", compact = true, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        ).apply {
            setOnClickListener {
                ExerciseBlockEditorDialogController(context, catalog, dp) { newBlock ->
                    blocks.add(newBlock); renderBlocks()
                }.show()
            }
        }
        val addCardioBtn = ViewStyling.actionButton(
            context, "Add cardio block", "#0EA5E9", compact = true, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        ).apply {
            setOnClickListener {
                CardioBlockEditorDialogController(context, dp) { newCardio ->
                    cardioBlocks.add(newCardio); renderCardio()
                }.show()
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            addView(label("Session name"))
            addView(nameEdit)
            addView(label("Strength blocks"))
            addView(blockList); addView(addStrengthBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(label("Cardio blocks"))
            addView(cardioList); addView(addCardioBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        }

        renderBlocks(); renderCardio()

        AlertDialog.Builder(context)
            .setTitle(if (initial == null) "New session" else "Edit session")
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = nameEdit.text.toString().trim().ifEmpty { suggestedName }
                if (blocks.isEmpty() && cardioBlocks.isEmpty()) {
                    AlertDialog.Builder(context).setTitle("Add at least one block")
                        .setMessage("A session needs at least one strength or cardio block.")
                        .setPositiveButton("OK", null).show()
                    return@setPositiveButton
                }
                onSave(SessionTemplate(
                    id = sessionId,
                    name = name,
                    blocks = blocks.toList(),
                    cardioBlocks = cardioBlocks.toList(),
                ))
            }
            .show()
    }

    private fun blockRow(idx: Int, block: ExerciseBlock, list: MutableList<ExerciseBlock>, refresh: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(12).toFloat())
            setPadding(dp(10), dp(6), dp(10), dp(6))

            val exName = catalog.byId(block.exerciseId)?.name ?: "(unknown: ${block.exerciseId})"
            addView(TextView(context).apply {
                text = "$exName · ${block.sets.size} sets"
                textSize = 13f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(linkBtn("Edit") {
                ExerciseBlockEditorDialogController(context, catalog, dp) { updated ->
                    list[idx] = updated; refresh()
                }.show(initial = block)
            })
            addView(linkBtn("Del") { list.removeAt(idx); refresh() })

            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) }
        }

    private fun cardioRow(idx: Int, block: CardioBlock, list: MutableList<CardioBlock>, refresh: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(12).toFloat())
            setPadding(dp(10), dp(6), dp(10), dp(6))

            val name = block.description?.takeIf { it.isNotBlank() } ?: block.activityKind.name.lowercase()
            addView(TextView(context).apply {
                text = "Cardio: $name" + (block.targetDurationMin?.let { " · ${it} min" } ?: "")
                textSize = 13f
                setTextColor(Color.parseColor("#FDE68A"))
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(linkBtn("Edit") {
                CardioBlockEditorDialogController(context, dp) { updated ->
                    list[idx] = updated; refresh()
                }.show(initial = block)
            })
            addView(linkBtn("Del") { list.removeAt(idx); refresh() })

            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) }
        }

    private fun emptyLine(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(Color.parseColor("#94A3B8"))
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun linkBtn(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 13f
        setTextColor(Color.parseColor("#60A5FA"))
        setPadding(dp(8), dp(6), dp(8), dp(6))
        setOnClickListener { onClick() }
    }

    private fun label(s: String) = TextView(context).apply {
        text = s; textSize = 12f
        setTextColor(Color.parseColor("#CBD5E1"))
        setPadding(0, dp(10), 0, dp(4))
    }
}
