# Conventions & Rules — PIXELTV

This is the **canonical** rules/style reference for any AI agent or contributor. (The root `AGENTS.md` lists the 5 hard rules in brief; this expands them and adds code style.)

## Hard rules (do not break)

1. **Fallback always.** The native player is an *enhancement layer*. If a stream can't be sniffed (DRM/`blob:`/MSE) or the player errors, the app MUST fall back to WebView playback and never crash or dead-end. Any change that can make the app unusable when sniffing fails is wrong.
2. **Replay headers, don't drop them.** A sniffed `.m3u8` usually needs `Referer`, `Origin`, `User-Agent`, and `Cookie` or it returns `403`. Always pass captured headers from `StreamSniffer` → `PlayerActivity` → `DefaultHttpDataSource`. Sending just the URL breaks playback.
3. **Keep sniffer logic pure.** `StreamSniffer` / `MediaStream` must have **no Android imports** so they stay JVM-unit-testable. Get cookies via `CookieManager` in the Activity and pass them *in*; don't reach into Android from the sniffer. (A repo hook warns if `android.*` is imported there — see `.claude/hooks/pixeltv-guard.ps1`.)
4. **One endpoint, from Settings.** A single active endpoint owned by `SettingsManager`. Do not reintroduce hardcoded site lists or multi-site switching (`switchSite`, `CH+/CH-`). New URLs come from Settings only.
5. **Reuse the ad list.** Ad filtering uses `MainActivity.AD_BLOCK_LIST`. The sniffer also rejects those domains. Add new ad domains in one place.

## Code style (match the existing codebase)

- **Programmatic UI only.** Views are built in Kotlin (no `res/layout` XML). New screens follow the existing pattern (`HomeActivity`, `SettingsActivity`): `LinearLayout`/`FrameLayout` + `density`-scaled padding + the app palette (`#0D0D1A` background, `#7C4DFF` accent, glassmorphism cards).
- **TV-first focus.** Every interactive view sets `isFocusable = true` and a focus listener (scale/stroke change) so the D-pad works. Verify with arrow keys, not just touch.
- **Activity base class.** Activities extend `android.app.Activity` (not AppCompat) here. Keep it consistent unless a feature truly needs AppCompat.
- **Media3 unstable APIs.** Annotate player classes with `@androidx.media3.common.util.UnstableApi`.
- **Language.** Comments and user-facing strings are commonly **Bahasa Indonesia** — match the surrounding language.

## Testing

- New **pure logic ships with unit tests** (TDD: red → green → commit). Tests live in `app/src/test/kotlin/...` (JUnit4).
- The `StreamSniffer` and `SettingsManager` are designed to be JVM-unit-testable (no Android runtime). `SettingsManager` depends on a `KeyValueStore` seam; tests use an in-memory fake.
- Android UI (Activities) isn't unit-tested here — verify it **manually on a device/emulator** (TV + phone) and report results honestly. See [`skills/verifying-on-android-tv.md`](skills/verifying-on-android-tv.md).

## Versioning

- Bump **both** `versionCode` (integer +1) and `versionName` in `app/build.gradle.kts` together for any release.
- Add a matching `## Changelog` entry in `README.md`, and update `OverlayMenu.APP_VERSION` + the Home footer string.
- Current target after v3 work: **versionName `3.0.0`, versionCode `12`**.

## Workflow

- Flow: design ([`planning/design-spec.md`](planning/design-spec.md)) → plan ([`planning/implementation-plan.md`](planning/implementation-plan.md)) → TDD implementation with **frequent, small commits**. Don't bundle unrelated changes.
- For stream/player work, follow [`skills/implementing-stream-features.md`](skills/implementing-stream-features.md).
- Track progress in [`tasklist.md`](tasklist.md).
