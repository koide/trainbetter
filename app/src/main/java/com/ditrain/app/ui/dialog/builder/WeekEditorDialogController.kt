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
import com.ditrain.app.model.SessionTemplate
import com.ditrain.app.model.Week
import com.ditrain.app.storage.ExerciseCatalog
import com.ditrain.app.ui.ViewStyling
import java.util.UUID

class WeekEditorDialogController(
    private val context: Context,
    private val catalog: ExerciseCatalog,
    private val dp: (Int) -> Int,
    private val onSave: (Week) -> Unit,
) {

    fun show(
        weekIndex: Int,
        totalWeeks: Int,
        initial: Week? = null,
        previousWeek: Week? = null,
    ) {
        val labelEdit = EditText(context).apply {
            hint = "week label (e.g. Week 1, Heavy, Deload)"
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#94A3B8"))
            setText(initial?.label ?: "Week ${weekIndex + 1}")
        }
        val sessions: MutableList<SessionTemplate> = initial?.sessions?.toMutableList() ?: mutableListOf()
        val sessionList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        fun renderSessions() {
            sessionList.removeAllViews()
            if (sessions.isEmpty()) {
                sessionList.addView(TextView(context).apply {
                    text = "No sessions yet."
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(0, dp(8), 0, dp(8))
                })
            } else {
                sessions.forEachIndexed { idx, s -> sessionList.addView(sessionRow(idx, s, sessions, ::renderSessions)) }
            }
        }

        val addBtn = ViewStyling.actionButton(
            context, "Add session", "#7C3AED", compact = true, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        ).apply {
            setOnClickListener {
                val nextLetter = ('A' + sessions.size).coerceAtMost('Z')
                SessionEditorDialogController(context, catalog, dp) { newSession ->
                    sessions.add(newSession); renderSessions()
                }.show(suggestedName = "Day $nextLetter")
            }
        }

        val copyFromPrev: View? = previousWeek?.takeIf { it.sessions.isNotEmpty() }?.let { prev ->
            ViewStyling.actionButton(
                context, "Copy ${prev.sessions.size} sessions from previous week", "#475569", compact = true, dp = dp,
                roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
            ).apply {
                setOnClickListener {
                    // Generate fresh session ids so they're unique within the routine.
                    sessions.clear()
                    prev.sessions.forEach { s ->
                        sessions.add(s.copy(id = UUID.randomUUID().toString().take(8)))
                    }
                    renderSessions()
                }
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            addView(TextView(context).apply {
                text = "Week ${weekIndex + 1} of $totalWeeks"
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
            })
            addView(label("Week label"))
            addView(labelEdit)
            addView(label("Sessions"))
            addView(sessionList)
            addView(addBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
            if (copyFromPrev != null) {
                addView(copyFromPrev, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
            }
        }

        renderSessions()

        AlertDialog.Builder(context)
            .setTitle("Build week ${weekIndex + 1}")
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (weekIndex + 1 < totalWeeks) "Next ▸" else "Review ▸") { _, _ ->
                if (sessions.isEmpty()) {
                    AlertDialog.Builder(context).setTitle("Add at least one session")
                        .setMessage("Every week needs at least one session.")
                        .setPositiveButton("OK", null).show()
                    return@setPositiveButton
                }
                val finalLabel = labelEdit.text.toString().trim().ifEmpty { "Week ${weekIndex + 1}" }
                onSave(Week(label = finalLabel, sessions = sessions.toList()))
            }
            .show()
    }

    private fun sessionRow(idx: Int, session: SessionTemplate, list: MutableList<SessionTemplate>, refresh: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(12).toFloat())
            setPadding(dp(10), dp(6), dp(10), dp(6))

            val blockSummary = session.blocks.size.let { b ->
                val c = session.cardioBlocks.size
                buildString {
                    if (b > 0) append("$b strength")
                    if (b > 0 && c > 0) append(" · ")
                    if (c > 0) append("$c cardio")
                }
            }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = session.name; textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                })
                addView(TextView(context).apply {
                    text = blockSummary
                    textSize = 12f
                    setTextColor(Color.parseColor("#94A3B8"))
                })
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(linkBtn("Edit") {
                SessionEditorDialogController(context, catalog, dp) { updated ->
                    list[idx] = updated; refresh()
                }.show(initial = session)
            })
            addView(linkBtn("Del") { list.removeAt(idx); refresh() })

            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) }
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
