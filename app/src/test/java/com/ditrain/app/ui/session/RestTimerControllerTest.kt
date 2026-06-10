package com.ditrain.app.ui.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerControllerTest {

    private class FakeClock(var now: Long = 0L) : RestTimerController.Clock {
        override fun elapsedRealtime(): Long = now
        fun advanceMs(ms: Long) { now += ms }
    }

    @Test
    fun `idle controller reports no remaining time and no overshoot`() {
        val clock = FakeClock()
        val c = RestTimerController(clock)
        assertNull(c.targetSec)
        assertEquals(0L, c.elapsedMs())
        assertFalse(c.crossedTarget())
    }

    @Test
    fun `starting with a target seeds elapsed at zero`() {
        val clock = FakeClock(now = 10_000L)
        val c = RestTimerController(clock)
        c.start(targetSec = 90)
        assertEquals(90, c.targetSec)
        assertEquals(0L, c.elapsedMs())
        assertFalse(c.crossedTarget())
    }

    @Test
    fun `elapsedMs grows with the clock`() {
        val clock = FakeClock(now = 10_000L)
        val c = RestTimerController(clock)
        c.start(targetSec = 90)
        clock.advanceMs(30_000L)
        assertEquals(30_000L, c.elapsedMs())
    }

    @Test
    fun `crossedTarget flips true the first tick past target and stays true after`() {
        val clock = FakeClock(now = 0L)
        val c = RestTimerController(clock)
        c.start(targetSec = 60)
        clock.advanceMs(59_000L)
        assertFalse(c.crossedTarget())
        clock.advanceMs(2_000L) // now at 61s
        assertTrue(c.crossedTarget())
        clock.advanceMs(60_000L)
        assertTrue(c.crossedTarget())
    }

    @Test
    fun `crossedTarget never fires when target is zero`() {
        val clock = FakeClock(now = 0L)
        val c = RestTimerController(clock)
        c.start(targetSec = 0)
        clock.advanceMs(10_000L)
        assertFalse(c.crossedTarget())
    }

    @Test
    fun `pause freezes the elapsed reading and resume continues from that point`() {
        val clock = FakeClock(now = 0L)
        val c = RestTimerController(clock)
        c.start(targetSec = 120)
        clock.advanceMs(20_000L)
        c.pause()
        clock.advanceMs(60_000L)        // wall clock moves but timer is paused
        assertEquals(20_000L, c.elapsedMs())
        c.resume()
        clock.advanceMs(10_000L)
        assertEquals(30_000L, c.elapsedMs())
    }

    @Test
    fun `adjust changes targetSec without affecting elapsed`() {
        val clock = FakeClock(now = 0L)
        val c = RestTimerController(clock)
        c.start(targetSec = 60)
        clock.advanceMs(20_000L)
        c.adjustTarget(deltaSec = 30)
        assertEquals(90, c.targetSec)
        assertEquals(20_000L, c.elapsedMs())
    }

    @Test
    fun `adjust does not let target go negative`() {
        val clock = FakeClock(now = 0L)
        val c = RestTimerController(clock)
        c.start(targetSec = 30)
        c.adjustTarget(deltaSec = -90)
        assertEquals(0, c.targetSec)
    }

    @Test
    fun `reset returns elapsed to zero and clears crossed flag`() {
        val clock = FakeClock(now = 0L)
        val c = RestTimerController(clock)
        c.start(targetSec = 30)
        clock.advanceMs(45_000L)
        assertTrue(c.crossedTarget())
        c.reset()
        assertEquals(0L, c.elapsedMs())
        assertFalse(c.crossedTarget())
    }

    @Test
    fun `stop returns to idle`() {
        val clock = FakeClock(now = 0L)
        val c = RestTimerController(clock)
        c.start(targetSec = 30)
        clock.advanceMs(10_000L)
        c.stop()
        assertNull(c.targetSec)
        assertEquals(0L, c.elapsedMs())
    }

    @Test
    fun `consumeCrossedEdge returns true exactly once then false`() {
        val clock = FakeClock(now = 0L)
        val c = RestTimerController(clock)
        c.start(targetSec = 60)
        clock.advanceMs(30_000L)
        assertFalse(c.consumeCrossedEdge())
        clock.advanceMs(31_000L) // now at 61s
        assertTrue(c.consumeCrossedEdge())
        clock.advanceMs(10_000L)
        assertFalse(c.consumeCrossedEdge())
        // Reset re-arms the edge for the next crossing.
        c.reset()
        clock.advanceMs(61_000L)
        assertTrue(c.consumeCrossedEdge())
    }
}
