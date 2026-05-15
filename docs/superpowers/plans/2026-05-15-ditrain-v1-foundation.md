# DiTrain v1 — Plan 1: Foundation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the Android project for DiTrain with all data-model and storage classes ready, fully tested in JVM unit tests, and a buildable "Hello DiTrain" APK that installs and runs. No user-facing functionality yet beyond the launch screen; this milestone exists to lock down the schema, persistence, and project plumbing so all later milestones can focus on UI + flows.

**Architecture:**
- Mirrors DiRead/ReadBetter exactly: AppCompat single-Activity, programmatic Kotlin UI, no Compose, no Material library, no Room. The only deviation from DiRead's dependency list is `kotlinx-serialization-json` (with its Gradle plugin) — required because routine JSON is real schema.
- All persisted entities serialize through one shared `Json` instance configured in `util/JsonIo.kt`. Sealed types use `@SerialName` discriminators. Internal weight unit is always kg; display conversion is at the edge.
- Storage is plain JSON files under `context.filesDir`. Repositories expose suspend functions for `Dispatchers.IO`. All writes go through `util/AtomicWrite.kt` (write-tmp-then-rename) so partial writes can never corrupt the live file.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, JDK 17, Gradle 8.11.1, minSdk 23, targetSdk 35, AppCompat 1.7.0, `kotlinx-serialization-json` 1.7.3, JUnit 4.13.2. No Espresso, no Room, no DI framework.

**Reference spec:** `docs/superpowers/specs/2026-05-15-ditrain-v1-design.md` (the design document brainstormed alongside this plan). Sections cited inline.

---

## Pre-flight (engineer setup — one-time)

Before starting, the engineer must have:
- JDK 17 installed (e.g. `C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot`)
- Android Studio's SDK at `$env:LOCALAPPDATA\Android\Sdk` (or whatever path; we just need `JAVA_HOME` and `ANDROID_HOME` set)
- The repository at `C:\Users\Usuario\Documents\TrainBetter` (already initialized as a git repo on `main`)

The first thing every shell session needs:
```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
```

(Matches the README of the sibling project DiRead.)

---

## File Structure (Plan 1)

This plan creates the following files. Subsystems referenced from the spec are noted in parentheses.

```
TrainBetter/
├── build.gradle.kts                            # root build script
├── settings.gradle.kts                         # root settings
├── gradle.properties                           # androidx flag
├── gradlew, gradlew.bat                        # gradle wrapper (binary copies)
├── gradle/wrapper/gradle-wrapper.properties    # gradle 8.11.1 distribution
├── gradle/wrapper/gradle-wrapper.jar           # binary copy from DiRead
├── local.properties.example                    # sample sdk.dir for engineer
├── README.md                                   # build instructions
├── keystore.properties.example                 # sample (no real keys)
└── app/
    ├── build.gradle.kts                        # appcompat + serialization + signing
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml             # single Activity, VIBRATE permission
        │   ├── res/values/strings.xml          # app_name = "DiTrain"
        │   ├── res/values/styles.xml           # DayNight NoActionBar theme
        │   ├── res/drawable/ic_launcher_background.xml   # placeholder
        │   ├── res/drawable/ic_launcher_foreground.xml   # placeholder
        │   ├── res/mipmap-anydpi-v26/ic_launcher.xml
        │   ├── res/mipmap-anydpi-v26/ic_launcher_round.xml
        │   ├── assets/exercises.json           # 5 starter entries (full ~150 in Plan 2)
        │   └── java/com/ditrain/app/
        │       ├── MainActivity.kt             # creates HomeViewController
        │       ├── model/
        │       │   ├── Units.kt                # WeightUnit + conversion
        │       │   ├── Rpe.kt                  # EffortMode (RPE/RIR)
        │       │   ├── Exercise.kt             # Exercise, MovementPattern, MuscleGroup, Equipment
        │       │   ├── Routine.kt              # Routine, Week, SessionTemplate, ExerciseBlock,
        │       │   │                           # SetPrescription sealed, RepsTarget, LoadTarget,
        │       │   │                           # LoopMode, CardioBlock, CardioKind
        │       │   ├── SessionLog.kt           # SessionLog, ExecutedExercise, LoggedSet sealed,
        │       │   │                           # MiniSet, CardioLog
        │       │   └── AppState.kt             # AppState, ScheduledSession, Settings,
        │       │                               # ThemeMode
        │       ├── util/
        │       │   ├── JsonIo.kt               # shared Json instance
        │       │   ├── AtomicWrite.kt          # write-tmp-then-rename
        │       │   └── DateFmt.kt              # LocalDateIso, InstantIso typealiases + helpers
        │       ├── storage/
        │       │   ├── ExerciseCatalog.kt      # bundled + customs + soft delete
        │       │   ├── RoutineRepository.kt    # one JSON per routine
        │       │   ├── SessionLogRepository.kt # sessions.json + size-based rollover
        │       │   └── AppStateRepository.kt   # whole-file rewrite
        │       └── ui/
        │           ├── ViewStyling.kt          # ported from DiRead, ready for extensions
        │           └── home/
        │               └── HomeViewController.kt  # placeholder rendering for Plan 1
        └── test/java/com/ditrain/app/
            ├── model/
            │   ├── UnitsTest.kt
            │   ├── RpeTest.kt
            │   ├── ExerciseSerializationTest.kt
            │   ├── RoutineSerializationTest.kt
            │   ├── SessionLogSerializationTest.kt
            │   └── AppStateSerializationTest.kt
            ├── util/
            │   ├── JsonIoTest.kt
            │   └── AtomicWriteTest.kt
            └── storage/
                ├── ExerciseCatalogTest.kt
                ├── RoutineRepositoryTest.kt
                ├── SessionLogRepositoryTest.kt
                └── AppStateRepositoryTest.kt
```

No file in this milestone exceeds ~250 lines of source. Model files are the longest; everything else stays small and focused.

**Files deliberately NOT in this plan:**
- `BackupArchive.kt` (full implementation in Plan 6)
- All dialog controllers (Plans 2–6)
- `SetEntryView`, `CardioBlockView`, `RestTimerController` (Plan 3 and Plan 4)
- `E1rmChartView`, `CalendarHeatView` (Plan 5)
- `progression/` package (Plan 5)
- `importing/` package (Plan 2)
- Full `exercises.json` catalog of 150 entries (Plan 2)

---

## Conventions used in every task

