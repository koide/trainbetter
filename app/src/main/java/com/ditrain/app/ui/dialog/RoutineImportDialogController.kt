package com.ditrain.app.ui.dialog

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
import com.ditrain.app.ui.ViewStyling

/**
 * Routine import dialog. Three modes for the user:
 *  - Paste JSON: large EditText + Parse button → invokes [onParse] with the pasted text.
 *  - Pick file: invokes [onPickFile] which the Activity handles via SAF.
 *  - Bundled examples: shows a list of asset filenames; tap → invokes [onParse] with the
 *    file's contents (loaded by [loadBundledExample]).
 *
 * The dialog only handles user input. Parse result rendering is the caller's job (typically
 * by showing a RoutinePreview on Success, or a toast/banner on failure).
 */
class RoutineImportDialogController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val bundledExamples: List<BundledExample>,
    private val loadBundledExample: (assetPath: String) -> String,
    private val onParse: (json: String) -> Unit,
    private val onPickFile: () -> Unit,
) {

    data class BundledExample(val assetPath: String, val displayName: String, val description: String)

    fun show() {
        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Import routine")
            .setView(ScrollView(context).apply {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    addView(tabRow)
                    addView(body)
                })
            })
            .setNegativeButton("Cancel", null)
            .create()

        fun renderPaste() {
            body.removeAllViews()
            val edit = EditText(context).apply {
                hint = "Paste routine JSON here…"
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#94A3B8"))
                minLines = 6
                maxLines = 18
                isSingleLine = false
            }
            body.addView(edit, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            body.addView(ViewStyling.actionButton(
                context, "Parse", "#2563EB", compact = false, dp = dp,
                roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
            ).apply {
                setOnClickListener {
                    val raw = edit.text.toString()
                    if (raw.isBlank()) return@setOnClickListener
                    dialog.dismiss()
                    onParse(raw)
                }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        }

        fun renderPickFile() {
            body.removeAllViews()
            body.addView(TextView(context).apply {
                text = "Choose a routine JSON file from your device."
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 14f
                setPadding(0, 0, 0, dp(10))
            })
            body.addView(ViewStyling.actionButton(
                context, "Open file…", "#2563EB", compact = false, dp = dp,
                roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
            ).apply {
                setOnClickListener {
                    dialog.dismiss()
                    onPickFile()
                }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        fun renderBundled() {
            body.removeAllViews()
            if (bundledExamples.isEmpty()) {
                body.addView(TextView(context).apply {
                    text = "No bundled examples available."
                    setTextColor(Color.parseColor("#94A3B8"))
                })
                return
            }
            bundledExamples.forEach { example ->
                body.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(16).toFloat())
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        val raw = loadBundledExample(example.assetPath)
                        dialog.dismiss()
                        onParse(raw)
                    }
                    addView(TextView(context).apply {
                        text = example.displayName
                        textSize = 15f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                    })
                    addView(TextView(context).apply {
                        text = example.description
                        textSize = 13f
                        setTextColor(Color.parseColor("#CBD5E1"))
                        setPadding(0, dp(4), 0, 0)
                    })
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
                })
            }
        }

        fun tab(label: String, onClick: () -> Unit) =
            ViewStyling.actionButton(
                context, label, "#475569", compact = true, dp = dp,
                roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
            ).apply { setOnClickListener { onClick() } }

        tabRow.addView(tab("Paste") { renderPaste() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
        tabRow.addView(tab("Pick file") { renderPickFile() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        tabRow.addView(tab("Examples") { renderBundled() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(4) })

        renderPaste()
        dialog.show()
    }
}
