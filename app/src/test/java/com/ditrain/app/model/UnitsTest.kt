package com.ditrain.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {

    @Test fun `kg to lb uses exact 2_2046226 factor`() {
        assertEquals(220.46226218, 100.0.kgToLb(), 1e-8)
    }

    @Test fun `lb to kg uses exact 0_45359237 factor`() {
        assertEquals(45.359237, 100.0.lbToKg(), 1e-9)
    }

    @Test fun `kg to display lb roundtrips exactly`() {
        assertEquals(100.0, 100.0.kgToLb().lbToKg(), 1e-9)
    }

    @Test fun `display rounds kg to nearest half kg`() {
        assertEquals(60.0, WeightUnit.KG.formatForInput(60.20).toDouble(), 1e-9)
        assertEquals(60.5, WeightUnit.KG.formatForInput(60.35).toDouble(), 1e-9)
    }

    @Test fun `display rounds lb to nearest one pound`() {
        // 60 kg = 132.277... lb; rounded to nearest pound = 132
        assertEquals(132.0, WeightUnit.LB.formatForInput(60.0).toDouble(), 1e-9)
        // 60.5 kg = 133.379... lb; rounds to 133
        assertEquals(133.0, WeightUnit.LB.formatForInput(60.5).toDouble(), 1e-9)
    }
}
