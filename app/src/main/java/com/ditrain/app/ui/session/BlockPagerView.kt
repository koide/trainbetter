package com.ditrain.app.ui.session

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ditrain.app.ui.ViewStyling

/**
 * Vertical layout:
 *   - top strip: ◄  · · ● · ·  ►  (clickable arrows + small dot per page)
 *   - body: a FrameLayout that hosts exactly one child View at a time
 *
 * The parent rebuilds the body via [setPage] when the cursor changes. The pager
 * does not own block data — it only orchestrates which child View is visible.
 */
class BlockPagerView(
    context: Context,
    private val dp: (Int) -> Int,
    private val pageCount: Int,
    private val onPrev: () -> Unit,
    private val onNext: () -> Unit,
) : LinearLayout(context) {

    private val dots: LinearLayout
    private val body: FrameLayout
    private val prevBtn: TextView
    private val nextBtn: TextView

    init {
        orientation = VERTICAL

        val topStrip = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        prevBtn = arrowButton("◄") { onPrev() }
        nextBtn = arrowButton("►") { onNext() }
        dots = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(0, WRAP_CONTENT, 1f)
        }
        topStrip.addView(prevBtn)
        topStrip.addView(dots)
        topStrip.addView(nextBtn)

        addView(topStrip)

        body = FrameLayout(context)
        addView(body, LayoutParams(MATCH_PARENT, 0, 1f))
    }

    private fun arrowButton(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 22f
        setTextColor(Color.WHITE)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        isClickable = true
        setOnClickListener { onClick() }
    }

    fun setPage(index: Int, child: View) {
        body.removeAllViews()
        body.addView(child, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        renderDots(index)
        prevBtn.alpha = if (index <= 0) 0.3f else 1.0f
        nextBtn.alpha = if (index >= pageCount - 1) 0.3f else 1.0f
    }

    private fun renderDots(activeIndex: Int) {
        dots.removeAllViews()
        for (i in 0 until pageCount) {
            val dot = View(context).apply {
                background = ViewStyling.roundedBackground(
                    if (i == activeIndex) "#FDE68A" else "#334155",
                    if (i == activeIndex) "#FDE68A" else "#334155",
                    dp(1), dp(999).toFloat(),
                )
            }
            dots.addView(dot, LayoutParams(dp(8), dp(8)).apply {
                marginStart = dp(4); marginEnd = dp(4)
            })
        }
    }
}
