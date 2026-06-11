package com.ditrain.app.ui.session

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ditrain.app.model.CardioBlock
import com.ditrain.app.model.ExerciseBlock
import com.ditrain.app.model.LoggedSet
import com.ditrain.app.model.Routine
import com.ditrain.app.model.SessionTemplate
import com.ditrain.app.model.Settings
import com.ditrain.app.storage.ExerciseCatalog
import com.ditrain.app.ui.ViewStyling
import com.ditrain.app.util.InstantIso
import com.ditrain.app.util.parseInstant
import java.time.Duration
import java.time.Instant

/**
 * Top-level session-screen controller. Holds the visible View; reads/writes
 * through [SessionState]. Calls [onMutated] after every state change so the
 * activity can persist a snapshot.
 *
 * Block order (paged): all strength `blocks` first, then all `cardioBlocks`
 * (rendered read-only via [CardioBlockPlaceholderView] until Plan 4).
 */
class SessionViewController(
    private val context: Context,
    private val catalog: ExerciseCatalog,
    private val dp: (Int) -> Int,
    private val settings: Settings,
    private val nowIso: () -> InstantIso,
    private val resolveLastTopWeightKg: (exerciseId: String) -> Double?,
    private val resolveLatestE1rmKg: (exerciseId: String) -> Double?,
    private val onMutated: () -> Unit,
    private val onAbortSaveInProgress: () -> Unit,
    private val onAbortDiscard: () -> Unit,
    private val onFinish: () -> Unit,
) {

    private lateinit var routine: Routine
    private lateinit var template: SessionTemplate
    private lateinit var state: SessionState
    private val restTimer = RestTimerController()

    private lateinit var root: LinearLayout
    private lateinit var pager: BlockPagerView
    private val restTimerView: RestTimerView by lazy {
        RestTimerView(
            context = context,
            controller = restTimer,
            dp = dp,
            onDismiss = { restTimer.stop(); refreshBottomStrip() },
            hapticEnabled = { settings.restTimerHaptic },
        )
    }

    fun buildView(
        routine: Routine,
        template: SessionTemplate,
        state: SessionState,
    ): View {
        this.routine = routine
        this.template = template
        this.state = state

        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#000000"))
        }

        root.addView(topBar())

        pager = BlockPagerView(
            context = context, dp = dp,
            pageCount = template.blocks.size + template.cardioBlocks.size,
            onPrev = { goPrev() },
            onNext = { goNext() },
        )
        root.addView(pager, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        root.addView(bottomStripContainer, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        renderCurrentPage()
        refreshBottomStrip()
        return root
    }

    private val bottomStripContainer = FrameLayout(context).apply {
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private fun topBar(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(8))
        background = ViewStyling.roundedBackground("#0F172A", "#1F2937", dp(1), dp(0).toFloat())

        addView(TextView(context).apply {
            text = "◄"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(4), dp(16), dp(4))
            isClickable = true
            setOnClickListener {
                AbortConfirmDialogController(
                    context = context,
                    onSaveInProgress = onAbortSaveInProgress,
                    onDiscard = onAbortDiscard,
                ).show()
            }
        })
        addView(TextView(context).apply {
            text = "${template.name} · ${routine.name}"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        addView(elapsedClock())
        addView(TextView(context).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Color.parseColor("#FCA5A5"))
            setPadding(dp(16), dp(4), dp(8), dp(4))
            isClickable = true
            setOnClickListener {
                AbortConfirmDialogController(
                    context = context,
                    onSaveInProgress = onAbortSaveInProgress,
                    onDiscard = onAbortDiscard,
                ).show()
            }
        })
    }

    private fun elapsedClock(): TextView = TextView(context).apply {
        textSize = 14f
        setTextColor(Color.parseColor("#CBD5E1"))
        val started = parseInstant(state.log.startedAt)
        text = formatElapsed(Duration.between(started, Instant.now()))
    }

    private fun goPrev() {
        if (!currentPageIsStrength()) {
            if (cardioCursor > 0) {
                cardioCursor -= 1
            } else {
                cardioCursor = -1
                // Land on the last strength block
                state.cursor = template.blocks.size - 1
            }
            renderCurrentPage(); refreshBottomStrip(); return
        }
        if (state.cursor > 0) {
            state.cursor -= 1
            renderCurrentPage(); refreshBottomStrip()
        }
    }

    private fun goNext() {
        if (currentPageIsStrength()) {
            if (state.cursor < template.blocks.size - 1) {
                state.cursor += 1
                renderCurrentPage(); refreshBottomStrip(); return
            }
            if (template.cardioBlocks.isNotEmpty()) {
                cardioCursor = 0
                renderCurrentPage(); refreshBottomStrip(); return
            }
        } else {
            if (cardioCursor < template.cardioBlocks.size - 1) {
                cardioCursor += 1
                renderCurrentPage(); refreshBottomStrip()
            }
        }
    }

    private var cardioCursor: Int = -1   // -1 = strength side is active
    private fun currentPageIsStrength(): Boolean = cardioCursor < 0

    private fun pagerIndex(): Int = when {
        currentPageIsStrength() -> state.cursor
        else -> template.blocks.size + cardioCursor
    }

    private fun renderCurrentPage() {
        if (currentPageIsStrength()) {
            val block = template.blocks[state.cursor]
            val view = StrengthBlockView(
                context = context, catalog = catalog, dp = dp,
                unit = settings.weightUnit, nowIso = nowIso,
                resolveLastTopWeightKg = resolveLastTopWeightKg,
                resolveLatestE1rmKg = resolveLatestE1rmKg,
                onLogged = { logged -> handleLogged(block, logged) },
                onSkip = { handleSkip() },
            )
            view.bind(block, state.log.executed[state.cursor])
            pager.setPage(pagerIndex(), view)
        } else {
            val block: CardioBlock = template.cardioBlocks[cardioCursor]
            val view = CardioBlockPlaceholderView(context, dp)
            view.bind(block)
            pager.setPage(pagerIndex(), view)
        }
    }

    private fun handleLogged(block: ExerciseBlock, logged: LoggedSet) {
        val prevSet = state.previousLoggedSetAnywhere()
        val withRest: LoggedSet = when (logged) {
            is LoggedSet.Straight -> logged.copy(
                restSec = computeRestSec(prevSet?.performedAt, logged.performedAt),
            )
            is LoggedSet.MyoRep -> logged.copy(
                restSec = computeRestSec(prevSet?.performedAt, logged.performedAt),
            )
        }
        state.appendSetToCurrentBlock(withRest)
        onMutated()
        renderCurrentPage()
        startRestTimerFor(block)
        refreshBottomStrip()
    }

    private fun handleSkip() {
        state.skipCurrentBlock()
        onMutated()
        renderCurrentPage()
        refreshBottomStrip()
    }

    private fun computeRestSec(prev: InstantIso?, now: InstantIso): Int? {
        if (prev == null) return null
        val a = parseInstant(prev)
        val b = parseInstant(now)
        val seconds = Duration.between(a, b).seconds
        return if (seconds >= 0) seconds.toInt() else null
    }

    private fun startRestTimerFor(block: ExerciseBlock) {
        val justLoggedIdx = state.log.executed[state.cursor].sets.size - 1
        val nextInBlock = block.sets.getOrNull(justLoggedIdx + 1)
        val nextRest = nextInBlock?.rest
            ?: nextStrengthBlock()?.sets?.firstOrNull()?.rest
            ?: 0
        restTimer.start(targetSec = nextRest)
    }

    private fun nextStrengthBlock(): ExerciseBlock? =
        template.blocks.getOrNull(state.cursor + 1)

    private fun refreshBottomStrip() {
        bottomStripContainer.removeAllViews()

        val prescribedCounts = template.blocks.map { it.sets.size }
        val allStrengthDone = state.isAllStrengthDone(prescribedCounts)

        if (allStrengthDone) {
            val finish = ViewStyling.actionButton(
                context, "Finish session", "#16A34A", compact = false, dp = dp,
                roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
            )
            finish.setOnClickListener { onFinish() }
            bottomStripContainer.addView(finish, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            return
        }
        if (currentPageIsStrength()) {
            bottomStripContainer.addView(restTimerView, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }

    private fun formatElapsed(d: Duration): String {
        val s = d.seconds.coerceAtLeast(0)
        return "%02d:%02d".format(s / 60, s % 60)
    }
}
