# DiTrain v1 — Plan 2: Routine & exercise infrastructure

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make routines importable and viewable end-to-end. After this milestone, the user can: open the app, tap the overflow menu, import a routine via paste-JSON or pick-file (SAF) or pick-from-bundled-examples, see the routine's structure, activate it, browse the bundled exercise catalog, and inspect any exercise (open its description URL). The app's data model is fully populated even though session execution is still Plan 3.

**Architecture:**
- Pre-task: storage repos converted to `suspend` + `Dispatchers.IO` (Plan 1 shipped synchronous; this is the carry-over). All UI calls into storage from coroutine scopes on the Activity's `lifecycleScope`.
- Mirrors DiRead's dialog-controller pattern (each controller has `show()` and constructor-injected `context`, `dp`, `roundedBackground`, callbacks). One Activity, many controllers.
- `RoutineImporter` is a pure parser/validator with no Android imports — fully JVM-testable.
- The new `progression/ScheduleLayout.kt` (logic only) lays out `ScheduledSession`s when a routine is activated.

**Tech Stack:** Adds `kotlinx-coroutines-android` (1.9.0). Everything else from Plan 1 carries forward.

**Reference spec:** `docs/superpowers/specs/2026-05-15-ditrain-v1-design.md` — §4.2 Routine, §6.1 Import, §6.2 Activate, §5.3 Dialogs, §5.5 First-run.

**Reference plan:** This plan builds on `docs/superpowers/plans/2026-05-15-ditrain-v1-foundation.md` (tagged `v0.1.0-foundation`).

---

## Pre-flight assumptions

- Working directory: `C:\Users\Usuario\Documents\TrainBetter`
- Branch: `main` (user-consented to direct commits)
- Last commit at start: `b63ed21` (tag `v0.1.0-foundation`)
- Build env: `JAVA_HOME` and `ANDROID_HOME` set per Plan 1's README
- Bash tool is preferred for shell commands (no permission prompts). Gradle env inline: `JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" ./gradlew.bat ...`

---

## Conventions

- **Commit messages** follow Conventional Commits, single line, **no `Co-Authored-By:` or any other trailer** (user has explicitly forbidden it).
- **TDD cadence** for each test-bearing task: write failing test → run, see fail → implement → run, see pass → commit.
- **Verification** for UI tasks (where no test): `./gradlew.bat assembleDebug` succeeds.
- **No emojis in code or commit messages** unless the user explicitly asks.

---

## File Structure

Files this plan creates or modifies:

```
TrainBetter/
├── app/
│   ├── build.gradle.kts                         # MODIFY: add kotlinx-coroutines-android
│   └── src/
│       ├── main/
│       │   ├── assets/
│       │   │   ├── exercises.json               # EXPAND: ~45 entries
│       │   │   └── example_routines/
│       │   │       └── simple-ab-template.json  # CREATE
│       │   └── java/com/ditrain/app/
│       │       ├── MainActivity.kt              # MODIFY: wire ExerciseCatalog + AppState load, MainMenu invocation
│       │       ├── importing/
│       │       │   ├── ImportResult.kt          # CREATE: sealed outcome of RoutineImporter
│       │       │   └── RoutineImporter.kt       # CREATE: JSON -> Routine with validation
│       │       ├── progression/
│       │       │   └── ScheduleLayout.kt        # CREATE: lays out ScheduledSessions
│       │       ├── storage/
│       │       │   ├── RoutineRepository.kt     # MODIFY: suspend
│       │       │   ├── SessionLogRepository.kt  # MODIFY: suspend
│       │       │   ├── AppStateRepository.kt    # MODIFY: suspend
│       │       │   └── ExerciseCatalog.kt       # MODIFY: suspend on mutators
│       │       └── ui/
│       │           ├── ViewStyling.kt           # MODIFY: add chip() + dangerButton()
│       │           ├── home/
│       │           │   └── HomeViewController.kt  # MODIFY: add overflow icon + onMenuClick
│       │           └── dialog/
│       │               ├── MainMenuDialogController.kt
│       │               ├── RoutineListDialogController.kt
│       │               ├── RoutinePreviewDialogController.kt
│       │               ├── RoutineImportDialogController.kt
│       │               ├── ExercisePickerDialogController.kt
│       │               ├── ExerciseDetailDialogController.kt
│       │               └── WeeklyPatternDialogController.kt
│       └── test/java/com/ditrain/app/
│           ├── importing/
│           │   └── RoutineImporterTest.kt
│           ├── progression/
│           │   └── ScheduleLayoutTest.kt
│           └── storage/                          # MODIFY: tests wrap suspend calls in runBlocking
│               ├── RoutineRepositoryTest.kt
│               ├── SessionLogRepositoryTest.kt
│               ├── AppStateRepositoryTest.kt
│               └── ExerciseCatalogTest.kt
```

**Files deliberately deferred to later plans:**
- `ui/session/*` (Plan 3)
- `ui/dialog/SessionCalendarDialogController.kt`, `E1rmChartDialogController.kt`, `PrListDialogController.kt`, `ExerciseHistoryDialogController.kt` (Plan 5)
- `storage/BackupArchive.kt`, `ui/dialog/BackupDialogController.kt`, `ui/dialog/GlossaryDialogController.kt`, `ui/dialog/SettingsDialogController.kt`, `ui/dialog/AboutDialogController.kt`, `ui/dialog/AbortConfirmDialogController.kt` (Plan 6)
- Full 150-exercise catalog (Plan 6 — Plan 2 ships a curated ~45)

---

## Task 1: Convert storage repositories to `suspend` + `Dispatchers.IO`

**Why:** Plan 1's task code shipped synchronous storage; the spec (§2) and Plan 1's preamble called for `suspend` + `Dispatchers.IO`. Wiring UI to synchronous storage from the main thread would block UI. Fix before any UI lands.

**Files:**
- Modify: `app/build.gradle.kts` (add coroutines dep)
- Modify: `app/src/main/java/com/ditrain/app/storage/RoutineRepository.kt`
- Modify: `app/src/main/java/com/ditrain/app/storage/SessionLogRepository.kt`
- Modify: `app/src/main/java/com/ditrain/app/storage/AppStateRepository.kt`
- Modify: `app/src/main/java/com/ditrain/app/storage/ExerciseCatalog.kt` (mutators only — reads/byId stay sync since the data is in-memory)
- Modify: `app/src/test/java/com/ditrain/app/storage/RoutineRepositoryTest.kt`
- Modify: `app/src/test/java/com/ditrain/app/storage/SessionLogRepositoryTest.kt`
- Modify: `app/src/test/java/com/ditrain/app/storage/AppStateRepositoryTest.kt`
- Modify: `app/src/test/java/com/ditrain/app/storage/ExerciseCatalogTest.kt`

- [ ] **Step 1: Add coroutines dependency**

Edit `app/build.gradle.kts`. Add to the `dependencies { … }` block (preserving existing entries):

```kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
```

- [ ] **Step 2: Convert `RoutineRepository`**

Edit `app/src/main/java/com/ditrain/app/storage/RoutineRepository.kt`. Replace existing content with:

```kotlin
package com.ditrain.app.storage

import com.ditrain.app.model.Routine
import com.ditrain.app.util.AtomicWrite
import com.ditrain.app.util.JsonIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One JSON file per routine, under [filesDir]/routines/. Loads are forgiving:
 * a corrupt file returns null and is left in place for manual recovery
 * (the design spec §7 calls for not auto-deleting user data on parse failure).
 */
class RoutineRepository(filesDir: File) {

    private val dir: File = File(filesDir, "routines")

    suspend fun save(routine: Routine) = withContext(Dispatchers.IO) {
        val target = File(dir, "${routine.id}.json")
        AtomicWrite.writeText(target, JsonIo.json.encodeToString(Routine.serializer(), routine))
    }

    suspend fun load(id: String): Routine? = withContext(Dispatchers.IO) {
        val file = File(dir, "$id.json")
        if (!file.exists()) return@withContext null
        runCatching {
            JsonIo.json.decodeFromString(Routine.serializer(), file.readText())
        }.getOrNull()
    }

    suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        if (!dir.exists()) return@withContext emptyList()
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(dir, "$id.json")
        file.exists() && file.delete()
    }
}
```

- [ ] **Step 3: Convert `SessionLogRepository`**

Edit `app/src/main/java/com/ditrain/app/storage/SessionLogRepository.kt`. Add the imports and convert public functions (`append`, `upsert`, `delete`, `loadAll`, `loadByDateRange`) to `suspend` wrapped in `withContext(Dispatchers.IO)`. The private helpers (`readLive`, `writeLive`, `maybeRollover`, `quarantineLiveFile`, `readAllArchives`, `archiveFiles`, `decodeArchiveRange`) stay non-suspend — they're called from already-IO-dispatched code.

Replace the existing file with:

```kotlin
package com.ditrain.app.storage

import com.ditrain.app.model.SessionLog
import com.ditrain.app.util.AtomicWrite
import com.ditrain.app.util.JsonIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * Sessions persist to a single JSON array at filesDir/logs/sessions.json, sorted by
 * performedDate ascending. When sessions.json crosses [rolloverBytes], it's atomically
 * archived into logs/archive/sessions-{firstDate}_{lastDate}.json and a fresh empty
 * file replaces it.
 *
 * Reads are forgiving: a corrupt live file is renamed to sessions.corrupt.{ts}.json
 * and a fresh empty live file is created. Corrupt archive files are skipped (not
 * renamed — they may still be recoverable manually).
 */
class SessionLogRepository(
    filesDir: File,
    val rolloverBytes: Long = DEFAULT_ROLLOVER_BYTES,
) {
    private val logsDir = File(filesDir, "logs")
    private val liveFile = File(logsDir, "sessions.json")
    private val archiveDir = File(logsDir, "archive")

    private val listSerializer = ListSerializer(SessionLog.serializer())

    suspend fun append(log: SessionLog) = withContext(Dispatchers.IO) {
        val current = readLive().toMutableList()
        current.add(log)
        writeLive(current)
        maybeRollover()
    }

    suspend fun upsert(log: SessionLog) = withContext(Dispatchers.IO) {
        val current = readLive().toMutableList()
        val idx = current.indexOfFirst { it.id == log.id }
        if (idx >= 0) current[idx] = log else current.add(log)
        writeLive(current)
        maybeRollover()
    }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        val current = readLive().toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) writeLive(current)
        removed
    }

    suspend fun loadAll(): List<SessionLog> = withContext(Dispatchers.IO) {
        val all = mutableListOf<SessionLog>()
        all.addAll(readAllArchives())
        all.addAll(readLive())
        all.sortedBy { it.performedDate }
    }

    /** Returns logs whose performedDate is within [from..to] inclusive. */
    suspend fun loadByDateRange(from: String, to: String): List<SessionLog> = withContext(Dispatchers.IO) {
        val all = mutableListOf<SessionLog>()
        archiveFiles().forEach { f ->
            val (firstDate, lastDate) = decodeArchiveRange(f.name) ?: return@forEach
            if (lastDate < from || firstDate > to) return@forEach
            runCatching { JsonIo.json.decodeFromString(listSerializer, f.readText()) }
                .getOrNull()?.let { all.addAll(it.filter { l -> l.performedDate in from..to }) }
        }
        all.addAll(readLive().filter { it.performedDate in from..to })
        all.sortedBy { it.performedDate }
    }

    private fun readLive(): List<SessionLog> {
        if (!liveFile.exists()) return emptyList()
        val raw = liveFile.readText()
        return runCatching { JsonIo.json.decodeFromString(listSerializer, raw) }
            .getOrElse {
                quarantineLiveFile(raw)
                emptyList()
            }
    }

    private fun quarantineLiveFile(raw: String) {
        val ts = System.currentTimeMillis()
        val renamed = File(logsDir, "sessions.corrupt.${ts}.json")
        AtomicWrite.writeText(renamed, raw)
        if (!liveFile.delete()) {
            liveFile.renameTo(File(logsDir, "sessions.unremovable.${ts}.json"))
        }
    }

    private fun writeLive(list: List<SessionLog>) {
        val sorted = list.sortedBy { it.performedDate }
        AtomicWrite.writeText(liveFile, JsonIo.json.encodeToString(listSerializer, sorted))
    }

    private fun maybeRollover() {
        if (!liveFile.exists() || liveFile.length() <= rolloverBytes) return
        val live = readLive()
        if (live.size < 2) return
        val firstDate = live.first().performedDate
        val lastDate = live.last().performedDate
        val archiveFile = File(archiveDir, "sessions-${firstDate}_${lastDate}.json")
        AtomicWrite.writeText(archiveFile, JsonIo.json.encodeToString(listSerializer, live))
        AtomicWrite.writeText(liveFile, JsonIo.json.encodeToString(listSerializer, emptyList()))
    }

    private fun archiveFiles(): List<File> =
        archiveDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedBy { it.name } ?: emptyList()

    private fun readAllArchives(): List<SessionLog> =
        archiveFiles().flatMap { f ->
            runCatching { JsonIo.json.decodeFromString(listSerializer, f.readText()) }
                .getOrDefault(emptyList())
        }

    private fun decodeArchiveRange(name: String): Pair<String, String>? {
        val core = name.removePrefix("sessions-").removeSuffix(".json")
        val parts = core.split("_")
        return if (parts.size == 2) parts[0] to parts[1] else null
    }

    companion object {
        const val DEFAULT_ROLLOVER_BYTES: Long = 1L shl 20
    }
}
```

