# DiTrain v1 — Design

**Status:** Draft for implementation
**Date:** 2026-05-15
**Working directory:** `C:\Users\Usuario\Documents\TrainBetter`
**Public name / package:** `DiTrain` / `com.ditrain.app`

## 1. Purpose & audience

DiTrain is an Android app for experienced strength trainees who want to propose, maintain, and track their training while keeping full freedom to adapt sessions to special needs (injury, fatigue, equipment, time). It is **methodology-agnostic** in v1: it stores raw facts and prescription/actual diffs cleanly so a multi-lens analysis layer can be added in v2 without data migration.

**v1 is single-user, local-first, generic install.** No accounts, no backend, no network calls. Backup is manual JSON-zip export via Android's Storage Access Framework. Cloud sync (Google Drive) is explicitly out of scope for v1.

**Out of scope for v1:**
- Methodology analysis layer (volume landmarks, autoregulation, fatigue) — deferred to v2.
- Google Drive integration — deferred to v2.
- Multi-user, accounts, social features, Wear OS, widgets, notifications.
- Instrumented (Espresso) UI tests.

## 2. Stack & style

DiTrain mirrors DiRead/ReadBetter (`C:\Users\Usuario\Documents\ReadBetter`) exactly:

- Kotlin `2.0.21`, AGP `8.7.3`, JDK 17, `minSdk 23`, `targetSdk 35`, `compileSdk 35`
- Dependencies: `androidx.appcompat:appcompat:1.7.0`, `org.jetbrains.kotlinx:kotlinx-serialization-json` (the one real addition, for routine JSON), `junit:junit:4.13.2`
- Theme: `Theme.AppCompat.DayNight.NoActionBar`
- Single `MainActivity` + many dialog controllers (DiRead has ~10 in `ui/dialog/`)
- Programmatic UI in Kotlin — **no XML layouts** beyond `styles.xml` and the manifest
- Custom Views for novel widgets (e.g., `E1rmChartView`, `CalendarHeatView`, `SetEntryView`)
- `ViewStyling.kt` helper with `GradientDrawable` rounded backgrounds, ported from DiRead and extended
- File-based storage via repository classes, no Room/SQLite
- No Compose, no Material library, no Retrofit, no DI framework, no reactive framework
- Coroutines on `Dispatchers.IO` for file I/O, plain callbacks back to UI
- Portrait-only, single screen orientation (matches DiRead)

The aesthetic commitment is the minimalist, dialog-driven, no-framework-bloat shape of DiRead. The default answer for any tool/library/pattern decision is "what does DiRead do here?" Deviate only with a documented reason (kotlinx-serialization is justified because rolling a JSON parser is silly).

## 3. Package layout

```
com.ditrain.app
├── MainActivity.kt
├── model/
│   ├── Routine.kt              // Routine, Week, SessionTemplate, ExerciseBlock,
│   │                           //   SetPrescription (sealed), RepsTarget, LoadTarget,
│   │                           //   LoopMode
│   ├── Exercise.kt             // Exercise, MovementPattern, MuscleGroup, Equipment
│   ├── SessionLog.kt           // SessionLog, ExecutedExercise, LoggedSet (sealed),
│   │                           //   MiniSet
│   ├── Units.kt                // WeightUnit, kg<->lb conversions
│   └── Rpe.kt                  // EffortMode, RPE/RIR value types
├── storage/
│   ├── RoutineRepository.kt    // one JSON per routine in filesDir/routines/
│   ├── SessionLogRepository.kt // single filesDir/logs/sessions.json + archive rollover
│   ├── ExerciseCatalog.kt      // bundled assets/exercises.json + filesDir/custom_exercises.json
│   ├── AppState.kt             // filesDir/state.json (active routine, scheduled sessions, settings)
│   └── BackupArchive.kt        // zip/unzip export bundle
├── importing/
│   └── RoutineImporter.kt      // JSON -> Routine, validates against catalog
├── progression/
│   ├── E1rm.kt                 // Epley with RPE adjustment; Brzycki noted but unused
│   └── PrDetection.kt          // best e1RM / best load / best reps-at-load
├── ui/
│   ├── ViewStyling.kt          // ported from DiRead; extended with chip/dangerButton/setRowBg
│   ├── home/
│   │   └── HomeViewController.kt
│   ├── session/
│   │   ├── SessionEditorController.kt
│   │   └── SetEntryView.kt
│   ├── dialog/
│   │   ├── MainMenuController.kt
│   │   ├── RoutineListDialogController.kt
│   │   ├── RoutineImportDialogController.kt
│   │   ├── RoutinePreviewDialogController.kt
│   │   ├── WeeklyPatternDialogController.kt
│   │   ├── ExercisePickerDialogController.kt
│   │   ├── ExerciseDetailDialogController.kt
│   │   ├── ExerciseHistoryDialogController.kt
│   │   ├── SessionCalendarDialogController.kt
│   │   ├── E1rmChartDialogController.kt
│   │   ├── PrListDialogController.kt
│   │   ├── SettingsDialogController.kt
│   │   ├── BackupDialogController.kt
│   │   ├── AbortConfirmDialogController.kt
│   │   ├── AboutDialogController.kt
│   │   └── GlossaryDialogController.kt
│   └── view/
│       ├── E1rmChartView.kt
│       └── CalendarHeatView.kt
└── util/
    ├── DateFmt.kt
    ├── JsonIo.kt
    └── AtomicWrite.kt          // write-tmp-then-rename helper
```

