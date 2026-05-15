package com.ditrain.app.ui.home

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Placeholder Home for Plan 1. Renders the app name and a status sub-line.
 * Plan 3 replaces this with the real session-aware Home.
 */
class HomeViewController(private val context: Context) {

    fun buildView(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(48, 48, 48, 48)

        addView(TextView(context).apply {
            text = "DiTrain"
            textSize = 36f
            gravity = Gravity.CENTER
        })

        addView(TextView(context).apply {
            text = "Foundation milestone — no routine loaded yet"
            textSize = 14f
            gravity = Gravity.CENTER
            alpha = 0.7f
            setPadding(0, 16, 0, 0)
        })
    }
}