- [ ] **Step 4: Convert `AppStateRepository`**

Replace `app/src/main/java/com/ditrain/app/storage/AppStateRepository.kt`:

```kotlin
package com.ditrain.app.storage

import com.ditrain.app.model.AppState
import com.ditrain.app.model.Settings
import com.ditrain.app.util.AtomicWrite
import com.ditrain.app.util.JsonIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AppStateRepository(filesDir: File) {
    private val file = File(filesDir, "state.json")

    suspend fun load(): AppState = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext defaultState()
        runCatching {
            JsonIo.json.decodeFromString(AppState.serializer(), file.readText())
        }.getOrElse { defaultState() }
    }

    suspend fun save(state: AppState) = withContext(Dispatchers.IO) {
        AtomicWrite.writeText(file, JsonIo.json.encodeToString(AppState.serializer(), state))
    }

    private fun defaultState() = AppState(
        activeRoutineId = null,
        scheduledSessions = emptyList(),
        settings = Settings(),
    )
}
```

- [ ] **Step 5: Convert `ExerciseCatalog`** (mutators only)

The reads (`byId`, `visibleExercises`) operate on in-memory maps — keep them sync. `addCustom`, `softDelete`, `restore`, `hardDelete` write to disk — make them `suspend`. The companion `fromAssets` and `fromInMemory` stay sync constructors.

Replace `app/src/main/java/com/ditrain/app/storage/ExerciseCatalog.kt`:

```kotlin
package com.ditrain.app.storage

import com.ditrain.app.model.Exercise
import com.ditrain.app.util.AtomicWrite
import com.ditrain.app.util.JsonIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.InputStream

class ExerciseCatalog private constructor(
    private val bundled: Map<String, Exercise>,
    private val customsFile: File,
) {

    private val customs = mutableMapOf<String, Exercise>()

    init {
        if (customsFile.exists()) {
            runCatching {
                JsonIo.json.decodeFromString(ListSerializer(Exercise.serializer()), customsFile.readText())
            }.getOrNull()?.forEach { customs[it.id] = it }
        }
    }

    fun byId(id: String): Exercise? = customs[id] ?: bundled[id]

    fun visibleExercises(includeDeleted: Boolean = false): List<Exercise> {
        val all = bundled.values.toList() + customs.values
        return if (includeDeleted) all else all.filterNot { it.deleted }
    }

    suspend fun addCustom(ex: Exercise): Exercise = withContext(Dispatchers.IO) {
        require(ex.custom) { "addCustom requires Exercise.custom == true (id=${ex.id})" }
        customs[ex.id] = ex
        persistCustoms()
        ex
    }

    suspend fun softDelete(id: String): Boolean = withContext(Dispatchers.IO) {
        val existing = customs[id] ?: return@withContext false
        customs[id] = existing.copy(deleted = true)
        persistCustoms()
        true
    }

    suspend fun restore(id: String): Boolean = withContext(Dispatchers.IO) {
        val existing = customs[id] ?: return@withContext false
        customs[id] = existing.copy(deleted = false)
        persistCustoms()
        true
    }

    suspend fun hardDelete(id: String, isReferenced: (String) -> Boolean): Boolean = withContext(Dispatchers.IO) {
        if (id !in customs) return@withContext false
        if (isReferenced(id)) return@withContext false
        customs.remove(id)
        persistCustoms()
        true
    }

    private fun persistCustoms() {
        val list = customs.values.toList()
        AtomicWrite.writeText(
            customsFile,
            JsonIo.json.encodeToString(ListSerializer(Exercise.serializer()), list),
        )
    }

    companion object {
        fun fromAssets(bundledStream: InputStream, customsFile: File): ExerciseCatalog {
            val parsed = bundledStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val list = JsonIo.json.decodeFromString(ListSerializer(Exercise.serializer()), parsed)
            return ExerciseCatalog(list.associateBy { it.id }, customsFile)
        }

        fun fromInMemory(bundled: List<Exercise>, customsFile: File): ExerciseCatalog =
            ExerciseCatalog(bundled.associateBy { it.id }, customsFile)
    }
}
```

- [ ] **Step 6: Update storage tests to use `runBlocking`**

Each test file's test bodies need to wrap suspend calls. The simplest pattern (avoid `runTest` since the storage code doesn't use virtual time): add `import kotlinx.coroutines.runBlocking` and wrap every test body in `runBlocking { … }`.

Apply this transform to all four test files:
- `app/src/test/java/com/ditrain/app/storage/RoutineRepositoryTest.kt`
- `app/src/test/java/com/ditrain/app/storage/SessionLogRepositoryTest.kt`
- `app/src/test/java/com/ditrain/app/storage/AppStateRepositoryTest.kt`
- `app/src/test/java/com/ditrain/app/storage/ExerciseCatalogTest.kt`

Concrete example: this `RoutineRepositoryTest` test BEFORE:

```kotlin
@Test fun `save then load equals`() {
    val repo = repo()
    val r = sampleRoutine()
    repo.save(r)
    assertEquals(r, repo.load("r1"))
}
```

AFTER:

```kotlin
@Test fun `save then load equals`() = runBlocking {
    val repo = repo()
    val r = sampleRoutine()
    repo.save(r)
    assertEquals(r, repo.load("r1"))
}
```

Apply the same transform to every `@Test fun` in the four files. Make sure to add `import kotlinx.coroutines.runBlocking` to each test file.

- [ ] **Step 7: Run all unit tests**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
  ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" \
  ./gradlew.bat :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, 69 tests pass (same count as Plan 1).

- [ ] **Step 8: Build APK**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
  ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" \
  ./gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/build.gradle.kts \
        app/src/main/java/com/ditrain/app/storage/ \
        app/src/test/java/com/ditrain/app/storage/