- **Commits** match DiRead's Conventional Commits style: `feat(scope): …`, `chore(build): …`, `test(model): …`. No co-author trailer.
- **Tests** live in `app/src/test/java/...` and use plain JUnit 4 (DiRead's setup). No Espresso, no Robolectric.
- **TDD cadence** for every code task: write failing test → run, see fail → implement minimum → run, see green → commit.
- **Build verification commands** (run from repo root in PowerShell):
  - Unit tests: `.\gradlew.bat :app:testDebugUnitTest`
  - APK build: `.\gradlew.bat assembleDebug`
  - Single test run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.ditrain.app.model.UnitsTest"`

---

## Task 1: Gradle scaffold + manifest + empty MainActivity

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar` (copy)
- Create: `gradlew`, `gradlew.bat` (copies)
- Create: `local.properties.example`
- Create: `keystore.properties.example`
- Create: `README.md`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/styles.xml`
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create: `app/src/main/java/com/ditrain/app/MainActivity.kt`

This task has no automated test — the test is "the project builds." Treat the successful `assembleDebug` as the green bar.

- [ ] **Step 1: Copy gradle wrapper files from DiRead**

```powershell
Copy-Item "C:\Users\Usuario\Documents\ReadBetter\gradlew" "C:\Users\Usuario\Documents\TrainBetter\gradlew"
Copy-Item "C:\Users\Usuario\Documents\ReadBetter\gradlew.bat" "C:\Users\Usuario\Documents\TrainBetter\gradlew.bat"
New-Item -ItemType Directory -Path "C:\Users\Usuario\Documents\TrainBetter\gradle\wrapper" -Force | Out-Null
Copy-Item "C:\Users\Usuario\Documents\ReadBetter\gradle\wrapper\gradle-wrapper.jar" "C:\Users\Usuario\Documents\TrainBetter\gradle\wrapper\gradle-wrapper.jar"
Copy-Item "C:\Users\Usuario\Documents\ReadBetter\gradle\wrapper\gradle-wrapper.properties" "C:\Users\Usuario\Documents\TrainBetter\gradle\wrapper\gradle-wrapper.properties"
```

- [ ] **Step 2: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "DiTrain"
include(":app")
```

- [ ] **Step 3: Write `build.gradle.kts` (root)**

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
```

- [ ] **Step 4: Write `gradle.properties`**

```properties
android.useAndroidX=true
```

- [ ] **Step 5: Write `local.properties.example`**

```properties
# Copy this to local.properties and adjust sdk.dir to your Android SDK path.
sdk.dir=C\:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk
```

- [ ] **Step 6: Write `keystore.properties.example`**

```properties
# Copy this to keystore.properties before producing a signed release build.
storeFile=C:/path/to/release.keystore
storePassword=changeme
keyAlias=ditrain
keyPassword=changeme
```

- [ ] **Step 7: Write `app/build.gradle.kts`**

```kotlin
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

android {
    namespace = "com.ditrain.app"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "com.ditrain.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 8: Write `app/src/main/AndroidManifest.xml`**

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.VIBRATE" />
    <application
        android:theme="@style/AppTheme"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round">
        <activity android:name=".MainActivity" android:exported="true" android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

(VIBRATE is included now even though Plan 3's RestTimerController hasn't been written — it lets the manifest stay stable across milestones.)

- [ ] **Step 9: Write `app/src/main/res/values/strings.xml`**

```xml
<resources>
    <string name="app_name">DiTrain</string>
</resources>
```

- [ ] **Step 10: Write `app/src/main/res/values/styles.xml`**

```xml
<resources>
    <style name="AppTheme" parent="Theme.AppCompat.DayNight.NoActionBar" />
</resources>
```

- [ ] **Step 11: Write launcher icon resources**

`app/src/main/res/drawable/ic_launcher_background.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#0B1120"
        android:pathData="M0,0 L108,0 L108,108 L0,108 Z" />
</vector>
```

`app/src/main/res/drawable/ic_launcher_foreground.xml` — a stylized "T" so DiTrain's icon is visually distinct from DiRead's "D":
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Top accent triangle pointing down at the letter -->
    <path
        android:fillColor="#F97316"
        android:fillAlpha="0.55"
        android:pathData="M50,18 L58,18 L54,26 Z" />

    <!-- Capital T: horizontal crossbar + vertical stem -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M30,32 L78,32 L78,42 L30,42 Z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M49,42 L59,42 L59,86 L49,86 Z" />

    <!-- Bottom accent triangle pointing up at the letter -->
    <path
        android:fillColor="#F97316"
        android:fillAlpha="0.55"
        android:pathData="M50,90 L58,90 L54,82 Z" />
</vector>
```

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

`app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`: identical contents to `ic_launcher.xml`.

- [ ] **Step 12: Write `app/src/main/java/com/ditrain/app/MainActivity.kt` (placeholder)**

```kotlin
package com.ditrain.app

import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this).apply {
            text = "DiTrain"
            textSize = 32f
            gravity = Gravity.CENTER
        }
        setContentView(view)
    }
}
```

(Real `HomeViewController` wiring happens in Task 15 — this minimal `TextView` exists only so the first commit produces a runnable APK.)

- [ ] **Step 13: Write `README.md`**

```markdown
# DiTrain

A local-first Android strength + cardio training app for experienced trainees.

Mirrors the stack of [DiRead](https://example.invalid/) — AppCompat single-Activity, programmatic Kotlin UI, file-based JSON storage. See `docs/superpowers/specs/2026-05-15-ditrain-v1-design.md` for the full design.

## Build

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat assembleDebug
```

## Run tests

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## Install to connected device

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
.\gradlew.bat installDebug
```

## Release bundle

Copy `keystore.properties.example` to `keystore.properties`, set paths, then:
```powershell
.\gradlew.bat bundleRelease
```
```

- [ ] **Step 14: Write `local.properties` (engineer-local, not committed)**

```powershell
"sdk.dir=$($env:LOCALAPPDATA -replace '\\','\\')\\Android\\Sdk" | Out-File -Encoding ASCII "C:\Users\Usuario\Documents\TrainBetter\local.properties"
```

- [ ] **Step 15: Verify the project builds**

Run:
```powershell
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`. The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

If this fails because `sdk.dir` is wrong, fix `local.properties` and retry.

- [ ] **Step 16: Commit**

```powershell
git add build.gradle.kts settings.gradle.kts gradle.properties gradlew gradlew.bat gradle/wrapper/ local.properties.example keystore.properties.example README.md app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/ app/src/main/java/com/ditrain/app/MainActivity.kt
git commit -m "chore(build): scaffold DiTrain gradle project with placeholder MainActivity"
```

---

## Task 2: Port `ViewStyling.kt` from DiRead

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/ViewStyling.kt`

No test for this task — it's a pure-helper port. Subsequent UI tasks will exercise it.

- [ ] **Step 1: Copy `ViewStyling.kt` from DiRead, changing only the package**

```kotlin
package com.ditrain.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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
}
```

- [ ] **Step 2: Verify it compiles**

```powershell
.\gradlew.bat :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/java/com/ditrain/app/ui/ViewStyling.kt
git commit -m "feat(ui): port ViewStyling helper from DiRead"
```

---

## Task 3: `model/Units.kt` — weight unit + conversion

**Files:**
- Create: `app/src/main/java/com/ditrain/app/model/Units.kt`
- Create: `app/src/test/java/com/ditrain/app/model/UnitsTest.kt`

Spec reference: §4.3 ("Internal weight unit is always kg … `1 lb = 0.45359237 kg` exactly").

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ditrain.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {

    @Test fun `kg to lb uses exact 2_2046226 factor`() {
        assertEquals(220.46226, 100.0.kgToLb(), 1e-6)
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
```

- [ ] **Step 2: Run, expect FAIL**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.ditrain.app.model.UnitsTest"
```
Expected: compile failure (`WeightUnit`, `kgToLb`, `lbToKg`, `formatForInput` unresolved).

- [ ] **Step 3: Implement**

```kotlin
package com.ditrain.app.model

enum class WeightUnit(val label: String) {
    KG("kg"),
    LB("lb"),
    ;

    /**
     * Converts an internal kg value to a display string in this unit, rounded for input UX:
     *  - KG → nearest 0.5 kg
     *  - LB → nearest 1 lb
     *
     * Internal math elsewhere never goes through this rounding.
     */
    fun formatForInput(weightKg: Double): String = when (this) {
        KG -> {
            val halves = Math.round(weightKg * 2.0).toDouble() / 2.0
            if (halves % 1.0 == 0.0) halves.toInt().toString() else halves.toString()
        }
        LB -> {
            val pounds = Math.round(weightKg.kgToLb()).toDouble()
            pounds.toInt().toString()
        }
    }
}

private const val KG_PER_LB = 0.45359237
private const val LB_PER_KG = 1.0 / KG_PER_LB    // 2.20462262184…

fun Double.kgToLb(): Double = this * LB_PER_KG
fun Double.lbToKg(): Double = this * KG_PER_LB
```

- [ ] **Step 4: Run, expect PASS**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.ditrain.app.model.UnitsTest"
```
Expected: `BUILD SUCCESSFUL`, all 5 tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ditrain/app/model/Units.kt app/src/test/java/com/ditrain/app/model/UnitsTest.kt
git commit -m "feat(model): add WeightUnit with exact kg/lb conversion and display rounding"
```

---

## Task 4: `model/Rpe.kt` — effort scale types

**Files:**
- Create: `app/src/main/java/com/ditrain/app/model/Rpe.kt`
- Create: `app/src/test/java/com/ditrain/app/model/RpeTest.kt`

Spec reference: §12.4 ("RIR ≈ 10 − RPE … DiTrain accepts half-points (e.g., 8.5)").

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run, expect FAIL** (Effort / EffortMode unresolved).

- [ ] **Step 3: Implement**

```kotlin
package com.ditrain.app.model

/** UI preference for which effort scale to expose: RPE on a 1–10 scale, or RIR on a 0–5 scale. */
enum class EffortMode { RPE, RIR }

object Effort {
    /** Returns RIR (0..5) for an RPE in [1.0, 10.0]; returns null if RPE is out of bounds. */
    fun rpeToRir(rpe: Double): Int? {
        if (rpe < 1.0 || rpe > 10.0) return null
        val rir = (10.0 - rpe).toInt()        // truncates 8.5→1.5→1, 9.5→0.5→0
        return rir.coerceIn(0, 9)
    }

    /** Inverse of [rpeToRir] for integer RIR values: RPE = 10 − RIR. */
    fun rirToRpe(rir: Int): Double = (10 - rir).toDouble()
}
```

- [ ] **Step 4: Run, expect PASS**.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ditrain/app/model/Rpe.kt app/src/test/java/com/ditrain/app/model/RpeTest.kt
git commit -m "feat(model): add EffortMode and RPE/RIR conversion helpers"
```

---

## Task 5: `util/JsonIo.kt` — shared Json configuration

**Files:**
- Create: `app/src/main/java/com/ditrain/app/util/JsonIo.kt`
- Create: `app/src/test/java/com/ditrain/app/util/JsonIoTest.kt`

Spec reference: §4 (every persisted entity is `@Serializable`).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ditrain.app.util

