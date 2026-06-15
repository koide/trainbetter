# Windows setup for DiTrain

This guide gets a fresh Windows machine ready to build, test, and install DiTrain.

## Required tools

- Git
- JDK 17
- Android Studio, including Android SDK
- Android SDK Platform-Tools (`adb`)
- Android SDK Platform 35 / Build Tools

Gradle does **not** need to be installed globally. This repo uses the checked-in Gradle wrapper (`gradlew.bat`), which downloads Gradle 8.11.1 automatically.

## 1. Install base tools

Open PowerShell as your normal user.

```powershell
winget install --id Git.Git -e
winget install --id EclipseAdoptium.Temurin.17.JDK -e
winget install --id Google.AndroidStudio -e
```

Restart PowerShell after installation so `PATH` updates are picked up.

## 2. Install Android SDK components

Open **Android Studio** once and complete the first-run setup. Then go to:

`Settings > Languages & Frameworks > Android SDK`

Install/check:

- **SDK Platforms**
  - Android 15 / API 35
- **SDK Tools**
  - Android SDK Platform-Tools
  - Android SDK Build-Tools
  - Android SDK Command-line Tools
  - Android Emulator, if you want to run an emulator

Default SDK location on Windows is usually:

```text
C:\Users\YOUR_USER\AppData\Local\Android\Sdk
```

## 3. Set environment variables

For the current PowerShell session:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:Path="$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:Path"
```

If your JDK folder has a different patch version, adjust `JAVA_HOME`. You can find installed JDKs with:

```powershell
Get-ChildItem "C:\Program Files\Eclipse Adoptium"
```

To make the variables permanent for your user:

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot", "User")
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
[Environment]::SetEnvironmentVariable("Path", "$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin;" + [Environment]::GetEnvironmentVariable("Path", "User"), "User")
```

Restart PowerShell after making permanent changes.

## 4. Clone the repo

```powershell
git clone https://github.com/koide/trainbetter.git
cd trainbetter
```

## 5. Optional local SDK file

Gradle can use `ANDROID_HOME`, but you may also create `local.properties`:

```powershell
Copy-Item local.properties.example local.properties
```

Edit `local.properties` if your SDK is not in the default location:

```properties
sdk.dir=C\:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk
```

## 6. Verify tools

```powershell
java -version
& "$env:ANDROID_HOME\platform-tools\adb.exe" version
.\gradlew.bat --version
```

Expected:

- Java reports version 17
- `adb` prints its version
- Gradle wrapper downloads/prints Gradle 8.11.1

## 7. Accept Android licenses

```powershell
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
```

Accept the licenses when prompted.

If `sdkmanager.bat` is not found, install **Android SDK Command-line Tools** from Android Studio's SDK Tools tab.

## 8. Build and test

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

## 9. Install on a device

On your Android phone/tablet:

1. Enable Developer options.
2. Enable USB debugging.
3. Connect via USB.
4. Accept the debugging prompt on the device.

Then run:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
.\gradlew.bat installDebug
```

You should see your device listed as `device`, not `unauthorized`.

## 10. Run on an emulator instead

In Android Studio:

1. Open `Tools > Device Manager`.
2. Create a virtual device.
3. Use an API 35 system image if available.
4. Start the emulator.

Then:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
.\gradlew.bat installDebug
```

## 11. Release bundle, optional

For a release bundle, copy and fill in the keystore config:

```powershell
Copy-Item keystore.properties.example keystore.properties
.\gradlew.bat bundleRelease
```

`keystore.properties` is local machine secret config and should not be committed.

## Dependency versions

Project dependencies are declared in Gradle files:

- Android Gradle Plugin: 8.7.3
- Kotlin: 2.0.21
- Gradle wrapper: 8.11.1
- compileSdk / targetSdk: 35
- minSdk: 23
- AppCompat: 1.7.0
- kotlinx-serialization-json: 1.7.3
- kotlinx-coroutines-android/test: 1.9.0
- JUnit: 4.13.2
