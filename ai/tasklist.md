# Task List — PIXELTV v3.0 Native Player

The planned implementation, broken into 9 TDD tasks. **This file is the live status tracker.** Exact step-by-step instructions (with full code, commands, and commit messages) for each task are in [`planning/implementation-plan.md`](planning/implementation-plan.md) — this is the index/status view.

**Branch:** `feat/native-player-v3` · **Goal:** native ad-free Media3 player fed by WebView stream-sniffing + single configurable endpoint, with WebView fallback.

**Legend:** ✅ done & reviewed · 🟡 in progress · ⬜ not started
**Verify column:** `unit` = JVM unit-testable here · `compile` = `compileDebugKotlin` · `manual` = needs device/emulator (Android Studio/TV).

| # | Task | Status | Verify | Key files |
|---|------|--------|--------|-----------|
| 0 | **Dependencies, version bump, test wiring** — Media3 1.4.1 + JUnit; version → 3.0.0/12; smoke test | ✅ | unit | `app/build.gradle.kts`, `app/src/test/.../SmokeTest.kt` |
| 1 | **`MediaStream` model** — `url`/`type`/`headers` + `StreamType` enum | ⬜ | compile | `stream/MediaStream.kt` |
| 2 | **`StreamSniffer` (TDD)** — classify `.m3u8`/`.mpd`/`.mp4`, reject ads/`.ts`, build replay headers; **11 tests** | ⬜ | unit | `stream/StreamSniffer.kt` (+ test) |
| 3 | **`SettingsManager` (TDD)** — endpoint/autoSniff/UA, validation, defaults, in-memory fake; **7 tests** | ⬜ | unit | `data/KeyValueStore.kt`, `data/SettingsManager.kt` (+ test) |
| 4 | **`PlayerActivity` (Media3)** — HLS/DASH/Progressive, header replay, D-pad, error→finish; manifest entry | ⬜ | compile + manual | `PlayerActivity.kt`, `AndroidManifest.xml` |
| 5 | **`SettingsActivity`** — programmatic TV form (URL, auto-sniff, UA, save/reset); manifest entry | ⬜ | compile + manual | `SettingsActivity.kt`, `AndroidManifest.xml` |
| 6 | **Wire sniffer + pill into `MainActivity`** — sniff in `shouldInterceptRequest`, “▶ Putar tanpa iklan”, endpoint from settings, drop multi-site | ⬜ | compile + manual | `MainActivity.kt` |
| 7 | **Simplify `HomeActivity` + Settings in `OverlayMenu`** — Start/Settings cards, drop 3-site grid | ⬜ | compile + manual | `HomeActivity.kt`, `ui/OverlayMenu.kt` |
| 8 | **Shrink `SiteManager`, README changelog, final verify** | ⬜ | compile + unit | `data/SiteManager.kt`, `README.md` |

## Recommended execution order & checkpoints

1. **Tasks 1–3** are the fully unit-verifiable core (model + sniffer + settings, 18 tests total). Do these first; they prove out the logic on any machine.
2. **Tasks 4–7** are Android UI — they compile-check here but need manual verification on a device/emulator (see [`skills/verifying-on-android-tv.md`](skills/verifying-on-android-tv.md)).
3. **Task 8** is cleanup + docs + a full `clean assembleDebug testDebugUnitTest`.

Each task in the plan follows: write failing test → run red → implement → run green → commit (for `unit` tasks), or implement → compile → manual-check → commit (for UI tasks).

## How to resume (any agent)

1. Check out `feat/native-player-v3`; run `git log --oneline` to see which tasks are committed (the checkboxes here are a guide; **git history is the source of truth** for progress).
2. Open [`planning/implementation-plan.md`](planning/implementation-plan.md), find the next task, and follow its steps verbatim (it contains the exact code).
3. Build/test per [`build-and-test.md`](build-and-test.md). Obey the hard rules in [`conventions.md`](conventions.md).
4. Update the Status column here as you complete each task.

## Progress log

- **Task 0** — ✅ committed `88fc059`. Media3 1.4.1 resolved cleanly; smoke test passes; spec + code-quality reviewed. (Preceded by `c9392ec` adding the Gradle wrapper, and `5427b9c` adding docs/tooling.)
- **Tasks 1–8** — ⬜ not started.
