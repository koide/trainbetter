package com.ditrain.app.ui.session

import com.ditrain.app.model.LoggedSet
import com.ditrain.app.model.SessionLog
import com.ditrain.app.util.InstantIso

/**
 * Mutable in-memory holder for the in-progress [SessionLog] plus the pager cursor
 * (index into [SessionLog.executed]; cardio blocks are paged separately and aren't
 * tracked here in Plan 3).
 *
 * Mutations are explicit method calls (no observers); the [SessionViewController]
 * calls back into the activity after every mutation to persist a snapshot of [log]
 * to disk.
 */
class SessionState(initial: SessionLog) {

    var log: SessionLog = initial
        private set

    /** Index into [log].executed for the currently visible strength block. */
    var cursor: Int = 0

    fun appendSetToCurrentBlock(loggedSet: LoggedSet) {
        val current = log.executed[cursor]
        val updated = current.copy(sets = current.sets + loggedSet, skipped = false)
        log = log.copy(executed = log.executed.toMutableList().also { it[cursor] = updated })
    }

    fun skipCurrentBlock() {
        val current = log.executed[cursor]
        val updated = current.copy(sets = emptyList(), skipped = true)
        log = log.copy(executed = log.executed.toMutableList().also { it[cursor] = updated })
    }

    /** Most recent logged set in the current block, or null if none yet. */
    fun previousLoggedSetInCurrentBlock(): LoggedSet? =
        log.executed[cursor].sets.lastOrNull()

    /** Most recent logged set anywhere in this session (for first-set rest-timer seeding). */
    fun previousLoggedSetAnywhere(): LoggedSet? =
        log.executed.flatMap { it.sets }.maxByOrNull { it.performedAt }

    fun isStrengthBlockComplete(prescribedSetCount: Int): Boolean {
        val block = log.executed[cursor]
        return block.skipped || block.sets.size >= prescribedSetCount
    }

    fun isAllStrengthDone(prescribedSetCounts: List<Int>): Boolean =
        log.executed.indices.all { i ->
            val block = log.executed[i]
            block.skipped || block.sets.size >= prescribedSetCounts[i]
        }

    fun markCompleted(at: InstantIso) {
        log = log.copy(completedAt = at)
    }
}