git commit -m "refactor(storage): convert repos to suspend on Dispatchers.IO"
```

Single line, no trailer.

---

## Task 2: Expand bundled exercise catalog to ~45 entries

**Files:**
- Modify: `app/src/main/assets/exercises.json`

No tests for this task — `ExerciseCatalogTest.fromAssets` would exercise the asset loader, but the existing tests use `fromInMemory`. The acceptance is "valid JSON, parses via the existing catalog, app builds."

- [ ] **Step 1: Replace `app/src/main/assets/exercises.json`** with the full ~45-entry catalog. Write exactly:

```json
[
  {
    "id": "barbell-back-squat",
    "name": "Barbell Back Squat",
    "aliases": ["Back Squat", "Squat"],
    "pattern": "SQUAT",
    "primaryMuscles": ["QUADS", "GLUTES"],
    "secondaryMuscles": ["HAMSTRINGS", "LOWER_BACK"],
    "equipment": ["BARBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/Quadriceps/BBSquat"
  },
  {
    "id": "barbell-front-squat",
    "name": "Barbell Front Squat",
    "aliases": ["Front Squat"],
    "pattern": "SQUAT",
    "primaryMuscles": ["QUADS", "GLUTES"],
    "secondaryMuscles": ["UPPER_BACK"],
    "equipment": ["BARBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/Quadriceps/BBFrontSquat"
  },
  {
    "id": "bulgarian-split-squat",
    "name": "Bulgarian Split Squat",
    "aliases": ["BSS", "Rear-foot Elevated Split Squat"],
    "pattern": "LUNGE",
    "primaryMuscles": ["QUADS", "GLUTES"],
    "secondaryMuscles": ["HAMSTRINGS", "ADDUCTORS"],
    "equipment": ["DUMBBELL", "BARBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/Quadriceps/DBBulgarianSquat"
  },
  {
    "id": "goblet-squat",
    "name": "Goblet Squat",
    "aliases": ["DB Goblet Squat"],
    "pattern": "SQUAT",
    "primaryMuscles": ["QUADS", "GLUTES"],
    "secondaryMuscles": ["ADDUCTORS"],
    "equipment": ["DUMBBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/Quadriceps/DBGobletSquat"
  },
  {
    "id": "leg-press",
    "name": "Leg Press",
    "aliases": ["Machine Leg Press"],
    "pattern": "SQUAT",
    "primaryMuscles": ["QUADS", "GLUTES"],
    "secondaryMuscles": ["HAMSTRINGS"],
    "equipment": ["MACHINE"],
    "descriptionUrl": "https://exrx.net/WeightExercises/Quadriceps/LVLegPress"
  },
  {
    "id": "barbell-deadlift",
    "name": "Barbell Deadlift",
    "aliases": ["Conventional Deadlift"],
    "pattern": "HINGE",
    "primaryMuscles": ["HAMSTRINGS", "GLUTES", "LOWER_BACK"],
    "secondaryMuscles": ["UPPER_BACK", "FOREARMS"],
    "equipment": ["BARBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/ErectorSpinae/BBDeadlift"
  },
  {
    "id": "romanian-deadlift",
    "name": "Romanian Deadlift",
    "aliases": ["RDL"],
    "pattern": "HINGE",
    "primaryMuscles": ["HAMSTRINGS", "GLUTES"],
    "secondaryMuscles": ["LOWER_BACK", "FOREARMS"],
    "equipment": ["BARBELL", "DUMBBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/Hamstrings/BBStraightLegDeadlift"
  },
  {
    "id": "trap-bar-deadlift",
    "name": "Trap-bar Deadlift",
    "aliases": ["Hex-bar Deadlift"],
    "pattern": "HINGE",
    "primaryMuscles": ["QUADS", "GLUTES", "HAMSTRINGS"],
    "secondaryMuscles": ["UPPER_BACK", "FOREARMS"],
    "equipment": ["BARBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/ErectorSpinae/TBDeadlift"
  },
  {
    "id": "good-morning",
    "name": "Good Morning",
    "aliases": [],
    "pattern": "HINGE",
    "primaryMuscles": ["HAMSTRINGS", "LOWER_BACK"],
    "secondaryMuscles": ["GLUTES"],
    "equipment": ["BARBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/ErectorSpinae/BBGoodMorning"
  },
  {
    "id": "hip-thrust",
    "name": "Hip Thrust",
    "aliases": ["Barbell Hip Thrust"],
    "pattern": "HINGE",
    "primaryMuscles": ["GLUTES"],
    "secondaryMuscles": ["HAMSTRINGS"],
    "equipment": ["BARBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/Glutes/BBHipThrust"
  },
  {
    "id": "barbell-bench-press",
    "name": "Barbell Bench Press",
    "aliases": ["Bench"],
    "pattern": "HORIZONTAL_PUSH",
    "primaryMuscles": ["CHEST", "TRICEPS"],
    "secondaryMuscles": ["FRONT_DELT"],
    "equipment": ["BARBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/PectoralSternal/BBBenchPress"
  },
  {
    "id": "incline-barbell-bench",
    "name": "Incline Barbell Bench Press",
    "aliases": ["Incline Bench"],
    "pattern": "HORIZONTAL_PUSH",
    "primaryMuscles": ["CHEST", "FRONT_DELT"],
    "secondaryMuscles": ["TRICEPS"],
    "equipment": ["BARBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/PectoralClavicular/BBInclineBenchPress"
  },
  {
    "id": "dumbbell-bench-press",
    "name": "Dumbbell Bench Press",
    "aliases": ["DB Bench"],
    "pattern": "HORIZONTAL_PUSH",
    "primaryMuscles": ["CHEST", "TRICEPS"],
    "secondaryMuscles": ["FRONT_DELT"],
    "equipment": ["DUMBBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/PectoralSternal/DBBenchPress"
  },
  {
    "id": "push-up",
    "name": "Push-up",
    "aliases": [],
    "pattern": "HORIZONTAL_PUSH",
    "primaryMuscles": ["CHEST", "TRICEPS"],
    "secondaryMuscles": ["FRONT_DELT", "ABS"],
    "equipment": ["BODYWEIGHT"],
    "descriptionUrl": "https://exrx.net/WeightExercises/PectoralSternal/BWPushup"
  },
  {
    "id": "dumbbell-floor-press",
    "name": "Dumbbell Floor Press",
    "aliases": [],
    "pattern": "HORIZONTAL_PUSH",
    "primaryMuscles": ["CHEST", "TRICEPS"],
    "secondaryMuscles": ["FRONT_DELT"],
    "equipment": ["DUMBBELL"],
    "descriptionUrl": null
  },
  {
    "id": "barbell-overhead-press",
    "name": "Barbell Overhead Press",
    "aliases": ["OHP", "Strict Press", "Military Press"],
    "pattern": "VERTICAL_PUSH",
    "primaryMuscles": ["FRONT_DELT", "TRICEPS"],
    "secondaryMuscles": ["SIDE_DELT", "TRAPS"],
    "equipment": ["BARBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/DeltoidAnterior/BBMilitaryPress"
  },
  {
    "id": "dumbbell-shoulder-press",
    "name": "Dumbbell Shoulder Press",
    "aliases": ["DB Press"],
    "pattern": "VERTICAL_PUSH",
    "primaryMuscles": ["FRONT_DELT", "TRICEPS"],
    "secondaryMuscles": ["SIDE_DELT"],
    "equipment": ["DUMBBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/DeltoidAnterior/DBShoulderPress"
  },
  {
    "id": "landmine-press",
    "name": "Landmine Press",
    "aliases": [],
    "pattern": "VERTICAL_PUSH",
    "primaryMuscles": ["FRONT_DELT", "TRICEPS"],
    "secondaryMuscles": ["UPPER_BACK", "ABS"],
    "equipment": ["BARBELL"],
    "descriptionUrl": null
  },
  {
    "id": "pike-push-up",
    "name": "Pike Push-up",
    "aliases": [],
    "pattern": "VERTICAL_PUSH",
    "primaryMuscles": ["FRONT_DELT", "TRICEPS"],
    "secondaryMuscles": ["SIDE_DELT"],
    "equipment": ["BODYWEIGHT"],
    "descriptionUrl": null
  },
  {
    "id": "barbell-row",
    "name": "Barbell Row",
    "aliases": ["Bent-over Row"],
    "pattern": "HORIZONTAL_PULL",
    "primaryMuscles": ["UPPER_BACK", "LATS"],
    "secondaryMuscles": ["BICEPS", "REAR_DELT", "LOWER_BACK"],
    "equipment": ["BARBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/BackGeneral/BBBentOverRow"
  },
  {
    "id": "pendlay-row",
    "name": "Pendlay Row",
    "aliases": [],
    "pattern": "HORIZONTAL_PULL",
    "primaryMuscles": ["UPPER_BACK", "LATS"],
    "secondaryMuscles": ["BICEPS", "REAR_DELT"],
    "equipment": ["BARBELL"],
    "descriptionUrl": null
  },
  {
    "id": "dumbbell-row",
    "name": "Dumbbell Row",
    "aliases": ["One-arm DB Row"],
    "pattern": "HORIZONTAL_PULL",
    "primaryMuscles": ["LATS", "UPPER_BACK"],
    "secondaryMuscles": ["BICEPS", "REAR_DELT"],
    "equipment": ["DUMBBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/BackGeneral/DBBentOverRow"
  },
  {
    "id": "cable-row",
    "name": "Cable Row",
    "aliases": ["Seated Row"],
    "pattern": "HORIZONTAL_PULL",
    "primaryMuscles": ["UPPER_BACK", "LATS"],
    "secondaryMuscles": ["BICEPS", "REAR_DELT"],
    "equipment": ["CABLE", "MACHINE"],
    "descriptionUrl": "https://exrx.net/WeightExercises/BackGeneral/CBSeatedRow"
  },
  {
    "id": "inverted-row",
    "name": "Inverted Row",
    "aliases": ["Bodyweight Row"],
    "pattern": "HORIZONTAL_PULL",
    "primaryMuscles": ["UPPER_BACK", "LATS"],
    "secondaryMuscles": ["BICEPS", "REAR_DELT"],
    "equipment": ["BODYWEIGHT"],
    "descriptionUrl": null
  },
  {
    "id": "pull-up",
    "name": "Pull-up",
    "aliases": ["Overhand Chin-up"],
    "pattern": "VERTICAL_PULL",
    "primaryMuscles": ["LATS", "BICEPS"],
    "secondaryMuscles": ["UPPER_BACK", "FOREARMS"],
    "equipment": ["BODYWEIGHT"],
    "descriptionUrl": "https://exrx.net/WeightExercises/LatissimusDorsi/BWPullup"
  },
  {
    "id": "chin-up",
    "name": "Chin-up",
    "aliases": ["Underhand Pull-up"],
    "pattern": "VERTICAL_PULL",
    "primaryMuscles": ["LATS", "BICEPS"],
    "secondaryMuscles": ["UPPER_BACK", "FOREARMS"],
    "equipment": ["BODYWEIGHT"],
    "descriptionUrl": "https://exrx.net/WeightExercises/LatissimusDorsi/BWChinup"
  },
  {
    "id": "lat-pulldown",
    "name": "Lat Pulldown",
    "aliases": [],
    "pattern": "VERTICAL_PULL",
    "primaryMuscles": ["LATS", "BICEPS"],
    "secondaryMuscles": ["UPPER_BACK", "REAR_DELT"],
    "equipment": ["CABLE", "MACHINE"],
    "descriptionUrl": "https://exrx.net/WeightExercises/LatissimusDorsi/CBLatPulldown"
  },
  {
    "id": "walking-lunge",
    "name": "Walking Lunge",
    "aliases": [],
    "pattern": "LUNGE",
    "primaryMuscles": ["QUADS", "GLUTES"],
    "secondaryMuscles": ["HAMSTRINGS", "ADDUCTORS"],
    "equipment": ["DUMBBELL", "BARBELL", "BODYWEIGHT"],
    "descriptionUrl": "https://exrx.net/WeightExercises/Quadriceps/DBLunge"
  },
  {
    "id": "reverse-lunge",
    "name": "Reverse Lunge",
    "aliases": [],
    "pattern": "LUNGE",
    "primaryMuscles": ["QUADS", "GLUTES"],
    "secondaryMuscles": ["HAMSTRINGS"],
    "equipment": ["DUMBBELL", "BARBELL", "BODYWEIGHT"],
    "descriptionUrl": null
  },
  {
    "id": "step-up",
    "name": "Step-up",
    "aliases": [],
    "pattern": "LUNGE",
    "primaryMuscles": ["QUADS", "GLUTES"],
    "secondaryMuscles": ["HAMSTRINGS"],
    "equipment": ["DUMBBELL", "BODYWEIGHT"],
    "descriptionUrl": null
  },
  {
    "id": "farmers-carry",
    "name": "Farmer's Carry",
    "aliases": ["Farmer's Walk"],
    "pattern": "CARRY",
    "primaryMuscles": ["FOREARMS", "TRAPS"],
    "secondaryMuscles": ["UPPER_BACK", "ABS", "QUADS"],
    "equipment": ["DUMBBELL", "BARBELL"],
    "descriptionUrl": null
  },
  {
    "id": "suitcase-carry",
    "name": "Suitcase Carry",
    "aliases": [],
    "pattern": "CARRY",
    "primaryMuscles": ["OBLIQUES", "FOREARMS"],
    "secondaryMuscles": ["ABS", "UPPER_BACK"],
    "equipment": ["DUMBBELL"],
    "descriptionUrl": null
  },
  {
    "id": "plank",
    "name": "Plank",
    "aliases": ["Front Plank"],
    "pattern": "CORE",
    "primaryMuscles": ["ABS"],
    "secondaryMuscles": ["OBLIQUES", "LOWER_BACK"],
    "equipment": ["BODYWEIGHT"],
    "descriptionUrl": "https://exrx.net/WeightExercises/RectusAbdominis/BWFrontPlank"
  },
  {
    "id": "hanging-leg-raise",
    "name": "Hanging Leg Raise",
    "aliases": ["Knee Raise"],
    "pattern": "CORE",
    "primaryMuscles": ["ABS"],
    "secondaryMuscles": ["OBLIQUES", "FOREARMS"],
    "equipment": ["BODYWEIGHT"],
    "descriptionUrl": "https://exrx.net/WeightExercises/HipFlexors/BWHangingLegRaise"
  },
  {
    "id": "ab-wheel-rollout",
    "name": "Ab Wheel Rollout",
    "aliases": [],
    "pattern": "CORE",
    "primaryMuscles": ["ABS"],
    "secondaryMuscles": ["OBLIQUES", "LATS"],
    "equipment": ["OTHER"],
    "descriptionUrl": null
  },
  {
    "id": "pallof-press",
    "name": "Pallof Press",
    "aliases": [],
    "pattern": "CORE",
    "primaryMuscles": ["OBLIQUES", "ABS"],
    "secondaryMuscles": [],
    "equipment": ["CABLE", "BAND"],
    "descriptionUrl": null
  },
  {
    "id": "barbell-curl",
    "name": "Barbell Curl",
    "aliases": ["BB Curl"],
    "pattern": "ISOLATION",
    "primaryMuscles": ["BICEPS"],
    "secondaryMuscles": ["FOREARMS"],
    "equipment": ["BARBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/Biceps/BBCurl"
  },
  {
    "id": "dumbbell-curl",
    "name": "Dumbbell Curl",
    "aliases": ["DB Curl"],
    "pattern": "ISOLATION",
    "primaryMuscles": ["BICEPS"],
    "secondaryMuscles": ["FOREARMS"],
    "equipment": ["DUMBBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/Biceps/DBCurl"
  },
  {
    "id": "hammer-curl",
    "name": "Hammer Curl",
    "aliases": [],
    "pattern": "ISOLATION",
    "primaryMuscles": ["BICEPS", "FOREARMS"],
    "secondaryMuscles": [],
    "equipment": ["DUMBBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/Brachialis/DBHammerCurl"
  },
  {
    "id": "triceps-pushdown",
    "name": "Triceps Pushdown",
    "aliases": ["Cable Pushdown"],
    "pattern": "ISOLATION",
    "primaryMuscles": ["TRICEPS"],
    "secondaryMuscles": [],
    "equipment": ["CABLE"],
    "descriptionUrl": "https://exrx.net/WeightExercises/Triceps/CBPushdown"
  },
  {
    "id": "lateral-raise",
    "name": "Lateral Raise",
    "aliases": ["DB Side Raise"],
    "pattern": "ISOLATION",
    "primaryMuscles": ["SIDE_DELT"],
    "secondaryMuscles": ["FRONT_DELT", "TRAPS"],
    "equipment": ["DUMBBELL", "CABLE"],
    "descriptionUrl": "https://exrx.net/WeightExercises/DeltoidLateral/DBLateralRaise"
  },
  {
    "id": "calf-raise",
    "name": "Calf Raise",
    "aliases": ["Standing Calf Raise"],
    "pattern": "ISOLATION",
    "primaryMuscles": ["CALVES"],
    "secondaryMuscles": [],
    "equipment": ["MACHINE", "BARBELL", "DUMBBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/Calves/LVStandingCalfRaise"
  },
  {
    "id": "leg-curl",
    "name": "Leg Curl",
    "aliases": ["Hamstring Curl"],
    "pattern": "ISOLATION",
    "primaryMuscles": ["HAMSTRINGS"],
    "secondaryMuscles": [],
    "equipment": ["MACHINE"],
    "descriptionUrl": "https://exrx.net/WeightExercises/Hamstrings/LVLyingLegCurl"
  },
  {
    "id": "leg-extension",
    "name": "Leg Extension",
    "aliases": [],
    "pattern": "ISOLATION",
    "primaryMuscles": ["QUADS"],
    "secondaryMuscles": [],
    "equipment": ["MACHINE"],
    "descriptionUrl": "https://exrx.net/WeightExercises/Quadriceps/LVLegExtension"
  },
  {
    "id": "face-pull",
    "name": "Face Pull",
    "aliases": [],
    "pattern": "ISOLATION",
    "primaryMuscles": ["REAR_DELT", "UPPER_BACK"],
    "secondaryMuscles": ["TRAPS"],
    "equipment": ["CABLE", "BAND"],
    "descriptionUrl": null
  },
  {
    "id": "cable-fly",
    "name": "Cable Fly",
    "aliases": ["Cable Crossover"],
    "pattern": "ISOLATION",
    "primaryMuscles": ["CHEST"],
    "secondaryMuscles": ["FRONT_DELT"],
    "equipment": ["CABLE"],
    "descriptionUrl": "https://exrx.net/WeightExercises/PectoralSternal/CBStandingCableFly"
  },
  {
    "id": "reverse-pec-deck",
    "name": "Reverse Pec Deck",
    "aliases": ["Rear Delt Machine"],
    "pattern": "ISOLATION",
    "primaryMuscles": ["REAR_DELT"],
    "secondaryMuscles": ["UPPER_BACK"],
    "equipment": ["MACHINE"],
    "descriptionUrl": null
  }
]
```

- [ ] **Step 2: Verify JSON parses**

Run a quick verification that the app build still succeeds (proves no JSON syntax errors):

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
  ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" \
  ./gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/exercises.json
git commit -m "feat(catalog): expand bundled exercise catalog to 47 entries"
```

Single line, no trailer.

---

## Task 3: Bundled example routine

**Files:**
- Create: `app/src/main/assets/example_routines/simple-ab-template.json`

- [ ] **Step 1: Write the bundled example**

Create `app/src/main/assets/example_routines/simple-ab-template.json` with exactly:

```json
{
  "id": "simple-ab-template",
  "name": "Simple A/B Template",
  "description": "Two alternating full-body sessions with classic compound lifts. Repeats indefinitely until you switch routines.",
  "author": "DiTrain bundled examples",
  "schemaVersion": 1,
  "loopMode": "REPEAT",
  "defaultWeeklyPattern": [0, 2, 4],
  "weeks": [
    {
      "label": "Week 1",
      "sessions": [
        {
          "id": "day-a",
          "name": "Day A",
          "blocks": [
            {
              "exerciseId": "barbell-back-squat",
              "sets": [
                {
                  "type": "straight",
                  "reps": { "type": "fixed", "reps": 5 },
                  "load": { "type": "pct_1rm", "pct": 0.8 },
                  "rpeTarget": null,
                  "rest": 180,
                  "tempo": null,
                  "notes": null
                },
                {
                  "type": "straight",
                  "reps": { "type": "fixed", "reps": 5 },
                  "load": { "type": "pct_1rm", "pct": 0.8 },
                  "rpeTarget": null,
                  "rest": 180,
                  "tempo": null,
                  "notes": null
                },
                {
                  "type": "straight",
                  "reps": { "type": "fixed", "reps": 5 },
                  "load": { "type": "pct_1rm", "pct": 0.8 },
                  "rpeTarget": null,
                  "rest": 180,
                  "tempo": null,
                  "notes": null
                }
              ],
              "notes": null
            },
            {
              "exerciseId": "barbell-bench-press",
              "sets": [
                {
                  "type": "straight",
                  "reps": { "type": "fixed", "reps": 5 },
                  "load": { "type": "pct_1rm", "pct": 0.8 },
                  "rpeTarget": null,
                  "rest": 150,
                  "tempo": null,
                  "notes": null
                },
                {
                  "type": "straight",
                  "reps": { "type": "fixed", "reps": 5 },
                  "load": { "type": "pct_1rm", "pct": 0.8 },
                  "rpeTarget": null,
                  "rest": 150,
                  "tempo": null,
                  "notes": null
                },
                {
                  "type": "straight",
                  "reps": { "type": "fixed", "reps": 5 },
                  "load": { "type": "pct_1rm", "pct": 0.8 },
                  "rpeTarget": null,
                  "rest": 150,
                  "tempo": null,
                  "notes": null
                }
              ],
              "notes": null
            },
            {
              "exerciseId": "barbell-row",
              "sets": [
                {
                  "type": "straight",
                  "reps": { "type": "range", "min": 6, "max": 10 },
                  "load": { "type": "open" },
                  "rpeTarget": 8.0,
                  "rest": 120,
                  "tempo": null,
                  "notes": null
                },
                {
                  "type": "straight",
                  "reps": { "type": "range", "min": 6, "max": 10 },
                  "load": { "type": "open" },
                  "rpeTarget": 8.0,
                  "rest": 120,
                  "tempo": null,
                  "notes": null
                },
                {
                  "type": "straight",
                  "reps": { "type": "range", "min": 6, "max": 10 },
                  "load": { "type": "open" },
                  "rpeTarget": 8.0,
                  "rest": 120,
                  "tempo": null,
                  "notes": null
                }
              ],
              "notes": null
            }
          ],
          "cardioBlocks": []
        },
        {
          "id": "day-b",
          "name": "Day B",
          "blocks": [
            {
              "exerciseId": "barbell-deadlift",
              "sets": [
                {
                  "type": "straight",
                  "reps": { "type": "fixed", "reps": 3 },
                  "load": { "type": "pct_1rm", "pct": 0.85 },
                  "rpeTarget": null,
                  "rest": 240,
                  "tempo": null,
                  "notes": null
                },
                {
                  "type": "straight",
                  "reps": { "type": "fixed", "reps": 3 },
                  "load": { "type": "pct_1rm", "pct": 0.85 },
                  "rpeTarget": null,
                  "rest": 240,
                  "tempo": null,
                  "notes": null
                },
                {
                  "type": "straight",
                  "reps": { "type": "fixed", "reps": 3 },
                  "load": { "type": "pct_1rm", "pct": 0.85 },
                  "rpeTarget": null,
                  "rest": 240,
                  "tempo": null,
                  "notes": null
                }
              ],
              "notes": null
            },
            {
              "exerciseId": "barbell-overhead-press",
              "sets": [
                {
                  "type": "straight",
                  "reps": { "type": "fixed", "reps": 5 },
                  "load": { "type": "pct_1rm", "pct": 0.75 },
                  "rpeTarget": null,
                  "rest": 150,
                  "tempo": null,
                  "notes": null
                },
                {
                  "type": "straight",
                  "reps": { "type": "fixed", "reps": 5 },
                  "load": { "type": "pct_1rm", "pct": 0.75 },
                  "rpeTarget": null,
                  "rest": 150,
                  "tempo": null,
                  "notes": null
                },
                {
                  "type": "straight",
                  "reps": { "type": "fixed", "reps": 5 },
                  "load": { "type": "pct_1rm", "pct": 0.75 },
                  "rpeTarget": null,
                  "rest": 150,
                  "tempo": null,
                  "notes": null
                }
              ],
              "notes": null
            },
            {
              "exerciseId": "chin-up",
              "sets": [
                {
                  "type": "straight",
                  "reps": { "type": "amrap" },
                  "load": { "type": "open" },
                  "rpeTarget": null,
                  "rest": 120,
                  "tempo": null,
                  "notes": null
                },
                {
                  "type": "straight",
                  "reps": { "type": "amrap" },
                  "load": { "type": "open" },
                  "rpeTarget": null,
                  "rest": 120,
                  "tempo": null,
                  "notes": null
                },
                {
                  "type": "straight",
                  "reps": { "type": "amrap" },
                  "load": { "type": "open" },
                  "rpeTarget": null,
                  "rest": 120,
                  "tempo": null,
                  "notes": null
                }
              ],
              "notes": null
            }
          ],
          "cardioBlocks": []
        }
      ]
    }
  ]
}
```

- [ ] **Step 2: Verify the build still succeeds**

```bash
./gradlew.bat assembleDebug
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/example_routines/simple-ab-template.json
git commit -m "feat(catalog): add bundled Simple A/B Template example routine"
```

---

## Task 4: `importing/ImportResult.kt` — sealed import outcome

**Files:**
- Create: `app/src/main/java/com/ditrain/app/importing/ImportResult.kt`

No tests — this is a pure value type used by `RoutineImporter` (Task 5) whose tests cover the outcomes.

- [ ] **Step 1: Implement**

```kotlin
package com.ditrain.app.importing

import com.ditrain.app.model.Routine

/**
 * Outcome of [RoutineImporter.parse]. Either a valid [Success] holding the parsed
 * Routine, or a categorized failure the import dialog can render to the user.
 */
sealed interface ImportResult {

    data class Success(val routine: Routine) : ImportResult

    /** JSON failed to parse (bad syntax). */
    data class ParseError(val message: String) : ImportResult

    /** JSON parsed but contained unsupported schemaVersion. */
    data class UnsupportedSchemaVersion(val schemaVersion: Int) : ImportResult

    /** JSON parsed but routine references exerciseIds not present in the catalog. */
    data class MissingExerciseIds(val missingIds: List<String>) : ImportResult

    /** Structural validation failed (empty weeks, sessions with no blocks, OTHER cardio missing description). */
    data class InvalidStructure(val reason: String) : ImportResult
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew.bat :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/importing/ImportResult.kt
git commit -m "feat(importing): add ImportResult sealed outcome type"
```

---

## Task 5: `importing/RoutineImporter.kt` — JSON → Routine with validation

**Files:**
- Create: `app/src/main/java/com/ditrain/app/importing/RoutineImporter.kt`
- Create: `app/src/test/java/com/ditrain/app/importing/RoutineImporterTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/ditrain/app/importing/RoutineImporterTest.kt`:

```kotlin
package com.ditrain.app.importing

import com.ditrain.app.model.Equipment
import com.ditrain.app.model.Exercise
import com.ditrain.app.model.MovementPattern
import com.ditrain.app.model.MuscleGroup
import com.ditrain.app.storage.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RoutineImporterTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun catalog(extraIds: List<String> = emptyList()): ExerciseCatalog {
        val base = listOf(
            ex("squat", MovementPattern.SQUAT),
            ex("bench", MovementPattern.HORIZONTAL_PUSH),
            ex("row", MovementPattern.HORIZONTAL_PULL),
        ) + extraIds.map { ex(it, MovementPattern.ISOLATION) }
        return ExerciseCatalog.fromInMemory(base, customsFile = File(tmp.root, "c.json"))
    }

    private fun ex(id: String, pattern: MovementPattern) = Exercise(
        id = id, name = id, pattern = pattern,
        primaryMuscles = listOf(MuscleGroup.QUADS),
        equipment = listOf(Equipment.BARBELL),
        descriptionUrl = null,
    )

    private val minimalValid = """
        {
          "id": "r1",
          "name": "R1",
          "loopMode": "REPEAT",
          "schemaVersion": 1,
          "weeks": [
            {
              "label": "Week 1",
              "sessions": [
                {
                  "id": "d1",
                  "name": "Day 1",
                  "blocks": [
                    {
                      "exerciseId": "squat",
                      "sets": [
                        { "type": "straight", "reps": {"type":"fixed","reps":5}, "load": {"type":"open"} }
                      ]
                    }
                  ],
                  "cardioBlocks": []
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test fun `valid JSON returns Success with parsed routine`() {
        val r = RoutineImporter.parse(minimalValid, catalog())
        assertTrue(r is ImportResult.Success)
        assertEquals("r1", (r as ImportResult.Success).routine.id)
    }

    @Test fun `malformed JSON returns ParseError`() {
        val r = RoutineImporter.parse("{ bad", catalog())
        assertTrue("expected ParseError, got $r", r is ImportResult.ParseError)
    }

    @Test fun `wrong schemaVersion returns UnsupportedSchemaVersion`() {
        val r = RoutineImporter.parse(
            minimalValid.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99"),
            catalog(),
        )
        assertEquals(ImportResult.UnsupportedSchemaVersion(99), r)
    }

    @Test fun `missing exerciseId returns MissingExerciseIds with that id`() {
        val r = RoutineImporter.parse(
            minimalValid.replace("\"exerciseId\": \"squat\"", "\"exerciseId\": \"nonexistent\""),
            catalog(),
        )
        assertTrue(r is ImportResult.MissingExerciseIds)
        assertEquals(listOf("nonexistent"), (r as ImportResult.MissingExerciseIds).missingIds)
    }

    @Test fun `multiple missing exercise ids are all reported`() {
        val twoMissing = minimalValid
            .replace("\"exerciseId\": \"squat\"", "\"exerciseId\": \"unknown-a\"")
            .replace(
                "\"cardioBlocks\": []",
                """"cardioBlocks": [], "extraBlocks": []""".replace("extraBlocks", "blocksB")
            )
        // Simpler: directly inject a routine with two unknown blocks.
        val custom = """
            {
              "id": "r2", "name": "R2", "loopMode": "REPEAT", "schemaVersion": 1,
              "weeks": [{
                "label": "W1",
                "sessions": [{
                  "id": "d1", "name": "D1",
                  "blocks": [
                    {"exerciseId": "unknown-a", "sets": [{"type":"straight","reps":{"type":"fixed","reps":5},"load":{"type":"open"}}]},
                    {"exerciseId": "unknown-b", "sets": [{"type":"straight","reps":{"type":"fixed","reps":5},"load":{"type":"open"}}]}
                  ],
                  "cardioBlocks": []
                }]
              }]
            }
        """.trimIndent()
        val r = RoutineImporter.parse(custom, catalog())
        assertTrue(r is ImportResult.MissingExerciseIds)
        assertEquals(setOf("unknown-a", "unknown-b"), (r as ImportResult.MissingExerciseIds).missingIds.toSet())
    }

    @Test fun `empty weeks returns InvalidStructure`() {
        val empty = minimalValid.replace(
            """"weeks": [""",
            """"weeks_disabled_field": ["""
        ).replace("}\n        ]\n      }\n    ]", "}\n        ]\n      }\n    ],\n    \"weeks\": []")
        val r = RoutineImporter.parse(empty, catalog())
        assertTrue("expected InvalidStructure, got $r", r is ImportResult.InvalidStructure)
    }

    @Test fun `session with no blocks and no cardio is invalid`() {
        val noBlocks = """
            {
              "id": "r3", "name": "R3", "loopMode": "REPEAT", "schemaVersion": 1,
              "weeks": [{
                "label": "W1",
                "sessions": [{"id": "d1", "name": "D1", "blocks": [], "cardioBlocks": []}]
              }]
            }
        """.trimIndent()
        val r = RoutineImporter.parse(noBlocks, catalog())
        assertTrue(r is ImportResult.InvalidStructure)
    }

    @Test fun `cardio-only session is valid (no strength blocks needed)`() {
        val cardioOnly = """
            {
              "id": "r4", "name": "R4", "loopMode": "REPEAT", "schemaVersion": 1,
              "weeks": [{
                "label": "W1",
                "sessions": [{
                  "id": "d1", "name": "D1",
                  "blocks": [],
                  "cardioBlocks": [{"activityKind": "RUNNING", "targetDurationMin": 30}]
                }]
              }]
            }
        """.trimIndent()
        val r = RoutineImporter.parse(cardioOnly, catalog())
        assertTrue("expected Success, got $r", r is ImportResult.Success)
    }

    @Test fun `cardio block of kind OTHER without description is invalid`() {
        val badCardio = """
            {
              "id": "r5", "name": "R5", "loopMode": "REPEAT", "schemaVersion": 1,
              "weeks": [{
                "label": "W1",
                "sessions": [{
                  "id": "d1", "name": "D1",
                  "blocks": [],
                  "cardioBlocks": [{"activityKind": "OTHER", "targetDurationMin": 25}]
                }]
              }]
            }
        """.trimIndent()
        val r = RoutineImporter.parse(badCardio, catalog())
        assertTrue("expected InvalidStructure, got $r", r is ImportResult.InvalidStructure)
    }

    @Test fun `cardio OTHER with description is valid`() {
        val okCardio = """
            {
              "id": "r6", "name": "R6", "loopMode": "REPEAT", "schemaVersion": 1,
              "weeks": [{
                "label": "W1",
                "sessions": [{
                  "id": "d1", "name": "D1",
                  "blocks": [],
                  "cardioBlocks": [{"activityKind": "OTHER", "description": "Stair-master intervals", "targetDurationMin": 25}]
                }]
              }]
            }
        """.trimIndent()
        val r = RoutineImporter.parse(okCardio, catalog())
        assertTrue("expected Success, got $r", r is ImportResult.Success)
    }
}
```

Run, expect FAIL:
```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.ditrain.app.importing.RoutineImporterTest"
```

- [ ] **Step 2: Implement** at `app/src/main/java/com/ditrain/app/importing/RoutineImporter.kt`:

```kotlin
package com.ditrain.app.importing

import com.ditrain.app.model.CardioKind
import com.ditrain.app.model.Routine
import com.ditrain.app.storage.ExerciseCatalog
import com.ditrain.app.util.JsonIo
import kotlinx.serialization.SerializationException

/**
 * Parses a routine JSON string into a [Routine] and validates it against the catalog.
 * Pure JVM, no Android imports — fully testable as a unit.
 *
 * Validation order:
 *   1. JSON deserialization (kotlinx-serialization). Failure → [ImportResult.ParseError].
 *   2. schemaVersion == [SUPPORTED_SCHEMA_VERSION]. Otherwise → [ImportResult.UnsupportedSchemaVersion].
 *   3. Structural: every week has ≥1 session; every session has ≥1 strength block OR ≥1 cardio block.
 *      Cardio blocks of kind OTHER require a non-blank description.
 *      Failures → [ImportResult.InvalidStructure].
 *   4. Catalog: every `ExerciseBlock.exerciseId` resolves in the catalog (including soft-deleted entries).
 *      Failures → [ImportResult.MissingExerciseIds] with the full set of unknown ids.
 *
 * On success: [ImportResult.Success] with the parsed Routine.
 */
object RoutineImporter {

    const val SUPPORTED_SCHEMA_VERSION: Int = 1

    fun parse(json: String, catalog: ExerciseCatalog): ImportResult {
        val routine: Routine = try {
            JsonIo.json.decodeFromString(Routine.serializer(), json)
        } catch (e: SerializationException) {
            return ImportResult.ParseError(e.message ?: "couldn't parse JSON")
        } catch (e: IllegalArgumentException) {
            return ImportResult.ParseError(e.message ?: "couldn't parse JSON")
        }

        if (routine.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return ImportResult.UnsupportedSchemaVersion(routine.schemaVersion)
        }

        validateStructure(routine)?.let { return it }
        validateCatalog(routine, catalog)?.let { return it }

        return ImportResult.Success(routine)
    }

    private fun validateStructure(routine: Routine): ImportResult.InvalidStructure? {
        if (routine.weeks.isEmpty()) {
            return ImportResult.InvalidStructure("routine has no weeks")
        }
        routine.weeks.forEachIndexed { wIdx, week ->
            if (week.sessions.isEmpty()) {
                return ImportResult.InvalidStructure("week ${wIdx + 1} (${week.label}) has no sessions")
            }
            week.sessions.forEach { session ->
                if (session.blocks.isEmpty() && session.cardioBlocks.isEmpty()) {
                    return ImportResult.InvalidStructure(
                        "session ${session.id} (${session.name}) has neither strength blocks nor cardio blocks"
                    )
                }
                session.cardioBlocks.forEach { cb ->
                    if (cb.activityKind == CardioKind.OTHER && cb.description.isNullOrBlank()) {
                        return ImportResult.InvalidStructure(
                            "session ${session.id}: cardio block of kind OTHER requires a description"
                        )
                    }
                }
            }
        }
        return null
    }

    private fun validateCatalog(routine: Routine, catalog: ExerciseCatalog): ImportResult.MissingExerciseIds? {
        val missing = linkedSetOf<String>()
        for (week in routine.weeks) {
            for (session in week.sessions) {
                for (block in session.blocks) {
                    // catalog.byId resolves deleted entries too — references to soft-deleted
                    // customs are intentionally allowed (spec §6.1).
                    if (catalog.byId(block.exerciseId) == null) {
                        missing.add(block.exerciseId)
                    }
                }
            }
        }
        return if (missing.isEmpty()) null else ImportResult.MissingExerciseIds(missing.toList())
    }
}
```

- [ ] **Step 3: Run, expect PASS** (10 tests)

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.ditrain.app.importing.RoutineImporterTest"
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ditrain/app/importing/RoutineImporter.kt \
        app/src/test/java/com/ditrain/app/importing/RoutineImporterTest.kt
git commit -m "feat(importing): add RoutineImporter with schema + structure + catalog validation"
```

---

## Task 6: `progression/ScheduleLayout.kt` — calendar layout logic

**Files:**
- Create: `app/src/main/java/com/ditrain/app/progression/ScheduleLayout.kt`
- Create: `app/src/test/java/com/ditrain/app/progression/ScheduleLayoutTest.kt`

This is pure logic — given a routine + start-date + weekday pattern, produce a list of `ScheduledSession`s. Spec §6.2 governs.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/ditrain/app/progression/ScheduleLayoutTest.kt`:

```kotlin
package com.ditrain.app.progression

import com.ditrain.app.model.ExerciseBlock
import com.ditrain.app.model.LoadTarget
import com.ditrain.app.model.LoopMode
import com.ditrain.app.model.RepsTarget
import com.ditrain.app.model.Routine
import com.ditrain.app.model.SessionTemplate
import com.ditrain.app.model.SetPrescription
import com.ditrain.app.model.Week
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ScheduleLayoutTest {

    private fun straightBlock() = ExerciseBlock("squat", sets = listOf(
        SetPrescription.Straight(RepsTarget.Fixed(5), LoadTarget.Open)
    ))

    private fun routine(
        loopMode: LoopMode,
        weeks: Int,
        sessionsPerWeek: Int,
    ): Routine {
        val sessions = (1..sessionsPerWeek).map { idx ->
            SessionTemplate("s$idx", "Session $idx", blocks = listOf(straightBlock()))
        }
        val weekList = (1..weeks).map { Week("Week $it", sessions) }
        return Routine(
            id = "r", name = "R", loopMode = loopMode,
            weeks = weekList,
        )
    }

    @Test fun `ONCE mesocycle produces exactly weeks times sessionsPerWeek entries`() {
        val r = routine(LoopMode.ONCE, weeks = 4, sessionsPerWeek = 3)
        // Mon=0, Wed=2, Fri=4 — 3 sessions/week
        val start = LocalDate.of(2026, 5, 18)   // a Monday
        val out = ScheduleLayout.lay(r, weeklyPattern = listOf(0, 2, 4), startDate = start, futureWeeks = 8)
        assertEquals(12, out.size)
    }

    @Test fun `ONCE first sessions land on chosen weekdays in order`() {
        val r = routine(LoopMode.ONCE, weeks = 1, sessionsPerWeek = 3)
        val start = LocalDate.of(2026, 5, 18)   // Monday
        val out = ScheduleLayout.lay(r, weeklyPattern = listOf(0, 2, 4), startDate = start, futureWeeks = 8)
        assertEquals(3, out.size)
        assertEquals("2026-05-18", out[0].date)   // Mon
        assertEquals("2026-05-20", out[1].date)   // Wed
        assertEquals("2026-05-22", out[2].date)   // Fri
        assertEquals("s1", out[0].sessionTemplateId)
        assertEquals("s2", out[1].sessionTemplateId)
        assertEquals("s3", out[2].sessionTemplateId)
    }

    @Test fun `REPEAT lays out futureWeeks worth of sessions`() {
        val r = routine(LoopMode.REPEAT, weeks = 1, sessionsPerWeek = 3)
        val start = LocalDate.of(2026, 5, 18)
        val out = ScheduleLayout.lay(r, weeklyPattern = listOf(0, 2, 4), startDate = start, futureWeeks = 8)
        assertEquals(8 * 3, out.size)
    }

    @Test fun `REPEAT cycles through weeks when routine has multiple weeks`() {
        val r = routine(LoopMode.REPEAT, weeks = 2, sessionsPerWeek = 3)
        val start = LocalDate.of(2026, 5, 18)
        val out = ScheduleLayout.lay(r, weeklyPattern = listOf(0, 2, 4), startDate = start, futureWeeks = 4)
        assertEquals(12, out.size)
        assertEquals(0, out[0].weekIndex)
        assertEquals(0, out[2].weekIndex)
        assertEquals(1, out[3].weekIndex)
        assertEquals(1, out[5].weekIndex)
        assertEquals(0, out[6].weekIndex)     // cycle back to week 0 after week 1
        assertEquals(1, out[9].weekIndex)
    }

    @Test fun `start on a non-pattern weekday advances to first matching weekday`() {
        val r = routine(LoopMode.ONCE, weeks = 1, sessionsPerWeek = 1)
        val start = LocalDate.of(2026, 5, 19)   // Tuesday
        val out = ScheduleLayout.lay(r, weeklyPattern = listOf(2), startDate = start, futureWeeks = 8)
        assertEquals(1, out.size)
        assertEquals("2026-05-20", out[0].date)   // first Wednesday on/after Tue 19th
    }

    @Test fun `throws when sessionsPerWeek exceeds weekly pattern size`() {
        val r = routine(LoopMode.ONCE, weeks = 1, sessionsPerWeek = 4)
        try {
            ScheduleLayout.lay(r, weeklyPattern = listOf(0, 2, 4), startDate = LocalDate.now(), futureWeeks = 8)
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message!!.contains("4 sessions/week"))
        }
    }

    @Test fun `routineId weekIndex sessionTemplateId are set correctly`() {
        val r = routine(LoopMode.ONCE, weeks = 1, sessionsPerWeek = 2)
        val out = ScheduleLayout.lay(r, weeklyPattern = listOf(0, 2), startDate = LocalDate.of(2026, 5, 18), futureWeeks = 8)
        assertEquals("r", out[0].routineId)
        assertEquals(0, out[0].weekIndex)
        assertEquals("s1", out[0].sessionTemplateId)
        assertEquals("s2", out[1].sessionTemplateId)
    }
}
```

Run, expect FAIL.

- [ ] **Step 2: Implement** at `app/src/main/java/com/ditrain/app/progression/ScheduleLayout.kt`:

```kotlin
package com.ditrain.app.progression

import com.ditrain.app.model.LoopMode
import com.ditrain.app.model.Routine
import com.ditrain.app.model.ScheduledSession
import com.ditrain.app.util.iso
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Lays out [ScheduledSession]s for a freshly-activated routine, given:
 *  - the user's chosen [weeklyPattern] (Mon=0..Sun=6),
 *  - a [startDate] to begin scheduling from (sessions roll forward from this date),
 *  - [futureWeeks] — how many weeks to schedule ahead for REPEAT routines (ignored for ONCE).
 *
 * For [LoopMode.ONCE], emits exactly `weeks.size × sessionsPerWeek` entries.
 * For [LoopMode.REPEAT], emits `futureWeeks × sessionsPerWeek` entries, cycling through the
 * routine's weeks as needed.
 *
 * Throws [IllegalArgumentException] if any week has more sessions than the weekly pattern can hold.
 */
object ScheduleLayout {

    fun lay(
        routine: Routine,
        weeklyPattern: List<Int>,
        startDate: LocalDate,
        futureWeeks: Int = 8,
    ): List<ScheduledSession> {
        require(weeklyPattern.isNotEmpty()) { "weeklyPattern must be non-empty" }
        require(weeklyPattern.all { it in 0..6 }) { "weeklyPattern entries must be 0..6 (Mon..Sun)" }

        // Validate every week of the routine fits the pattern.
        routine.weeks.forEachIndexed { idx, week ->
            require(week.sessions.size <= weeklyPattern.size) {
                "Routine prescribes ${week.sessions.size} sessions/week for week ${idx + 1} (${week.label}), " +
                "but the user picked only ${weeklyPattern.size} training days."
            }
        }

        val sortedPattern = weeklyPattern.sorted()
        val totalWeeksToSchedule = when (routine.loopMode) {
            LoopMode.ONCE -> routine.weeks.size
            LoopMode.REPEAT -> futureWeeks
        }

        val out = mutableListOf<ScheduledSession>()
        var cursorMonday = mondayOnOrBefore(startDate)

        for (i in 0 until totalWeeksToSchedule) {
            val routineWeekIndex = i % routine.weeks.size
            val week = routine.weeks[routineWeekIndex]
            week.sessions.forEachIndexed { sessionIdx, session ->
                val weekday = sortedPattern[sessionIdx]
                val date = cursorMonday.plusDays(weekday.toLong())
                if (date < startDate) return@forEachIndexed   // skip past dates in the first week
                out.add(ScheduledSession(
                    date = date.iso(),
                    routineId = routine.id,
                    weekIndex = routineWeekIndex,
                    sessionTemplateId = session.id,
                ))
            }
            cursorMonday = cursorMonday.plusWeeks(1)
        }

        return out
    }

    private fun mondayOnOrBefore(date: LocalDate): LocalDate {
        val daysFromMonday = (date.dayOfWeek.value + 6) % 7   // Mon=1..Sun=7 → Mon=0..Sun=6
        return date.minusDays(daysFromMonday.toLong())
    }
}
```

- [ ] **Step 3: Run, expect PASS** (7 tests)

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.ditrain.app.progression.ScheduleLayoutTest"
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ditrain/app/progression/ScheduleLayout.kt \
        app/src/test/java/com/ditrain/app/progression/ScheduleLayoutTest.kt
git commit -m "feat(progression): add ScheduleLayout for routine activation"
```

---

## Task 7: Extend `ui/ViewStyling.kt` with `chip` + `dangerButton`

**Files:**
- Modify: `app/src/main/java/com/ditrain/app/ui/ViewStyling.kt`

- [ ] **Step 1: Append two helpers to the `ViewStyling` object**

Edit the file to add (preserving existing functions):

```kotlin
    fun chip(context: Context, text: String, fillColor: String, dp: (Int) -> Int): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.WHITE)
            background = roundedBackground(fillColor, fillColor, dp(2), dp(10).toFloat())
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

    fun dangerButton(
        context: Context,
        label: String,
        compact: Boolean,
        dp: (Int) -> Int,
    ): Button =
        Button(context).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = if (compact) 14f else 15f
            background = roundedBackground("#B91C1C", "#B91C1C", dp(2), dp(14).toFloat())
            val pad = if (compact) dp(6) else dp(14)
            setPadding(pad, pad, pad, pad)
            minHeight = if (compact) dp(40) else 0
            minimumHeight = if (compact) dp(40) else 0
        }
```

You also need to add an import for `android.widget.TextView` to the existing imports.

The final file should look like:

```kotlin
package com.ditrain.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

object ViewStyling {
    fun roundedBackground(fillColor: String, strokeColor: String, strokeWidth: Int, radius: Float) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.parseColor(fillColor))
            setStroke(strokeWidth, Color.parseColor(strokeColor))
        }

    fun actionButton(
        context: Context,
        label: String,
        fillColor: String,
        compact: Boolean,
        dp: (Int) -> Int,
        roundedBackground: (String, String, Float) -> GradientDrawable
    ): Button =
        Button(context).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = if (compact) 14f else 15f
            background = roundedBackground(fillColor, fillColor, dp(14).toFloat())
            val pad = if (compact) dp(6) else dp(14)
            setPadding(pad, pad, pad, pad)
            minHeight = if (compact) dp(40) else 0
            minimumHeight = if (compact) dp(40) else 0
        }

    fun dialogInputContainer(context: Context, input: EditText, dp: (Int) -> Int): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(8))
            addView(input, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

    fun chip(context: Context, text: String, fillColor: String, dp: (Int) -> Int): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.WHITE)
            background = roundedBackground(fillColor, fillColor, dp(2), dp(10).toFloat())
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

    fun dangerButton(
        context: Context,
        label: String,
        compact: Boolean,
        dp: (Int) -> Int,
    ): Button =
        Button(context).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = if (compact) 14f else 15f
            background = roundedBackground("#B91C1C", "#B91C1C", dp(2), dp(14).toFloat())
            val pad = if (compact) dp(6) else dp(14)
            setPadding(pad, pad, pad, pad)
            minHeight = if (compact) dp(40) else 0
            minimumHeight = if (compact) dp(40) else 0
        }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew.bat :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/ViewStyling.kt
git commit -m "feat(ui): extend ViewStyling with chip and dangerButton helpers"
```

---

## Task 8: `MainMenuDialogController`

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/dialog/MainMenuDialogController.kt`

A simple list dialog with rows for: Routines, Import routine, About (the last is a placeholder for Plan 6 — for now wire it to nothing). No tests; AppCompat dialog code is exercised by manual smoke.

- [ ] **Step 1: Implement**

Create the file with:

```kotlin
package com.ditrain.app.ui.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.ui.ViewStyling

/**
 * The "⋮" overflow menu shown from Home. Each row is a labeled action that opens
 * a sub-dialog. Plan 6 adds Settings / Backup / Glossary / About; Plan 2 ships
 * only Routines + Import routine.
 */
class MainMenuDialogController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val onOpenRoutines: () -> Unit,
    private val onImportRoutine: () -> Unit,
) {
    fun show() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Menu")
            .setView(ScrollView(context).apply { addView(container) })
            .setNegativeButton("Close", null)
            .create()

        container.addView(row("Routines", "Browse, activate, or delete saved routines") {
            dialog.dismiss()
            onOpenRoutines()
        })
        container.addView(row("Import routine", "Paste JSON, pick a file, or load a bundled example") {
            dialog.dismiss()
            onImportRoutine()
        })

        dialog.show()
    }

    private fun row(title: String, subtitle: String, onClick: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(20).toFloat())
            setPadding(dp(14), dp(14), dp(14), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            addView(TextView(context).apply {
                text = title
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = subtitle
                textSize = 13f
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, dp(4), 0, 0)
            })

            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew.bat :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/dialog/MainMenuDialogController.kt
git commit -m "feat(ui): add MainMenuDialogController with Routines and Import routine entries"
```

---

## Task 9: `ExerciseDetailDialogController`

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/dialog/ExerciseDetailDialogController.kt`

Shows muscles, pattern, equipment, and an "Open description URL" button (intent.ACTION_VIEW). Read-only for v1; the "Show history" affordance is deferred to Plan 5.

- [ ] **Step 1: Implement**

```kotlin
package com.ditrain.app.ui.dialog

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.model.Equipment
import com.ditrain.app.model.Exercise
import com.ditrain.app.model.MuscleGroup
import com.ditrain.app.ui.ViewStyling

/**
 * Read-only detail view for one Exercise: name, primary/secondary muscles as chips,
 * pattern + equipment, and an "Open description URL" action when [Exercise.descriptionUrl] is set.
 */
class ExerciseDetailDialogController(
    private val context: Context,
    private val dp: (Int) -> Int,
) {
    fun show(exercise: Exercise) {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))

            addView(TextView(context).apply {
                text = exercise.name
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })

            if (exercise.aliases.isNotEmpty()) {
                addView(TextView(context).apply {
                    text = "Aliases: " + exercise.aliases.joinToString(", ")
                    textSize = 13f
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(0, dp(4), 0, dp(4))
                })
            }

            addView(metaRow("Pattern", exercise.pattern.name.replace('_', ' ').lowercase().capitalizeFirst()))
            addView(metaRow("Equipment", exercise.equipment.joinToString(", ") { it.label() }))

            addView(label("Primary muscles"))
            addView(muscleChipRow(exercise.primaryMuscles, "#1D4ED8"))

            if (exercise.secondaryMuscles.isNotEmpty()) {
                addView(label("Secondary muscles"))
                addView(muscleChipRow(exercise.secondaryMuscles, "#475569"))
            }

            if (exercise.deleted) {
                addView(TextView(context).apply {
                    text = "This exercise is soft-deleted. It's hidden from pickers but still referenced by historical sessions."
                    textSize = 13f
                    setTextColor(Color.parseColor("#FCA5A5"))
                    setPadding(0, dp(10), 0, 0)
                })
            }
        }

        val builder = AlertDialog.Builder(context)
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton("Close", null)

        val url = exercise.descriptionUrl
        if (!url.isNullOrBlank()) {
            builder.setPositiveButton("Open description") { _, _ ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                // If no browser handles the intent, silently swallow.
            }
        }

        builder.show()
    }

    private fun label(text: String) = TextView(context).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.parseColor("#CBD5E1"))
        setPadding(0, dp(10), 0, dp(4))
    }

    private fun metaRow(label: String, value: String) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(4), 0, 0)
        addView(TextView(context).apply {
            text = "$label:"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(dp(110), WRAP_CONTENT)
        })
        addView(TextView(context).apply {
            text = value
            textSize = 13f
            setTextColor(Color.WHITE)
        })
    }

    private fun muscleChipRow(muscles: List<MuscleGroup>, fillColor: String): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
        }
        muscles.forEach { m ->
            row.addView(
                ViewStyling.chip(context, m.label(), fillColor, dp).apply {
                    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                        marginEnd = dp(6)
                        topMargin = dp(2)
                        bottomMargin = dp(2)
                    }
                }
            )
        }
        return row
    }
}

private fun String.capitalizeFirst(): String =
    if (isEmpty()) this else this[0].uppercaseChar() + substring(1)

private fun Equipment.label(): String = name.lowercase().replace('_', ' ')

private fun MuscleGroup.label(): String = name.lowercase().replace('_', ' ')
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew.bat :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/dialog/ExerciseDetailDialogController.kt
git commit -m "feat(ui): add ExerciseDetailDialogController with description URL intent"
```

---

## Task 10: `ExercisePickerDialogController`

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/dialog/ExercisePickerDialogController.kt`

Scrolling list of exercises with a text search field at the top. Filter chips for movement pattern / equipment are deferred to Plan 3 (where the picker becomes session-editor-driven); Plan 2 uses just name search. Tap an exercise → fires callback + dismisses.

- [ ] **Step 1: Implement**

```kotlin
package com.ditrain.app.ui.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.model.Exercise
import com.ditrain.app.storage.ExerciseCatalog
import com.ditrain.app.ui.ViewStyling

/**
 * Searchable list of catalog exercises. Tap a row → [onPicked] fires and the dialog closes.
 * Soft-deleted entries are hidden unless [includeDeleted] is true.
 */
class ExercisePickerDialogController(
    private val context: Context,
    private val catalog: ExerciseCatalog,
    private val dp: (Int) -> Int,
    private val onPicked: (Exercise) -> Unit,
    private val onDetail: ((Exercise) -> Unit)? = null,
    private val includeDeleted: Boolean = false,
    private val title: String = "Pick an exercise",
) {
    fun show() {
        val search = EditText(context).apply {
            hint = "Search by name…"
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
        }

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val all = catalog.visibleExercises(includeDeleted = includeDeleted)
            .sortedBy { it.name.lowercase() }

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(8))
                addView(search)
                addView(ScrollView(context).apply { addView(list) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(400)).apply { topMargin = dp(8) })
            })
            .setNegativeButton("Cancel", null)
            .create()

        fun render(filter: String) {
            list.removeAllViews()
            val needle = filter.trim().lowercase()
            val filtered = if (needle.isEmpty()) all else all.filter { ex ->
                ex.name.lowercase().contains(needle) || ex.aliases.any { it.lowercase().contains(needle) }
            }
            if (filtered.isEmpty()) {
                list.addView(TextView(context).apply {
                    text = "No matches"
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(0, dp(12), 0, dp(12))
                    gravity = Gravity.CENTER
                })
            } else {
                filtered.forEach { ex -> list.addView(row(ex, dialog)) }
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { render(s?.toString().orEmpty()) }
        })

        render("")
        dialog.show()
    }

    private fun row(ex: Exercise, dialog: AlertDialog): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(16).toFloat())
            setPadding(dp(14), dp(10), dp(14), dp(10))

            val text = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = ex.name
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                })
                addView(TextView(context).apply {
                    text = ex.pattern.name.lowercase().replace('_', ' ') +
                           " · " + ex.equipment.joinToString(", ") { it.name.lowercase() }
                    textSize = 12f
                    setTextColor(Color.parseColor("#94A3B8"))
                })
            }
            addView(text, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

            if (onDetail != null) {
                addView(TextView(context).apply {
                    text = "Info"
                    setTextColor(Color.parseColor("#60A5FA"))
                    textSize = 14f
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setOnClickListener { onDetail.invoke(ex) }
                })
            }

            setOnClickListener {
                onPicked(ex)
                dialog.dismiss()
            }

            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew.bat :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/dialog/ExercisePickerDialogController.kt
git commit -m "feat(ui): add ExercisePickerDialogController with name search"
```

---

## Task 11: `RoutinePreviewDialogController`

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/dialog/RoutinePreviewDialogController.kt`

Read-only view of a routine's structure. Two action buttons: "Save" and "Save & activate". The dialog is reused by both the import flow (Task 13) and the routine list "view" action (Task 12).

- [ ] **Step 1: Implement**

```kotlin
package com.ditrain.app.ui.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.model.LoadTarget
import com.ditrain.app.model.LoopMode
import com.ditrain.app.model.RepsTarget
import com.ditrain.app.model.Routine
import com.ditrain.app.model.SetPrescription
import com.ditrain.app.storage.ExerciseCatalog
import com.ditrain.app.ui.ViewStyling

/**
 * Read-only structural view of a routine. Used by the import flow (where buttons are
 * "Save"/"Save & activate") and by the routine list (where buttons are "Activate"/"Close").
 *
 * Renders week → session → exercise block → set prescription rows.
 */
class RoutinePreviewDialogController(
    private val context: Context,
    private val catalog: ExerciseCatalog,
    private val dp: (Int) -> Int,
) {

    enum class Mode { IMPORT, VIEW }

    fun show(
        routine: Routine,
        mode: Mode,
        onPrimary: () -> Unit,
        onSecondary: () -> Unit,
        primaryLabel: String,
        secondaryLabel: String,
    ) {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))

            addView(TextView(context).apply {
                text = routine.name
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            routine.description?.let {
                addView(TextView(context).apply {
                    text = it
                    textSize = 13f
                    setTextColor(Color.parseColor("#CBD5E1"))
                    setPadding(0, dp(4), 0, dp(4))
                })
            }
            addView(metaLine("Type", when (routine.loopMode) {
                LoopMode.ONCE -> "Mesocycle (${routine.weeks.size} weeks, runs once)"
                LoopMode.REPEAT -> "Template (${routine.weeks.size} week${if (routine.weeks.size == 1) "" else "s"}, repeats)"
            }))
            routine.author?.let { addView(metaLine("Author", it)) }
            routine.defaultWeeklyPattern?.let { pattern ->
                addView(metaLine("Default weekdays", pattern.joinToString(", ") { weekdayName(it) }))
            }

            routine.weeks.forEachIndexed { wIdx, week ->
                addView(sectionHeader("Week ${wIdx + 1}: ${week.label}"))
                week.sessions.forEach { session ->
                    addView(sessionCard(session.name, session.blocks, session.cardioBlocks))
                }
            }
        }

        AlertDialog.Builder(context)
            .setTitle(if (mode == Mode.IMPORT) "Preview import" else "Routine")
            .setView(ScrollView(context).apply { addView(content) })
            .setNeutralButton(secondaryLabel) { _, _ -> onSecondary() }
            .setPositiveButton(primaryLabel) { _, _ -> onPrimary() }
            .show()
    }

    private fun metaLine(label: String, value: String) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(4), 0, 0)
        addView(TextView(context).apply {
            text = "$label:"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(dp(120), WRAP_CONTENT)
        })
        addView(TextView(context).apply {
            text = value
            textSize = 13f
            setTextColor(Color.WHITE)
        })
    }

    private fun sectionHeader(text: String) = TextView(context).apply {
        this.text = text
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#93C5FD"))
        setPadding(0, dp(14), 0, dp(6))
    }

    private fun sessionCard(
        sessionName: String,
        blocks: List<com.ditrain.app.model.ExerciseBlock>,
        cardioBlocks: List<com.ditrain.app.model.CardioBlock>,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(16).toFloat())
        setPadding(dp(12), dp(10), dp(12), dp(10))

        addView(TextView(context).apply {
            text = sessionName
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })

        blocks.forEach { b ->
            val exercise = catalog.byId(b.exerciseId)
            val exName = exercise?.name ?: "(unknown: ${b.exerciseId})"
            addView(TextView(context).apply {
                text = "$exName  —  ${formatSets(b.sets)}"
                textSize = 13f
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, dp(4), 0, 0)
            })
        }
        cardioBlocks.forEach { c ->
            val kindLabel = if (c.activityKind.name == "OTHER" && !c.description.isNullOrBlank())
                "Cardio: ${c.description}" else "Cardio: ${c.activityKind.name.lowercase()}"
            val mins = c.targetDurationMin?.let { " · ${it} min" } ?: ""
            val bpm = c.targetAvgBpm?.let { " · target ${it} bpm" } ?: ""
            addView(TextView(context).apply {
                text = "$kindLabel$mins$bpm"
                textSize = 13f
                setTextColor(Color.parseColor("#FDE68A"))
                setPadding(0, dp(4), 0, 0)
            })
        }

        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        }
    }

    private fun formatSets(sets: List<SetPrescription>): String {
        val first = sets.firstOrNull() ?: return "no sets"
        val sameAcross = sets.all { it::class == first::class && setsEqual(it, first) }
        return if (sameAcross) "${sets.size} × ${formatOne(first)}"
        else sets.joinToString("; ") { formatOne(it) }
    }

    private fun setsEqual(a: SetPrescription, b: SetPrescription): Boolean = a == b

    private fun formatOne(s: SetPrescription): String = when (s) {
        is SetPrescription.Straight -> "${formatReps(s.reps)} @ ${formatLoad(s.load)}" +
                (s.rpeTarget?.let { " @ RPE $it" } ?: "")
        is SetPrescription.MyoRep -> "myo: ${formatReps(s.activationReps)} act + ${s.miniSetCount}×${s.miniSetTargetReps}"
    }

    private fun formatReps(r: RepsTarget): String = when (r) {
        is RepsTarget.Fixed -> "${r.reps} reps"
        is RepsTarget.Range -> "${r.min}-${r.max} reps"
        RepsTarget.Amrap -> "AMRAP"
        is RepsTarget.AmrapMin -> "AMRAP ≥${r.min}"
    }

    private fun formatLoad(l: LoadTarget): String = when (l) {
        is LoadTarget.AbsoluteKg -> "${l.kg} kg"
        is LoadTarget.PctOneRm -> "${(l.pct * 100).toInt()}% 1RM"
        is LoadTarget.RpeTarget -> "RPE ${l.rpe}"
        is LoadTarget.RelativeToLast -> "last + ${l.deltaKg} kg"
        LoadTarget.Open -> "open load"
    }

    private fun weekdayName(idx: Int): String = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[idx]
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew.bat :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/dialog/RoutinePreviewDialogController.kt
git commit -m "feat(ui): add RoutinePreviewDialogController for import + view flows"
```

---

## Task 12: `RoutineListDialogController`

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/dialog/RoutineListDialogController.kt`

