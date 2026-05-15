package com.ditrain.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

object ViewStyling {
    fun roundedBackground(fillColor: String, strokeColor: String, strokeWidth: Int, radius: Float) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.parseColor(fillColor))
            setStroke(strokeWidth, Color.parseColor(strokeColor))
        }

    fun actionButton(
        context: Context,
        label: String,
        fillColor: String,
        compact: Boolean,
        dp: (Int) -> Int,
        roundedBackground: (String, String, Float) -> GradientDrawable
    ): Button =
        Button(context).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = if (compact) 14f else 15f
            background = roundedBackground(fillColor, fillColor, dp(14).toFloat())
            val pad = if (compact) dp(6) else dp(14)
            setPadding(pad, pad, pad, pad)
            minHeight = if (compact) dp(40) else 0
            minimumHeight = if (compact) dp(40) else 0
        }

    fun dialogInputContainer(context: Context, input: EditText, dp: (Int) -> Int): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(8))
            addView(input, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
}
