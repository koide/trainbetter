# DiTrain v1 — Plan 3: Session execution (strength + rest timer)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A user with an active routine can tap **Start session** on Home, execute every prescribed strength `ExerciseBlock` set-by-set (straight + myo-rep), with a working inter-set rest timer, then **Finish** the session. The completed `SessionLog` lands in `sessions.json` and the `ScheduledSession` advances. Cardio block UI, adaptation flows (substitute / skip / ad-hoc), exercise history, PR/e1RM views, calendar reschedule, and backup are explicitly **deferred to later plans**.

**Architecture:**
- A single new `SessionViewController` (mirrors `HomeViewController`'s shape) owns the visible session page. It renders one `ExerciseBlock` at a time via a `BlockPagerView` (programmatic horizontal pager backed by simple swap-the-child, not a `ViewPager` — DiRead-style minimalism).
- `SessionState` is a plain in-memory mutable holder for the in-progress `SessionLog` plus pager cursor. Lifecycle owner is `MainActivity`; persistence to disk happens after every Log-set and every Skip.
- `RestTimerController` owns the bottom-strip countdown. It uses `SystemClock.elapsedRealtime()` for the visible display and stamps `restSec` on logged sets from the previous logged set's `performedAt` (single source of truth per spec §6.6).
- A new `progression/E1rm.kt` is added now (not Plan 5) because `LoadTarget.PctOneRm` set-row prefill needs it. PR-detection and chart views stay in Plan 5.
- Cardio blocks in the active routine are **rendered as read-only "Cardio · pending (Plan 4)" placeholder pages** and never block the Finish button — they're treated as auto-skipped for the purpose of completion detection. Plan 4 replaces the placeholder with a real `CardioBlockView` and removes the auto-skip.
- Adaptation flows from spec §6.3 (one-time substitute, persistent substitute, skip exercise, add ad-hoc) are deliberately out of this plan. The block header in this milestone shows the exercise name as plain text (no tap → ExercisePicker wiring). A `+ Add set` affordance IS in scope so the user can exceed the prescribed set count if they want; **Skip exercise** is in scope as the single adaptation present in v1 of this milestone, because Finish detection needs a "this block isn't getting logged" path.

**Tech Stack:** No new dependencies. Reuses `kotlinx-coroutines-android`, `kotlinx-serialization-json`, AppCompat, JUnit, `kotlinx-coroutines-test` — all already on the classpath.

**Reference spec:** `docs/superpowers/specs/2026-05-15-ditrain-v1-design.md` — §4.3 SessionLog, §5.2 Session editor, §6.3 Run a session, §6.6 Rest timer, §8 e1RM formula.

**Reference plans:**
- Plan 1 (`docs/superpowers/plans/2026-05-15-ditrain-v1-foundation.md`, tag `v0.1.0-foundation`)
- Plan 2 (`docs/superpowers/plans/2026-05-15-ditrain-v1-routines.md`, tag `v0.2.0-routines`)
- Plan 2.5 (`docs/superpowers/plans/2026-05-16-ditrain-v1-builder.md`, tag `v0.3.0-builder`)

---

## Pre-flight assumptions

- Working directory: `C:\Users\Usuario\Documents\TrainBetter`
- Branch: `main` (user-consented to direct commits)
- Last commit at plan start: `80bb2c2` (current HEAD; post-`v0.3.1-templates-and-edit`)
- Build env vars set: `JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot"` and `ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk"`
- Bash tool is preferred for shell commands (no permission prompts on Windows). Gradle env inline:
  `JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat ...`

---

## Conventions

- **Commit messages** follow Conventional Commits, single line. **No `Co-Authored-By:`, `Signed-off-by:`, or any other trailer.** The user has explicitly forbidden them.
- **TDD cadence** for test-bearing tasks: write failing test → run, see fail → implement → run, see pass → commit.
- **Verification** for pure-UI tasks: `assembleDebug` succeeds plus a one-paragraph note describing what would be visible on the device.
- **No emojis** in code or commit messages unless the user explicitly asks.
- **Never use `--no-verify`** when committing. If a hook fails, fix the underlying issue.
- **Do not modify** `MainActivity.kt` more than necessary. The Activity is already large; each new flow gets one new `private fun openX()` and one new field if needed. No refactor.

---

## File Structure

Files this plan creates or modifies:

```
TrainBetter/
└── app/src/
    ├── main/java/com/ditrain/app/
    │   ├── MainActivity.kt                          # MODIFY: add openSession() flow + sessionLogRepo field
    │   ├── progression/
    │   │   └── E1rm.kt                              # CREATE: Epley w/ RPE adjustment
    │   ├── ui/
    │   │   ├── home/
    │   │   │   └── HomeViewController.kt            # MODIFY: replace placeholder card with today-card + Start/Resume button
    │   │   └── session/
    │   │       ├── SessionState.kt                  # CREATE: in-memory holder for the in-progress SessionLog
    │   │       ├── SessionViewController.kt         # CREATE: top-level session screen
    │   │       ├── BlockPagerView.kt                # CREATE: prev/next arrow nav across blocks
    │   │       ├── StrengthBlockView.kt             # CREATE: header + logged-sets list + active SetEntryView + Add/Skip
    │   │       ├── SetEntryView.kt                  # CREATE: active-set input row (straight + myo-rep variants)
    │   │       ├── CardioBlockPlaceholderView.kt    # CREATE: read-only stub until Plan 4
    │   │       ├── RestTimerController.kt           # CREATE: controller for the bottom-strip timer
    │   │       ├── RestTimerView.kt                 # CREATE: bottom-strip view bound to controller
    │   │       ├── AbortConfirmDialogController.kt  # CREATE: ✕ → Save / Discard / Cancel
    │   │       └── SessionPrescription.kt           # CREATE: pure-function load + reps prefill resolver
    │   └── res/
    │       └── (no XML changes)
    └── test/java/com/ditrain/app/
        ├── progression/
        │   └── E1rmTest.kt                          # CREATE
        └── ui/session/
            ├── SessionStateTest.kt                  # CREATE
            ├── SessionPrescriptionTest.kt           # CREATE
            └── RestTimerControllerTest.kt           # CREATE (with FakeClock)
```

**Files deliberately deferred:**
- Cardio session UI / cardio log → Plan 4.
- Substitute (today / persistent), Add ad-hoc, ExercisePicker integration in the session screen → Plan 5.
- ExerciseHistory, PRs, E1rmChart, Calendar reschedule → Plan 5.
- Settings, Backup, About, Glossary → Plan 6.

---

## Task 1: Add `E1rm.kt` — pure-function 1-RM estimate

**Why first:** `SetPrescription.Straight` with `LoadTarget.PctOneRm` needs to resolve to a kg value when its row appears in `SetEntryView`. The resolver in Task 7 imports `E1rm.estimate(...)`. Pure JVM, fully testable.

**Files:**
- Create: `app/src/main/java/com/ditrain/app/progression/E1rm.kt`
- Test: `app/src/test/java/com/ditrain/app/progression/E1rmTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/ditrain/app/progression/E1rmTest.kt`:

```kotlin
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
    fun `single rep at rpe 10 returns the weight itself`() {
        // 1RM is the weight when reps=1, RPE=10 (no adjustment, no reserve)
        val e = E1rm.estimate(weightKg = 150.0, reps = 1, rpe = 10.0)
        assertEquals(150.0, e!!, tol)
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
}
```

- [ ] **Step 2: Run the test, expect failure**

Run:
```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat :app:testDebugUnitTest --tests "com.ditrain.app.progression.E1rmTest"
```
Expected: build fails with `unresolved reference: E1rm`.

- [ ] **Step 3: Implement `E1rm.kt`**

Create `app/src/main/java/com/ditrain/app/progression/E1rm.kt`:

```kotlin
package com.ditrain.app.progression

/**
 * Estimated 1-rep max (e1RM) from a sub-maximal set.
 *
 * Primary formula: Epley with RPE adjustment when RPE is in [1.0, 10.0]:
 *   weightKg * (1 + reps / 30) * (1 + 0.0333 * (10 - rpe))
 *
 * Fallback: plain Epley when RPE is null or out of range:
 *   weightKg * (1 + reps / 30)
 *
 * Returns null if [weightKg] <= 0 or [reps] < 1 (no estimate is meaningful).
 */
object E1rm {

    fun estimate(weightKg: Double, reps: Int, rpe: Double?): Double? {
        if (weightKg <= 0.0 || reps < 1) return null
        val plain = weightKg * (1.0 + reps.toDouble() / 30.0)
        val rpeAdj = if (rpe != null && rpe in 1.0..10.0) {
            1.0 + 0.0333 * (10.0 - rpe)
        } else 1.0
        return plain * rpeAdj
    }
}
```

- [ ] **Step 4: Run the test, expect pass**

Run:
```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat :app:testDebugUnitTest --tests "com.ditrain.app.progression.E1rmTest"
```
Expected: `BUILD SUCCESSFUL` and 6 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ditrain/app/progression/E1rm.kt app/src/test/java/com/ditrain/app/progression/E1rmTest.kt
git commit -m "feat(progression): add E1rm.estimate with Epley + RPE adjustment"
```

---

## Task 2: Add `SessionPrescription.kt` — pure resolver for set prefill

**Why:** Both straight and myo-rep set rows need to compute a suggested weight (kg) and rep target from a `SetPrescription`, given the most-recent e1RM and most-recent same-exercise top set. Pulling this into a pure object keeps `SetEntryView` thin and unit-testable.

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/session/SessionPrescription.kt`
- Test: `app/src/test/java/com/ditrain/app/ui/session/SessionPrescriptionTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/ditrain/app/ui/session/SessionPrescriptionTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the test, expect failure**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat :app:testDebugUnitTest --tests "com.ditrain.app.ui.session.SessionPrescriptionTest"
```
Expected: `unresolved reference: SessionPrescription`.

- [ ] **Step 3: Implement `SessionPrescription.kt`**

Create `app/src/main/java/com/ditrain/app/ui/session/SessionPrescription.kt`:

```kotlin
package com.ditrain.app.ui.session

import com.ditrain.app.model.LoadTarget
import com.ditrain.app.model.RepsTarget
import com.ditrain.app.model.SetPrescription

/**
 * Pure resolver: turns a [SetPrescription] + prior history into an initial suggestion
 * for the active set row's weight and reps fields, plus textual hints to show when
 * a value cannot be computed.
 *
 * Internal units are kg. UI is responsible for converting for display.
 */
object SessionPrescription {

    data class Resolved(
        val suggestedWeightKg: Double?,   // null = leave field blank
        val weightHint: String?,          // shown next to the field when there is no weight
        val repsHint: String,             // always populated; shown next to reps field
    )

    fun resolve(
        prescription: SetPrescription,
        latestE1rmKg: Double?,
        lastTopWeightKg: Double?,
    ): Resolved {
        val repsTarget = when (prescription) {
            is SetPrescription.Straight -> prescription.reps
            is SetPrescription.MyoRep -> prescription.activationReps
        }
        val load = when (prescription) {
            is SetPrescription.Straight -> prescription.load
            is SetPrescription.MyoRep -> prescription.load
        }

        val (weightKg, weightHint) = when (load) {
            is LoadTarget.AbsoluteKg -> load.kg to null
            is LoadTarget.PctOneRm -> {
                if (latestE1rmKg != null) (latestE1rmKg * load.pct) to null
                else null to "${(load.pct * 100).toInt()}% (no prior e1RM yet)"
            }
            is LoadTarget.RpeTarget -> null to "target @ RPE ${trimZero(load.rpe)}"
            is LoadTarget.RelativeToLast -> {
                if (lastTopWeightKg != null) (lastTopWeightKg + load.deltaKg) to null
                else null to "${formatDelta(load.deltaKg)} vs last (no prior)"
            }
            LoadTarget.Open -> null to null
        }

        val repsHint = when (repsTarget) {
            is RepsTarget.Fixed -> repsTarget.reps.toString()
            is RepsTarget.Range -> "${repsTarget.min}-${repsTarget.max}"
            RepsTarget.Amrap -> "AMRAP"
            is RepsTarget.AmrapMin -> "AMRAP, min ${repsTarget.min}"
        }

        return Resolved(weightKg, weightHint, repsHint)
    }

    private fun trimZero(d: Double): String =
        if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

    private fun formatDelta(d: Double): String {
        val sign = if (d >= 0) "+" else ""
        val s = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
        return "$sign$s kg"
    }
}
```

- [ ] **Step 4: Run the test, expect pass**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat :app:testDebugUnitTest --tests "com.ditrain.app.ui.session.SessionPrescriptionTest"
```
Expected: `BUILD SUCCESSFUL` and 9 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/session/SessionPrescription.kt app/src/test/java/com/ditrain/app/ui/session/SessionPrescriptionTest.kt
git commit -m "feat(session): add SessionPrescription resolver for set prefill"
```

---

## Task 3: Add `SessionState` — in-memory holder for the in-progress session

**Why:** A single mutable container for the in-progress `SessionLog` plus pager cursor. `SessionViewController` reads and writes through it; `MainActivity` persists snapshots after every mutation. Pure data + small mutators, fully unit-testable.

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/session/SessionState.kt`
- Test: `app/src/test/java/com/ditrain/app/ui/session/SessionStateTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/ditrain/app/ui/session/SessionStateTest.kt`:

```kotlin
package com.ditrain.app.ui.session

import com.ditrain.app.model.ExecutedExercise
import com.ditrain.app.model.LoggedSet
import com.ditrain.app.model.MiniSet
import com.ditrain.app.model.SessionLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateTest {

    private fun emptyLog(numBlocks: Int): SessionLog = SessionLog(
        id = "s1",
        routineId = "r1",
        weekIndex = 0,
        sessionTemplateId = "push-a",
        scheduledDate = "2026-06-02",
        performedDate = "2026-06-02",
        startedAt = "2026-06-02T10:00:00Z",
        completedAt = null,
        executed = List(numBlocks) { ExecutedExercise(exerciseId = "ex-$it", sets = emptyList()) },
        cardioExecuted = emptyList(),
    )

    @Test
    fun `appendStraightSet adds a logged set to the current strength block`() {
        val state = SessionState(emptyLog(numBlocks = 2))
        state.cursor = 0

        val newSet = LoggedSet.Straight(
            weightKg = 100.0,
            reps = 5,
            performedAt = "2026-06-02T10:05:00Z",
            rpe = 8.0,
        )
        state.appendSetToCurrentBlock(newSet)

        assertEquals(1, state.log.executed[0].sets.size)
        assertEquals(newSet, state.log.executed[0].sets[0])
        assertEquals(0, state.log.executed[1].sets.size)
    }

    @Test
    fun `appendMyoRepSet adds a myo-rep set to the current block`() {
        val state = SessionState(emptyLog(numBlocks = 1))
        state.cursor = 0
        val myo = LoggedSet.MyoRep(
            weightKg = 60.0,
            activationReps = 15,
            performedAt = "2026-06-02T10:10:00Z",
            miniSets = listOf(MiniSet(reps = 5), MiniSet(reps = 4)),
        )
        state.appendSetToCurrentBlock(myo)
        assertEquals(1, state.log.executed[0].sets.size)
        assertEquals(myo, state.log.executed[0].sets[0])
    }

    @Test
    fun `skipCurrentBlock marks the executed exercise as skipped and clears sets`() {
        val state = SessionState(emptyLog(numBlocks = 2))
        state.cursor = 1
        // Pre-populate with a set to verify it gets cleared
        state.appendSetToCurrentBlock(LoggedSet.Straight(
            weightKg = 50.0, reps = 10, performedAt = "2026-06-02T10:15:00Z"))
        state.skipCurrentBlock()
        assertTrue(state.log.executed[1].skipped)
        assertTrue(state.log.executed[1].sets.isEmpty())
        // Other blocks untouched
        assertFalse(state.log.executed[0].skipped)
    }

    @Test
    fun `previousLoggedStraightSet returns the most recent straight set in the current block`() {
        val state = SessionState(emptyLog(numBlocks = 1))
        state.cursor = 0
        val s1 = LoggedSet.Straight(weightKg = 60.0, reps = 8, performedAt = "2026-06-02T10:01:00Z")
        val s2 = LoggedSet.Straight(weightKg = 65.0, reps = 6, performedAt = "2026-06-02T10:04:00Z")
        state.appendSetToCurrentBlock(s1)
        state.appendSetToCurrentBlock(s2)
        assertEquals(s2, state.previousLoggedSetInCurrentBlock())
    }

    @Test
    fun `isStrengthBlockComplete is true when sets meet prescribed count`() {
        val state = SessionState(emptyLog(numBlocks = 1))
        state.cursor = 0
        assertFalse(state.isStrengthBlockComplete(prescribedSetCount = 3))
        state.appendSetToCurrentBlock(LoggedSet.Straight(60.0, 8, "2026-06-02T10:01:00Z"))
        state.appendSetToCurrentBlock(LoggedSet.Straight(60.0, 8, "2026-06-02T10:04:00Z"))
        assertFalse(state.isStrengthBlockComplete(prescribedSetCount = 3))
        state.appendSetToCurrentBlock(LoggedSet.Straight(60.0, 8, "2026-06-02T10:07:00Z"))
        assertTrue(state.isStrengthBlockComplete(prescribedSetCount = 3))
    }

    @Test
    fun `isStrengthBlockComplete also returns true for skipped blocks`() {
        val state = SessionState(emptyLog(numBlocks = 1))
        state.cursor = 0
        state.skipCurrentBlock()
        assertTrue(state.isStrengthBlockComplete(prescribedSetCount = 3))
    }

    @Test
    fun `markCompleted stamps completedAt and is idempotent in-memory`() {
        val state = SessionState(emptyLog(numBlocks = 1))
        state.markCompleted("2026-06-02T11:00:00Z")
        assertEquals("2026-06-02T11:00:00Z", state.log.completedAt)
        // No exception on re-mark (caller's responsibility)
        state.markCompleted("2026-06-02T11:05:00Z")
        assertEquals("2026-06-02T11:05:00Z", state.log.completedAt)
    }
}
```

- [ ] **Step 2: Run the test, expect failure**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat :app:testDebugUnitTest --tests "com.ditrain.app.ui.session.SessionStateTest"
```
Expected: `unresolved reference: SessionState`.

- [ ] **Step 3: Implement `SessionState.kt`**

Create `app/src/main/java/com/ditrain/app/ui/session/SessionState.kt`:

```kotlin
package com.ditrain.app.ui.session

import com.ditrain.app.model.ExecutedExercise
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
```

- [ ] **Step 4: Run the test, expect pass**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat :app:testDebugUnitTest --tests "com.ditrain.app.ui.session.SessionStateTest"
```
Expected: `BUILD SUCCESSFUL` and 7 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/session/SessionState.kt app/src/test/java/com/ditrain/app/ui/session/SessionStateTest.kt
git commit -m "feat(session): add SessionState holder for in-progress session log"
```

---

## Task 4: Add `RestTimerController` with a `FakeClock` test seam

**Why:** The rest timer is the single most behavior-rich piece of this milestone (spec §6.6). It must be unit-testable without a real device, which means the time source has to be injectable. The controller stores all bookkeeping; the view in Task 5 is a thin reader.

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/session/RestTimerController.kt`
- Test: `app/src/test/java/com/ditrain/app/ui/session/RestTimerControllerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/ditrain/app/ui/session/RestTimerControllerTest.kt`:

```kotlin
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
}
```

- [ ] **Step 2: Run the test, expect failure**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat :app:testDebugUnitTest --tests "com.ditrain.app.ui.session.RestTimerControllerTest"
```
Expected: `unresolved reference: RestTimerController`.

