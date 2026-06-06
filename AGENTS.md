# AGENTS.md — PIXELTV

> **You are an AI agent or assistant working on this repository. Read this file first.**
> This is the vendor-neutral entry point (works for any model/tool: Claude, GPT/Codex, Gemini, Cursor, Copilot, …). Deeper docs live in [`ai/`](ai/README.md). Tool-specific files (`CLAUDE.md`, `GEMINI.md`, `.cursorrules`) just point here.

## What this project is

PIXELTV is an **Android TV & mobile streaming app** (Kotlin, programmatic UI — no XML layouts). It wraps streaming site(s) in a WebView. It is being upgraded to a **hybrid model**:

- **Browse** in a WebView (one configurable endpoint, editable in a Settings screen).
- **Watch** in a **native Media3/ExoPlayer** that plays the `.m3u8`/`.mpd`/`.mp4` stream captured ("sniffed") from the page — **ad-free**.
- **Fallback**: if no stream can be sniffed (DRM/`blob:`), the ad-blocked WebView keeps playing. The native player is an enhancement layer, never a hard dependency.

**Current state:** v3.0 work is in progress on branch `feat/native-player-v3`. Task 0 (deps + version bump + test harness) is done; Tasks 1–8 remain. See [`ai/tasklist.md`](ai/tasklist.md).

## Hard rules (do not break)

1. **Fallback always.** If a stream can't be sniffed or the player errors, fall back to WebView. Never crash or dead-end when sniffing fails.
2. **Replay headers.** A sniffed `.m3u8` usually needs `Referer`, `Origin`, `User-Agent`, `Cookie` or it returns `403`. Thread captured headers from the sniffer → player → `DefaultHttpDataSource`. Never send just the URL.
3. **Keep sniffer logic pure.** `stream/StreamSniffer` and `stream/MediaStream` must have **no Android imports** (so they stay JVM-unit-testable). Get cookies via `CookieManager` in the Activity and pass them in.
4. **One endpoint, from Settings.** A single active endpoint owned by `SettingsManager`. Do not reintroduce hardcoded site lists or multi-site switching.
5. **Reuse the ad list.** Ad filtering uses `MainActivity.AD_BLOCK_LIST`; the sniffer rejects those domains too. Add new ad domains in one place.

## Build & test (Windows PowerShell, Gradle wrapper)

```powershell
.\gradlew.bat testDebugUnitTest      # JVM unit tests (sniffer, settings)
.\gradlew.bat compileDebugKotlin     # fast compile check
.\gradlew.bat assembleDebug          # build debug APK
```

JDK = Android Studio's bundled JBR. If `JAVA_HOME` isn't set in your shell, set it inline:
`$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat <task> --console=plain`
Details: [`ai/build-and-test.md`](ai/build-and-test.md).

## Where to go next

| You want… | Read |
|---|---|
| The full picture | [`ai/project-overview.md`](ai/project-overview.md) |
| Architecture & data flow | [`ai/architecture.md`](ai/architecture.md) |
| **The planned task list + status** | [`ai/tasklist.md`](ai/tasklist.md) |
| Exact step-by-step plan (with code) | [`ai/planning/implementation-plan.md`](ai/planning/implementation-plan.md) |
| Design rationale | [`ai/planning/design-spec.md`](ai/planning/design-spec.md) |
| Coding rules & style | [`ai/conventions.md`](ai/conventions.md) |
| How to work on stream/player features | [`ai/skills/implementing-stream-features.md`](ai/skills/implementing-stream-features.md) |
| How to verify on a TV | [`ai/skills/verifying-on-android-tv.md`](ai/skills/verifying-on-android-tv.md) |
| Reusable reviewer/build agent roles | [`ai/agents/`](ai/agents/) |
| Future ideas | [`ai/roadmap.md`](ai/roadmap.md) |

**Tech stack:** Kotlin · Android (minSdk 21 / targetSdk 34) · AndroidX · WebView · Media3 1.4.1 (ExoPlayer/HLS/DASH/UI) · JUnit4 · Gradle 8.7 (AGP 8.4.0). Comments/UI strings are often in Bahasa Indonesia — match the surrounding language.