## 4. Data model

All persisted entities are `@Serializable` (kotlinx-serialization). Sealed types use `@SerialName` discriminators.

### 4.1 `Exercise` (catalog entry)

```kotlin
@Serializable data class Exercise(
    val id: String,                  // stable slug, e.g. "barbell-back-squat"
    val name: String,                // display name
    val aliases: List<String> = emptyList(),
    val pattern: MovementPattern,    // SQUAT, HINGE, HORIZONTAL_PUSH, VERTICAL_PUSH,
                                     // HORIZONTAL_PULL, VERTICAL_PULL, LUNGE, CARRY,
                                     // CORE, ISOLATION
    val primaryMuscles: List<MuscleGroup>,   // QUADS, HAMSTRINGS, GLUTES, CHEST,
                                             // UPPER_BACK, LATS, FRONT_DELT, SIDE_DELT,
                                             // REAR_DELT, BICEPS, TRICEPS, FOREARMS,
                                             // ABS, OBLIQUES, CALVES, LOWER_BACK,
                                             // ADDUCTORS, ABDUCTORS, TRAPS, NECK
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val equipment: List<Equipment>,  // BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT, BAND, OTHER
    val descriptionUrl: String?,     // e.g. ExRx page; null for user-created
    val custom: Boolean = false,
    val parentId: String? = null,    // when this is a fork of a bundled exercise
)
```

Bundled catalog lives at `app/src/main/assets/exercises.json` and ships ~150 entries. User-added customs live at `filesDir/custom_exercises.json` and merge into the in-memory `ExerciseCatalog` at load time.

### 4.2 `Routine` (template — fixed or mesocycle, unified)

```kotlin
@Serializable data class Routine(
    val id: String,
    val name: String,
    val description: String? = null,
    val author: String? = null,
    val schemaVersion: Int = 1,
    val loopMode: LoopMode,                // ONCE (mesocycle) or REPEAT (template)
    val weeks: List<Week>,                 // a 1-week REPEAT routine is just weeks.size == 1
    val defaultWeeklyPattern: List<Int>?,  // optional: weekday indices the user trains
                                           // (0=Mon..6=Sun). Null = prompt on activation.
)

@Serializable data class Week(
    val label: String,                     // "Week 1", "Deload", "Heavy", etc.
    val sessions: List<SessionTemplate>,
)

@Serializable data class SessionTemplate(
    val id: String,                        // stable within the routine, e.g. "push-a"
    val name: String,                      // "Push A"
    val blocks: List<ExerciseBlock>,
)

@Serializable data class ExerciseBlock(
    val exerciseId: String,                // references Exercise.id
    val notes: String? = null,
    val sets: List<SetPrescription>,       // ordered; size = prescribed set count
)

@Serializable sealed interface SetPrescription {
    val rest: Int?                         // seconds; null = unspecified
    val tempo: String?                     // e.g. "3-1-1"; null = unspecified
    val notes: String?

    @Serializable @SerialName("straight")
    data class Straight(
        val reps: RepsTarget,              // FIXED(n) | RANGE(a,b) | AMRAP | AMRAP_MIN(n)
        val load: LoadTarget,              // ABSOLUTE_KG(x) | PCT_1RM(p) | RPE_TARGET(r)
                                           //  | RELATIVE_TO_LAST(deltaKg) | OPEN
        val rpeTarget: Double? = null,     // optional RPE cap independent of load type
        override val rest: Int? = null,
        override val tempo: String? = null,
        override val notes: String? = null,
    ) : SetPrescription

    @Serializable @SerialName("myo_rep")
    data class MyoRep(
        val activationReps: RepsTarget,
        val load: LoadTarget,
        val miniSetTargetReps: Int,        // e.g. 5
        val miniSetCount: Int,             // target number of mini-sets (may stop short)
        val miniSetRestSec: Int = 15,
        val rpeStopThreshold: Double? = 10.0,  // stop mini-sets when this RPE hit
        override val rest: Int? = null,
        override val tempo: String? = null,
        override val notes: String? = null,
    ) : SetPrescription
}
```

`RepsTarget`, `LoadTarget`, `LoopMode`, `MovementPattern`, `MuscleGroup`, `Equipment` are sealed/enum types under `model/`.

Persisted at `filesDir/routines/{routine.id}.json` — one routine per file.

### 4.3 `SessionLog` (what actually happened)

