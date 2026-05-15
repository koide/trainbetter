package com.ditrain.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RpeTest {

    @Test fun `rpe to rir converts on integer points`() {
        assertEquals(2, Effort.rpeToRir(8.0))
        assertEquals(0, Effort.rpeToRir(10.0))
        assertEquals(5, Effort.rpeToRir(5.0))
    }

    @Test fun `rpe to rir rounds half points toward floor`() {
        // RPE 8.5 → RIR 1 (i.e. ~1.5 reps in reserve, rounded down to 1)
        assertEquals(1, Effort.rpeToRir(8.5))
        // RPE 9.5 → RIR 0
        assertEquals(0, Effort.rpeToRir(9.5))
    }

    @Test fun `rpe out of bounds returns null rir`() {
        assertNull(Effort.rpeToRir(0.5))
        assertNull(Effort.rpeToRir(10.5))
    }

    @Test fun `rir to rpe is the inverse on integers`() {
        assertEquals(8.0, Effort.rirToRpe(2), 1e-9)
        assertEquals(10.0, Effort.rirToRpe(0), 1e-9)
    }
}
