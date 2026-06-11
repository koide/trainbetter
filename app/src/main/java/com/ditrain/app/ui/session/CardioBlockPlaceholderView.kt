package com.ditrain.app.ui.session

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ditrain.app.model.CardioBlock
import com.ditrain.app.ui.ViewStyling

/**
 * Read-only placeholder for cardio blocks in Plan 3. Cardio logging UI lands
 * in Plan 4; until then, blocks render their prescription and a note. They do
 * not block Finish.
 */
class CardioBlockPlaceholderView(
    context: Context,
    private val dp: (Int) -> Int,
) : ScrollView(context) {

    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
    }

    init { addView(column, LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }

    fun bind(block: CardioBlock) {
        column.removeAllViews()
        column.addView(TextView(context).apply {
            text = "Cardio · ${block.activityKind.name.lowercase()}"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        val descriptors = buildList {
            block.description?.takeIf { it.isNotBlank() }?.let { add(it) }
            block.targetDurationMin?.let { add("target ${it} min") }
            block.targetAvgBpm?.let { add("target ${it} bpm") }
        }
        if (descriptors.isNotEmpty()) {
            column.addView(TextView(context).apply {
                text = descriptors.joinToString(" · ")
                textSize = 13f
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, dp(4), 0, 0)
            })
        }
        column.addView(TextView(context).apply {
            text = "Cardio logging arrives in the next update. This block does not block Finish."
            textSize = 13f
            setTextColor(Color.parseColor("#FDE68A"))
            background = ViewStyling.roundedBackground("#111827", "#475569", dp(1), dp(14).toFloat())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(14)
        })
    }
}
