---
name: implementing-stream-features
description: Use when adding or changing PIXELTV stream-sniffing or native-player features — editing StreamSniffer, MediaStream, PlayerActivity, the WebView intercept, or anything touching how a .m3u8/.mpd/.mp4 is captured and played. Encodes the project's non-obvious conventions so playback doesn't break.
---

# Implementing Stream Features (PIXELTV)

PIXELTV captures a stream from the WebView and plays it in a native Media3 player to bypass iframe ads. This is fragile by nature — follow these conventions or playback silently breaks (black screen / `403`).

**Announce:** "Using implementing-stream-features to guide this stream/player change."

## The pipeline (know it before you touch it)

```
WebView shouldInterceptRequest(url)
   └─ StreamSniffer.inspect(url, userAgent, pageUrl, cookie) → MediaStream?  [pure, no Android]
        └─ MainActivity shows "▶ Putar tanpa iklan" (runOnUiThread)
             └─ PlayerActivity(url, type, headers) → DefaultHttpDataSource(headers) → ExoPlayer
```

## Rules (the ones that bite)

1. **Keep `StreamSniffer` / `MediaStream` pure** — no `android.*` imports. Cookies come from `CookieManager` *in the Activity* and are passed *into* `inspect()`. This keeps the unit tests pure-JVM.
2. **Thread the headers all the way through.** `Referer`, `Origin`, `User-Agent`, `Cookie` are captured by the sniffer and MUST reach `DefaultHttpDataSource.setDefaultRequestProperties(...)`. Most `403`s are a dropped header. When debugging playback, check the headers first.
3. **`shouldInterceptRequest` is off-thread.** Any UI change (showing the pill) goes in `runOnUiThread {}`.
4. **Reset on navigation.** Call `sniffer.clear()` + hide the pill in `onPageStarted`, or a stale stream from the previous page plays.
5. **Manifests, not segments.** The sniffer wants `.m3u8`/`.mpd`/`.mp4`; it rejects `.ts` chunks and ad domains. Don't loosen this to match segments.
6. **Fallback is sacred.** If `inspect()` returns null or the player errors, the WebView must keep working. Never make native playback a hard requirement.
7. **Release the player** in `onStop`/`onDestroy`; annotate Media3 classes with `@UnstableApi`.

## TDD workflow for sniffer/classification changes

The sniffer is the one piece that's cheap to test — so test it.

1. Add the case to `app/src/test/kotlin/com/streamtv/app/stream/StreamSnifferTest.kt` first (e.g., a new container type, a tricky query string, a new ad domain).
2. Run red: `.\gradlew.bat testDebugUnitTest --tests "com.streamtv.app.stream.StreamSnifferTest"`
3. Implement the minimal change in `StreamSniffer.inspect()`.
4. Run green. Commit.

For `SettingsManager` changes, do the same against `SettingsManagerTest` (it uses an in-memory `KeyValueStore` fake — no Android needed).

## Player changes (PlayerActivity)

- Pick the `MediaSource` by `StreamType` (`HlsMediaSource` / `DashMediaSource` / `ProgressiveMediaSource`), all built on the header-carrying `DefaultHttpDataSource.Factory`.
- These can't be JVM unit-tested → verify on a device. Use the **`verifying-on-android-tv`** skill and report results honestly (which titles played native, which fell back).

## When a site stops working

Sniffing is site-dependent. If native playback regresses after a site changes its player:
1. Confirm the `.m3u8` is still an HTTP request (not `blob:`/MSE) by watching `shouldInterceptRequest` logs.
2. If it moved to `blob:`/DRM → it legitimately falls back to WebView; that's by design, don't force it.
3. If it's still HTTP but `403`s → compare the headers the site's player sends vs. what we replay; add the missing one to the sniffer's header map.

## Definition of done

- New classification logic has a unit test and it's green.
- Headers verified to reach the player.
- Fallback path manually confirmed (a non-sniffable title still plays in WebView).
- `versionCode`/`versionName` + changelog updated if this is a release.
