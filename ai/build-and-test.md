# Build & Test — PIXELTV

The project builds with the **Gradle wrapper** and the **Android Studio bundled JDK (JBR)**. It can be built from Android Studio or from the command line.

## Toolchain

| Piece | Value |
|---|---|
| Build system | Gradle **8.7** (wrapper committed: `./gradlew` / `.\gradlew.bat`) |
| Android Gradle Plugin | 8.4.0 |
| Kotlin | 1.9.22 |
| JDK | Android Studio JBR (Java 21), e.g. `C:\Program Files\Android\Android Studio\jbr` |
| Android SDK | e.g. `C:\Users\<you>\AppData\Local\Android\Sdk` |
| minSdk / targetSdk | 21 / 34 |

## One-time local setup

1. **`local.properties`** (gitignored, machine-specific) must point at your SDK:
   ```properties
   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   ```
   Android Studio creates this automatically when you open the project. For CLI-only, create it by hand.
2. **A JDK must be discoverable.** Either set `JAVA_HOME` (recommended) to the Android Studio JBR, or ensure `java` is on `PATH`.

## Commands (Windows PowerShell)

```powershell
.\gradlew.bat testDebugUnitTest                  # JVM unit tests (StreamSniffer, SettingsManager, SmokeTest)
.\gradlew.bat testDebugUnitTest --tests "com.streamtv.app.stream.StreamSnifferTest"   # one class
.\gradlew.bat compileDebugKotlin                 # fast compile check
.\gradlew.bat assembleDebug                      # build debug APK -> app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat clean assembleDebug testDebugUnitTest   # full verify before a release
```

macOS/Linux: use `./gradlew` instead of `.\gradlew.bat`.

### If `JAVA_HOME` isn't set in your shell

Set it inline in the same command:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --console=plain
```

> Note for AI agents: a tool session that started *before* `JAVA_HOME` was set at the OS level won't see it in freshly-spawned shells — use the inline form above. A restarted session inherits it and can call `.\gradlew.bat` directly.

## Install & launch on a device/emulator

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.streamtv.app/.SplashActivity
adb logcat -s PIXELTV ExoPlayerImpl AndroidRuntime   # watch app/player/crash logs
```

For the full on-device verification checklist (D-pad, native player, fallback), see [`skills/verifying-on-android-tv.md`](skills/verifying-on-android-tv.md).
