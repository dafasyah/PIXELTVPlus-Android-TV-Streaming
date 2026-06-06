# Project Overview — PIXELTV

## What it is

PIXELTV is an **Android TV + mobile streaming app** built with **Kotlin** and a **programmatic UI** (views are constructed in Kotlin; there are no XML layouts under `res/layout`). Historically (v1–v2.1) it was a WebView that loaded streaming sites with an ad blocker, bookmarks, history, a draggable floating menu, and D-pad/remote navigation via injected JavaScript.

## The v3.0 upgrade (current work)

**Problem being solved:** the embedded iframe players on streaming sites are full of ads/popups and are awkward to control with a TV remote.

**Solution (hybrid model):**
- Keep **browsing** in the WebView, but simplify to **one configurable endpoint** (default iDlix) that the user can change in a new **Settings** screen. The 3 hardcoded sites are dropped.
- While browsing, **sniff** the media stream: intercept WebView network requests, detect a `.m3u8`/`.mpd`/`.mp4` manifest, and capture the headers needed to replay it (`Referer`, `Origin`, `User-Agent`, `Cookie`).
- When a stream is detected, show a **“▶ Putar tanpa iklan”** button. Tapping it opens a **native Media3/ExoPlayer** that plays the stream fullscreen, ad-free, with full remote control.
- If nothing can be sniffed (DRM/`blob:`/MSE), the existing **ad-blocked WebView** keeps playing (**automatic fallback**). The native player never becomes a hard dependency.

**Why this approach:** it bypasses iframe ads entirely for sniffable streams, gives a real TV-native playback experience, and stays robust via the fallback. It was chosen over (B) pure WebView ad-blocking and (C) a full native catalog rewrite. See [`planning/design-spec.md`](planning/design-spec.md) §2.

## Current status (2026-06)

- Branch: **`feat/native-player-v3`** (off `main`).
- **Done:** project tooling + docs; CLI build environment set up; **Task 0** (Media3 1.4.1 + JUnit deps, version → **3.0.0 / code 12**, smoke test) — implemented and reviewed.
- **Remaining:** Tasks 1–8 (model, sniffer + tests, settings + tests, player, settings screen, MainActivity wiring, Home/menu, cleanup). Track in [`tasklist.md`](tasklist.md).

## App flow (target v3.0)

```
SplashActivity
  └─> HomeActivity   ( ▶ Mulai Streaming | ⚙️ Settings | 📋 Lanjut Nonton )
        └─> MainActivity        (WebView; endpoint from SettingsManager; sniffs streams)
              └─> PlayerActivity (native Media3 player; header replay; D-pad controls)
        SettingsActivity        (edit endpoint URL, auto-sniff toggle, User-Agent)
```

## Package layout (`app/src/main/kotlin/com/streamtv/app/`)

- Activities: `SplashActivity`, `HomeActivity`, `MainActivity` (WebView), `PlayerActivity` (new, Media3), `SettingsActivity` (new).
- `stream/` — **pure logic** (no Android imports): `StreamSniffer`, `MediaStream`.
- `data/` — persistence: `SettingsManager` (+ `KeyValueStore` seam), `BookmarkManager`, `HistoryManager`, `SiteManager` (shrinking to a default-URL constant).
- `ui/` — `OverlayMenu`.

## Constraints

- minSdk 21 / targetSdk 34; Media3 supports minSdk 21.
- Programmatic UI, `android.app.Activity` base class (not AppCompat) by existing convention.
- Comments and user-facing strings are frequently **Bahasa Indonesia** — match the surrounding language.
