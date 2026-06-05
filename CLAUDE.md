# PIXELTV — Project Rules for Claude

Android TV & mobile streaming app. **Kotlin + WebView**, evolving toward a **hybrid model**: browse in WebView, watch in a **native Media3 player** fed by stream-sniffing, with a single configurable endpoint.

> Active design & plan:
> - Spec: `docs/superpowers/specs/2026-06-05-pixeltv-native-player-design.md`
> - Plan: `docs/superpowers/plans/2026-06-05-pixeltv-native-player.md`

## Architecture (where things go)

- **Activities** live in `app/src/main/kotlin/com/streamtv/app/` (`Splash`, `Home`, `Main`=WebView, `Player`=Media3, `Settings`).
- **Pure logic** (testable, no Android imports) lives in `stream/` (`StreamSniffer`, `MediaStream`) and behind seams like `data/KeyValueStore`.
- **Data/persistence** lives in `data/` (`SettingsManager`, `BookmarkManager`, `HistoryManager`). All use SharedPreferences `"pixeltv_prefs"`.
- **UI helpers** live in `ui/` (`OverlayMenu`).
- **Three layers, three responsibilities:** WebView = *browse*, ExoPlayer = *watch*, SettingsManager = *config*. Don't blur them.

## Hard rules (do not break)

1. **Fallback always.** The native player is an *enhancement layer*. If a stream can't be sniffed (DRM/`blob:`/MSE) or the player errors, the app MUST fall back to WebView playback and never crash or dead-end. Any change that can make the app unusable when sniffing fails is wrong.
2. **Replay headers, don't drop them.** A sniffed `.m3u8` usually needs `Referer`, `Origin`, `User-Agent`, and `Cookie` or it returns `403`. Always pass captured headers from `StreamSniffer` → `PlayerActivity` → `DefaultHttpDataSource`. If you "simplify" by sending just the URL, playback breaks.
3. **Keep sniffer logic pure.** `StreamSniffer`/`MediaStream` must have **no Android imports** so they stay JVM-unit-testable. Get cookies via `CookieManager` in the Activity and pass them *in*; don't reach into Android from the sniffer.
4. **One endpoint, from Settings.** There is a single active endpoint owned by `SettingsManager`. Do not reintroduce hardcoded site lists or multi-site switching (`switchSite`, `CH+/CH-`). New URLs come from Settings only.
5. **Reuse the ad list.** Ad filtering uses `MainActivity.AD_BLOCK_LIST`. The sniffer also rejects those domains. Add new ad domains in one place.

## Code style (match the existing codebase)

- **Programmatic UI only** — this project builds views in Kotlin (no XML layouts in `res/layout`). New screens follow the same pattern (see `HomeActivity`, `SettingsActivity`): `LinearLayout`/`FrameLayout` + `density`-scaled padding + glassmorphism colors (`#0D0D1A` bg, `#7C4DFF` accent).
- **TV-first focus:** every interactive view sets `isFocusable = true` and a focus listener (scale/stroke change) so the D-pad works. Test navigation with arrow keys, not just touch.
- **Activities extend `android.app.Activity`** (not AppCompat) here. Keep it consistent unless a feature needs AppCompat.
- **Media3 unstable APIs:** annotate player classes with `@androidx.media3.common.util.UnstableApi`.
- Comments and user-facing strings are commonly **Bahasa Indonesia** — match the surrounding language.

## Build & test (Windows PowerShell, Gradle wrapper)

```powershell
.\gradlew.bat testDebugUnitTest      # run JVM unit tests (sniffer, settings)
.\gradlew.bat assembleDebug          # build debug APK
.\gradlew.bat compileDebugKotlin     # fast compile check
.\gradlew.bat clean assembleDebug testDebugUnitTest   # full verify before a release
```

- Unit tests live in `app/src/test/kotlin/...` (JUnit4). **New pure logic must ship with tests** — TDD: red → green → commit.
- Android UI (Activities) isn't unit-tested here; verify it **manually on a device/emulator** (TV + phone) and say so honestly in the result.

## Versioning

- Bump **both** `versionCode` (integer +1) and `versionName` in `app/build.gradle.kts` together for any release.
- Add a matching `## Changelog` entry in `README.md` and update `OverlayMenu.APP_VERSION` + the Home footer string.
- Current target after this work: **versionName `3.0.0`, versionCode `12`**.

## Workflow expectations

- This project uses the **superpowers** flow: brainstorm → spec (`docs/superpowers/specs/`) → plan (`docs/superpowers/plans/`) → TDD implementation with frequent commits.
- For stream/player work, the project skill **`implementing-stream-features`** captures the conventions; for manual checks use **`verifying-on-android-tv`**.
- Commit in small, working increments. Don't bundle unrelated changes.