```kotlin
@Serializable data class SessionLog(
    val id: String,                        // uuid
    val routineId: String?,                // null if ad-hoc / unaffiliated session
    val weekIndex: Int?,                   // index into Routine.weeks; null for ad-hoc
    val sessionTemplateId: String?,        // null for ad-hoc
    val scheduledDate: LocalDateIso,
    val performedDate: LocalDateIso,       // may differ from scheduledDate (reschedule)
    val startedAt: InstantIso,
    val completedAt: InstantIso?,          // null while in-progress
    val executed: List<ExecutedExercise>,
    val notes: String? = null,
)

@Serializable data class ExecutedExercise(
    val exerciseId: String,                // final exerciseId actually performed
    val substitutedFromId: String? = null, // if one-time sub, original prescribed exercise
    val skipped: Boolean = false,
    val sets: List<LoggedSet>,
)

@Serializable sealed interface LoggedSet {
    val performedAt: InstantIso
    val notes: String?

    @Serializable @SerialName("straight")
    data class Straight(
        val weightKg: Double,              // ALWAYS stored kg; UI toggles display
        val reps: Int,
        val rpe: Double? = null,
        val rir: Int? = null,
        val restSec: Int? = null,
        val tempo: String? = null,
        override val performedAt: InstantIso,
        override val notes: String? = null,
    ) : LoggedSet

    @Serializable @SerialName("myo_rep")
    data class MyoRep(
        val weightKg: Double,
        val activationReps: Int,
        val activationRpe: Double? = null,
        val miniSets: List<MiniSet>,
        val restSec: Int? = null,
        val tempo: String? = null,
        override val performedAt: InstantIso,
        override val notes: String? = null,
    ) : LoggedSet
}

@Serializable data class MiniSet(val reps: Int, val rpe: Double? = null)
```

**Storage:** all session logs persist to a single JSON array at `filesDir/logs/sessions.json`, sorted by `performedDate` ascending.

**Rollover:** when `sessions.json` exceeds **1 MB** on any write, the file is atomically renamed to `filesDir/logs/archive/sessions-{firstDate}_{lastDate}.json` (using the min/max `performedDate` in the file) and a fresh empty `sessions.json` starts the next session log. The threshold is generous — ~200 typical sessions, roughly a year of heavy training — so rollover is rare. The threshold is a single constant in `SessionLogRepository` (`val ROLLOVER_BYTES = 1L shl 20`) and trivially adjustable later.

**Reads:** the repository loads `sessions.json` eagerly on first access. Archive files are loaded lazily and only when a query's date range overlaps an archive's filename-encoded range. The repository caches an in-memory index of `archiveFile -> dateRange` on startup. The active sorted list returned to callers is a merged view; archive lookup is transparent.

Rationale for single-file-plus-rollover over monthly partitioning: data volume is small enough that partitioning by date yields no real benefit, and "one file, occasionally rolled" is simpler to reason about, to back up by hand, and to debug.

**Internal weight unit is always kg.** UI converts at the edge. Conversion uses `1 lb = 0.45359237 kg` exactly. Display rounds to 0.5 kg or 1 lb increments; internal math never rounds.

**`LoggedSet` is a separate sealed type from `SetPrescription`**, not a mutation of it. Prescription is the immutable plan; the log records what happened. This makes prescribed-vs-actual diffs trivial and lets the v2 analysis layer compare them.

### 4.4 `AppState`

```kotlin
@Serializable data class AppState(
    val schemaVersion: Int = 1,
    val activeRoutineId: String?,
    val scheduledSessions: List<ScheduledSession>,
    val settings: Settings,
)

@Serializable data class ScheduledSession(
    val date: LocalDateIso,
    val routineId: String,
    val weekIndex: Int,
    val sessionTemplateId: String,
    val sessionLogId: String? = null,    // set once user starts logging it
)

@Serializable data class Settings(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val effortMode: EffortMode = EffortMode.RPE,      // RPE or RIR in the UI
    val barWeightKg: Double = 20.0,
    val theme: ThemeMode = ThemeMode.SYSTEM,
)
```

Persisted at `filesDir/state.json`. Whole-file rewrite on every change (file is small).

### 4.5 Backup archive

Manual export bundles, into a single `.zip`:

```
exercises_custom.json
routines/*.json
logs/sessions.json
logs/archive/sessions-*.json     // only present if rollover has occurred
state.json
manifest.json                    // { schemaVersion, exportedAt, appVersion }
```

Import unzips into a staging dir, validates `manifest.schemaVersion`, then applies per a user-chosen conflict mode (see 6.5).

## 5. UI & navigation