- [ ] **Step 3: Implement `RestTimerController.kt`**

Create `app/src/main/java/com/ditrain/app/ui/session/RestTimerController.kt`:

```kotlin
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
        val started = startedAtElapsed ?: return
        if (pausedRemainingMs != null) return         // already paused
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
```

- [ ] **Step 4: Run the test, expect pass**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat :app:testDebugUnitTest --tests "com.ditrain.app.ui.session.RestTimerControllerTest"
```
Expected: `BUILD SUCCESSFUL` and 10 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/session/RestTimerController.kt app/src/test/java/com/ditrain/app/ui/session/RestTimerControllerTest.kt
git commit -m "feat(session): add RestTimerController with injectable clock"
```

---

## Task 5: Add `RestTimerView` — bottom-strip view bound to the controller

**Why:** Renders the controller's state. Polls via a `Handler` on the main thread; the polling is owned by the view so it's automatic when the view is attached.

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/session/RestTimerView.kt`

No new tests — pure View glue.

- [ ] **Step 1: Implement `RestTimerView.kt`**

Create `app/src/main/java/com/ditrain/app/ui/session/RestTimerView.kt`:

```kotlin
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
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
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
```

- [ ] **Step 2: Build to verify it compiles**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/session/RestTimerView.kt
git commit -m "feat(session): add RestTimerView bound to RestTimerController"
```

