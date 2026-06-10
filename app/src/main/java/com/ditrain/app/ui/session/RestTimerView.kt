package com.ditrain.app.ui.session

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import com.ditrain.app.ui.ViewStyling

/**
 * Renders the rest timer in the bottom strip of the session editor. Reads from
 * [RestTimerController] on a ~100 ms tick while attached to a window.
 *
 * Layout: `[-30s]  ⏱ Rest: m:ss / m:ss  [+30s]   ✕`
 */
class RestTimerView(
    context: Context,
    private val controller: RestTimerController,
    private val dp: (Int) -> Int,
    private val onDismiss: () -> Unit,
    private val hapticEnabled: () -> Boolean,
) : LinearLayout(context) {

    private val timeText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val tickIntervalMs = 100L

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = ViewStyling.roundedBackground("#111827", "#334155", dp(1), dp(20).toFloat())
        setPadding(dp(10), dp(10), dp(10), dp(10))

        addView(chipButton("-30s") { controller.adjustTarget(-30) })
        timeText = TextView(context).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        addView(timeText, LayoutParams(0, WRAP_CONTENT, 1f).apply {
            marginStart = dp(8); marginEnd = dp(8)
        })
        addView(chipButton("+30s") { controller.adjustTarget(30) })
        addView(chipButton("✕") { onDismiss() }.apply {
            (layoutParams as? LayoutParams)?.marginStart = dp(8)
        })

        timeText.setOnClickListener {
            if (controller.elapsedMs() > 0 && controller.targetSec != null) {
                // pause/resume toggle
                if (isPaused()) controller.resume() else controller.pause()
            }
        }
        timeText.setOnLongClickListener {
            controller.reset()
            true
        }
    }

    private fun chipButton(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 13f
        setTextColor(Color.parseColor("#CBD5E1"))
        background = ViewStyling.roundedBackground("#1F2937", "#475569", dp(1), dp(999).toFloat())
        setPadding(dp(10), dp(6), dp(10), dp(6))
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun isPaused(): Boolean {
        // The controller doesn't expose pause state directly; we infer from
        // whether two ticks ~100ms apart yield the same elapsedMs.
        val a = controller.elapsedMs()
        val b = controller.elapsedMs()
        return a == b && a > 0L
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(tick)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            render()
            if (controller.consumeCrossedEdge() && hapticEnabled()) vibrateShort()
            handler.postDelayed(this, tickIntervalMs)
        }
    }

    private fun render() {
        val target = controller.targetSec
        val elapsed = controller.elapsedMs() / 1000L
        timeText.text = when {
            target == null -> "⏱ Rest: idle"
            target == 0 -> "⏱ Rest: ${formatMmSs(elapsed)} (no target)"
            elapsed <= target -> "⏱ Rest: ${formatMmSs(elapsed)} / ${formatMmSs(target.toLong())}"
            else -> "⏱ Rest: +${formatMmSs(elapsed - target.toLong())} over"
        }
        val crossed = target != null && target > 0 && elapsed > target.toLong()
        timeText.setTextColor(Color.parseColor(if (crossed) "#34D399" else "#FDE68A"))
    }

    private fun formatMmSs(totalSec: Long): String {
        val s = totalSec.coerceAtLeast(0)
        val m = s / 60
        val r = s % 60
        return "%d:%02d".format(m, r)
    }

    private fun vibrateShort() {
        val ms = 200L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(VibratorManager::class.java) ?: return
            vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Vibrator::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        }
    }
}