import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonIoTest {

    @Serializable
    data class Sample(val a: Int, val b: String? = null)

    @Test fun `unknown keys are ignored on decode`() {
        val raw = """{"a":1,"b":"x","unknown":"ignore me"}"""
        val parsed = JsonIo.json.decodeFromString(Sample.serializer(), raw)
        assertEquals(Sample(1, "x"), parsed)
    }

    @Test fun `encode uses pretty printing with two-space indent`() {
        val out = JsonIo.json.encodeToString(Sample.serializer(), Sample(1, "x"))
        assertTrue(out.contains("\n  \"a\""))
    }

    @Test fun `defaults are encoded so files are self-documenting`() {
        val out = JsonIo.json.encodeToString(Sample.serializer(), Sample(1, b = null))
        assertTrue(out.contains("\"b\": null"))
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (JsonIo unresolved).

- [ ] **Step 3: Implement**

```kotlin
package com.ditrain.app.util

import kotlinx.serialization.json.Json

/**
 * Shared Json instance used by every repository. Configured to:
 *  - tolerate unknown fields so future schema additions don't crash older builds reading newer files;
 *  - pretty-print so files are debuggable / hand-editable;
 *  - encode default values so manually-authored JSON doesn't need to specify them once and forget the next time.
 */
object JsonIo {
    val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        classDiscriminator = "type"
    }
}
```

- [ ] **Step 4: Run, expect PASS**.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ditrain/app/util/JsonIo.kt app/src/test/java/com/ditrain/app/util/JsonIoTest.kt
git commit -m "feat(util): add shared Json configuration with type discriminator"
```

---

## Task 6: `util/DateFmt.kt` — date/instant typealiases and helpers

**Files:**
- Create: `app/src/main/java/com/ditrain/app/util/DateFmt.kt`

No tests for this task — it's just typealiases and trivial helpers that get exercised by serialization tests in later tasks.

- [ ] **Step 1: Implement**

```kotlin
package com.ditrain.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ISO-8601 LocalDate string, e.g. "2026-05-14". Stored as String for serialization simplicity.
 * Use [parseLocalDate] / [LocalDate.iso] to cross the boundary.
 */
typealias LocalDateIso = String

/** ISO-8601 instant string (UTC, "Z"-suffixed), e.g. "2026-05-14T16:42:00Z". */
typealias InstantIso = String

private val LOCAL_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun parseLocalDate(s: LocalDateIso): LocalDate = LocalDate.parse(s, LOCAL_DATE_FMT)
fun LocalDate.iso(): LocalDateIso = format(LOCAL_DATE_FMT)

fun parseInstant(s: InstantIso): Instant = Instant.parse(s)
fun Instant.iso(): InstantIso = toString()
```

- [ ] **Step 2: Verify it compiles**

```powershell
.\gradlew.bat :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/java/com/ditrain/app/util/DateFmt.kt
git commit -m "feat(util): add LocalDateIso/InstantIso typealiases and parse helpers"
```

---

## Task 7: `model/Exercise.kt` — catalog entry + enums + serialization

**Files:**
- Create: `app/src/main/java/com/ditrain/app/model/Exercise.kt`
- Create: `app/src/test/java/com/ditrain/app/model/ExerciseSerializationTest.kt`

Spec reference: §4.1.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ditrain.app.model

import com.ditrain.app.util.JsonIo
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSerializationTest {

    @Test fun `bundled exercise round-trips`() {
        val ex = Exercise(
            id = "barbell-back-squat",
            name = "Barbell Back Squat",
            aliases = listOf("Back Squat", "Squat"),
            pattern = MovementPattern.SQUAT,
            primaryMuscles = listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            secondaryMuscles = listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.LOWER_BACK),
            equipment = listOf(Equipment.BARBELL),
            descriptionUrl = "https://exrx.net/WeightExercises/Quadriceps/BBSquat",
            custom = false,
            parentId = null,
            deleted = false,
        )
        val raw = JsonIo.json.encodeToString(Exercise.serializer(), ex)
        val back = JsonIo.json.decodeFromString(Exercise.serializer(), raw)
        assertEquals(ex, back)
    }

    @Test fun `custom forked exercise round-trips with parentId`() {
        val ex = Exercise(
            id = "low-bar-squat-my-stance",
            name = "Low-bar squat (my stance)",
            pattern = MovementPattern.SQUAT,
            primaryMuscles = listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            equipment = listOf(Equipment.BARBELL),
            descriptionUrl = null,
            custom = true,
            parentId = "barbell-back-squat",
        )
        val back = JsonIo.json.decodeFromString(
            Exercise.serializer(),
            JsonIo.json.encodeToString(Exercise.serializer(), ex)
        )
        assertEquals(ex, back)
        assertTrue(back.custom)
        assertEquals("barbell-back-squat", back.parentId)
    }

    @Test fun `soft-deleted flag round-trips`() {
        val ex = Exercise(
            id = "weird-machine",
            name = "Weird Machine",
            pattern = MovementPattern.ISOLATION,
            primaryMuscles = listOf(MuscleGroup.BICEPS),
            equipment = listOf(Equipment.MACHINE),
            descriptionUrl = null,
            custom = true,
            deleted = true,
        )
        val back = JsonIo.json.decodeFromString(
            Exercise.serializer(),
            JsonIo.json.encodeToString(Exercise.serializer(), ex)
        )
        assertTrue(back.deleted)
        assertEquals(ex, back)
    }

    @Test fun `unknown future field is ignored`() {
        val raw = """{"id":"x","name":"X","pattern":"ISOLATION","primaryMuscles":["BICEPS"],"equipment":["MACHINE"],"descriptionUrl":null,"future_field":"ok"}"""
        val back = JsonIo.json.decodeFromString(Exercise.serializer(), raw)
        assertEquals("X", back.name)
        assertFalse(back.custom)
        assertFalse(back.deleted)
    }

    @Test fun `list of exercises round-trips`() {
        val list = listOf(
            Exercise("a", "A", pattern = MovementPattern.HINGE,
                primaryMuscles = listOf(MuscleGroup.HAMSTRINGS),
                equipment = listOf(Equipment.BARBELL), descriptionUrl = null),
            Exercise("b", "B", pattern = MovementPattern.CARRY,
                primaryMuscles = listOf(MuscleGroup.FOREARMS),
                equipment = listOf(Equipment.DUMBBELL), descriptionUrl = null),
        )
        val serializer = ListSerializer(Exercise.serializer())
        val back = JsonIo.json.decodeFromString(serializer,
            JsonIo.json.encodeToString(serializer, list))
        assertEquals(list, back)
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (Exercise / MovementPattern / etc. unresolved).

- [ ] **Step 3: Implement**

```kotlin
package com.ditrain.app.model

import kotlinx.serialization.Serializable

enum class MovementPattern {
    SQUAT, HINGE,
    HORIZONTAL_PUSH, VERTICAL_PUSH,
    HORIZONTAL_PULL, VERTICAL_PULL,
    LUNGE, CARRY, CORE, ISOLATION,
}

enum class MuscleGroup {
    QUADS, HAMSTRINGS, GLUTES,
    CHEST, UPPER_BACK, LATS,
    FRONT_DELT, SIDE_DELT, REAR_DELT,
    BICEPS, TRICEPS, FOREARMS,
    ABS, OBLIQUES, LOWER_BACK,
    CALVES, TRAPS, NECK,
    ADDUCTORS, ABDUCTORS,
}

enum class Equipment {
    BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT, BAND, OTHER,
}

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val pattern: MovementPattern,
    val primaryMuscles: List<MuscleGroup>,
    val equipment: List<Equipment>,
    val descriptionUrl: String?,
    val aliases: List<String> = emptyList(),
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val custom: Boolean = false,
    val parentId: String? = null,
    val deleted: Boolean = false,
)
```

- [ ] **Step 4: Run, expect PASS**.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ditrain/app/model/Exercise.kt app/src/test/java/com/ditrain/app/model/ExerciseSerializationTest.kt
git commit -m "feat(model): add Exercise + MovementPattern/MuscleGroup/Equipment with serialization"
```

---

## Task 8: `model/Routine.kt` — full routine schema (incl. cardio)

**Files:**
- Create: `app/src/main/java/com/ditrain/app/model/Routine.kt`
- Create: `app/src/test/java/com/ditrain/app/model/RoutineSerializationTest.kt`

Spec reference: §4.2 (Routine, Week, SessionTemplate, ExerciseBlock, SetPrescription, RepsTarget, LoadTarget, LoopMode, CardioBlock, CardioKind).

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run, expect FAIL** (Routine + supporting types unresolved).

- [ ] **Step 3: Implement**

```kotlin
package com.ditrain.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class LoopMode { ONCE, REPEAT }

@Serializable
data class Routine(
    val id: String,
    val name: String,
    val loopMode: LoopMode,
    val weeks: List<Week>,
    val description: String? = null,
    val author: String? = null,
    val schemaVersion: Int = 1,
    /** Weekday indices the user trains, Mon=0..Sun=6. Null = prompt on activation. */
    val defaultWeeklyPattern: List<Int>? = null,
)

@Serializable
data class Week(
    val label: String,
    val sessions: List<SessionTemplate>,
)

@Serializable
data class SessionTemplate(
    val id: String,
    val name: String,
    val blocks: List<ExerciseBlock> = emptyList(),
    val cardioBlocks: List<CardioBlock> = emptyList(),
)

@Serializable
data class ExerciseBlock(
    val exerciseId: String,
    val sets: List<SetPrescription>,
    val notes: String? = null,
)

@Serializable
sealed interface SetPrescription {
    val rest: Int?
    val tempo: String?
    val notes: String?

    @Serializable @SerialName("straight")
    data class Straight(
        val reps: RepsTarget,
        val load: LoadTarget,
        val rpeTarget: Double? = null,
        override val rest: Int? = null,
        override val tempo: String? = null,
        override val notes: String? = null,
    ) : SetPrescription

    @Serializable @SerialName("myo_rep")
    data class MyoRep(
        val activationReps: RepsTarget,
        val load: LoadTarget,
        val miniSetTargetReps: Int,
        val miniSetCount: Int,
        val miniSetRestSec: Int = 15,
        val rpeStopThreshold: Double? = 10.0,
        override val rest: Int? = null,
        override val tempo: String? = null,
        override val notes: String? = null,
    ) : SetPrescription
}

@Serializable
sealed interface RepsTarget {
    @Serializable @SerialName("fixed")
    data class Fixed(val reps: Int) : RepsTarget

    @Serializable @SerialName("range")
    data class Range(val min: Int, val max: Int) : RepsTarget

    @Serializable @SerialName("amrap")
    data object Amrap : RepsTarget

    @Serializable @SerialName("amrap_min")
    data class AmrapMin(val min: Int) : RepsTarget
}

@Serializable
sealed interface LoadTarget {
    @Serializable @SerialName("absolute_kg")
    data class AbsoluteKg(val kg: Double) : LoadTarget

    @Serializable @SerialName("pct_1rm")
    data class PctOneRm(val pct: Double) : LoadTarget   // 0.80 = 80 %

    @Serializable @SerialName("rpe_target")
    data class RpeTarget(val rpe: Double) : LoadTarget

    @Serializable @SerialName("relative_to_last")
    data class RelativeToLast(val deltaKg: Double) : LoadTarget

    @Serializable @SerialName("open")
    data object Open : LoadTarget
}

enum class CardioKind {
    RUNNING, SWIMMING, CYCLING, ROWING,
    WALKING, ELLIPTICAL, HIKING, OTHER,
}

@Serializable
data class CardioBlock(
    val activityKind: CardioKind,
    val description: String? = null,
    val targetDurationMin: Int? = null,
    val targetAvgBpm: Int? = null,
    val notes: String? = null,
)
```

- [ ] **Step 4: Run, expect PASS** (all 7 tests in `RoutineSerializationTest`).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ditrain/app/model/Routine.kt app/src/test/java/com/ditrain/app/model/RoutineSerializationTest.kt
git commit -m "feat(model): add Routine schema with sealed prescription/reps/load types and cardio blocks"
```

---

## Task 9: `model/SessionLog.kt` — what actually happened

**Files:**
- Create: `app/src/main/java/com/ditrain/app/model/SessionLog.kt`
- Create: `app/src/test/java/com/ditrain/app/model/SessionLogSerializationTest.kt`

Spec reference: §4.3.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ditrain.app.model

import com.ditrain.app.util.JsonIo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLogSerializationTest {

    private fun roundTrip(s: SessionLog): SessionLog {
        val raw = JsonIo.json.encodeToString(SessionLog.serializer(), s)
        return JsonIo.json.decodeFromString(SessionLog.serializer(), raw)
    }

    @Test fun `straight set log round-trips with rpe rir rest tempo`() {
        val s = SessionLog(
            id = "u1",
            routineId = "r1",
            weekIndex = 0,
            sessionTemplateId = "push-a",
            scheduledDate = "2026-05-14",
            performedDate = "2026-05-14",
            startedAt = "2026-05-14T16:00:00Z",
            completedAt = "2026-05-14T17:00:00Z",
            executed = listOf(
                ExecutedExercise("bench", sets = listOf(
                    LoggedSet.Straight(
                        weightKg = 100.0,
                        reps = 5,
                        rpe = 8.0,
                        rir = 2,
                        restSec = 180,
                        tempo = "3-1-1",
                        performedAt = "2026-05-14T16:05:00Z",
                        notes = "felt strong",
                    )
                ))
            ),
        )
        assertEquals(s, roundTrip(s))
    }

    @Test fun `myo-rep log with partial mini-sets round-trips`() {
        val s = SessionLog(
            id = "u2",
            routineId = null,
            weekIndex = null,
            sessionTemplateId = null,
            scheduledDate = "2026-05-14",
            performedDate = "2026-05-14",
            startedAt = "2026-05-14T16:00:00Z",
            completedAt = null,
            executed = listOf(
                ExecutedExercise("curl", sets = listOf(
                    LoggedSet.MyoRep(
                        weightKg = 15.0,
                        activationReps = 14,
                        activationRpe = 9.5,
                        miniSets = listOf(
                            MiniSet(reps = 5, rpe = 9.0),
                            MiniSet(reps = 5, rpe = 9.5),
                            MiniSet(reps = 4, rpe = 10.0),   // cluster aborted on rep 4
                        ),
                        performedAt = "2026-05-14T16:30:00Z",
                    )
                ))
            ),
        )
        assertEquals(s, roundTrip(s))
    }

    @Test fun `cardio-only log round-trips`() {
        val s = SessionLog(
            id = "u3",
            routineId = "r1",
            weekIndex = 0,
            sessionTemplateId = "easy-run",
            scheduledDate = "2026-05-15",
            performedDate = "2026-05-15",
            startedAt = "2026-05-15T07:00:00Z",
            completedAt = "2026-05-15T07:32:00Z",
            cardioExecuted = listOf(
                CardioLog(
                    activityKind = CardioKind.RUNNING,
                    durationMin = 32,
                    avgBpm = 142,
                    performedAt = "2026-05-15T07:32:00Z",
                )
            ),
        )
        val back = roundTrip(s)
        assertEquals(s, back)
        assertTrue(back.executed.isEmpty())
        assertTrue(back.cardioExecuted.isNotEmpty())
    }

    @Test fun `mixed session log round-trips`() {
        val s = SessionLog(
            id = "u4",
            routineId = "r1",
            weekIndex = 0,
            sessionTemplateId = "lift-plus-walk",
            scheduledDate = "2026-05-16",
            performedDate = "2026-05-16",
            startedAt = "2026-05-16T16:00:00Z",
            completedAt = "2026-05-16T17:30:00Z",
            executed = listOf(
                ExecutedExercise("squat", sets = listOf(
                    LoggedSet.Straight(120.0, reps = 5, rpe = 8.0,
                        performedAt = "2026-05-16T16:20:00Z")
                ))
            ),
            cardioExecuted = listOf(
                CardioLog(CardioKind.WALKING, durationMin = 15,
                    performedAt = "2026-05-16T17:30:00Z")
            ),
        )
        assertEquals(s, roundTrip(s))
    }

    @Test fun `skipped exercise round-trips with empty sets`() {
        val s = SessionLog(
            id = "u5", routineId = null, weekIndex = null, sessionTemplateId = null,
            scheduledDate = "2026-05-14", performedDate = "2026-05-14",
            startedAt = "2026-05-14T16:00:00Z", completedAt = null,
            executed = listOf(ExecutedExercise("ohp", skipped = true, sets = emptyList())),
        )
        val back = roundTrip(s)
        assertTrue(back.executed[0].skipped)
        assertEquals(emptyList<LoggedSet>(), back.executed[0].sets)
    }

    @Test fun `substituted exercise records original id`() {
        val s = SessionLog(
            id = "u6", routineId = null, weekIndex = null, sessionTemplateId = null,
            scheduledDate = "2026-05-14", performedDate = "2026-05-14",
            startedAt = "2026-05-14T16:00:00Z", completedAt = null,
            executed = listOf(
                ExecutedExercise(
                    exerciseId = "landmine-press",
                    substitutedFromId = "ohp",
                    sets = listOf(LoggedSet.Straight(40.0, reps = 8, rpe = 7.0,
                        performedAt = "2026-05-14T16:05:00Z"))
                )
            ),
        )
        val back = roundTrip(s)
        assertEquals("ohp", back.executed[0].substitutedFromId)
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (SessionLog + supporting types unresolved).

- [ ] **Step 3: Implement**

```kotlin
package com.ditrain.app.model

import com.ditrain.app.util.InstantIso
import com.ditrain.app.util.LocalDateIso
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionLog(
    val id: String,
    val routineId: String?,
    val weekIndex: Int?,
    val sessionTemplateId: String?,
    val scheduledDate: LocalDateIso,
    val performedDate: LocalDateIso,
    val startedAt: InstantIso,
    val completedAt: InstantIso?,
    val executed: List<ExecutedExercise> = emptyList(),
    val cardioExecuted: List<CardioLog> = emptyList(),
    val notes: String? = null,
)

@Serializable
data class ExecutedExercise(
    val exerciseId: String,
    val substitutedFromId: String? = null,
    val skipped: Boolean = false,
    val sets: List<LoggedSet> = emptyList(),
)

@Serializable
sealed interface LoggedSet {
    val performedAt: InstantIso
    val notes: String?

    @Serializable @SerialName("straight")
    data class Straight(
        val weightKg: Double,
        val reps: Int,
        override val performedAt: InstantIso,
        val rpe: Double? = null,
        val rir: Int? = null,
        val restSec: Int? = null,
        val tempo: String? = null,
        override val notes: String? = null,
    ) : LoggedSet

    @Serializable @SerialName("myo_rep")
    data class MyoRep(
        val weightKg: Double,
        val activationReps: Int,
        override val performedAt: InstantIso,
        val activationRpe: Double? = null,
        val miniSets: List<MiniSet> = emptyList(),
        val restSec: Int? = null,
        val tempo: String? = null,
        override val notes: String? = null,
    ) : LoggedSet
}

@Serializable
data class MiniSet(val reps: Int, val rpe: Double? = null)

@Serializable
data class CardioLog(
    val activityKind: CardioKind,
    val durationMin: Int,
    val performedAt: InstantIso,
    val substitutedFromKind: CardioKind? = null,
    val description: String? = null,
    val avgBpm: Int? = null,
    val notes: String? = null,
    val skipped: Boolean = false,
)
```

- [ ] **Step 4: Run, expect PASS**.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ditrain/app/model/SessionLog.kt app/src/test/java/com/ditrain/app/model/SessionLogSerializationTest.kt
git commit -m "feat(model): add SessionLog with sealed LoggedSet, MiniSet and CardioLog"
```

---

## Task 10: `model/AppState.kt` — settings + active routine + scheduled sessions

**Files:**
- Create: `app/src/main/java/com/ditrain/app/model/AppState.kt`
- Create: `app/src/test/java/com/ditrain/app/model/AppStateSerializationTest.kt`

Spec reference: §4.4.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ditrain.app.model

import com.ditrain.app.util.JsonIo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateSerializationTest {

    @Test fun `default app state round-trips`() {
        val s = AppState(activeRoutineId = null, scheduledSessions = emptyList(),
            settings = Settings())
        val raw = JsonIo.json.encodeToString(AppState.serializer(), s)
        val back = JsonIo.json.decodeFromString(AppState.serializer(), raw)
        assertEquals(s, back)
        assertEquals(WeightUnit.KG, back.settings.weightUnit)
        assertEquals(EffortMode.RPE, back.settings.effortMode)
        assertEquals(20.0, back.settings.barWeightKg, 1e-9)
        assertEquals(ThemeMode.SYSTEM, back.settings.theme)
        assertTrue(back.settings.restTimerHaptic)
        assertEquals(false, back.settings.showDeletedExercises)
    }

    @Test fun `scheduled session round-trips with linked log`() {
        val sch = ScheduledSession(
            date = "2026-05-14",
            routineId = "r1",
            weekIndex = 0,
            sessionTemplateId = "push-a",
            sessionLogId = "log-uuid-123",
        )
        val raw = JsonIo.json.encodeToString(ScheduledSession.serializer(), sch)
        val back = JsonIo.json.decodeFromString(ScheduledSession.serializer(), raw)
        assertEquals(sch, back)
    }

    @Test fun `non-default settings round-trip`() {
        val s = Settings(
            weightUnit = WeightUnit.LB,
            effortMode = EffortMode.RIR,
            barWeightKg = 15.0,
            theme = ThemeMode.DARK,
            restTimerHaptic = false,
            showDeletedExercises = true,
        )
        val raw = JsonIo.json.encodeToString(Settings.serializer(), s)
        val back = JsonIo.json.decodeFromString(Settings.serializer(), raw)
        assertEquals(s, back)
    }
}
```

- [ ] **Step 2: Run, expect FAIL**.

- [ ] **Step 3: Implement**

```kotlin
package com.ditrain.app.model

import com.ditrain.app.util.LocalDateIso
import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AppState(
    val activeRoutineId: String?,
    val scheduledSessions: List<ScheduledSession>,
    val settings: Settings,
    val schemaVersion: Int = 1,
)

@Serializable
data class ScheduledSession(
    val date: LocalDateIso,
    val routineId: String,
    val weekIndex: Int,
    val sessionTemplateId: String,
    val sessionLogId: String? = null,
)

@Serializable
data class Settings(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val effortMode: EffortMode = EffortMode.RPE,
    val barWeightKg: Double = 20.0,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val restTimerHaptic: Boolean = true,
    val showDeletedExercises: Boolean = false,
)
```

- [ ] **Step 4: Run, expect PASS**.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ditrain/app/model/AppState.kt app/src/test/java/com/ditrain/app/model/AppStateSerializationTest.kt
git commit -m "feat(model): add AppState with ScheduledSession and Settings"
```

---

## Task 11: `util/AtomicWrite.kt` — write-tmp-then-rename helper

**Files:**
- Create: `app/src/main/java/com/ditrain/app/util/AtomicWrite.kt`
- Create: `app/src/test/java/com/ditrain/app/util/AtomicWriteTest.kt`

Spec reference: §7 ("write to `{path}.tmp`, fsync, atomic rename").

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ditrain.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AtomicWriteTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `writes file with content`() {
        val target = File(tmp.root, "out.json")
        AtomicWrite.writeText(target, "hello")
        assertEquals("hello", target.readText())
    }

    @Test fun `removes tmp file after successful write`() {
        val target = File(tmp.root, "out.json")
        AtomicWrite.writeText(target, "x")
        val tmpFile = File(tmp.root, "out.json.tmp")
        assertFalse(tmpFile.exists())
    }

    @Test fun `overwrites existing file atomically`() {
        val target = File(tmp.root, "out.json")
        target.writeText("old content")
        AtomicWrite.writeText(target, "new content")
        assertEquals("new content", target.readText())
    }

    @Test fun `creates parent directory if missing`() {
        val nested = File(tmp.root, "a/b/c/out.json")
        AtomicWrite.writeText(nested, "deep")
        assertTrue(nested.exists())
        assertEquals("deep", nested.readText())
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (AtomicWrite unresolved).

- [ ] **Step 3: Implement**

```kotlin
package com.ditrain.app.util

import java.io.File
import java.io.FileOutputStream

object AtomicWrite {

    /**
     * Writes [content] to [target] as atomically as the host filesystem allows:
     *  1. write to "${target}.tmp"
     *  2. fsync (flush + sync) so the tmp file is durable before rename
     *  3. renameTo onto target — on POSIX filesystems (Android internal storage)
     *     this atomically replaces target. If the FS refuses (some SD-card
     *     layouts), fall back to delete + rename, then to copy + delete.
     *
     * java.nio.file.Files.move with ATOMIC_MOVE would be cleaner but requires
     * API 26+; minSdk 23 forces this slightly-uglier ladder.
     *
     * Parent directories are created if missing.
     */
    fun writeText(target: File, content: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(tmp).use { fos ->
            fos.write(content.toByteArray(Charsets.UTF_8))
            fos.fd.sync()
        }
        if (tmp.renameTo(target)) return                     // happy path: atomic on POSIX
        if (target.exists() && !target.delete()) {
            tmp.delete()
            throw java.io.IOException("Failed to replace $target")
        }
        if (tmp.renameTo(target)) return
        tmp.copyTo(target, overwrite = true)
        tmp.delete()
    }
}
```

- [ ] **Step 4: Run, expect PASS** (all 4 tests).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ditrain/app/util/AtomicWrite.kt app/src/test/java/com/ditrain/app/util/AtomicWriteTest.kt
git commit -m "feat(util): add AtomicWrite for tmp-then-rename file writes"
```

---

## Task 12: `storage/ExerciseCatalog.kt` — bundled + customs + soft delete

**Files:**
- Create: `app/src/main/assets/exercises.json` (5 starter entries)
- Create: `app/src/main/java/com/ditrain/app/storage/ExerciseCatalog.kt`
- Create: `app/src/test/java/com/ditrain/app/storage/ExerciseCatalogTest.kt`

Spec reference: §4.1 (soft-delete semantics) and §3 (`ExerciseCatalog` location).

- [ ] **Step 1: Write the starter bundled catalog**

`app/src/main/assets/exercises.json`:
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
    "id": "barbell-overhead-press",
    "name": "Barbell Overhead Press",
    "aliases": ["OHP", "Strict Press"],
    "pattern": "VERTICAL_PUSH",
    "primaryMuscles": ["FRONT_DELT", "TRICEPS"],
    "secondaryMuscles": ["SIDE_DELT", "TRAPS"],
    "equipment": ["BARBELL"],
    "descriptionUrl": "https://exrx.net/WeightExercises/DeltoidAnterior/BBMilitaryPress"
  },
  {
    "id": "pull-up",
    "name": "Pull-up",
    "aliases": ["Chin-up (overhand)"],
    "pattern": "VERTICAL_PULL",
    "primaryMuscles": ["LATS", "BICEPS"],
    "secondaryMuscles": ["UPPER_BACK", "FOREARMS"],
    "equipment": ["BODYWEIGHT"],
    "descriptionUrl": "https://exrx.net/WeightExercises/LatissimusDorsi/BWPullup"
  }
]
```

(Full ~150 entries land in Plan 2; 5 is enough to exercise the catalog plumbing.)

- [ ] **Step 2: Write the failing test**

```kotlin
package com.ditrain.app.storage

import com.ditrain.app.model.Equipment
import com.ditrain.app.model.Exercise
import com.ditrain.app.model.MovementPattern
import com.ditrain.app.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExerciseCatalogTest {

    @get:Rule val tmp = TemporaryFolder()

    private val bundled = listOf(
        Exercise(
            id = "back-squat", name = "Back Squat",
            pattern = MovementPattern.SQUAT,
            primaryMuscles = listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            equipment = listOf(Equipment.BARBELL),
            descriptionUrl = "https://example/back-squat",
        ),
        Exercise(
            id = "bench", name = "Bench Press",
            pattern = MovementPattern.HORIZONTAL_PUSH,
            primaryMuscles = listOf(MuscleGroup.CHEST),
            equipment = listOf(Equipment.BARBELL),
            descriptionUrl = "https://example/bench",
        ),
    )

    @Test fun `bundled-only load exposes both entries`() {
        val catalog = ExerciseCatalog.fromInMemory(bundled, customsFile = File(tmp.root, "custom.json"))
        assertEquals(2, catalog.visibleExercises().size)
        assertEquals(bundled[0], catalog.byId("back-squat"))
    }

    @Test fun `custom exercise is added and resolvable`() {
        val customs = File(tmp.root, "custom.json")
        val catalog = ExerciseCatalog.fromInMemory(bundled, customsFile = customs)
        val custom = Exercise(
            id = "low-bar-my-stance", name = "Low-bar (my stance)",
            pattern = MovementPattern.SQUAT,
            primaryMuscles = listOf(MuscleGroup.QUADS),
            equipment = listOf(Equipment.BARBELL),
            descriptionUrl = null, custom = true, parentId = "back-squat",
        )
        catalog.addCustom(custom)
        assertEquals(3, catalog.visibleExercises().size)
        assertEquals(custom, catalog.byId("low-bar-my-stance"))
        // Persisted to disk
        assertTrue(customs.exists())
    }

    @Test fun `soft-deleted custom is hidden by default but still resolves by id`() {
        val customs = File(tmp.root, "custom.json")
        val catalog = ExerciseCatalog.fromInMemory(bundled, customsFile = customs)
        val custom = Exercise(
            id = "my-curl", name = "My Curl",
            pattern = MovementPattern.ISOLATION,
            primaryMuscles = listOf(MuscleGroup.BICEPS),
            equipment = listOf(Equipment.DUMBBELL),
            descriptionUrl = null, custom = true,
        )
        catalog.addCustom(custom)
        catalog.softDelete("my-curl")

        assertEquals(2, catalog.visibleExercises().size)
        val stillResolvable = catalog.byId("my-curl")
        assertEquals(custom.copy(deleted = true), stillResolvable)
        assertTrue(stillResolvable!!.deleted)
    }

    @Test fun `soft-deleted custom included when includeDeleted=true`() {
        val customs = File(tmp.root, "custom.json")
        val catalog = ExerciseCatalog.fromInMemory(bundled, customsFile = customs)
        catalog.addCustom(Exercise(
            id = "x", name = "X",
            pattern = MovementPattern.ISOLATION,
            primaryMuscles = listOf(MuscleGroup.BICEPS),
            equipment = listOf(Equipment.DUMBBELL),
            descriptionUrl = null, custom = true,
        ))
        catalog.softDelete("x")
        assertEquals(2, catalog.visibleExercises().size)
        assertEquals(3, catalog.visibleExercises(includeDeleted = true).size)
    }

    @Test fun `restore clears deleted flag`() {
        val catalog = ExerciseCatalog.fromInMemory(bundled, customsFile = File(tmp.root, "c.json"))
        catalog.addCustom(Exercise(
            id = "x", name = "X",
            pattern = MovementPattern.ISOLATION,
            primaryMuscles = listOf(MuscleGroup.BICEPS),
            equipment = listOf(Equipment.DUMBBELL),
            descriptionUrl = null, custom = true,
        ))
        catalog.softDelete("x")
        catalog.restore("x")
        assertFalse(catalog.byId("x")!!.deleted)
    }

    @Test fun `hard-delete is refused when references exist`() {
        val catalog = ExerciseCatalog.fromInMemory(bundled, customsFile = File(tmp.root, "c.json"))
        catalog.addCustom(Exercise(
            id = "x", name = "X",
            pattern = MovementPattern.ISOLATION,
            primaryMuscles = listOf(MuscleGroup.BICEPS),
            equipment = listOf(Equipment.DUMBBELL),
            descriptionUrl = null, custom = true,
        ))
        val refused = catalog.hardDelete("x", isReferenced = { id -> id == "x" })
        assertFalse(refused)
        assertEquals(3, catalog.visibleExercises(includeDeleted = true).size)
    }

    @Test fun `hard-delete succeeds when no references`() {
        val catalog = ExerciseCatalog.fromInMemory(bundled, customsFile = File(tmp.root, "c.json"))
        catalog.addCustom(Exercise(
            id = "y", name = "Y",
            pattern = MovementPattern.ISOLATION,
            primaryMuscles = listOf(MuscleGroup.BICEPS),
            equipment = listOf(Equipment.DUMBBELL),
            descriptionUrl = null, custom = true,
        ))
        val ok = catalog.hardDelete("y", isReferenced = { _ -> false })
        assertTrue(ok)
        assertNull(catalog.byId("y"))
    }

    @Test fun `bundled exercise cannot be soft or hard deleted`() {
        val catalog = ExerciseCatalog.fromInMemory(bundled, customsFile = File(tmp.root, "c.json"))
        val softOk = catalog.softDelete("back-squat")
        assertFalse(softOk)
        val hardOk = catalog.hardDelete("back-squat", isReferenced = { false })
        assertFalse(hardOk)
        assertFalse(catalog.byId("back-squat")!!.deleted)
    }

    @Test fun `customs persisted between catalog instances`() {
        val customs = File(tmp.root, "custom.json")
        val cat1 = ExerciseCatalog.fromInMemory(bundled, customsFile = customs)
        cat1.addCustom(Exercise(
            id = "z", name = "Z",
            pattern = MovementPattern.ISOLATION,
            primaryMuscles = listOf(MuscleGroup.BICEPS),
            equipment = listOf(Equipment.DUMBBELL),
            descriptionUrl = null, custom = true,
        ))
        val cat2 = ExerciseCatalog.fromInMemory(bundled, customsFile = customs)
        assertEquals("Z", cat2.byId("z")?.name)
    }
}
```

- [ ] **Step 3: Run, expect FAIL** (ExerciseCatalog unresolved).

- [ ] **Step 4: Implement**

```kotlin
package com.ditrain.app.storage

import com.ditrain.app.model.Exercise
import com.ditrain.app.util.AtomicWrite
import com.ditrain.app.util.JsonIo
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.InputStream

/**
 * In-memory catalog of all exercises. Composed of:
 *  - immutable bundled entries (parsed from `assets/exercises.json`)
 *  - mutable custom entries (persisted to a JSON file under filesDir)
 *
 * Soft delete only applies to customs. Bundled entries cannot be deleted.
 */
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

    fun addCustom(ex: Exercise): Exercise {
        require(ex.custom) { "addCustom requires Exercise.custom == true (id=${ex.id})" }
        customs[ex.id] = ex
        persistCustoms()
        return ex
    }

    /** Returns true if the exercise was soft-deleted; false if id is unknown or bundled. */
    fun softDelete(id: String): Boolean {
        val existing = customs[id] ?: return false
        customs[id] = existing.copy(deleted = true)
        persistCustoms()
        return true
    }

    fun restore(id: String): Boolean {
        val existing = customs[id] ?: return false
        customs[id] = existing.copy(deleted = false)
        persistCustoms()
        return true
    }

    /**
     * Permanently removes a custom exercise. Refuses when [isReferenced] returns true.
     * Returns true on success, false if id is unknown/bundled or still referenced.
     */
    fun hardDelete(id: String, isReferenced: (String) -> Boolean): Boolean {
        if (id !in customs) return false
        if (isReferenced(id)) return false
        customs.remove(id)
        persistCustoms()
        return true
    }

    private fun persistCustoms() {
        val list = customs.values.toList()
        AtomicWrite.writeText(
            customsFile,
            JsonIo.json.encodeToString(ListSerializer(Exercise.serializer()), list),
        )
    }

    companion object {
        /** Load bundled entries from an InputStream (e.g., AssetManager.open("exercises.json")). */
        fun fromAssets(bundledStream: InputStream, customsFile: File): ExerciseCatalog {
            val parsed = bundledStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val list = JsonIo.json.decodeFromString(ListSerializer(Exercise.serializer()), parsed)
            return ExerciseCatalog(list.associateBy { it.id }, customsFile)
        }

        /** Test seam: build a catalog from an already-decoded list. */
        fun fromInMemory(bundled: List<Exercise>, customsFile: File): ExerciseCatalog =
            ExerciseCatalog(bundled.associateBy { it.id }, customsFile)
    }
}
```

- [ ] **Step 5: Run, expect PASS** (all 9 tests).

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/assets/exercises.json app/src/main/java/com/ditrain/app/storage/ExerciseCatalog.kt app/src/test/java/com/ditrain/app/storage/ExerciseCatalogTest.kt
git commit -m "feat(storage): add ExerciseCatalog with bundled+custom merge and soft delete"
```

---

## Task 13: `storage/RoutineRepository.kt` — one JSON per routine

**Files:**
- Create: `app/src/main/java/com/ditrain/app/storage/RoutineRepository.kt`
- Create: `app/src/test/java/com/ditrain/app/storage/RoutineRepositoryTest.kt`

Spec reference: §4.2 ("Persisted at `filesDir/routines/{routine.id}.json` — one routine per file").

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ditrain.app.storage

import com.ditrain.app.model.ExerciseBlock
import com.ditrain.app.model.LoadTarget
import com.ditrain.app.model.LoopMode
import com.ditrain.app.model.RepsTarget
import com.ditrain.app.model.Routine
import com.ditrain.app.model.SessionTemplate
import com.ditrain.app.model.SetPrescription
import com.ditrain.app.model.Week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RoutineRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun sampleRoutine(id: String = "r1") = Routine(
        id = id, name = "R-$id", loopMode = LoopMode.REPEAT,
        weeks = listOf(Week("Week 1", listOf(
            SessionTemplate("a", "Push A", blocks = listOf(
                ExerciseBlock(
                    "bench",
                    sets = listOf(SetPrescription.Straight(
                        reps = RepsTarget.Fixed(5),
                        load = LoadTarget.AbsoluteKg(60.0),
                    ))
                )
            ))
        )))
    )

    private fun repo() = RoutineRepository(tmp.root)

    @Test fun `save then load equals`() {
        val repo = repo()
        val r = sampleRoutine()
        repo.save(r)
        assertEquals(r, repo.load("r1"))
    }

    @Test fun `save creates routines subdir`() {
        repo().save(sampleRoutine())
        assertTrue(tmp.root.resolve("routines").exists())
        assertTrue(tmp.root.resolve("routines/r1.json").exists())
    }

    @Test fun `load missing returns null`() {
        assertNull(repo().load("nope"))
    }

    @Test fun `overwrite same id replaces content`() {
        val repo = repo()
        repo.save(sampleRoutine())
        val updated = sampleRoutine().copy(name = "renamed")
        repo.save(updated)
        assertEquals("renamed", repo.load("r1")?.name)
    }

    @Test fun `list returns saved ids only`() {
        val repo = repo()
        repo.save(sampleRoutine("a"))
        repo.save(sampleRoutine("b"))
        assertEquals(setOf("a", "b"), repo.list().toSet())
    }

    @Test fun `delete removes the file`() {
        val repo = repo()
        repo.save(sampleRoutine("d"))
        assertTrue(repo.delete("d"))
        assertNull(repo.load("d"))
        assertFalse(tmp.root.resolve("routines/d.json").exists())
    }

    @Test fun `delete missing returns false`() {
        assertFalse(repo().delete("never"))
    }

    @Test fun `corrupt file load returns null and is not deleted`() {
        val repo = repo()
        val routinesDir = tmp.root.resolve("routines")
        routinesDir.mkdirs()
        val corrupt = routinesDir.resolve("bad.json")
        corrupt.writeText("{ this is not valid")
        assertNull(repo.load("bad"))
        assertTrue(corrupt.exists())   // never auto-delete user data
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (RoutineRepository unresolved).

- [ ] **Step 3: Implement**

```kotlin
package com.ditrain.app.storage

import com.ditrain.app.model.Routine
import com.ditrain.app.util.AtomicWrite
import com.ditrain.app.util.JsonIo
import java.io.File

/**
 * One JSON file per routine, under [filesDir]/routines/. Loads are forgiving:
 * a corrupt file returns null and is left in place for manual recovery
 * (the design spec §7 calls for not auto-deleting user data on parse failure).
 */
class RoutineRepository(filesDir: File) {

    private val dir: File = File(filesDir, "routines")

    fun save(routine: Routine) {
        val target = File(dir, "${routine.id}.json")
        AtomicWrite.writeText(target, JsonIo.json.encodeToString(Routine.serializer(), routine))
    }

    fun load(id: String): Routine? {
        val file = File(dir, "$id.json")
        if (!file.exists()) return null
        return runCatching {
            JsonIo.json.decodeFromString(Routine.serializer(), file.readText())
        }.getOrNull()
    }

    fun list(): List<String> {
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    fun delete(id: String): Boolean {
        val file = File(dir, "$id.json")
        return file.exists() && file.delete()
    }
}
```

- [ ] **Step 4: Run, expect PASS** (all 8 tests).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ditrain/app/storage/RoutineRepository.kt app/src/test/java/com/ditrain/app/storage/RoutineRepositoryTest.kt
git commit -m "feat(storage): add RoutineRepository with corrupt-file-safe loads"
```

---

## Task 14: `storage/SessionLogRepository.kt` — sessions.json + size-based rollover

**Files:**
- Create: `app/src/main/java/com/ditrain/app/storage/SessionLogRepository.kt`
- Create: `app/src/test/java/com/ditrain/app/storage/SessionLogRepositoryTest.kt`

Spec reference: §4.3 storage paragraph + §6.6 trigger conditions. This is the most algorithmically interesting storage class — focus tests on rollover.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ditrain.app.storage

import com.ditrain.app.model.ExecutedExercise
import com.ditrain.app.model.LoggedSet
import com.ditrain.app.model.SessionLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionLogRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun log(id: String, date: String, weightKg: Double = 100.0): SessionLog =
        SessionLog(
            id = id,
            routineId = null,
            weekIndex = null,
            sessionTemplateId = null,
            scheduledDate = date,
            performedDate = date,
            startedAt = "${date}T16:00:00Z",
            completedAt = "${date}T17:00:00Z",
            executed = listOf(ExecutedExercise("bench", sets = listOf(
                LoggedSet.Straight(weightKg, reps = 5, performedAt = "${date}T16:05:00Z")
            )))
        )

    private fun repo(rolloverBytes: Long = 1L shl 20) = SessionLogRepository(tmp.root, rolloverBytes)

    @Test fun `append writes to sessions json sorted by performedDate`() {
        val repo = repo()
        repo.append(log("a", "2026-05-02"))
        repo.append(log("b", "2026-05-01"))    // earlier — should sort first
        val all = repo.loadAll()
        assertEquals(listOf("b", "a"), all.map { it.id })
    }

    @Test fun `creates logs subdir on first write`() {
        repo().append(log("only", "2026-05-01"))
        assertTrue(File(tmp.root, "logs").exists())
        assertTrue(File(tmp.root, "logs/sessions.json").exists())
    }

    @Test fun `update existing id replaces in place`() {
        val repo = repo()
        repo.append(log("a", "2026-05-01", weightKg = 80.0))
        val updated = log("a", "2026-05-01", weightKg = 100.0)
        repo.upsert(updated)
        val loaded = repo.loadAll().single()
        assertEquals(100.0, (loaded.executed[0].sets[0] as LoggedSet.Straight).weightKg, 1e-9)
    }

    @Test fun `delete by id removes the entry`() {
        val repo = repo()
        repo.append(log("a", "2026-05-01"))
        repo.append(log("b", "2026-05-02"))
        assertTrue(repo.delete("a"))
        assertEquals(listOf("b"), repo.loadAll().map { it.id })
    }

    @Test fun `rollover archives the live file when bytes exceed threshold`() {
        // Tiny threshold forces a rollover after the first big write
        val repo = repo(rolloverBytes = 200L)
        repo.append(log("a", "2026-05-01"))     // first append, fits
        repo.append(log("b", "2026-05-10"))     // pushes past 200 B → triggers rollover

        // The live file should now contain exactly the most-recent post-rollover content
        val live = File(tmp.root, "logs/sessions.json")
        val archiveDir = File(tmp.root, "logs/archive")
        assertTrue(archiveDir.exists())
        val archives = archiveDir.listFiles()?.map { it.name } ?: emptyList()
        assertEquals(1, archives.size)
        assertTrue(archives.single().startsWith("sessions-2026-05-01_2026-05-10"))

        // live file still exists; loadAll covers live + archive seamlessly
        assertTrue(live.exists())
        val all = repo.loadAll()
        assertEquals(setOf("a", "b"), all.map { it.id }.toSet())
    }

    @Test fun `load returns merged chronological list across live and archive`() {
        val repo = repo(rolloverBytes = 200L)
        repo.append(log("old", "2026-01-01"))
        repo.append(log("mid", "2026-03-01"))   // forces rollover; old + mid archived
        repo.append(log("new", "2026-06-01"))   // lives in live file

        val all = repo.loadAll()
        assertEquals(listOf("old", "mid", "new"), all.map { it.id })
    }

    @Test fun `archive filename encodes correct first and last performedDate`() {
        val repo = repo(rolloverBytes = 200L)
        repo.append(log("a", "2026-02-15"))
        repo.append(log("b", "2026-04-01"))     // triggers rollover containing both
        val archives = File(tmp.root, "logs/archive").listFiles()!!
        assertEquals(1, archives.size)
        assertEquals("sessions-2026-02-15_2026-04-01.json", archives[0].name)
    }

    @Test fun `corrupt live file is renamed and a fresh one created`() {
        val live = File(tmp.root, "logs/sessions.json")
        live.parentFile.mkdirs()
        live.writeText("{ this is broken")
        val repo = repo()
        val all = repo.loadAll()
        assertEquals(emptyList<SessionLog>(), all)
        // corrupt file was renamed, not deleted
        assertNull(File(tmp.root, "logs").listFiles()?.firstOrNull { it.name == "sessions.json" && it.readText().contains("broken") })
        assertNotNull(File(tmp.root, "logs").listFiles()?.firstOrNull { it.name.startsWith("sessions.corrupt.") })
    }

    @Test fun `corrupt archive is skipped and other data still loads`() {
        val repo = repo()
        repo.append(log("live", "2026-06-01"))
        // Forge a bogus archive
        val archiveDir = File(tmp.root, "logs/archive").apply { mkdirs() }
        File(archiveDir, "sessions-2026-01-01_2026-02-01.json").writeText("not json")
        val all = repo.loadAll()
        // The live entry still loads even though the archive is unreadable
        assertEquals(listOf("live"), all.map { it.id })
    }

    @Test fun `loadByDateRange opens only overlapping archives`() {
        val repo = repo(rolloverBytes = 200L)
        repo.append(log("oldest", "2025-01-01"))
        repo.append(log("middle", "2025-06-01"))   // triggers rollover with both
        repo.append(log("recent", "2026-03-01"))
        val ids = repo.loadByDateRange(from = "2026-01-01", to = "2026-12-31").map { it.id }
        assertEquals(listOf("recent"), ids)
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (SessionLogRepository unresolved).

- [ ] **Step 3: Implement**

```kotlin
package com.ditrain.app.storage

import com.ditrain.app.model.SessionLog
import com.ditrain.app.util.AtomicWrite
import com.ditrain.app.util.JsonIo
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

    fun append(log: SessionLog) {
        val current = readLive().toMutableList()
        current.add(log)
        writeLive(current)
        maybeRollover()
    }

    fun upsert(log: SessionLog) {
        val current = readLive().toMutableList()
        val idx = current.indexOfFirst { it.id == log.id }
        if (idx >= 0) current[idx] = log else current.add(log)
        writeLive(current)
        maybeRollover()
    }

    fun delete(id: String): Boolean {
        val current = readLive().toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) writeLive(current)
        return removed
    }

    fun loadAll(): List<SessionLog> {
        val all = mutableListOf<SessionLog>()
        all.addAll(readAllArchives())
        all.addAll(readLive())
        return all.sortedBy { it.performedDate }
    }

    /** Returns logs whose performedDate is within [from..to] inclusive. */
    fun loadByDateRange(from: String, to: String): List<SessionLog> {
        val all = mutableListOf<SessionLog>()
        archiveFiles().forEach { f ->
            val (firstDate, lastDate) = decodeArchiveRange(f.name) ?: return@forEach
            if (lastDate < from || firstDate > to) return@forEach
            runCatching { JsonIo.json.decodeFromString(listSerializer, f.readText()) }
                .getOrNull()?.let { all.addAll(it.filter { l -> l.performedDate in from..to }) }
        }
        all.addAll(readLive().filter { it.performedDate in from..to })
        return all.sortedBy { it.performedDate }
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
        liveFile.delete()
    }

    private fun writeLive(list: List<SessionLog>) {
        val sorted = list.sortedBy { it.performedDate }
        AtomicWrite.writeText(liveFile, JsonIo.json.encodeToString(listSerializer, sorted))
    }

    private fun maybeRollover() {
        if (!liveFile.exists() || liveFile.length() <= rolloverBytes) return
        val live = readLive()
        if (live.isEmpty()) return
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

    /** Returns Pair(firstDate, lastDate) for "sessions-YYYY-MM-DD_YYYY-MM-DD.json"; null on malformed names. */
    private fun decodeArchiveRange(name: String): Pair<String, String>? {
        val core = name.removePrefix("sessions-").removeSuffix(".json")
        val parts = core.split("_")
        return if (parts.size == 2) parts[0] to parts[1] else null
    }

    companion object {
        const val DEFAULT_ROLLOVER_BYTES: Long = 1L shl 20      // 1 MiB
    }
}
```

- [ ] **Step 4: Run, expect PASS** (all 10 tests).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ditrain/app/storage/SessionLogRepository.kt app/src/test/java/com/ditrain/app/storage/SessionLogRepositoryTest.kt
git commit -m "feat(storage): add SessionLogRepository with size-based archive rollover"
```

---

## Task 15: `storage/AppStateRepository.kt` — whole-file rewrite

**Files:**
- Create: `app/src/main/java/com/ditrain/app/storage/AppStateRepository.kt`
- Create: `app/src/test/java/com/ditrain/app/storage/AppStateRepositoryTest.kt`

Spec reference: §4.4 ("Whole-file rewrite on every change").

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ditrain.app.storage

import com.ditrain.app.model.AppState
import com.ditrain.app.model.EffortMode
import com.ditrain.app.model.ScheduledSession
import com.ditrain.app.model.Settings
import com.ditrain.app.model.ThemeMode
import com.ditrain.app.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppStateRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `load on fresh dir returns defaults`() {
        val s = AppStateRepository(tmp.root).load()
        assertEquals(null, s.activeRoutineId)
        assertEquals(emptyList<ScheduledSession>(), s.scheduledSessions)
        assertEquals(WeightUnit.KG, s.settings.weightUnit)
        assertEquals(EffortMode.RPE, s.settings.effortMode)
    }

    @Test fun `save then load roundtrips`() {
        val repo = AppStateRepository(tmp.root)
        val s = AppState(
            activeRoutineId = "abc",
            scheduledSessions = listOf(
                ScheduledSession(date = "2026-05-14", routineId = "abc",
                    weekIndex = 0, sessionTemplateId = "push-a")
            ),
            settings = Settings(
                weightUnit = WeightUnit.LB,
                theme = ThemeMode.DARK,
                restTimerHaptic = false,
            ),
        )
        repo.save(s)
        assertEquals(s, repo.load())
    }

    @Test fun `state file lands at filesDir slash state json`() {
        val repo = AppStateRepository(tmp.root)
        repo.save(AppState(null, emptyList(), Settings()))
        assertTrue(tmp.root.resolve("state.json").exists())
    }

    @Test fun `corrupt state json falls back to defaults`() {
        tmp.root.resolve("state.json").writeText("not json")
        val s = AppStateRepository(tmp.root).load()
        assertEquals(null, s.activeRoutineId)
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (AppStateRepository unresolved).

- [ ] **Step 3: Implement**

```kotlin
package com.ditrain.app.storage

import com.ditrain.app.model.AppState
import com.ditrain.app.model.Settings
import com.ditrain.app.util.AtomicWrite
import com.ditrain.app.util.JsonIo
import java.io.File

class AppStateRepository(filesDir: File) {
    private val file = File(filesDir, "state.json")

    fun load(): AppState {
        if (!file.exists()) return defaultState()
        return runCatching {
            JsonIo.json.decodeFromString(AppState.serializer(), file.readText())
        }.getOrElse { defaultState() }
    }

    fun save(state: AppState) {
        AtomicWrite.writeText(file, JsonIo.json.encodeToString(AppState.serializer(), state))
    }

    private fun defaultState() = AppState(
        activeRoutineId = null,
        scheduledSessions = emptyList(),
        settings = Settings(),
    )
}
```

- [ ] **Step 4: Run, expect PASS**.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ditrain/app/storage/AppStateRepository.kt app/src/test/java/com/ditrain/app/storage/AppStateRepositoryTest.kt
git commit -m "feat(storage): add AppStateRepository with corrupt-fallback to defaults"
```

---

## Task 16: `ui/home/HomeViewController.kt` + wire into `MainActivity`

**Files:**
- Create: `app/src/main/java/com/ditrain/app/ui/home/HomeViewController.kt`
- Modify: `app/src/main/java/com/ditrain/app/MainActivity.kt`

The Home view is a placeholder for Plan 1 — just a title and a small status line proving the app is wired through. Subsequent plans replace the body.

- [ ] **Step 1: Write `HomeViewController.kt`**

```kotlin
package com.ditrain.app.ui.home

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Placeholder Home for Plan 1. Renders the app name and a status sub-line.
 * Plan 3 replaces this with the real session-aware Home.
 */
class HomeViewController(private val context: Context) {

    fun buildView(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(48, 48, 48, 48)

        addView(TextView(context).apply {
            text = "DiTrain"
            textSize = 36f
            gravity = Gravity.CENTER
        })

        addView(TextView(context).apply {
            text = "Foundation milestone — no routine loaded yet"
            textSize = 14f
            gravity = Gravity.CENTER
            alpha = 0.7f
            setPadding(0, 16, 0, 0)
        })
    }
}
```

- [ ] **Step 2: Rewrite `MainActivity.kt` to use the controller**

```kotlin
package com.ditrain.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ditrain.app.ui.home.HomeViewController

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(HomeViewController(this).buildView())
    }
}
```

- [ ] **Step 3: Build the APK**

```powershell
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Manual smoke test on a device or emulator**

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
.\gradlew.bat installDebug
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.ditrain.app/.MainActivity
```
Expected on screen: "DiTrain" centered with the subtitle "Foundation milestone — no routine loaded yet". The launcher icon shows the white-T glyph.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ditrain/app/ui/home/HomeViewController.kt app/src/main/java/com/ditrain/app/MainActivity.kt
git commit -m "feat(ui): wire MainActivity to a placeholder HomeViewController"
```

---

## Task 17: Full test suite + final foundation verification

This is the last task of Plan 1. No new code — it verifies the milestone is green.

- [ ] **Step 1: Run all unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`. Roughly 40+ tests across the model, util, and storage packages, all passing.

If anything fails, fix the failure in place (the spec is authoritative — fall back to it for any ambiguity) and re-run before committing the milestone.

- [ ] **Step 2: Build a release-signed bundle to confirm the signing config compiles**

```powershell
.\gradlew.bat bundleDebug
```
Expected: `BUILD SUCCESSFUL`. The bundle output ends in `app/build/outputs/bundle/debug/app-debug.aab`.

- [ ] **Step 3: Tag the milestone commit**

```powershell
git tag -a v0.1.0-foundation -m "Plan 1: Foundation milestone complete"
```

- [ ] **Step 4: Push tag (optional — only if remote exists; otherwise skip)**

Skip if no remote is configured (which is the current state of this repo).

---

## What's NOT yet done (handoff to Plan 2)

The runnable APK shows "DiTrain" and a placeholder line. None of the user-facing flows from spec §6 exist yet. Plan 2 picks up here:

- `assets/exercises.json` extended to ~150 entries
- `importing/RoutineImporter.kt` + validation + dialog
- `RoutineListDialogController`, `RoutinePreviewDialogController`, `ExercisePickerDialogController`, `ExerciseDetailDialogController`
- Wiring those into a `MainMenuController` that the Home overflow opens

Plan 3 then adds the real Home (today's session) and the session editor with rest timer. Plans 4–6 cover cardio, history/analytics, and polish/release.

---

## Plan-1 self-review

**Spec coverage** (against `2026-05-15-ditrain-v1-design.md`):
- §2 Stack & style — Task 1 (build files, manifest, theme) ✓
- §3 Package layout — every file in this plan's "Files Created in Plan 1" list maps to the layout in §3 ✓
- §4.1 Exercise — Task 7 ✓
- §4.2 Routine schema (incl. cardio) — Task 8 ✓
- §4.3 SessionLog (incl. cardio + myo-rep) — Task 9 ✓
- §4.4 AppState/Settings — Task 10 ✓
- §4.5 Backup archive — deliberately out of scope (Plan 6)
- §5 UI/navigation — placeholder Home only; full UI is Plans 2–5
- §6 Key flows — none yet; Plans 2–6
- §7 Error handling — Tasks 13, 14, 15 cover corrupt-file behaviour for routines, logs, and state respectively ✓
- §8 Edge cases — exercise soft delete (Task 12) ✓; remaining edge cases involve UI flows from later plans
- §9 Testing — model, util, storage all covered; UI-controller and importer tests in later plans ✓
- §10 Build & release — Tasks 1 and 17 ✓
- §12 Glossary — out of scope (Plan 6)

**Type consistency check:**
- `Exercise`, `Routine`, `SessionLog`, `AppState` field names match the spec verbatim ✓
- `LoggedSet.Straight.performedAt` is in the constructor position used by the test fixtures in Task 9 ✓
- `SessionLogRepository.DEFAULT_ROLLOVER_BYTES = 1L shl 20` matches the spec's "ROLLOVER_BYTES = 1L shl 20" callout ✓
- `Settings.restTimerHaptic` and `Settings.showDeletedExercises` match the names used in §4.4 ✓
- `ExerciseCatalog.softDelete / restore / hardDelete` match the names used in §6.6 / §8 ✓

**Placeholder scan:** None — every step has runnable code or a real shell command.

**Test surface:** ~40 tests across UnitsTest (5), RpeTest (4), JsonIoTest (3), AtomicWriteTest (4), ExerciseSerializationTest (5), RoutineSerializationTest (8), SessionLogSerializationTest (6), AppStateSerializationTest (3), ExerciseCatalogTest (9), RoutineRepositoryTest (8), SessionLogRepositoryTest (10), AppStateRepositoryTest (4). All run against the JVM target — no Android instrumentation required for Plan 1.
