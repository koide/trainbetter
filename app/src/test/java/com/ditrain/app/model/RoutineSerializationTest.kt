package com.ditrain.app.model

import com.ditrain.app.util.JsonIo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineSerializationTest {

    private fun roundTrip(r: Routine): Routine {
        val raw = JsonIo.json.encodeToString(Routine.serializer(), r)
        return JsonIo.json.decodeFromString(Routine.serializer(), raw)
    }

    @Test fun `straight set prescription round-trips with all fields`() {
        val r = Routine(
            id = "r1", name = "R1",
            loopMode = LoopMode.REPEAT,
            weeks = listOf(
                Week("Week 1", listOf(
                    SessionTemplate("a", "Push A", blocks = listOf(
                        ExerciseBlock(
                            exerciseId = "bench",
                            sets = listOf(
                                SetPrescription.Straight(
                                    reps = RepsTarget.Fixed(6),
                                    load = LoadTarget.PctOneRm(0.80),
                                    rpeTarget = 8.0,
                                    rest = 180,
                                    tempo = "3-1-1",
                                    notes = "paused",
                                )
                            )
                        )
                    ))
                ))
            ),
        )
        assertEquals(r, roundTrip(r))
    }

    @Test fun `myo-rep prescription round-trips`() {
        val r = Routine(
            id = "r2", name = "R2",
            loopMode = LoopMode.ONCE,
            weeks = listOf(
                Week("Week 1", listOf(
                    SessionTemplate("a", "Arms", blocks = listOf(
                        ExerciseBlock(
                            exerciseId = "curl",
                            sets = listOf(
                                SetPrescription.MyoRep(
                                    activationReps = RepsTarget.Fixed(15),
                                    load = LoadTarget.AbsoluteKg(20.0),
                                    miniSetTargetReps = 5,
                                    miniSetCount = 3,
                                    miniSetRestSec = 15,
                                    rpeStopThreshold = 10.0,
                                )
                            )
                        )
                    ))
                ))
            ),
        )
        assertEquals(r, roundTrip(r))
    }

    @Test fun `all reps target variants round-trip`() {
        listOf(
            RepsTarget.Fixed(5),
            RepsTarget.Range(6, 10),
            RepsTarget.Amrap,
            RepsTarget.AmrapMin(3),
        ).forEach { target ->
            val raw = JsonIo.json.encodeToString(RepsTarget.serializer(), target)
            val back = JsonIo.json.decodeFromString(RepsTarget.serializer(), raw)
            assertEquals(target, back)
        }
    }

    @Test fun `all load target variants round-trip`() {
        listOf(
            LoadTarget.AbsoluteKg(60.0),
            LoadTarget.PctOneRm(0.75),
            LoadTarget.RpeTarget(8.0),
            LoadTarget.RelativeToLast(2.5),
            LoadTarget.Open,
        ).forEach { target ->
            val raw = JsonIo.json.encodeToString(LoadTarget.serializer(), target)
            val back = JsonIo.json.decodeFromString(LoadTarget.serializer(), raw)
            assertEquals(target, back)
        }
    }

    @Test fun `cardio-only session round-trips`() {
        val r = Routine(
            id = "r3", name = "R3",
            loopMode = LoopMode.REPEAT,
            weeks = listOf(
                Week("Week 1", listOf(
                    SessionTemplate("c", "Easy Run", cardioBlocks = listOf(
                        CardioBlock(
                            activityKind = CardioKind.RUNNING,
                            targetDurationMin = 30,
                            targetAvgBpm = 140,
                        )
                    ))
                ))
            ),
        )
        assertEquals(r, roundTrip(r))
    }

    @Test fun `mixed session round-trips`() {
        val r = Routine(
            id = "r4", name = "R4",
            loopMode = LoopMode.REPEAT,
            weeks = listOf(
                Week("Week 1", listOf(
                    SessionTemplate("h", "Lift + Walk",
                        blocks = listOf(
                            ExerciseBlock(
                                "squat",
                                sets = listOf(SetPrescription.Straight(
                                    reps = RepsTarget.Range(6, 8),
                                    load = LoadTarget.Open,
                                ))
                            )
                        ),
                        cardioBlocks = listOf(
                            CardioBlock(activityKind = CardioKind.WALKING, targetDurationMin = 15)
                        )
                    )
                ))
            ),
        )
        val back = roundTrip(r)
        assertEquals(r, back)
        assertTrue(back.weeks[0].sessions[0].blocks.isNotEmpty())
        assertTrue(back.weeks[0].sessions[0].cardioBlocks.isNotEmpty())
    }

    @Test fun `cardio kind OTHER with description round-trips`() {
        val block = CardioBlock(
            activityKind = CardioKind.OTHER,
            description = "Interval pyramid on stair-master",
            targetDurationMin = 25,
        )
        val raw = JsonIo.json.encodeToString(CardioBlock.serializer(), block)
        val back = JsonIo.json.decodeFromString(CardioBlock.serializer(), raw)
        assertEquals(block, back)
    }

    @Test fun `repeat loop with one week serializes identically to fixed template intent`() {
        val r = Routine(
            id = "fixed", name = "Fixed A-B",
            loopMode = LoopMode.REPEAT,
            weeks = listOf(
                Week("Week 1", listOf(
                    SessionTemplate("a", "A", blocks = listOf(
                        ExerciseBlock("squat", sets = listOf(
                            SetPrescription.Straight(
                                reps = RepsTarget.Fixed(5),
                                load = LoadTarget.AbsoluteKg(100.0),
                            )
                        ))
                    )),
                    SessionTemplate("b", "B", blocks = listOf(
                        ExerciseBlock("bench", sets = listOf(
                            SetPrescription.Straight(
                                reps = RepsTarget.Fixed(5),
                                load = LoadTarget.AbsoluteKg(80.0),
                            )
                        ))
                    )),
                ))
            ),
        )
        assertEquals(r, roundTrip(r))
        assertEquals(1, r.weeks.size)
        assertEquals(LoopMode.REPEAT, r.loopMode)
    }
}
