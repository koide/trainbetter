package com.ditrain.app.ui.session

import android.os.SystemClock

/**
 * Owns the bottom-strip inter-set countdown. Display state is read on every UI tick;
 * mutations are explicit calls from [SessionViewController] when a set is logged,
 * when the user pauses/resumes/adjusts, or when the visible page changes.
 *
 * Time source is injectable via [Clock] so unit tests can drive it with a fake.
 *
 * Note: the `restSec` actually stamped on a [LoggedSet] is *not* read from this
 * controller — it's derived from the previous logged set's `performedAt` in
 * [SessionViewController] so pause/reset interactions never corrupt the captured
 * rest duration (spec §6.6 "single source of truth").
 */
class RestTimerController(
    private val clock: Clock = SystemClockSource,
) {

    interface Clock { fun elapsedRealtime(): Long }
    object SystemClockSource : Clock {
        override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    }

    /** Prescribed duration in seconds; null when idle, 0 means count-up only. */
    var targetSec: Int? = null
        private set

    private var startedAtElapsed: Long? = null
    private var pausedRemainingMs: Long? = null
    private var crossedTargetLatched: Boolean = false

    fun start(targetSec: Int) {
        this.targetSec = targetSec.coerceAtLeast(0)
        this.startedAtElapsed = clock.elapsedRealtime()
        this.pausedRemainingMs = null
        this.crossedTargetLatched = false
    }

    fun stop() {
        targetSec = null
        startedAtElapsed = null
        pausedRemainingMs = null
        crossedTargetLatched = false
    }

    fun pause() {
        // If startedAtElapsed is null, we're either idle or already paused — both no-op.
        val started = startedAtElapsed ?: return
        pausedRemainingMs = clock.elapsedRealtime() - started
        startedAtElapsed = null
    }

    fun resume() {
        val frozen = pausedRemainingMs ?: return
        startedAtElapsed = clock.elapsedRealtime() - frozen
        pausedRemainingMs = null
    }

    /** Adjusts target by `deltaSec` (positive or negative). Target floors at 0. */
    fun adjustTarget(deltaSec: Int) {
        val current = targetSec ?: return
        targetSec = (current + deltaSec).coerceAtLeast(0)
    }

    /** Resets elapsed to zero without changing targetSec. */
    fun reset() {
        if (targetSec == null) return
        startedAtElapsed = if (pausedRemainingMs != null) null else clock.elapsedRealtime()
        if (pausedRemainingMs != null) pausedRemainingMs = 0L
        crossedTargetLatched = false
    }

    /** Elapsed since [start], in ms. Returns 0 when idle. */
    fun elapsedMs(): Long {
        pausedRemainingMs?.let { return it }
        val started = startedAtElapsed ?: return 0L
        return clock.elapsedRealtime() - started
    }

    /**
     * Returns true the first time elapsed exceeds [targetSec], and stays true thereafter
     * for the same start. Caller is responsible for firing haptic feedback only on the
     * leading edge — use [consumeCrossedEdge] for that.
     */
    fun crossedTarget(): Boolean {
        val t = targetSec ?: return false
        if (t == 0) return false
        if (elapsedMs() > t * 1000L) crossedTargetLatched = true
        return crossedTargetLatched
    }

    /**
     * Returns true exactly once when the timer crosses the target for the first time
     * since [start]. After that, returns false until the next [start] or [reset].
     */
    fun consumeCrossedEdge(): Boolean {
        val t = targetSec ?: return false
        if (t == 0) return false
        if (crossedTargetLatched) return false           // already consumed
        if (elapsedMs() > t * 1000L) {
            crossedTargetLatched = true
            return true
        }
        return false
    }
}