---

## Task 6: Add `SetEntryView` — active-set input row

**Why:** The active set row. Renders two layouts (straight vs. myo-rep activation/mini-set). On Log, it returns a fully-built `LoggedSet` via callback; `SessionViewController` appends it to state and triggers the timer.

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/session/SetEntryView.kt`

No new tests — view glue. The prefill logic is already covered by `SessionPrescriptionTest`.

- [ ] **Step 1: Implement `SetEntryView.kt`**

Create `app/src/main/java/com/ditrain/app/ui/session/SetEntryView.kt`:

```kotlin
package com.ditrain.app.ui.session

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.ditrain.app.model.LoggedSet
import com.ditrain.app.model.MiniSet
import com.ditrain.app.model.SetPrescription
import com.ditrain.app.model.WeightUnit
import com.ditrain.app.model.kgToLb
import com.ditrain.app.model.lbToKg
import com.ditrain.app.ui.ViewStyling
import com.ditrain.app.util.InstantIso

/**
 * Active-set input row. Two layouts depending on the prescription type:
 *  - Straight: weight | reps | rpe | (note) | Log
 *  - Myo-rep activation: same as straight; on Log, the row transitions to a sequence
 *    of mini-set rows (reps | rpe | Log mini · End cluster).
 *
 * The view stays fully reusable: the parent rebuilds it once per "next set to log,"
 * passing a fresh prescription resolution.
 *
 * Internal weights are kg. When the display unit is LB, the field shows lb and the
 * onLog conversion happens here.
 */
