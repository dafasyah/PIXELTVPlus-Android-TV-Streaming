# Architecture — PIXELTV v3.0

Hybrid model: **WebView = browse**, **ExoPlayer = watch**, **SettingsManager = config**. Keep these three responsibilities separate.

## The stream pipeline (the heart of v3)

```
WebView.shouldInterceptRequest(url)            ← runs OFF the UI thread
   │  (ad URLs are blocked here as before)
   ▼
StreamSniffer.inspect(url, userAgent, pageUrl, cookie) → MediaStream?   ← PURE, no Android imports
   │  classifies .m3u8 (HLS) / .mpd (DASH) / .mp4 (PROGRESSIVE); rejects ad domains + .ts segments
   │  builds replay headers: Referer=pageUrl, Origin, User-Agent, Cookie
   ▼
MainActivity: stream detected → runOnUiThread { show "▶ Putar tanpa iklan" pill }
   ▼
PlayerActivity(url, type, headers)
   │  DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)   ← header replay (avoids 403)
   │  HlsMediaSource / DashMediaSource / ProgressiveMediaSource by type
   ▼
ExoPlayer plays fullscreen, ad-free, D-pad controls
   │  onPlayerError → toast + finish() → back to WebView (fallback)
```

## Components

| Unit | File | Responsibility | Android deps? |
|---|---|---|---|
| `MediaStream` | `stream/MediaStream.kt` | Immutable: `url`, `type` (`StreamType` HLS/DASH/PROGRESSIVE), `headers` | **No** |
| `StreamSniffer` | `stream/StreamSniffer.kt` | Classify request URL → `MediaStream?`; hold `latest`; `clear()` on navigation; reject ads/segments | **No** (JVM-testable) |
| `KeyValueStore` | `data/KeyValueStore.kt` | Persistence seam (interface + `PrefsKeyValueStore`) so settings logic is testable | impl only |
| `SettingsManager` | `data/SettingsManager.kt` | endpoint URL + `autoSniff` + `userAgent`; defaults; `setEndpoint()` validation; `resetToDefaults()` | via seam |
| `PlayerActivity` | `PlayerActivity.kt` | Media3 player; replay headers; D-pad; error → finish (fallback); release in onStop/onDestroy | Yes (`@UnstableApi`) |
| `SettingsActivity` | `SettingsActivity.kt` | Programmatic TV form to edit endpoint/options | Yes |
| `MainActivity` | `MainActivity.kt` | WebView browse; wire sniffer in `shouldInterceptRequest`; pill button; endpoint from settings | Yes |
| `HomeActivity` | `HomeActivity.kt` | Simplified launcher (Start / Settings / Continue) | Yes |
| `OverlayMenu` | `ui/OverlayMenu.kt` | In-player menu; add Settings entry | Yes |

## Key interfaces

```kotlin
enum class StreamType { HLS, DASH, PROGRESSIVE }
data class MediaStream(val url: String, val type: StreamType, val headers: Map<String, String>)

class StreamSniffer(private val adDomains: List<String>) {
    val latest: MediaStream?                    // most recent detection
    fun inspect(url: String, userAgent: String, pageUrl: String, cookie: String?): MediaStream?
    fun clear()                                 // call on new page navigation
}

class SettingsManager(store: KeyValueStore) {
    var endpointUrl: String; var autoSniff: Boolean; var userAgent: String
    fun setEndpoint(url: String): Boolean       // false (and no save) if not http(s)
    fun resetToDefaults()
    companion object { const val DEFAULT_ENDPOINT; const val DEFAULT_UA; fun from(ctx): SettingsManager }
}
```

## Fallback matrix

| Situation | Behavior |
|---|---|
| No stream sniffed | Pill hidden; WebView plays normally (fallback) |
| `autoSniff` off | Sniffer not called; pure WebView |
| ExoPlayer error (403 / codec / DRM) | Toast + `finish()` → back to WebView on the same page |
| Invalid endpoint in Settings | Rejected with toast; previous/default kept |
| Multiple `.m3u8` seen | Keep the most recent non-ad manifest carrying the page Referer |

## Threading & lifecycle notes

- `shouldInterceptRequest` runs **off the UI thread** → wrap any UI change (showing the pill) in `runOnUiThread {}`.
- Call `sniffer.clear()` + hide the pill in `onPageStarted` so a stale stream doesn't leak across pages.
- Release the ExoPlayer in `onStop`/`onDestroy`; never leak it.

Full rationale and the file inventory are in [`planning/design-spec.md`](planning/design-spec.md).
