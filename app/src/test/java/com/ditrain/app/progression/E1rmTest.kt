package com.ditrain.app.progression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class E1rmTest {

    private val tol = 0.1

    @Test
    fun `epley plain matches reference when rpe is null`() {
        // 100 kg x 5 with no RPE -> 100 * (1 + 5/30) = 116.6667 kg
        val e = E1rm.estimate(weightKg = 100.0, reps = 5, rpe = null)
        assertEquals(116.6667, e!!, tol)
    }

    @Test
    fun `epley with rpe adjustment matches reference`() {
        // 100 kg x 5 @ RPE 8 -> 100 * (1 + 5/30) * (1 + 0.0333 * (10 - 8))
        //                    = 116.6667 * 1.0666  ≈ 124.44 kg
        val e = E1rm.estimate(weightKg = 100.0, reps = 5, rpe = 8.0)
        assertEquals(124.44, e!!, tol)
    }

    @Test
    fun `single rep at rpe 10 returns plain epley (no rpe boost)`() {
        // reps=1, RPE=10 yields rpeAdj=1, so result is plain Epley: 150 * (1 + 1/30) = 155.0
        val e = E1rm.estimate(weightKg = 150.0, reps = 1, rpe = 10.0)
        assertEquals(155.0, e!!, tol)
    }

    @Test
    fun `zero or negative weight returns null`() {
        assertNull(E1rm.estimate(weightKg = 0.0, reps = 5, rpe = 8.0))
        assertNull(E1rm.estimate(weightKg = -10.0, reps = 5, rpe = 8.0))
    }

    @Test
    fun `zero or negative reps returns null`() {
        assertNull(E1rm.estimate(weightKg = 100.0, reps = 0, rpe = 8.0))
        assertNull(E1rm.estimate(weightKg = 100.0, reps = -3, rpe = 8.0))
    }

    @Test
    fun `out of range rpe is ignored and falls back to plain epley`() {
        val plain = E1rm.estimate(weightKg = 100.0, reps = 5, rpe = null)!!
        val withBadRpe = E1rm.estimate(weightKg = 100.0, reps = 5, rpe = 11.5)!!
        assertEquals(plain, withBadRpe, tol)
    }

    @Test
    fun `zero rpe is treated as absent and falls back to plain epley`() {
        // Defensive: callers should pass null for "no RPE recorded", but 0.0 is
        // a plausible sentinel; document the behavior with a test so it does
        // not silently change.
        val plain = E1rm.estimate(weightKg = 100.0, reps = 5, rpe = null)!!
        val withZeroRpe = E1rm.estimate(weightKg = 100.0, reps = 5, rpe = 0.0)!!
        assertEquals(plain, withZeroRpe, tol)
    }
}