Lists saved routines (read from `RoutineRepository.list()` + load each). Each row shows name and a tap → preview. Per-row delete button uses the danger style. Activation is wired via the activate callback (Task 14 wiring).

- [ ] **Step 1: Implement**

```kotlin
package com.ditrain.app.ui.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.model.Routine
import com.ditrain.app.ui.ViewStyling

/**
 * Lists saved routines with per-row View / Activate / Delete actions. The activate
 * button just invokes [onActivate(routine)] — the parent wires it into the
 * AppState + schedule-layout flow (Task 14).
 */
class RoutineListDialogController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val activeRoutineId: String?,
    private val onView: (Routine) -> Unit,
    private val onActivate: (Routine) -> Unit,
    private val onDelete: (Routine) -> Unit,
) {

    fun show(routines: List<Routine>) {
        if (routines.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("Routines")
                .setMessage("No routines saved yet. Use \"Import routine\" from the menu to add one.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Routines")
            .setView(ScrollView(context).apply { addView(container) })
            .setNegativeButton("Close", null)
            .create()

        routines.forEach { r -> container.addView(row(r, dialog)) }
        dialog.show()
    }

    private fun row(r: Routine, dialog: AlertDialog): View {
        val isActive = r.id == activeRoutineId

        val viewBtn = ViewStyling.actionButton(
            context = context, label = "View",
            fillColor = "#2563EB", compact = true, dp = dp,
            roundedBackground = { fill, stroke, radius ->
                ViewStyling.roundedBackground(fill, stroke, dp(2), radius)
            },
        ).apply { setOnClickListener { onView(r) } }

        val activateBtn = ViewStyling.actionButton(
            context = context,
            label = if (isActive) "Active" else "Activate",
            fillColor = if (isActive) "#16A34A" else "#7C3AED",
            compact = true, dp = dp,
            roundedBackground = { fill, stroke, radius ->
                ViewStyling.roundedBackground(fill, stroke, dp(2), radius)
            },
        ).apply {
            isEnabled = !isActive
            setOnClickListener { onActivate(r) }
        }

        val deleteBtn = ViewStyling.dangerButton(context, "Delete", compact = true, dp = dp).apply {
            setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle("Delete \"${r.name}\"?")
                    .setMessage(
                        if (isActive)
                            "This routine is currently active. Deleting it will clear all future scheduled sessions for it. Past session logs are kept in history."
                        else
                            "Past session logs that reference this routine remain in history."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ -> onDelete(r) }
                    .show()
            }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(20).toFloat())
            setPadding(dp(14), dp(14), dp(14), dp(14))

            addView(TextView(context).apply {
                text = r.name
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = "${r.weeks.size} week${if (r.weeks.size == 1) "" else "s"} · " +
                       (if (r.loopMode.name == "REPEAT") "repeats" else "runs once")
                textSize = 13f
                setTextColor(Color.parseColor("#94A3B8"))
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(viewBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
                addView(activateBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
                addView(deleteBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })

            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) }
        }
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew.bat :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/dialog/RoutineListDialogController.kt
git commit -m "feat(ui): add RoutineListDialogController with view/activate/delete rows"
```