class SetEntryView(
    context: Context,
    private val dp: (Int) -> Int,
    private val unit: WeightUnit,
    private val nowIso: () -> InstantIso,
) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        background = ViewStyling.roundedBackground("#111827", "#3B82F6", dp(2), dp(20).toFloat())
        setPadding(dp(14), dp(14), dp(14), dp(14))
        layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
    }

    fun renderStraight(
        prescription: SetPrescription.Straight,
        resolved: SessionPrescription.Resolved,
        onLog: (LoggedSet.Straight) -> Unit,
    ) {
        removeAllViews()

        addView(headerText("Set #${childCount + 1}: " + resolved.repsHint +
                (resolved.weightHint?.let { " · $it" } ?: "")))

        val weightInput = numberInput(hint = unit.label).apply {
            resolved.suggestedWeightKg?.let { kg ->
                setText(unit.formatForInput(kg))
            }
        }
        val repsInput = numberInput(hint = "reps").apply {
            // Pre-fill reps when prescription is a single fixed number
            if (resolved.repsHint.toIntOrNull() != null) setText(resolved.repsHint)
        }
        val rpeInput = numberInput(hint = "RPE").apply {
            prescription.rpeTarget?.let { setText(trimZero(it)) }
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val noteInput = EditText(context).apply {
            hint = "note (optional)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
        }

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(weightInput, LayoutParams(0, WRAP_CONTENT, 1.3f).apply { marginEnd = dp(4) })
            addView(repsInput, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
            addView(rpeInput, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        }
        addView(row)
        addView(noteInput, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        val logBtn = ViewStyling.actionButton(
            context, "Log set", "#2563EB", compact = false, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        )
        logBtn.setOnClickListener {
            val parsedKg = parseWeightToKg(weightInput.text.toString()) ?: return@setOnClickListener toast("Weight required")
            val parsedReps = repsInput.text.toString().toIntOrNull() ?: return@setOnClickListener toast("Reps required")
            if (parsedReps <= 0) return@setOnClickListener toast("Reps must be positive")
            val rpe = rpeInput.text.toString().toDoubleOrNull()
            onLog(LoggedSet.Straight(
                weightKg = parsedKg,
                reps = parsedReps,
                performedAt = nowIso(),
                rpe = rpe,
                notes = noteInput.text.toString().ifBlank { null },
            ))
        }
        addView(logBtn, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
    }

    /**
     * Renders the myo-rep activation row (first stage). When activation is logged,
     * call [renderMyoRepMiniSet] with each subsequent mini-set, then [renderMyoRepClosing]
     * to attach the accumulated mini-sets onto a single [LoggedSet.MyoRep].
     */
    fun renderMyoRepActivation(
        prescription: SetPrescription.MyoRep,
        resolved: SessionPrescription.Resolved,
        onActivationLogged: (weightKg: Double, activationReps: Int, activationRpe: Double?) -> Unit,
    ) {
        removeAllViews()

        addView(headerText("Myo-rep activation: " + resolved.repsHint +
                (resolved.weightHint?.let { " · $it" } ?: "")))

        val weightInput = numberInput(hint = unit.label).apply {
            resolved.suggestedWeightKg?.let { setText(unit.formatForInput(it)) }
        }
        val repsInput = numberInput(hint = "reps").apply {
            if (resolved.repsHint.toIntOrNull() != null) setText(resolved.repsHint)
        }
        val rpeInput = numberInput(hint = "RPE")

        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(weightInput, LayoutParams(0, WRAP_CONTENT, 1.3f).apply { marginEnd = dp(4) })
            addView(repsInput, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
            addView(rpeInput, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        })

        val logBtn = ViewStyling.actionButton(
            context, "Log activation", "#2563EB", compact = false, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        )
        logBtn.setOnClickListener {
            val parsedKg = parseWeightToKg(weightInput.text.toString()) ?: return@setOnClickListener toast("Weight required")
            val parsedReps = repsInput.text.toString().toIntOrNull() ?: return@setOnClickListener toast("Reps required")
            val rpe = rpeInput.text.toString().toDoubleOrNull()
            onActivationLogged(parsedKg, parsedReps, rpe)
        }
        addView(logBtn, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
    }

    fun renderMyoRepMiniSet(
        miniIndex: Int,
        targetReps: Int,
        onLogged: (MiniSet) -> Unit,
        onEndCluster: () -> Unit,
    ) {
        removeAllViews()

        addView(headerText("Mini-set #$miniIndex (target $targetReps reps)"))

        val repsInput = numberInput(hint = "reps").apply { setText(targetReps.toString()) }
        val rpeInput = numberInput(hint = "RPE")

        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(repsInput, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
            addView(rpeInput, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        })

        val logBtn = ViewStyling.actionButton(
            context, "Log mini", "#2563EB", compact = false, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        )
        logBtn.setOnClickListener {
            val parsedReps = repsInput.text.toString().toIntOrNull() ?: return@setOnClickListener toast("Reps required")
            onLogged(MiniSet(reps = parsedReps, rpe = rpeInput.text.toString().toDoubleOrNull()))
        }
        val endBtn = ViewStyling.actionButton(
            context, "End cluster", "#475569", compact = false, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        )
        endBtn.setOnClickListener { onEndCluster() }

        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(logBtn, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
            addView(endBtn, LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        }, LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
    }

    private fun headerText(text: String) = TextView(context).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.parseColor("#CBD5E1"))
        setPadding(0, 0, 0, dp(8))
    }

    private fun numberInput(hint: String): EditText = EditText(context).apply {
        this.hint = hint
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#94A3B8"))
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    private fun parseWeightToKg(text: String): Double? {
        val d = text.toDoubleOrNull() ?: return null
        if (d <= 0.0) return null
        return when (unit) {
            WeightUnit.KG -> d
            WeightUnit.LB -> d.lbToKg()
        }
    }

    private fun trimZero(d: Double): String =
        if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

    private fun toast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/session/SetEntryView.kt
git commit -m "feat(session): add SetEntryView with straight and myo-rep variants"
```

---

## Task 7: Add `StrengthBlockView` — header + logged sets list + active row

**Why:** The visible body of a strength block: the exercise name, a list of already-logged sets in compact form, and the active `SetEntryView` underneath, plus an "Add set" / "Skip exercise" affordance pair. This is what each pager page renders.

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/session/StrengthBlockView.kt`

- [ ] **Step 1: Implement `StrengthBlockView.kt`**

Create `app/src/main/java/com/ditrain/app/ui/session/StrengthBlockView.kt`:

```kotlin
package com.ditrain.app.ui.session

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ditrain.app.model.ExecutedExercise
import com.ditrain.app.model.ExerciseBlock
import com.ditrain.app.model.LoggedSet
import com.ditrain.app.model.MiniSet
import com.ditrain.app.model.SetPrescription
import com.ditrain.app.model.WeightUnit
import com.ditrain.app.model.kgToLb
import com.ditrain.app.storage.ExerciseCatalog
import com.ditrain.app.ui.ViewStyling
import com.ditrain.app.util.InstantIso

/**
 * Renders one strength block: exercise name, prescription summary, list of
 * already-logged sets (compact), then the active SetEntryView, then the
 * "Add set" / "Skip exercise" actions.
 *
 * The active row is created for the next prescribed set (1-indexed). When the
 * prescribed set count has been logged, the active row becomes an "extra set"
 * row driven by the last prescription as a template; the user can stop by
 * tapping the next block.
 */
class StrengthBlockView(
    context: Context,
    private val catalog: ExerciseCatalog,
    private val dp: (Int) -> Int,
    private val unit: WeightUnit,
    private val nowIso: () -> InstantIso,
    private val resolveLastTopWeightKg: (exerciseId: String) -> Double?,
    private val resolveLatestE1rmKg: (exerciseId: String) -> Double?,
    private val onLogged: (LoggedSet) -> Unit,
    private val onSkip: () -> Unit,
) : ScrollView(context) {

    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
    }

    init {
        addView(column, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    fun bind(block: ExerciseBlock, executed: ExecutedExercise) {
        column.removeAllViews()

        val exercise = catalog.byId(block.exerciseId)
        val displayName = exercise?.name ?: "(unknown: ${block.exerciseId})"

        column.addView(TextView(context).apply {
            text = displayName
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })

        column.addView(TextView(context).apply {
            text = summarizePrescription(block)
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(4), 0, dp(4))
        })
        block.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            column.addView(TextView(context).apply {
                text = "notes: $notes"
                textSize = 13f
                setTextColor(Color.parseColor("#FDE68A"))
                setPadding(0, 0, 0, dp(8))
            })
        }

        if (executed.skipped) {
            column.addView(TextView(context).apply {
                text = "(skipped)"
                textSize = 16f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, dp(16), 0, dp(8))
            })
            return
        }

        executed.sets.forEachIndexed { idx, s ->
            column.addView(loggedSetRow(idx + 1, s))
        }

        val activeIdx = executed.sets.size
        val prescription = block.sets.getOrNull(activeIdx) ?: block.sets.lastOrNull()
        if (prescription != null) {
            val resolved = SessionPrescription.resolve(
                prescription = prescription,
                latestE1rmKg = resolveLatestE1rmKg(block.exerciseId),
                lastTopWeightKg = resolveLastTopWeightKg(block.exerciseId),
            )
            val entry = SetEntryView(context, dp, unit, nowIso)
            when (prescription) {
                is SetPrescription.Straight -> entry.renderStraight(
                    prescription = prescription,
                    resolved = resolved,
                    onLog = { logged -> onLogged(logged) },
                )
                is SetPrescription.MyoRep -> renderMyoRepFlow(entry, prescription, resolved)
            }
            column.addView(entry, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
        }

        column.addView(skipButton(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
    }

    private fun renderMyoRepFlow(
        entry: SetEntryView,
        prescription: SetPrescription.MyoRep,
        resolved: SessionPrescription.Resolved,
    ) {
        var activationWeightKg = 0.0
        var activationReps = 0
        var activationRpe: Double? = null
        val miniSets = mutableListOf<MiniSet>()

        fun finalize() {
            onLogged(LoggedSet.MyoRep(
                weightKg = activationWeightKg,
                activationReps = activationReps,
                activationRpe = activationRpe,
                miniSets = miniSets.toList(),
                performedAt = nowIso(),
            ))
        }

        fun renderNextMini() {
            val nextIdx = miniSets.size + 1
            if (nextIdx > prescription.miniSetCount) {
                finalize()
                return
            }
            entry.renderMyoRepMiniSet(
                miniIndex = nextIdx,
                targetReps = prescription.miniSetTargetReps,
                onLogged = { mini ->
                    miniSets.add(mini)
                    if (miniSets.size >= prescription.miniSetCount) finalize() else renderNextMini()
                },
                onEndCluster = { finalize() },
            )
        }

        entry.renderMyoRepActivation(
            prescription = prescription,
            resolved = resolved,
            onActivationLogged = { wKg, r, rpe ->
                activationWeightKg = wKg
                activationReps = r
                activationRpe = rpe
                renderNextMini()
            },
        )
    }

    private fun loggedSetRow(setNumber: Int, s: LoggedSet): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        background = ViewStyling.roundedBackground("#0F172A", "#1F2937", dp(1), dp(14).toFloat())
        setPadding(dp(12), dp(8), dp(12), dp(8))
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
        layoutParams = lp

        val summary = when (s) {
            is LoggedSet.Straight -> "Set $setNumber · ${formatKg(s.weightKg)} × ${s.reps}" +
                    (s.rpe?.let { " @ ${trimZero(it)}" } ?: "")
            is LoggedSet.MyoRep -> {
                val miniSum = s.miniSets.joinToString("+") { it.reps.toString() }
                "Set $setNumber · myo ${formatKg(s.weightKg)} · act ${s.activationReps}" +
                    (if (miniSum.isNotEmpty()) " · mini $miniSum" else "")
            }
        }
        addView(TextView(context).apply {
            text = summary
            textSize = 14f
            setTextColor(Color.WHITE)
        })
    }

    private fun skipButton(): TextView = TextView(context).apply {
        text = "⤵ Skip exercise"
        textSize = 14f
        setTextColor(Color.parseColor("#FCA5A5"))
        setPadding(dp(10), dp(10), dp(10), dp(10))
        background = ViewStyling.roundedBackground("#111827", "#7F1D1D", dp(1), dp(999).toFloat())
        isClickable = true
        setOnClickListener { onSkip() }
    }

    private fun summarizePrescription(block: ExerciseBlock): String {
        val count = block.sets.size
        val first = block.sets.firstOrNull()
        return when (first) {
            null -> "no sets prescribed"
            is SetPrescription.Straight -> "${count}×${SessionPrescription.resolve(first, null, null).repsHint}" +
                (block.sets.firstOrNull()?.rest?.let { " · ${it}s rest" } ?: "")
            is SetPrescription.MyoRep -> "$count cluster(s) · myo-rep"
        }
    }

    private fun formatKg(kg: Double): String = when (unit) {
        WeightUnit.KG -> if (kg % 1.0 == 0.0) "${kg.toInt()} kg" else "$kg kg"
        WeightUnit.LB -> {
            val lb = kg.kgToLb()
            "${lb.toInt()} lb"
        }
    }

    private fun trimZero(d: Double): String =
        if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
}
```

- [ ] **Step 2: Build to verify it compiles**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/session/StrengthBlockView.kt
git commit -m "feat(session): add StrengthBlockView rendering one block end-to-end"
```

---

## Task 8: Add `CardioBlockPlaceholderView` — Plan 4 stub

**Why:** Routines may already contain `cardioBlocks` (the bundled `fullbody-cardio-2x2.json` does). The pager has to render them without crashing, but real cardio logging is Plan 4. Render a clear stub and treat the block as auto-skipped for Finish detection.

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/session/CardioBlockPlaceholderView.kt`

- [ ] **Step 1: Implement `CardioBlockPlaceholderView.kt`**

```kotlin
package com.ditrain.app.ui.session

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ditrain.app.model.CardioBlock
import com.ditrain.app.ui.ViewStyling

/**
 * Read-only placeholder for cardio blocks in Plan 3. Cardio logging UI lands
 * in Plan 4; until then, blocks render their prescription and a note. They do
 * not block Finish.
 */
class CardioBlockPlaceholderView(
    context: Context,
    private val dp: (Int) -> Int,
) : ScrollView(context) {

    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
    }

    init { addView(column, LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }

    fun bind(block: CardioBlock) {
        column.removeAllViews()
        column.addView(TextView(context).apply {
            text = "Cardio · ${block.activityKind.name.lowercase()}"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        val descriptors = buildList {
            block.description?.takeIf { it.isNotBlank() }?.let { add(it) }
            block.targetDurationMin?.let { add("target ${it} min") }
            block.targetAvgBpm?.let { add("target ${it} bpm") }
        }
        if (descriptors.isNotEmpty()) {
            column.addView(TextView(context).apply {
                text = descriptors.joinToString(" · ")
                textSize = 13f
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, dp(4), 0, 0)
            })
        }
        column.addView(TextView(context).apply {
            text = "Cardio logging arrives in the next update. This block does not block Finish."
            textSize = 13f
            setTextColor(Color.parseColor("#FDE68A"))
            setPadding(0, dp(14), 0, 0)
            background = ViewStyling.roundedBackground("#111827", "#475569", dp(1), dp(14).toFloat())
            setPadding(dp(12), dp(12), dp(12), dp(12))
        })
    }
}
```

- [ ] **Step 2: Build, commit**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/ditrain/app/ui/session/CardioBlockPlaceholderView.kt
git commit -m "feat(session): add CardioBlockPlaceholderView stub until Plan 4"
```

---

## Task 9: Add `BlockPagerView` — prev/next page swap

**Why:** A minimal pager: shows one block-view at a time, with `◄` / `►` arrows in a small top strip and a dot row showing progress. No animations, no `ViewPager2` dependency — DiRead-style "just swap the child."

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/session/BlockPagerView.kt`

- [ ] **Step 1: Implement `BlockPagerView.kt`**

```kotlin
package com.ditrain.app.ui.session

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ditrain.app.ui.ViewStyling

/**
 * Vertical layout:
 *   - top strip: ◄  · · ● · ·  ►  (clickable arrows + small dot per page)
 *   - body: a FrameLayout that hosts exactly one child View at a time
 *
 * The parent rebuilds the body via [setPage] when the cursor changes. The pager
 * does not own block data — it only orchestrates which child View is visible.
 */
class BlockPagerView(
    context: Context,
    private val dp: (Int) -> Int,
    private val pageCount: Int,
    private val onPrev: () -> Unit,
    private val onNext: () -> Unit,
) : LinearLayout(context) {

    private val dots: LinearLayout
    private val body: FrameLayout
    private val prevBtn: TextView
    private val nextBtn: TextView

    init {
        orientation = VERTICAL
        background = Color.parseColor("#000000").let { setBackgroundColor(it); null }

        val topStrip = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        prevBtn = arrowButton("◄") { onPrev() }
        nextBtn = arrowButton("►") { onNext() }
        dots = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(0, WRAP_CONTENT, 1f)
        }
        topStrip.addView(prevBtn)
        topStrip.addView(dots)
        topStrip.addView(nextBtn)

        addView(topStrip)

        body = FrameLayout(context)
        addView(body, LayoutParams(MATCH_PARENT, 0, 1f))
    }

    private fun arrowButton(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 22f
        setTextColor(Color.WHITE)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        isClickable = true
        setOnClickListener { onClick() }
    }

    fun setPage(index: Int, child: View) {
        body.removeAllViews()
        body.addView(child, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        renderDots(index)
        prevBtn.alpha = if (index <= 0) 0.3f else 1.0f
        nextBtn.alpha = if (index >= pageCount - 1) 0.3f else 1.0f
    }

    private fun renderDots(activeIndex: Int) {
        dots.removeAllViews()
        for (i in 0 until pageCount) {
            val dot = View(context).apply {
                background = ViewStyling.roundedBackground(
                    if (i == activeIndex) "#FDE68A" else "#334155",
                    if (i == activeIndex) "#FDE68A" else "#334155",
                    dp(1), dp(999).toFloat(),
                )
            }
            dots.addView(dot, LayoutParams(dp(8), dp(8)).apply {
                marginStart = dp(4); marginEnd = dp(4)
            })
        }
    }
}
```

- [ ] **Step 2: Build, commit**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/ditrain/app/ui/session/BlockPagerView.kt
git commit -m "feat(session): add BlockPagerView for prev-next block navigation"
```

---

## Task 10: Add `AbortConfirmDialogController` — ✕ handler

**Why:** A focused dialog for the ✕ button in the session top-bar. Three actions: Save in-progress, Discard, Cancel. Mirrors the spec §6.3 wording exactly.

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/session/AbortConfirmDialogController.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.ditrain.app.ui.session

import android.content.Context
import androidx.appcompat.app.AlertDialog

class AbortConfirmDialogController(
    private val context: Context,
    private val onSaveInProgress: () -> Unit,
    private val onDiscard: () -> Unit,
) {
    fun show() {
        AlertDialog.Builder(context)
            .setTitle("Stop session?")
            .setMessage(
                "Save in progress: keep what you've logged so far; Home will offer Resume.\n\n" +
                "Discard: delete this session log entirely. Logged sets are lost.\n\n" +
                "Cancel: keep going."
            )
            .setPositiveButton("Save in progress") { _, _ -> onSaveInProgress() }
            .setNeutralButton("Cancel", null)
            .setNegativeButton("Discard") { _, _ -> onDiscard() }
            .show()
    }
}
```

- [ ] **Step 2: Build, commit**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/ditrain/app/ui/session/AbortConfirmDialogController.kt
git commit -m "feat(session): add AbortConfirmDialogController for X handler"
```

---

## Task 11: Add `SessionViewController` — wires everything together

**Why:** The top-level session screen. Owns the pager, the block views, the SetEntryView state machine, the rest timer view, and the top bar (back arrow / title / clock / ✕). Reads/writes through `SessionState`. Calls back to the activity on every mutation for persistence.

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/session/SessionViewController.kt`

- [ ] **Step 1: Implement**

Create `app/src/main/java/com/ditrain/app/ui/session/SessionViewController.kt`:

```kotlin
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

        // Bottom strip (rest timer or finish button) lives in its own slot
        // so we can swap it freely.
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
        if (state.cursor > 0) {
            state.cursor -= 1
            renderCurrentPage()
            refreshBottomStrip()
        } else if (state.cursor == 0 && cardioPageIndex() != null && cardioPageIndex()!! > 0) {
            // already on first strength block; no-op
        }
    }

    private fun goNext() {
        val total = template.blocks.size + template.cardioBlocks.size
        // cursor in 0..blocks.size-1 -> strength; blocks.size..total-1 -> cardio
        // We keep one cursor for strength (SessionState.cursor) and an internal one for cardio.
        if (currentPageIsStrength()) {
            if (state.cursor < template.blocks.size - 1) {
                state.cursor += 1
                renderCurrentPage(); refreshBottomStrip(); return
            }
            // jump to first cardio page if any
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
    private fun cardioPageIndex(): Int? = if (cardioCursor >= 0) cardioCursor else null

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
        // Capture restSec from previous set's performedAt to this one's, before appending
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
        // Re-render to show the newly-logged row and rebuild the active row for the next set
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
        // Find the next prescribed set's rest, falling back to the first set of the
        // next strength block, then to 0 (count-up only).
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
        } else {
            // Cardio placeholder: bottom strip is empty
        }
    }

    private fun formatElapsed(d: Duration): String {
        val s = d.seconds.coerceAtLeast(0)
        return "%02d:%02d".format(s / 60, s % 60)
    }
}
```

- [ ] **Step 2: Build, commit**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/ditrain/app/ui/session/SessionViewController.kt
git commit -m "feat(session): add SessionViewController coordinating pager, blocks, rest timer"
```

---

## Task 12: Update `HomeViewController` to expose Start/Resume button

**Why:** Home currently shows only a static "active routine" card. To enter the session screen, the user needs a tappable affordance. Resume vs. Start differs by whether an in-progress `SessionLog` exists for today's `ScheduledSession`.

**Files:**
- Modify: `app/src/main/java/com/ditrain/app/ui/home/HomeViewController.kt`

- [ ] **Step 1: Replace the file content with**

```kotlin
package com.ditrain.app.ui.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import com.ditrain.app.model.LoopMode
import com.ditrain.app.model.Routine
import com.ditrain.app.ui.ViewStyling
import com.ditrain.app.util.LocalDateIso

/**
 * Home view.
 *  - No active routine -> Welcome + Import CTA.
 *  - Active routine, no session today -> Active-routine card with "no session today" note.
 *  - Active routine, session today not started -> "Start session" button.
 *  - Active routine, session today in progress -> "Resume session" button.
 */
class HomeViewController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val onMenuClick: () -> Unit,
    private val onImportNow: () -> Unit,
    private val onStartOrResumeSession: () -> Unit,
) {

    data class TodayCard(
        val sessionTemplateName: String,
        val weekLabel: String,
        val scheduledDate: LocalDateIso,
        val resuming: Boolean,
    )

    fun buildView(activeRoutine: Routine?, todayCard: TodayCard?): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))

        addView(titleBar())
        addView(divider())

        if (activeRoutine == null) {
            addView(noRoutineCard())
        } else if (todayCard != null) {
            addView(todayCardView(activeRoutine, todayCard))
        } else {
            addView(activeRoutineRestCard(activeRoutine))
        }
    }

    private fun titleBar() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            text = "DiTrain"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        addView(TextView(context).apply {
            text = "⋮"
            textSize = 28f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(4), dp(16), dp(4))
            isClickable = true
            setOnClickListener { onMenuClick() }
        })
    }

    private fun divider() = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply {
            topMargin = dp(12); bottomMargin = dp(12)
        }
        setBackgroundColor(Color.parseColor("#334155"))
    }

    private fun noRoutineCard() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(20).toFloat())
        setPadding(dp(16), dp(16), dp(16), dp(16))
        addView(TextView(context).apply {
            text = "Welcome to DiTrain"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        addView(TextView(context).apply {
            text = "Import a routine to get started. You can paste JSON, pick a file, or try a bundled example."
            textSize = 14f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(8), 0, dp(14))
        })
        addView(ViewStyling.actionButton(
            context, "Import a routine", "#2563EB", compact = false, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        ).apply {
            setOnClickListener { onImportNow() }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun todayCardView(routine: Routine, today: TodayCard) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = ViewStyling.roundedBackground("#111827", "#3B82F6", dp(2), dp(20).toFloat())
        setPadding(dp(16), dp(16), dp(16), dp(16))

        addView(TextView(context).apply {
            text = "TODAY · ${today.scheduledDate}"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
        })
        addView(TextView(context).apply {
            text = today.sessionTemplateName
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(2), 0, 0)
        })
        addView(TextView(context).apply {
            text = "${today.weekLabel} · ${routine.name}"
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(4), 0, dp(14))
        })
        val btn = ViewStyling.actionButton(
            context,
            if (today.resuming) "Resume session" else "Start session",
            if (today.resuming) "#F59E0B" else "#2563EB",
            compact = false, dp = dp,
            roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
        )
        btn.setOnClickListener { onStartOrResumeSession() }
        addView(btn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun activeRoutineRestCard(routine: Routine) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(20).toFloat())
        setPadding(dp(16), dp(16), dp(16), dp(16))
        addView(TextView(context).apply {
            text = "Active routine"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
        })
        addView(TextView(context).apply {
            text = routine.name
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(2), 0, 0)
        })
        addView(TextView(context).apply {
            text = "${routine.weeks.size} week${if (routine.weeks.size == 1) "" else "s"} · " +
                    (if (routine.loopMode == LoopMode.REPEAT) "repeats indefinitely" else "runs once")
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(4), 0, dp(10))
        })
        addView(TextView(context).apply {
            text = "No session scheduled today. Tap ⋮ → Routines for the active routine, or pick another day on the calendar (coming in Plan 5)."
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
        })
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat assembleDebug
```
Expected: build fails — `MainActivity.kt` constructs `HomeViewController` with the old signature. That's fine; Task 13 fixes it. If the build only fails inside `MainActivity.kt`, proceed to Task 13.

If the build fails for **any other** reason, stop and report.

- [ ] **Step 3: Commit (build broken — Task 13 will fix `MainActivity`)**

```bash
git add app/src/main/java/com/ditrain/app/ui/home/HomeViewController.kt
git commit -m "feat(home): add today-session card and Start/Resume button"
```

> Note: this commit intentionally leaves the build broken — the next commit (Task 13) is the matching change in `MainActivity.kt`. The two land back-to-back; no other commits in between.

---

## Task 13: Wire `MainActivity.kt` — Start session, persist, finish, abort

**Why:** The activity is the glue. It computes "today's `ScheduledSession`", builds the `SessionLog`, swaps the content view to the `SessionViewController`'s root, persists snapshots on every mutation, and handles Finish/Abort.

**Files:**
- Modify: `app/src/main/java/com/ditrain/app/MainActivity.kt`

- [ ] **Step 1: Edit `MainActivity` — additions only**

In `MainActivity.kt`, make the following changes (preserving everything else verbatim):

**A. Add a `sessionLogRepo` field next to the existing repo fields:**

Replace:
```kotlin
    private lateinit var routineRepo: RoutineRepository
    private lateinit var appStateRepo: AppStateRepository
    private lateinit var catalog: ExerciseCatalog
