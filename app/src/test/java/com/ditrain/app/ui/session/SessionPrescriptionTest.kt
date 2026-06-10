package com.ditrain.app.ui.session

import com.ditrain.app.model.LoadTarget
import com.ditrain.app.model.RepsTarget
import com.ditrain.app.model.SetPrescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionPrescriptionTest {

    @Test
    fun `absolute kg returns the prescribed weight verbatim`() {
        val sp = SetPrescription.Straight(
            reps = RepsTarget.Fixed(5),
            load = LoadTarget.AbsoluteKg(102.5),
        )
        val r = SessionPrescription.resolve(sp, latestE1rmKg = null, lastTopWeightKg = null)
        assertEquals(102.5, r.suggestedWeightKg!!, 0.0001)
        assertEquals("5", r.repsHint)
    }

    @Test
    fun `percent of 1rm uses latest e1rm`() {
        val sp = SetPrescription.Straight(
            reps = RepsTarget.Range(3, 5),
            load = LoadTarget.PctOneRm(0.80),
        )
        val r = SessionPrescription.resolve(sp, latestE1rmKg = 200.0, lastTopWeightKg = null)
        assertEquals(160.0, r.suggestedWeightKg!!, 0.0001)
        assertEquals("3-5", r.repsHint)
    }

    @Test
    fun `percent of 1rm without an estimate returns null weight`() {
        val sp = SetPrescription.Straight(
            reps = RepsTarget.Fixed(8),
            load = LoadTarget.PctOneRm(0.75),
        )
        val r = SessionPrescription.resolve(sp, latestE1rmKg = null, lastTopWeightKg = null)
        assertNull(r.suggestedWeightKg)
    }

    @Test
    fun `rpe target leaves weight blank and surfaces rpe in the hint`() {
        val sp = SetPrescription.Straight(
            reps = RepsTarget.Fixed(5),
            load = LoadTarget.RpeTarget(8.5),
        )
        val r = SessionPrescription.resolve(sp, latestE1rmKg = 100.0, lastTopWeightKg = 100.0)
        assertNull(r.suggestedWeightKg)
        assertEquals("target @ RPE 8.5", r.weightHint)
    }

    @Test
    fun `relative to last applies delta when prior top weight exists`() {
        val sp = SetPrescription.Straight(
            reps = RepsTarget.Fixed(5),
            load = LoadTarget.RelativeToLast(2.5),
        )
        val r = SessionPrescription.resolve(sp, latestE1rmKg = null, lastTopWeightKg = 100.0)
        assertEquals(102.5, r.suggestedWeightKg!!, 0.0001)
    }

    @Test
    fun `relative to last without prior leaves blank with hint`() {
        val sp = SetPrescription.Straight(
            reps = RepsTarget.Fixed(5),
            load = LoadTarget.RelativeToLast(5.0),
        )
        val r = SessionPrescription.resolve(sp, latestE1rmKg = null, lastTopWeightKg = null)
        assertNull(r.suggestedWeightKg)
        assertEquals("+5.0 kg vs last (no prior)", r.weightHint)
    }

    @Test
    fun `open load leaves weight blank with no hint`() {
        val sp = SetPrescription.Straight(
            reps = RepsTarget.Amrap,
            load = LoadTarget.Open,
        )
        val r = SessionPrescription.resolve(sp, latestE1rmKg = null, lastTopWeightKg = null)
        assertNull(r.suggestedWeightKg)
        assertNull(r.weightHint)
        assertEquals("AMRAP", r.repsHint)
    }

    @Test
    fun `amrap min produces the threshold hint`() {
        val sp = SetPrescription.Straight(
            reps = RepsTarget.AmrapMin(8),
            load = LoadTarget.AbsoluteKg(80.0),
        )
        val r = SessionPrescription.resolve(sp, latestE1rmKg = null, lastTopWeightKg = null)
        assertEquals("AMRAP, min 8", r.repsHint)
    }

    @Test
    fun `myo-rep activation uses activationReps and load resolution`() {
        val sp = SetPrescription.MyoRep(
            activationReps = RepsTarget.Fixed(15),
            load = LoadTarget.PctOneRm(0.65),
            miniSetTargetReps = 5,
            miniSetCount = 4,
        )
        val r = SessionPrescription.resolve(sp, latestE1rmKg = 100.0, lastTopWeightKg = null)
        assertEquals(65.0, r.suggestedWeightKg!!, 0.0001)
        assertEquals("15", r.repsHint)
    }
}