---

## Task 13: `RoutineImportDialogController` + SAF integration

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/dialog/RoutineImportDialogController.kt`

Three sub-tabs (rendered as button row at top, content swap below): **Paste JSON**, **Pick file**, **Bundled examples**. The "Pick file" button delegates to a caller-provided launcher (the Activity holds the `ActivityResultLauncher`).

- [ ] **Step 1: Implement**

```kotlin
package com.ditrain.app.ui.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.ui.ViewStyling

/**
 * Routine import dialog. Three modes for the user:
 *  - Paste JSON: large EditText + Parse button → invokes [onParse] with the pasted text.
 *  - Pick file: invokes [onPickFile] which the Activity handles via SAF.
 *  - Bundled examples: shows a list of asset filenames; tap → invokes [onParse] with the
 *    file's contents (loaded by [loadBundledExample]).
 *
 * The dialog only handles user input. Parse result rendering is the caller's job (typically
 * by showing a RoutinePreview on Success, or a toast/banner on failure).
 */
class RoutineImportDialogController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val bundledExamples: List<BundledExample>,
    private val loadBundledExample: (assetPath: String) -> String,
    private val onParse: (json: String) -> Unit,
    private val onPickFile: () -> Unit,
) {

    data class BundledExample(val assetPath: String, val displayName: String, val description: String)

    fun show() {
        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Import routine")
            .setView(ScrollView(context).apply {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    addView(tabRow)
                    addView(body)
                })
            })
            .setNegativeButton("Cancel", null)
            .create()

        fun renderPaste() {
            body.removeAllViews()
            val edit = EditText(context).apply {
                hint = "Paste routine JSON here…"
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#94A3B8"))
                minLines = 6
                maxLines = 18
                isSingleLine = false
            }
            body.addView(edit, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            body.addView(ViewStyling.actionButton(
                context, "Parse", "#2563EB", compact = false, dp = dp,
                roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
            ).apply {
                setOnClickListener {
                    val raw = edit.text.toString()
                    if (raw.isBlank()) return@setOnClickListener
                    dialog.dismiss()
                    onParse(raw)
                }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        }

        fun renderPickFile() {
            body.removeAllViews()
            body.addView(TextView(context).apply {
                text = "Choose a routine JSON file from your device."
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 14f
                setPadding(0, 0, 0, dp(10))
            })
            body.addView(ViewStyling.actionButton(
                context, "Open file…", "#2563EB", compact = false, dp = dp,
                roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
            ).apply {
                setOnClickListener {
                    dialog.dismiss()
                    onPickFile()
                }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        fun renderBundled() {
            body.removeAllViews()
            if (bundledExamples.isEmpty()) {
                body.addView(TextView(context).apply {
                    text = "No bundled examples available."
                    setTextColor(Color.parseColor("#94A3B8"))
                })
                return
            }
            bundledExamples.forEach { example ->
                body.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ViewStyling.roundedBackground("#111827", "#334155", dp(2), dp(16).toFloat())
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        val raw = loadBundledExample(example.assetPath)
                        dialog.dismiss()
                        onParse(raw)
                    }
                    addView(TextView(context).apply {
                        text = example.displayName
                        textSize = 15f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                    })
                    addView(TextView(context).apply {
                        text = example.description
                        textSize = 13f
                        setTextColor(Color.parseColor("#CBD5E1"))
                        setPadding(0, dp(4), 0, 0)
                    })
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
                })
            }
        }

        fun tab(label: String, onClick: () -> Unit) =
            ViewStyling.actionButton(
                context, label, "#475569", compact = true, dp = dp,
                roundedBackground = { f, s, r -> ViewStyling.roundedBackground(f, s, dp(2), r) },
            ).apply { setOnClickListener { onClick() } }

        tabRow.addView(tab("Paste") { renderPaste() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
        tabRow.addView(tab("Pick file") { renderPickFile() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        tabRow.addView(tab("Examples") { renderBundled() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(4) })

        renderPaste()
        dialog.show()
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew.bat :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/dialog/RoutineImportDialogController.kt
git commit -m "feat(ui): add RoutineImportDialogController with paste/pick-file/examples tabs"
```

---

## Task 14: `WeeklyPatternDialogController`

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/dialog/WeeklyPatternDialogController.kt`

Shown during routine activation when `routine.defaultWeeklyPattern` is null. Mon–Sun checkboxes + a date picker for "Start on" (we'll use a simple text input for v1 — proper DatePicker is overkill given typical use is "today" or "next Monday").

- [ ] **Step 1: Implement**

```kotlin
package com.ditrain.app.ui.dialog

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ditrain.app.ui.ViewStyling
import java.time.LocalDate

/**
 * Asks the user which weekdays they train (Mon=0..Sun=6) and a start date. Returns
 * the choices via [onConfirm]. Cancel just dismisses without firing the callback.
 */
class WeeklyPatternDialogController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val onConfirm: (weeklyPattern: List<Int>, startDate: LocalDate) -> Unit,
) {

    fun show(prefilledPattern: List<Int>? = null, prefilledStart: LocalDate = LocalDate.now()) {
        val weekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val checkboxes = weekdayNames.mapIndexed { idx, name ->
            CheckBox(context).apply {
                text = name
                isChecked = prefilledPattern?.contains(idx) == true
                setTextColor(Color.WHITE)
            }
        }

        var startDate = prefilledStart
        val startDateView = TextView(context).apply {
            text = startDate.toString()
            textSize = 16f
            setTextColor(Color.parseColor("#93C5FD"))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = ViewStyling.roundedBackground("#1F2937", "#334155", dp(1), dp(8).toFloat())
            setOnClickListener {
                DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        startDate = LocalDate.of(y, m + 1, d)
                        text = startDate.toString()
                    },
                    startDate.year, startDate.monthValue - 1, startDate.dayOfMonth,
                ).show()
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            addView(TextView(context).apply {
                text = "Which days will you train?"
                textSize = 14f
                setTextColor(Color.parseColor("#CBD5E1"))
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                setPadding(0, dp(6), 0, dp(10))
                checkboxes.forEach { cb ->
                    addView(cb, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(4) })
                }
            })
            addView(TextView(context).apply {
                text = "Start on:"
                textSize = 14f
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, dp(4), 0, dp(4))
            })
            addView(startDateView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        AlertDialog.Builder(context)
            .setTitle("Schedule")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Activate") { _, _ ->
                val pattern = checkboxes.mapIndexedNotNull { idx, cb -> if (cb.isChecked) idx else null }
                if (pattern.isNotEmpty()) onConfirm(pattern, startDate)
            }
            .show()
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew.bat :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/dialog/WeeklyPatternDialogController.kt
git commit -m "feat(ui): add WeeklyPatternDialogController for routine activation"
```

---

## Task 15: Wire Home + MainActivity to the dialog flow

**Files:**
- Modify: `app/src/main/java/com/ditrain/app/ui/home/HomeViewController.kt`
- Modify: `app/src/main/java/com/ditrain/app/MainActivity.kt`

This is the integration task that connects all dialogs. After this task, the user can open the menu, browse routines, import a routine (paste/pick/bundled), preview it, save, activate (with weekly pattern picker), and see the active routine reflected back on Home.

- [ ] **Step 1: Replace `HomeViewController.kt`** with a version that exposes a menu callback and renders the active routine state:

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
import com.ditrain.app.model.Routine
import com.ditrain.app.ui.ViewStyling

/**
 * Home view for Plan 2. Renders:
 *  - A title bar with the app name and an overflow "⋮" affordance.
 *  - A body that adapts to whether a routine is active: a "No routine yet"
 *    placeholder or a summary of the active routine.
 *
 * Plan 3 replaces the body with "today's session" + Start button. The menu
 * affordance stays.
 */
class HomeViewController(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val onMenuClick: () -> Unit,
    private val onImportNow: () -> Unit,
) {

    fun buildView(activeRoutine: Routine?): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))

        addView(titleBar())
        addView(divider())

        if (activeRoutine == null) {
            addView(noRoutineCard())
        } else {
            addView(activeRoutineCard(activeRoutine))
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
            isFocusable = true
            setOnClickListener { onMenuClick() }
        })
    }

    private fun divider() = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
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

    private fun activeRoutineCard(routine: Routine) = LinearLayout(context).apply {
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
                    (if (routine.loopMode.name == "REPEAT") "repeats indefinitely" else "runs once")
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(4), 0, dp(14))
        })
        addView(TextView(context).apply {
            text = "Session execution arrives in the next milestone. For now you can browse the routine via Menu → Routines."
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
        })
    }
}
```

- [ ] **Step 2: Replace `MainActivity.kt`** with a version that loads state and wires every dialog flow:

```kotlin
package com.ditrain.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ditrain.app.importing.ImportResult
import com.ditrain.app.importing.RoutineImporter
import com.ditrain.app.model.AppState
import com.ditrain.app.model.Routine
import com.ditrain.app.progression.ScheduleLayout
import com.ditrain.app.storage.AppStateRepository
import com.ditrain.app.storage.ExerciseCatalog
import com.ditrain.app.storage.RoutineRepository
import com.ditrain.app.ui.dialog.ExerciseDetailDialogController
import com.ditrain.app.ui.dialog.ExercisePickerDialogController
import com.ditrain.app.ui.dialog.MainMenuDialogController
import com.ditrain.app.ui.dialog.RoutineImportDialogController
import com.ditrain.app.ui.dialog.RoutineListDialogController
import com.ditrain.app.ui.dialog.RoutinePreviewDialogController
import com.ditrain.app.ui.dialog.WeeklyPatternDialogController
import com.ditrain.app.ui.home.HomeViewController
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

class MainActivity : AppCompatActivity() {

    private val dp: (Int) -> Int = { dpToPx(it) }

    private lateinit var routineRepo: RoutineRepository
    private lateinit var appStateRepo: AppStateRepository
    private lateinit var catalog: ExerciseCatalog

    private var appState: AppState = AppState(null, emptyList(), com.ditrain.app.model.Settings())
    private var activeRoutine: Routine? = null

    private lateinit var rootContainer: FrameLayout
    private lateinit var home: HomeViewController

    private val pickRoutineFile: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val text = runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                toast("Couldn't read the selected file.")
                return@registerForActivityResult
            }
            handleImportText(text)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        routineRepo = RoutineRepository(filesDir)
        appStateRepo = AppStateRepository(filesDir)
        catalog = ExerciseCatalog.fromAssets(
            assets.open("exercises.json"),
            File(filesDir, "custom_exercises.json"),
        )

        home = HomeViewController(
            context = this,
            dp = dp,
            onMenuClick = { openMainMenu() },
            onImportNow = { openImport() },
        )

        rootContainer = FrameLayout(this)
        setContentView(rootContainer)

        renderHome()

        lifecycleScope.launch {
            appState = appStateRepo.load()
            activeRoutine = appState.activeRoutineId?.let { routineRepo.load(it) }
            renderHome()
        }
    }

    private fun renderHome() {
        rootContainer.removeAllViews()
        rootContainer.addView(home.buildView(activeRoutine))
    }

    // ───────────── Menu flow ─────────────

    private fun openMainMenu() {
        MainMenuDialogController(
            context = this,
            dp = dp,
            onOpenRoutines = { openRoutineList() },
            onImportRoutine = { openImport() },
        ).show()
    }

    private fun openRoutineList() = lifecycleScope.launch {
        val ids = routineRepo.list()
        val routines = ids.mapNotNull { routineRepo.load(it) }.sortedBy { it.name.lowercase() }
        RoutineListDialogController(
            context = this@MainActivity,
            dp = dp,
            activeRoutineId = appState.activeRoutineId,
            onView = { r -> openRoutinePreviewForView(r) },
            onActivate = { r -> startActivation(r) },
            onDelete = { r -> deleteRoutine(r) },
        ).show(routines)
    }

    private fun openRoutinePreviewForView(r: Routine) {
        RoutinePreviewDialogController(this, catalog, dp).show(
            routine = r,
            mode = RoutinePreviewDialogController.Mode.VIEW,
            onPrimary = { startActivation(r) },
            onSecondary = { /* close */ },
            primaryLabel = if (r.id == appState.activeRoutineId) "Re-activate" else "Activate",
            secondaryLabel = "Close",
        )
    }

    private fun deleteRoutine(r: Routine) = lifecycleScope.launch {
        val wasActive = r.id == appState.activeRoutineId
        routineRepo.delete(r.id)
        if (wasActive) {
            appState = appState.copy(activeRoutineId = null, scheduledSessions = emptyList())
            appStateRepo.save(appState)
            activeRoutine = null
            renderHome()
        }
        toast("Deleted \"${r.name}\".")
    }

    // ───────────── Import flow ─────────────

    private fun openImport() {
        RoutineImportDialogController(
            context = this,
            dp = dp,
            bundledExamples = listOf(
                RoutineImportDialogController.BundledExample(
                    assetPath = "example_routines/simple-ab-template.json",
                    displayName = "Simple A/B Template",
                    description = "Two full-body sessions that cycle Mon/Wed/Fri.",
                )
            ),
            loadBundledExample = { path ->
                assets.open(path).bufferedReader().use { it.readText() }
            },
            onParse = { json -> handleImportText(json) },
            onPickFile = {
                // Try both "application/json" and a wildcard to cover SAF providers
                // that don't register JSON-specific filtering.
                pickRoutineFile.launch(arrayOf("application/json", "*/*"))
            },
        ).show()
    }

    private fun handleImportText(json: String) {
        when (val result = RoutineImporter.parse(json, catalog)) {
            is ImportResult.Success -> previewAfterImport(result.routine)
            is ImportResult.ParseError -> showImportError("Couldn't parse JSON", result.message)
            is ImportResult.UnsupportedSchemaVersion -> showImportError(
                "Unsupported schema",
                "This routine was made by a newer (or older) DiTrain (schemaVersion=${result.schemaVersion}). Update the app.",
            )
            is ImportResult.InvalidStructure -> showImportError("Invalid routine structure", result.reason)
            is ImportResult.MissingExerciseIds -> showImportError(
                "Missing exercises",
                "These exercise ids aren't in the catalog yet:\n\n${result.missingIds.joinToString("\n") { "• $it" }}\n\nAdd them as custom exercises first, then retry.",
            )
        }
    }

    private fun showImportError(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun previewAfterImport(routine: Routine) {
        RoutinePreviewDialogController(this, catalog, dp).show(
            routine = routine,
            mode = RoutinePreviewDialogController.Mode.IMPORT,
            onPrimary = { saveImportedRoutine(routine, activate = true) },
            onSecondary = { saveImportedRoutine(routine, activate = false) },
            primaryLabel = "Save & activate",
            secondaryLabel = "Save only",
        )
    }

    private fun saveImportedRoutine(routine: Routine, activate: Boolean) = lifecycleScope.launch {
        routineRepo.save(routine)
        if (activate) {
            startActivation(routine)
        } else {
            toast("Saved \"${routine.name}\".")
        }
    }

    // ───────────── Activation flow ─────────────

    private fun startActivation(routine: Routine) {
        val pattern = routine.defaultWeeklyPattern
        if (pattern != null) {
            performActivation(routine, pattern, LocalDate.now())
        } else {
            WeeklyPatternDialogController(this, dp) { chosenPattern, startDate ->
                performActivation(routine, chosenPattern, startDate)
            }.show()
        }
    }

    private fun performActivation(
        routine: Routine,
        weeklyPattern: List<Int>,
        startDate: LocalDate,
    ) = lifecycleScope.launch {
        try {
            val scheduled = ScheduleLayout.lay(routine, weeklyPattern, startDate, futureWeeks = 8)
            appState = appState.copy(
                activeRoutineId = routine.id,
                scheduledSessions = scheduled,
            )
            appStateRepo.save(appState)
            activeRoutine = routine
            renderHome()
            toast("Activated \"${routine.name}\" with ${scheduled.size} scheduled sessions.")
        } catch (e: IllegalArgumentException) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Couldn't activate")
                .setMessage(e.message ?: "Unknown error")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // ───────────── Utilities ─────────────

    private fun dpToPx(dpValue: Int): Int =
        (dpValue * resources.displayMetrics.density).toInt()

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
```

- [ ] **Step 3: Build the APK**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
  ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" \
  ./gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ditrain/app/ui/home/HomeViewController.kt \
        app/src/main/java/com/ditrain/app/MainActivity.kt
git commit -m "feat(app): wire menu, import, list, preview, activation flows on Home"
```

---

## Task 16: Full test suite + final verification + milestone tag

- [ ] **Step 1: Run all unit tests**

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
  ANDROID_HOME="C:/Users/Usuario/AppData/Local/Android/Sdk" \
  ./gradlew.bat :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. Test count = Plan 1's 69 + Plan 2's new tests:
- `RoutineImporterTest` ~10 tests
- `ScheduleLayoutTest` ~7 tests

Total ≈ 86 tests.

- [ ] **Step 2: Build the release-signing-compatible bundle**

```bash
./gradlew.bat bundleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual smoke test on a device**

Install on the connected device and walk through:
- Launch the app → Home shows "Welcome to DiTrain" + "Import a routine" button.
- Tap the overflow ⋮ → MainMenu dialog.
- Tap "Import routine" → import dialog.
- Tap "Examples" → "Simple A/B Template" → preview shows the two sessions.
- Tap "Save & activate" → weekly pattern dialog → check Mon/Wed/Fri → "Activate".
- Home redraws with "Active routine: Simple A/B Template".
- Tap ⋮ → "Routines" → list shows the saved routine with "Active" badge.
- Tap "View" → preview dialog → tap "Close".
- Tap "Delete" → confirm → home reverts to the empty state.

If anything is wrong with the flows, fix it before continuing to the milestone tag.

- [ ] **Step 4: Tag the milestone**

```bash
git tag -a v0.2.0-routines -m "Plan 2: Routine & exercise infrastructure milestone complete"
```

---

## Plan-2 self-review

**Spec coverage** (`docs/superpowers/specs/2026-05-15-ditrain-v1-design.md`):
- §3 Package layout — `importing/RoutineImporter.kt`, `progression/ScheduleLayout.kt`, `ui/dialog/*Routine*`, `ui/dialog/*Exercise*`, `ui/dialog/MainMenu*`, `ui/dialog/WeeklyPattern*` all present ✓
- §4.1 Exercise (catalog of ~40+) — Task 2 ships 47 ✓
- §5.3 Dialog table — MainMenu, RoutineList, RoutineImport, RoutinePreview, WeeklyPattern, ExercisePicker, ExerciseDetail all built ✓
- §5.5 First-run — "No active routine" CTA in HomeViewController ✓
- §6.1 Import a routine — RoutineImporter validates schemaVersion, structure, catalog references; preview before save ✓
- §6.2 Activate a routine — ScheduleLayout enforces pattern.size >= sessions/week, lays out ONCE = exact, REPEAT = 8 weeks ahead ✓; weekly-pattern prompt on activation when `defaultWeeklyPattern` is null ✓

**Out of scope (correctly deferred):**
- Session execution + rest timer (Plan 3)
- Per-set logging, myo-rep flow, adaptation flows (Plan 3)
- Calendar dialog, e1RM chart, PR list (Plan 5)
- Backup/export, settings, glossary, about, signing config polish (Plan 6)
- Full ~150-exercise catalog (Plan 6 — Plan 2 ships 47)
- A second bundled example (Plan 6 — Plan 2 ships one to anchor the flow)

**Placeholder scan:** none.

**Type consistency check:**
- `ScheduleLayout.lay` matches the parameters used in `MainActivity.performActivation` ✓
- `ExerciseCatalog` has `byId`, `visibleExercises`, `fromAssets`, `fromInMemory` — same signatures all callers use ✓
- `RoutineImporter.parse` returns `ImportResult` — all branches handled in `MainActivity.handleImportText` ✓

**File-size sanity:** the biggest file in this plan is `MainActivity.kt` at ~220 lines. Worth watching but still focused on one responsibility (wiring controllers). If Plan 3 keeps growing it, split out a `MenuFlowController` or similar then.