```

with:
```kotlin
    private lateinit var routineRepo: RoutineRepository
    private lateinit var appStateRepo: AppStateRepository
    private lateinit var sessionLogRepo: com.ditrain.app.storage.SessionLogRepository
    private lateinit var catalog: ExerciseCatalog
```

**B. Initialize `sessionLogRepo` in `onCreate`.**

Replace:
```kotlin
        routineRepo = RoutineRepository(filesDir)
        appStateRepo = AppStateRepository(filesDir)
        catalog = ExerciseCatalog.fromAssets(
```

with:
```kotlin
        routineRepo = RoutineRepository(filesDir)
        appStateRepo = AppStateRepository(filesDir)
        sessionLogRepo = com.ditrain.app.storage.SessionLogRepository(filesDir)
        catalog = ExerciseCatalog.fromAssets(
```

**C. Update the `HomeViewController` constructor call to include `onStartOrResumeSession` and switch `renderHome()` to compute a `TodayCard`.**

Replace:
```kotlin
        home = HomeViewController(
            context = this,
            dp = dp,
            onMenuClick = { openMainMenu() },
            onImportNow = { openImport() },
        )
```

with:
```kotlin
        home = HomeViewController(
            context = this,
            dp = dp,
            onMenuClick = { openMainMenu() },
            onImportNow = { openImport() },
            onStartOrResumeSession = { openSession() },
        )
```

**D. Replace `renderHome()` body with one that computes `TodayCard`:**

Replace:
```kotlin
    private fun renderHome() {
        rootContainer.removeAllViews()
        rootContainer.addView(home.buildView(activeRoutine))
    }
```

with:
```kotlin
    private fun renderHome() {
        rootContainer.removeAllViews()
        val today = computeTodayCard()
        rootContainer.addView(home.buildView(activeRoutine, today))
    }

    private fun computeTodayCard(): com.ditrain.app.ui.home.HomeViewController.TodayCard? {
        val routine = activeRoutine ?: return null
        val todayIso = java.time.LocalDate.now().toString()
        // Earliest scheduled session with date <= today and no completed sessionLog yet.
        val candidate = appState.scheduledSessions
            .filter { it.date <= todayIso }
            .minByOrNull { it.date } ?: return null

        val week = routine.weeks.getOrNull(candidate.weekIndex) ?: return null
        val template = week.sessions.firstOrNull { it.id == candidate.sessionTemplateId } ?: return null
        val resuming = candidate.sessionLogId != null
        return com.ditrain.app.ui.home.HomeViewController.TodayCard(
            sessionTemplateName = template.name,
            weekLabel = week.label,
            scheduledDate = candidate.date,
            resuming = resuming,
        )
    }
```

**E. Add the `openSession()` flow as a new section, after the existing `// ───────────── Activation flow ─────────────` block. Append this block at the bottom of the class (above `// ───────────── Utilities ─────────────`):**

```kotlin
    // ───────────── Session execution flow ─────────────

    private var sessionController: com.ditrain.app.ui.session.SessionViewController? = null

    private fun openSession() = lifecycleScope.launch {
        val routine = activeRoutine ?: return@launch
        val todayIso = java.time.LocalDate.now().toString()
        val scheduled = appState.scheduledSessions
            .filter { it.date <= todayIso }
            .minByOrNull { it.date } ?: return@launch
        val week = routine.weeks.getOrNull(scheduled.weekIndex) ?: return@launch
        val template = week.sessions.firstOrNull { it.id == scheduled.sessionTemplateId } ?: return@launch

        val existingLogId = scheduled.sessionLogId
        val initialLog = if (existingLogId != null) {
            sessionLogRepo.loadAll().firstOrNull { it.id == existingLogId }
                ?: buildFreshSessionLog(routine, scheduled, template)
        } else {
            buildFreshSessionLog(routine, scheduled, template)
        }

        // If this is a fresh start, persist sessionLogId on the ScheduledSession
        // before any set is logged so crashes mid-session can be recovered.
        if (existingLogId == null) {
            val updatedScheduled = appState.scheduledSessions.map {
                if (it === scheduled) it.copy(sessionLogId = initialLog.id) else it
            }
            appState = appState.copy(scheduledSessions = updatedScheduled)
            appStateRepo.save(appState)
            sessionLogRepo.upsert(initialLog)
        }

        val state = com.ditrain.app.ui.session.SessionState(initialLog)
        val controller = com.ditrain.app.ui.session.SessionViewController(
            context = this@MainActivity,
            catalog = catalog,
            dp = dp,
            settings = appState.settings,
            nowIso = { java.time.Instant.now().toString() },
            resolveLastTopWeightKg = { exerciseId -> lastTopWeightKg(exerciseId) },
            resolveLatestE1rmKg = { exerciseId -> latestE1rmKg(exerciseId) },
            onMutated = { lifecycleScope.launch { sessionLogRepo.upsert(state.log) } },
            onAbortSaveInProgress = { closeSession() },
            onAbortDiscard = { lifecycleScope.launch { discardSession(initialLog.id, scheduled.sessionLogId == null || scheduled.sessionLogId == initialLog.id) } },
            onFinish = { lifecycleScope.launch { finishSession(state) } },
        )
        sessionController = controller
        rootContainer.removeAllViews()
        rootContainer.addView(controller.buildView(routine, template, state))
    }

    private fun buildFreshSessionLog(
        routine: com.ditrain.app.model.Routine,
        scheduled: com.ditrain.app.model.ScheduledSession,
        template: com.ditrain.app.model.SessionTemplate,
    ): com.ditrain.app.model.SessionLog {
        val nowIso = java.time.Instant.now().toString()
        return com.ditrain.app.model.SessionLog(
            id = java.util.UUID.randomUUID().toString(),
            routineId = routine.id,
            weekIndex = scheduled.weekIndex,
            sessionTemplateId = template.id,
            scheduledDate = scheduled.date,
            performedDate = java.time.LocalDate.now().toString(),
            startedAt = nowIso,
            completedAt = null,
            executed = template.blocks.map {
                com.ditrain.app.model.ExecutedExercise(exerciseId = it.exerciseId)
            },
            cardioExecuted = emptyList(),
        )
    }

    private fun closeSession() {
        sessionController = null
        renderHome()
    }

    private suspend fun discardSession(sessionId: String, clearSchedule: Boolean) {
        sessionLogRepo.delete(sessionId)
        if (clearSchedule) {
            val updated = appState.scheduledSessions.map {
                if (it.sessionLogId == sessionId) it.copy(sessionLogId = null) else it
            }
            appState = appState.copy(scheduledSessions = updated)
            appStateRepo.save(appState)
        }
        closeSession()
    }

    private suspend fun finishSession(state: com.ditrain.app.ui.session.SessionState) {
        state.markCompleted(java.time.Instant.now().toString())
        sessionLogRepo.upsert(state.log)
        // Drop the just-completed ScheduledSession (we keep history in sessions.json).
        val finishedLogId = state.log.id
        val updated = appState.scheduledSessions.filterNot { it.sessionLogId == finishedLogId }
        appState = appState.copy(scheduledSessions = updated)
        appStateRepo.save(appState)
        closeSession()
        toast("Session finished. ${state.log.executed.sumOf { it.sets.size }} sets logged.")
    }

    // History resolvers for set-row prefill.
    private var sessionHistoryCache: List<com.ditrain.app.model.SessionLog>? = null

    private fun lastTopWeightKg(exerciseId: String): Double? {
        val logs = sessionHistoryCacheOrLoad()
        return logs
            .sortedByDescending { it.performedDate }
            .flatMap { it.executed }
            .firstOrNull { it.exerciseId == exerciseId && !it.skipped && it.sets.isNotEmpty() }
            ?.sets
            ?.maxOfOrNull { s ->
                when (s) {
                    is com.ditrain.app.model.LoggedSet.Straight -> s.weightKg
                    is com.ditrain.app.model.LoggedSet.MyoRep -> s.weightKg
                }
            }
    }

    private fun latestE1rmKg(exerciseId: String): Double? {
        val logs = sessionHistoryCacheOrLoad()
        val candidates = logs
            .flatMap { it.executed }
            .filter { it.exerciseId == exerciseId && !it.skipped }
            .flatMap { it.sets }
            .mapNotNull { s ->
                when (s) {
                    is com.ditrain.app.model.LoggedSet.Straight ->
                        com.ditrain.app.progression.E1rm.estimate(s.weightKg, s.reps, s.rpe)
                    is com.ditrain.app.model.LoggedSet.MyoRep ->
                        com.ditrain.app.progression.E1rm.estimate(s.weightKg, s.activationReps, s.activationRpe)
                }
            }
        return candidates.maxOrNull()
    }

    private fun sessionHistoryCacheOrLoad(): List<com.ditrain.app.model.SessionLog> {
        sessionHistoryCache?.let { return it }
        // Synchronous-from-UI-thread is fine here because the cache is built once
        // per session-screen open; UI thread blocking on a small JSON read is
        // measured in milliseconds. Subsequent set-row renders read the cache.
        val logs = kotlinx.coroutines.runBlocking { sessionLogRepo.loadAll() }
        sessionHistoryCache = logs
        return logs
    }
```

**F. Invalidate the history cache when the session screen opens** — add this single line at the top of `openSession()`'s body (right after the `lifecycleScope.launch {` opening brace):

```kotlin
        sessionHistoryCache = null
```

So the start of `openSession()` reads:
```kotlin
    private fun openSession() = lifecycleScope.launch {
        sessionHistoryCache = null
        val routine = activeRoutine ?: return@launch
```

- [ ] **Step 2: Build to verify it compiles**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run the existing test suite to confirm nothing broke**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat :app:testDebugUnitTest
```
Expected: all existing tests pass (Plan 1+2+2.5 plus the new tests from this plan: `E1rmTest`, `SessionPrescriptionTest`, `SessionStateTest`, `RestTimerControllerTest`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ditrain/app/MainActivity.kt
git commit -m "feat(app): wire session execution flow into MainActivity"
```

---

## Task 14: Manual on-device smoke + final verification

**Why:** UI dialogs and the rest timer can't be exercised by JVM tests. A short device walkthrough is the acceptance gate before tagging.

- [ ] **Step 1: Build the debug APK and install**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat :app:installDebug
```
Expected: APK installs on the device.

- [ ] **Step 2: Manual smoke test on device**

Walk through these scenarios; mark each as it passes. If any fails, stop and document the failure in the commit-pending step (Step 4). Otherwise proceed.

1. **Cold start, no active routine:** Home shows Welcome card. Tap "Import a routine" → bundled examples available.
2. **Activate a routine** (e.g., "Simple A/B Template"). Pick Mon/Wed weekly pattern starting today.
3. **Home now shows TODAY card** with "Start session" button.
4. **Tap "Start session":**
   - Title bar shows session name + routine name + elapsed clock + ✕.
   - Pager shows first strength block. Exercise name, prescription summary, prefilled active row.
5. **Log a straight set** with weight + reps: row appears in the "logged sets" list above; new active row shows next set; rest timer starts counting at the bottom.
6. **Wait for rest timer to cross target:** the timer color shifts (amber → green) and a single haptic tick fires.
7. **Tap +30s / -30s:** target value visibly shifts.
8. **Tap timer:** pauses (display freezes). Tap again: resumes.
9. **Long-press timer:** elapsed resets to 0:00.
10. **Tap ►** to navigate to the next block. Tap **◄** to come back. The rest-timer counter keeps ticking in the background.
11. **Skip an exercise:** tap "Skip exercise" on a block. Block renders "(skipped)".
12. **Log a myo-rep set** if a block has one prescribed: activation flow → mini-set rows appear one at a time → "End cluster" finalizes.
13. **Finish:** once every strength block is logged or skipped, the bottom strip switches to a green "Finish session" button (cardio blocks are auto-eligible). Tap → toast "Session finished. N sets logged." Returns to Home with no Today card.
14. **Resume:** start another session, log a few sets, tap ✕ → "Save in progress". Home now shows "Resume session" (amber). Tap → returns to the same session with previously-logged sets present.
15. **Discard:** start a session, log one set, tap ✕ → "Discard". Home reverts; tapping Start a third time creates a fresh log.
16. **Cardio-bearing routine:** activate "Full-body + Cardio (2+2)". Cardio pages render the placeholder. Strength side still completes normally; Finish appears once strength is done.

- [ ] **Step 3: Run the full unit-test suite once more**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat :app:testDebugUnitTest
```
Expected: all green.

- [ ] **Step 4: Tag the milestone**

```bash
cd "C:/Users/Usuario/Documents/TrainBetter"
git tag v0.4.0-session
```

Report status to the user with: which device scenarios passed, which (if any) failed, and the new tag.

---

## Self-review checklist

Run through these before reporting Plan 3 complete:

1. **Spec coverage (§6.3, §6.6, §4.3):**
   - Start session picks earliest scheduled session ≤ today — Task 13 `computeTodayCard()` and `openSession()` use `minByOrNull { it.date }` on entries with `date <= today` ✓
   - `SessionLog` created in memory with `executed` placeholders per `ExerciseBlock` — Task 13 `buildFreshSessionLog` ✓
   - `ScheduledSession.sessionLogId` set immediately so crash mid-session can resume — Task 13 step E ✓
   - Strength logging: validate weight > 0 + reps > 0 — Task 6 `SetEntryView.renderStraight` `parseWeightToKg` and `toIntOrNull` ✓
   - Persist `SessionLog` on every Log — Task 13 `onMutated = { sessionLogRepo.upsert(...) }` ✓
   - Myo-rep collapses activation + mini-sets into one logged entry — Task 7 `renderMyoRepFlow` ✓
   - Skip exercise → `ExecutedExercise.skipped = true` — Task 3 `skipCurrentBlock`, Task 7 `onSkip` ✓
   - Rest timer auto-starts on Log — Task 11 `startRestTimerFor` ✓
   - Target seeded from next set's `rest`, with the documented fallback chain — Task 11 `startRestTimerFor` ✓
   - Past-zero continues counting, no auto-stop — Task 5 `render()` `"+m:ss over"` branch ✓
   - Single haptic tick at zero (not looping) — Task 5 `consumeCrossedEdge` + `vibrateShort` ✓
   - +30/−30 affects display only, not capture — Task 4 `adjustTarget` mutates `targetSec`, capture uses prior `performedAt` (Task 11 `computeRestSec`) ✓
   - `restSec` always derived from `performedAt` difference — Task 11 `computeRestSec` ✓
   - Mid-session reschedule disallowed — no reschedule entry point in session UI ✓
   - Abort: Save / Discard / Cancel — Task 10 `AbortConfirmDialogController` ✓
   - Finish appears once all strength blocks logged-or-skipped — Task 11 `refreshBottomStrip` using `isAllStrengthDone` ✓
   - Cardio blocks deferred (placeholder) — Task 8 ✓; auto-skipped for Finish — `isAllStrengthDone` only checks strength ✓
   - Vibrate permission already in manifest from Plan 1 — verified at v0.1.0 ✓
   - Internal weight is always kg; UI converts at the edge — Task 6 `parseWeightToKg` + `unit.formatForInput` ✓

2. **Placeholder scan:** no "TODO", "TBD", "add appropriate error handling," "similar to Task N" placeholders — all code blocks are complete. ✓

3. **Type consistency:**
   - `RestTimerController.Clock.elapsedRealtime(): Long` — matches `SystemClockSource` and `FakeClock` in tests ✓
   - `SessionState.cursor` (Int) referenced consistently in tests and `SessionViewController` ✓
   - `HomeViewController.TodayCard` field names match exactly between Task 12 definition and Task 13 `computeTodayCard()` ✓
   - `SessionViewController` constructor params match exactly between Task 11 declaration and Task 13 invocation (`context`, `catalog`, `dp`, `settings`, `nowIso`, `resolveLastTopWeightKg`, `resolveLatestE1rmKg`, `onMutated`, `onAbortSaveInProgress`, `onAbortDiscard`, `onFinish`) ✓
   - `SessionPrescription.Resolved` field names (`suggestedWeightKg`, `weightHint`, `repsHint`) match between Task 2 definition and Task 6 use ✓
   - `SessionState.previousLoggedSetAnywhere()` defined Task 3, used Task 11 ✓
   - `E1rm.estimate(weightKg, reps, rpe)` signature matches between Task 1 and Task 13's `latestE1rmKg` ✓

4. **Out-of-scope hygiene:** no exercise-picker substitution wiring; no calendar; no cardio logging; no PR/e1RM dialogs; no backup. Each is called out in the file structure section. ✓

End of self-review.
