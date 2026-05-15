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