`MainActivity` shows one of two top-level views at a time, swapped via a `HomeLayoutController` state machine (DiRead's `ReaderLayoutController` pattern).

### 5.1 Home

Vertical scrollable column. No bottom nav, no toolbar.

```
┌───────────────────────────────────────┐
│   DiTrain                       ⋮     │  overflow icon → MainMenu dialog
├───────────────────────────────────────┤
│   TODAY · Thu, May 14                 │
│   Push A · Week 2 of Mesocycle 1      │
│   [ Start session ]                   │
│   [ Reschedule… ]                     │
│   ──────────  History  ──────────     │
│   Last session · Tue, May 12          │
│   Pull A · 6 exercises · 24 sets      │
│   [ Calendar ] [ Per-ex ] [ PRs ]     │
│   [ e1RM ]                            │
└───────────────────────────────────────┘
```

- No active routine: top block replaced by "Import a routine to get started" CTA opening `RoutineImportDialogController`.
- Today has no scheduled session: "Today · rest day · next session: Sat, May 16 (Push B)" + a "Train anyway" button that opens a session picker.
- In-progress session exists: "Start session" becomes "Resume session" (see 6.3).

### 5.2 Session editor

```
┌───────────────────────────────────────┐
│  ◄ Push A · 1/5            16:42  ✕   │
├───────────────────────────────────────┤
│   Barbell Bench Press                 │  tap → ExercisePickerDialog for substitution
│   prescribed: 4×6 @ RPE 8             │
│   notes: paused                       │
│  ┌─────────────────────────────────┐  │
│  │ Set 1   60 kg × 6 @ 7   12:01   │  │  logged sets render compact
│  │ Set 2   60 kg × 6 @ 8   12:04   │  │
│  │ Set 3   [weight][reps][RPE]…    │  │  active set row (SetEntryView)
│  │         [note][ Log ]           │  │
│  └─────────────────────────────────┘  │
│  + Add set    ⤵ Skip exercise         │
├───────────────────────────────────────┤
│   ⏱  Rest: 1:32 / 2:00         ◄ ►    │
└───────────────────────────────────────┘
```

- Swipe horizontally or `◄ ►` to switch exercise; small dot strip top-of-screen shows progress.
- `SetEntryView` has two layouts: **straight** (weight, reps, RPE/RIR, optional note, Log) and **myo-rep** (activation row, then one mini-set row at a time appearing as the previous is logged; "End cluster" button finishes early).
- "Finish session" button appears in the bottom strip once every prescribed exercise is either fully logged or marked skipped.

### 5.3 Dialogs

| Dialog | Triggered from | Purpose |
|---|---|---|
| **MainMenu** | `⋮` overflow | Routines, Import routine, Export/Import backup, Settings, Glossary, About |
| **RoutineList** | MainMenu → Routines | Per-row: activate, view, duplicate, delete |
| **RoutineImport** | MainMenu → Import routine | Paste JSON · Pick file (SAF) · Bundled examples |
| **RoutinePreview** | RoutineList → view, RoutineImport | Read-only structural view; **Save** and **Save & activate** |
| **WeeklyPattern** | Routine activation when `defaultWeeklyPattern` is null | Mon–Sun checkboxes + Start-on date picker |
| **ExercisePicker** | Session editor → tap exercise name; also "Add ad-hoc" | Searchable list with filters by pattern/muscle/equipment. Actions: Substitute (today only), Substitute (rest of program), Add ad-hoc to this session |
| **ExerciseDetail** | Long-press exercise name; ExercisePicker | Muscles/pattern/equipment + "Open description URL" (Intent.ACTION_VIEW) + "Show history" |
| **ExerciseHistory** | ExerciseDetail; Home → Per-ex | Reverse-chronological log of all sets for one exercise. Date-range filter at top |
| **SessionCalendar** | Home → Reschedule, Home → Calendar | Month grid with three render layers: completed sessions, future scheduled, empty days. Drag-and-drop reschedule; tap future → quick-action sheet |
| **E1rmChart** | Home → e1RM | Pick exercise → `E1rmChartView` line plot; tap to inspect a point |
| **PrList** | Home → PRs | Per-exercise: best e1RM, best load, best reps-at-load. Tap → ExerciseHistory filtered |
| **Settings** | MainMenu → Settings | Weight unit (kg/lb), Effort mode (RPE/RIR), Bar weight, Theme |
| **Backup** | MainMenu → Export/Import backup | Export now (SAF), Import from file (SAF), replace-vs-merge |
| **AbortConfirm** | Session editor → ✕ | Save in-progress / Discard / Cancel |
| **About** | MainMenu → About | Version, source link, license, brief usage tips, e1RM formula doc |
| **Glossary** | MainMenu → Glossary | Searchable list of all DiTrain terms with definitions, sourced from bundled `assets/glossary.json`. Same content as §12 of this spec. |

### 5.4 Styling

`ViewStyling.kt` is ported from DiRead verbatim and extended:
- `setRowBackground(filled: Boolean, isPr: Boolean)` distinguishes logged set, active set, and PR-tagged sets (subtle accent border for PRs)
- `chip(text, fillColor)` for weekday markers in the calendar and routine metadata
- `dangerButton(...)` red-tinted variant for "Discard session," "Delete routine"

Icons: Android system drawables (`@android:drawable/ic_menu_*`) or unicode glyphs in TextViews. No vector icon library.

### 5.5 First-run experience

If no routines exist on launch:
1. Brief welcome card on Home explaining DiTrain.
2. Two visible CTAs: "Try an example routine" (loads from bundled assets) and "Import your own (JSON)".
3. Bundled examples (in `app/src/main/assets/example_routines/`): 3–4 routines covering different shapes — a 4-week mesocycle, an A/B/A/B fixed template, a 5/3/1-style block, a higher-frequency hypertrophy split. They double as schema documentation.

## 6. Key flows

### 6.1 Import a routine

1. MainMenu → Import routine → `RoutineImportDialogController`.
2. Three entry sub-tabs: Paste JSON, Pick file (SAF `ACTION_OPEN_DOCUMENT`, mime `application/json` / `*/*`), Bundled examples.
3. `RoutineImporter.parse(json)`:
   - Deserialize. Catch `SerializationException` → show "Couldn't parse: <line/col + message>".
   - Validate every `ExerciseBlock.exerciseId` resolves in `ExerciseCatalog`. Missing → block import, list missing IDs, offer "Open exercise picker to map each missing ID" or Cancel.
   - Validate `schemaVersion == 1`.
   - Validate structure: at least one week with at least one session with at least one block.
4. Show `RoutinePreview` with **Save** and **Save & activate**.
5. Save writes `filesDir/routines/{id}.json`. Conflict on existing id → Replace / Save as copy / Cancel.

### 6.2 Activate a routine and lay out the calendar

1. Tap **Activate** on a routine (or "Save & activate" from import).
2. If a routine is already active and has uncompleted scheduled sessions: confirm — "Switch active routine? Future scheduled sessions for <current> will be removed. Completed sessions stay in history." Replace, don't merge.
3. If `Routine.defaultWeeklyPattern` is null: prompt `WeeklyPatternDialog` (Mon–Sun + Start-on date, default today).
4. Lay out `ScheduledSession`s:
   - `loopMode = ONCE`: schedule exactly `weeks.size × sessionsPerWeek` rows.
   - `loopMode = REPEAT`: schedule the next **8 weeks** starting from "Start on" date.
5. Distribution: walk the chosen weekdays in order, placing `weeks[w].sessions[s]` onto consecutive matching weekdays. If routine prescribes more sessions/week than the user chose weekdays, activation fails with "routine prescribes N sessions/week but only M training days selected".
6. Persist to `AppState`, return to Home.
7. **Window extension**: when ≤ 2 weeks of unscheduled sessions remain ahead after each completed session (REPEAT mode only), extend by 4 more weeks.

### 6.3 Run a session

**Start:**
1. Home → **Start session** picks the earliest `ScheduledSession` with `date <= today` and `sessionLogId == null`. If today is empty, the button is hidden; user must pick via Reschedule.
2. `SessionEditorController` creates a `SessionLog` in memory: copies the template, sets `startedAt = now`, `scheduledDate` from the `ScheduledSession`, `performedDate = today`. Sets `ScheduledSession.sessionLogId` immediately and persists `state.json` so a crash mid-session can resume.
3. Each prescribed `ExerciseBlock` becomes an `ExecutedExercise` with empty `sets`.

**During:**
4. Active set row inputs default from prescription.
5. **Load resolution** at row creation:
   - `ABSOLUTE_KG(x)` → fill `x` (converted for display).
   - `PCT_1RM(p)` → fetch latest e1RM for this exercise; fill `p × e1RM`, rounded to nearest 0.5 kg or 1 lb depending on display unit.
   - `RPE_TARGET(r)` → leave weight empty, show RPE target as a hint.
   - `RELATIVE_TO_LAST(δ)` → look up the same exercise from the most recent completed `SessionLog`; fill `lastTopWeight + δ`. If no prior session, leave empty + hint.
   - `OPEN` → leave empty.
6. **Log a set**: validate `weight > 0` and `reps > 0`, append to `LoggedSet` list. Auto-start rest timer seeded from the next set's prescribed `rest`; the logged `restSec` of the just-logged set records actual elapsed rest since the previous logged set. Persist the `SessionLog` to `sessions.json` on every log (whole-file rewrite; triggers rollover if the threshold is crossed).
7. **Myo-rep flow**: active row starts as activation set with `activationReps` prescribed. On Log, the row collapses and a mini-set row appears. User logs each mini-set. **End cluster** button or hitting `miniSetCount` collapses the whole `MyoRep` into a single logged entry.
8. **One-time exercise sub**: tap exercise name → ExercisePicker → "Substitute (today only)". Updates `ExecutedExercise.exerciseId`, sets `substitutedFromId`. Prescribed sets stay (user decides whether to follow them).
9. **Persistent sub**: ExercisePicker → "Substitute (rest of program)". Mutates the active `Routine` (rewrites `{id}.json`); all future weeks' `ExerciseBlock`s referencing `substitutedFromId` get their `exerciseId` changed. A sidecar `routine.history.json` records each persistent edit (timestamp, from, to, scope).
10. **Skip exercise**: confirm → `ExecutedExercise.skipped = true`, sets list stays empty.
11. **Add ad-hoc exercise**: ExercisePicker → "Add to today only". Appends a new `ExecutedExercise` with no prescription parent. User logs sets freely.

**Finish:**
12. **✕ Abort**: AbortConfirm → Save in-progress (partial `SessionLog` persists, shown as "Resume session" on Home) or Discard (delete `SessionLog`, clear `ScheduledSession.sessionLogId`).
13. **Finish session**: when all prescribed exercises are fully logged or marked skipped, a **Finish** button appears in the bottom strip. On tap, set `completedAt = now`, persist. Advance the cursor.

Mid-session reschedule is disallowed — must finish or abort first.

### 6.4 Reschedule

1. Home → Reschedule → `SessionCalendarDialog`.
2. Month grid with three render layers: completed sessions (badge), scheduled future (chip with drag handle), empty days (tap-to-add).
3. Long-press a chip → drag to another day. **Only that one session moves; nothing cascades.** Users wanted "free range to adapt" — auto-cascading would override their intent.
4. Tap a future scheduled session → quick-action sheet: **Move to today**, **Move to…**, **Skip / delete**.
5. Cursor = whichever scheduled session has the earliest `date ≥ today`. Tie-breaker: earlier-in-routine-order.
6. Past unfilled scheduled sessions become **Missed** at app launch (per day rollover) and group at the top of the calendar. Not auto-deleted — user can long-press to remove or "Reschedule to today".

### 6.5 Export / import backup

**Export:**
1. MainMenu → Backup → Export now.
2. `BackupArchive.export()` writes a `.zip` to `cacheDir`, then launches `ACTION_CREATE_DOCUMENT` (SAF) with mime `application/zip`, suggested name `ditrain-backup-{YYYYMMDD-HHMM}.zip`.
3. User picks destination. System copies the temp file. Delete temp on success.

**Import:**
1. MainMenu → Backup → Import from file → SAF `ACTION_OPEN_DOCUMENT`, mime `application/zip`.
2. Unzip to `cacheDir/import-staging/`. Read `manifest.json`; fail-fast if `schemaVersion` exceeds supported.
3. Summary: "N routines, M months of logs, K custom exercises, exported on <date> from app v<x>".
4. **Conflict mode**: radio — **Replace everything** (default) or **Merge** (routines union by id with import winning; custom exercises union by id with import winning; logs concatenate by month, duplicate `SessionLog.id` skipped; state.json from import wins).
5. Confirm → snapshot `filesDir` to `cacheDir/import-rollback/`, apply file-by-file. Any failure restores from snapshot. Clean staging on success.

## 7. Error handling

**File I/O failures:**
- Reads: every `Json.decodeFromString(...)` wrapped in `runCatching`. On failure, log to in-memory `ErrorLog`, surface a non-modal banner on Home ("Couldn't load routine 'XYZ' — file may be corrupt. Tap for details"), continue with the rest. Never crash on a single bad file.
- Writes: write to `{path}.tmp`, fsync, atomic rename (`AtomicWrite` helper). If rename fails, toast + abort the user action — don't claim a set is logged when it isn't.
- SAF: `Dispatchers.IO`, progress callbacks, `finally` closes. Failure → toast + log.

**Corrupt `sessions.json`:** parse fails → rename to `sessions.corrupt.{timestamp}.json`, start fresh empty file, surface banner. Rename rather than delete: lets users recover manually.

**Corrupt archive file:** parse fails for one archive → skip it for queries (banner names which file), continue serving other archives + live `sessions.json`. User can move the renamed `.corrupt` file aside and import-merge a backup to restore.

**Missing exercise referenced by a SessionLog or Routine** (only possible after hand-editing or future-schema import): synthesize `Exercise(id=id, name="(unknown: $id)", pattern=ISOLATION, ...)` in memory. Never crash, never drop the log entry. Settings shows a "Resolve unknown exercises" entry that remaps each unknown ID to a real exercise (rewrites affected log/routine files).

**Schema version drift:**
- File `schemaVersion > current`: refuse, banner "Made by newer DiTrain. Update the app." No partial parse.
- File `schemaVersion < current`: route through `MigrationRegistry` (empty in v1, future-proofing). Migrations rewrite the file on read.

## 8. Edge cases

- **First-ever launch:** no `state.json`, no routines, empty logs dir, empty customs. `AppState` initialized to defaults. Home renders first-run experience (5.5).
- **Scheduled vs. performed date drift:** session scheduled on one day but performed on another stays in `sessions.json` ordered by `performedDate`. The `ScheduledSession` retains a back-reference via `sessionLogId`. All history views sort by `performedDate`.
- **Time zone / DST:** dates use `LocalDate` (no zone). Instants use UTC ISO-8601. Display in device local time. Rest timer uses `SystemClock.elapsedRealtime()`, not wall clock.
- **App backgrounded during rest timer:** ticker uses `SystemClock.elapsedRealtime()`, so it stays accurate. No notification fires (out of scope) — user must reopen the app.
- **kg/lb display:** JSON always stores `weightKg`. UI converts at the edge. Display rounding (0.5 kg / 1 lb); internal math never rounds.
- **e1RM with no priors:** every formula needs ≥ 1 logged set with `weight > 0` and `reps >= 1`. If none, chart renders "No data yet" and `PCT_1RM` prescriptions leave weight blank with a hint.
- **e1RM formula:** primary = Epley with RPE adjustment when RPE available: `weight × (1 + reps/30) × (1 + 0.0333 × (10 - RPE))`. Fallback = plain Epley. Documented in About.
- **Two routines active simultaneously:** not supported. `AppState.activeRoutineId` is single-valued.
- **Resume mid-session across restarts:** `SessionLog.completedAt == null` && `ScheduledSession.sessionLogId != null` → Home shows "Resume session".
- **Routine deleted while active:** confirm → remove from `routines/`, clear `activeRoutineId`, clear future `ScheduledSessions` for that routine. Past `SessionLog`s keep `routineId` as orphan string — history renders "(routine deleted)".
- **Custom exercise referenced by a routine, then deleted:** prompt "X routines and Y sessions reference this — delete anyway?" If confirmed, the exercise becomes an unknown ID handled per schema-drift rules.
- **Backup on fresh install** = full restore. Backup with existing data = the conflict-mode choice governs.

## 9. Testing

JUnit only (matches DiRead's test setup). No Espresso in v1.

**`model/`:**
- `RoutineSerializationTest`: round-trip every sealed subtype of `SetPrescription`.
- `SessionLogSerializationTest`: round-trip `LoggedSet`, including myo-rep with empty/partial mini-sets.
- `LoopMode == REPEAT` with one week round-trips identically to a fixed template.

**`storage/`** (use a `tempDirectoryRule()`-style helper):
- `RoutineRepositoryTest`: save → load → equals; overwrite; delete; list.
- `SessionLogRepositoryTest`: write/read/update/delete round-trip; sorted-by-`performedDate` invariant holds across writes. **Rollover**: writes that push `sessions.json` past 1 MB atomically produce `archive/sessions-{firstDate}_{lastDate}.json` with correct date range; `sessions.json` is empty afterward; the next write succeeds and is preserved. **Lazy archive load**: a date-range query that doesn't overlap an archive does not open it; one that does, opens and merges. **Merged ordering**: queries spanning live + archive return one chronological list.
- `BackupArchiveTest`: export → import in a fresh dir produces identical `filesDir` contents. Replace vs. merge modes. Corrupt zip rejected cleanly.
- `AppStateTest`: schedule layout for a 4-week mesocycle × 3 weekday pattern produces exactly 4×3 entries on correct weekdays. REPEAT mode produces 8 weeks ahead. Window-extension triggers at ≤ 2 weeks remaining.

**`importing/`:**
- `RoutineImporterTest`: each bundled example parses successfully and validates against the catalog. Tampered JSONs (missing exerciseId, missing schemaVersion, mismatched weekly pattern) fail with the expected error type.

**`progression/`:**
- `E1rmTest`: Epley-RPE formula matches known reference values within 0.1 kg (e.g., 100 kg × 5 @ RPE 8 → ~123 kg). Empty input → null.
- `PrDetectionTest`: best e1RM, best weight, best reps-at-load correctly detected across a synthetic log. Ties broken by earliest date.

**Manual / device testing:**
- Each dialog renders and dismisses in portrait, day and night themes.
- SAF flows (export/import, file pick for routine) succeed on a real device — emulator SAF is unreliable.
- Long-running session: 50+ sets across 8 exercises, including myo-rep clusters. File stays small (<50 KB), no jank, rest timer accurate.
- Backup round-trip on a real device with non-trivial data.

**Deliberately out of scope for v1 tests:** Espresso instrumented UI; property-based schedule-layout tests; performance benchmarking.

## 10. Build & release

- Mirror DiRead's `build.gradle.kts` (signing config from `keystore.properties`, `assembleDebug` / `bundleRelease`).
- Add `kotlinx-serialization-json` and apply the Kotlin serialization Gradle plugin.
- `versionCode 1`, `versionName "0.1.0"`.
- No new manifest permissions. Storage uses SAF (no `READ/WRITE_EXTERNAL_STORAGE`). No network.

## 11. Forward compatibility notes

- All persisted entities carry `schemaVersion`. `MigrationRegistry` ships empty.
- `LoggedSet` and `SetPrescription` are sealed — new variants land cleanly via `@SerialName`.
- v2 analysis layer reads only `SessionLog` and `Routine` files. No schema change required: it's a derived view.
- Persistent-sub `routine.history.json` sidecar already records edits; v2 can surface a "view routine history / revert" UI.
- Drive auto-backup in v2 reuses the same `BackupArchive` zip format; only the destination changes.

## 12. Glossary

This section defines every term DiTrain surfaces in its UI or routine JSON schema. The same content ships in `app/src/main/assets/glossary.json` and is rendered by `GlossaryDialogController` (MainMenu → Glossary) so the in-app text never drifts from this spec. The asset file is a JSON array of `{ term, category, definition }` records; entries are sorted by category then term in the dialog.

### 12.1 Programming

- **Routine** — a structured training program. In DiTrain, a routine has weeks, each week has sessions, each session has exercise blocks with prescribed sets. See *Mesocycle* and *Template*.
- **Mesocycle** — a routine with `loopMode = ONCE`: runs for a fixed number of weeks and ends.
- **Template** — informal name for a routine with `loopMode = REPEAT`: weeks cycle indefinitely.
- **Week** — a labelled group of sessions within a routine (e.g., "Week 1", "Heavy", "Deload").
- **Deload** — a week with deliberately reduced volume/intensity to dissipate fatigue. DiTrain doesn't enforce a deload pattern; it's a convention authors of routines may use as a Week label.
- **Session / Workout** — one training day's prescribed work. A session belongs to a week and lists its exercise blocks in order.
- **Block / Exercise block** — within a session, one exercise plus its prescribed sets.
- **Loop mode** — `ONCE` (mesocycle ends after N weeks) or `REPEAT` (weeks cycle indefinitely).
- **Weekly pattern** — the weekdays on which the user trains (e.g., Mon/Wed/Fri). Used at routine-activation time to lay out the calendar.

### 12.2 Sets and reps

- **Set** — a group of reps performed without rest.
- **Rep** — one full repetition of an exercise.
- **Straight set** — a single block of reps at one weight, no clusters.
- **Top set** — typically the heaviest set of an exercise on a given day. Surfaced in DiTrain as "best e1RM" / "best load" entries in the PR list.
- **AMRAP (As Many Reps As Possible)** — a prescription instructing the user to perform as many reps as they can with the prescribed weight. Often used for top sets.
- **Tempo** — speed of each rep, written as `eccentric-pause-concentric` in seconds (e.g., `3-1-1` = 3 sec down, 1 sec pause, 1 sec up).
- **Rest** — recovery time between sets, in seconds. May be prescribed by the routine or chosen freely.

### 12.3 Myo-reps

- **Myo-rep set** — a cluster technique where one activation set is followed by short-rest mini-sets at the same weight, accumulating fatigue with high stimulus efficiency. Stored as a single `MyoRep` set in DiTrain, not as separate sets.
- **Activation set** — the first set in a myo-rep cluster, typically taken to a high effort level (e.g., RPE 9 at 15 reps).
- **Mini-set** — each follow-up set in a myo-rep cluster, short and small (e.g., 5 reps with 15 sec rest), continuing until target count or stop threshold is hit.

### 12.4 Effort and load

- **RPE (Rate of Perceived Exertion)** — a 1–10 scale (with half-points) for self-rated set difficulty. 10 = absolute max, 8 = ~2 reps in reserve. DiTrain accepts half-points (e.g., 8.5).
- **RIR (Reps In Reserve)** — integer 0–5 estimating how many more reps could have been completed before failure. Equivalent to RPE for most users: RIR ≈ 10 − RPE.
- **1RM (One-rep max)** — the maximum weight that can be lifted for one rep with proper form.
- **e1RM (estimated 1RM)** — 1RM estimated from a sub-maximal set. DiTrain uses Epley with RPE adjustment: `weight × (1 + reps/30) × (1 + 0.0333 × (10 − RPE))`. Falls back to plain Epley when RPE missing.
- **%1RM** — load expressed as a percentage of estimated 1RM. DiTrain resolves to an absolute kg value at the moment a set is prescribed, using the most recent e1RM available for that exercise.
- **Bar weight** — the empty weight of the bar used for barbell exercises. Default 20 kg, configurable in Settings; affects load-suggestion display.
- **PR (Personal Record)** — best historical performance on an exercise. DiTrain tracks three PR types: best e1RM, best weight (for any reps), and best reps at a given weight.

### 12.5 Movement patterns

- **Squat** — knee-dominant, vertical-torso lower-body lift.
- **Hinge** — hip-dominant lower-body lift, e.g., deadlift, RDL, good morning.
- **Horizontal push** — pressing weight away from the chest horizontally, e.g., bench press, push-up.
- **Horizontal pull** — pulling weight to the torso horizontally, e.g., barbell row.
- **Vertical push** — pressing weight overhead, e.g., overhead press.
- **Vertical pull** — pulling weight down from overhead, e.g., pull-up, lat pull-down.
- **Lunge** — single-leg lower-body lift, e.g., walking lunge, Bulgarian split squat.
- **Carry** — loaded gait, e.g., farmer's carry, suitcase carry.
- **Core** — direct trunk work, e.g., plank, hanging leg raise.
- **Isolation** — single-joint movement, e.g., biceps curl, lateral raise.

### 12.6 Splits (informal — referenced in bundled examples, not stored in DiTrain's data model)

- **Full body** — every session trains the whole body. High frequency per muscle.
- **Upper/Lower** — alternating upper- and lower-body sessions. Typical 4-day-per-week split.
- **Push/Pull/Legs (PPL)** — three session types repeating: pushing muscles, pulling muscles, lower body. Typical 6-day split (PPL twice).
- **Body part split** ("bro split") — one body part per session. Lower frequency per muscle.

### 12.7 Muscle groups

DiTrain's catalog tags each exercise with primary and secondary muscles. Categories: **quads, hamstrings, glutes, chest, upper back, lats, front delt, side delt, rear delt, biceps, triceps, forearms, abs, obliques, lower back, calves, traps, neck, adductors, abductors**.

### 12.8 Equipment

Categories: **barbell, dumbbell, machine, cable, bodyweight, band, other**. Exercises can list multiple (e.g., barbell + cable for landmine variants).

### 12.9 Adaptation actions (DiTrain-specific)

- **Substitute (today only)** — replace a prescribed exercise with another from the catalog for this session only. The original prescription stays in the routine.
- **Substitute (rest of program)** — replace an exercise in the routine itself, going forward. Edits the routine file; a sidecar `routine.history.json` records the change for auditability.
- **Skip exercise** — mark a prescribed exercise as not performed in this session, with optional reason note.
- **Ad-hoc exercise** — an exercise added during the session that wasn't in the prescription. Logged but not attached to any prescription parent.
- **Reschedule** — move a `ScheduledSession` from its planned date to another date. Does not cascade to following sessions.
- **Missed session** — a `ScheduledSession` whose date has passed without being started or logged. Surfaces grouped at the top of the calendar dialog for the user to remove or reschedule.

### 12.10 Storage / backup

- **Scheduled session** — a calendar entry: routine + week + session-template + date. Created when a routine is activated.
- **Session log** — the persisted record of an actual training session, including all logged sets, executed exercises, performed date, and notes.
- **Active routine** — the single routine whose `ScheduledSession`s populate today's view. There is at most one at a time.
- **Backup archive** — a `.zip` produced by DiTrain's Export action, containing all routines, session logs, custom exercises, and app state, plus a `manifest.json`.
- **Rollover** — automatic archival of `sessions.json` to `logs/archive/sessions-{firstDate}_{lastDate}.json` when the live file exceeds 1 MB. Transparent to the user.

### 12.11 Out of scope for v1 (defined here for forward reference)

- **Volume landmarks (MEV/MAV/MRV)** — Renaissance Periodization framework for weekly hard sets per muscle. *v2 analysis layer.*
- **Autoregulation** — adjusting today's load/volume based on last session's RPE or recovery readiness. *v2.*
- **Tonnage** — total weight moved (sets × reps × load). *v2 analysis layer may surface.*
- **INOL** — Intensity × Number Of Lifts metric. *Possibly v2.*
- **Block / linear / DUP periodization** — programming structures. Not enforced; routines can express any structure via their week sequence.
