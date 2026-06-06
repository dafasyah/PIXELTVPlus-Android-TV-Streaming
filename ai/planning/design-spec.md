# PIXELTV v3.0 — Native Player + Single Endpoint (Design Spec)

**Date:** 2026-06-05
**Status:** Approved (brainstorming complete)
**Author:** Daffa + Claude

---

## 1. Goal

Upgrade PIXELTV so that:

1. **Browsing stays in WebView**, but **playback happens in a native, ad-free player** (Media3/ExoPlayer).
2. The app uses **one configurable streaming endpoint** instead of three hardcoded sites — the URL is editable in a **Settings** screen.
3. The app is **easier to operate** (simpler Home, fewer moving parts).

**Core motivation:** the embedded iframe players on streaming sites are full of ads/popups and are awkward to control with a TV remote. By capturing the `.m3u8` stream and playing it in our own player, we bypass the ads entirely and get full remote control.

## 2. Chosen Approach — "A: Sniff → Native Player, WebView Fallback"

Selected during brainstorming over two alternatives (B: pure WebView ad-block; C: full native rewrite).

- **Hybrid model.** WebView = *browse* layer. ExoPlayer = *watch* layer. Settings = *config* layer.
- **Stream sniffing.** While the user browses, the app intercepts WebView network requests (`shouldInterceptRequest`). When it sees a media manifest (`.m3u8` / `.mpd` / direct `.mp4`), it captures the URL **and the headers needed to replay it** (Referer, User-Agent, Origin, Cookie).
- **Native playback.** The captured stream is handed to a separate `PlayerActivity` (ExoPlayer) that plays it fullscreen with the captured headers re-applied.
- **Fallback always.** If nothing is sniffed (DRM/`blob:`/MSE-only), the existing ad-blocked WebView player keeps working. The native player is an *enhancement layer*, never a hard dependency. **The app must always remain usable.**

### Honest constraints (accepted)

- Sniffing does not work for 100% of streams (DRM/Widevine, `blob:`/MSE). These fall back to WebView.
- Some `.m3u8` require exact headers (Referer/Cookie/UA) or return `403`. We replay captured headers to mitigate.
- Sniffing rules are site-dependent and may need occasional maintenance. The fallback is what keeps the app robust against this.

## 3. Architecture

### 3.1 Activity flow

```
SplashActivity
  └─> HomeActivity   ( ▶ Mulai Streaming | ⚙️ Settings | 📋 Lanjut Nonton )
        └─> MainActivity        (WebView; endpoint from SettingsManager)
              │  browse → open movie page
              │  iframe requests .m3u8 ──► StreamSniffer.inspect() captures stream
              │  stream detected       ──► "▶ Putar tanpa iklan" pill appears
              └─> PlayerActivity       (ExoPlayer HLS, fullscreen, D-pad controls)
                    Back ──► returns to WebView
        SettingsActivity changes endpoint ──► next WebView load uses new URL
```

### 3.2 Components

| Unit | Responsibility | Depends on |
|---|---|---|
| `data/SettingsManager` | Read/write endpoint URL + flags (SharedPreferences); default + validation | Android `SharedPreferences` |
| `stream/MediaStream` | Immutable data: `url`, `type`, `headers` | — |
| `stream/StreamSniffer` | **Pure logic:** classify a request URL → playable `MediaStream?`; reject ad URLs; keep latest detected | `MediaStream`, ad-domain list |
| `PlayerActivity` | Media3 player: play `MediaStream` with header replay; D-pad controls; error → finish (fallback) | Media3 ExoPlayer/HLS/UI |
| `SettingsActivity` | TV-friendly form to edit endpoint + options | `SettingsManager` |
| `MainActivity` (mod) | Wire sniffer into WebView; show "Putar tanpa iklan"; load endpoint from settings | `StreamSniffer`, `SettingsManager`, `PlayerActivity` |
| `HomeActivity` (mod) | Simplified launcher (start / settings / continue) | `SettingsManager`, `HistoryManager` |
| `ui/OverlayMenu` (mod) | Add Settings + "Putar tanpa iklan" entries | existing |

### 3.3 Key interfaces

```kotlin
// stream/MediaStream.kt
enum class StreamType { HLS, DASH, PROGRESSIVE }

data class MediaStream(
    val url: String,
    val type: StreamType,
    val headers: Map<String, String>   // Referer, User-Agent, Origin, Cookie
)
```

```kotlin
// stream/StreamSniffer.kt
class StreamSniffer(private val adDomains: List<String>) {

    @Volatile
    var latest: MediaStream? = null
        private set

    /** Returns a MediaStream if [url] is a playable manifest, else null. Also updates [latest]. */
    fun inspect(
        url: String,
        userAgent: String,
        pageUrl: String,
        cookie: String?
    ): MediaStream?

    fun clear()   // called on new page navigation
}
```

Classification rules (in `inspect`):
1. Lowercase the URL. If it contains any `adDomains` entry → return `null`.
2. Type by extension/substring (checked before `?query`):
   - contains `.m3u8` → `HLS`
   - contains `.mpd` → `DASH`
   - ends with `.mp4` or `.mkv` → `PROGRESSIVE`
   - otherwise → `null`
3. Exclude HLS media segments: if it contains `.ts?` or ends with `.ts` → `null` (we want manifests, not chunks).
4. Build headers: `Referer = pageUrl`, `Origin =` scheme+host of `pageUrl`, `User-Agent = userAgent`, and `Cookie = cookie` if non-null/non-blank.
5. Construct `MediaStream`, set `latest`, return it.

```kotlin
// data/SettingsManager.kt
class SettingsManager(context: Context) {
    var endpointUrl: String        // default DEFAULT_ENDPOINT, persisted
    var autoSniff: Boolean         // default true
    var userAgent: String          // default DEFAULT_UA

    /** Validates http/https; returns false and does not save if invalid. */
    fun setEndpoint(url: String): Boolean

    fun resetToDefaults()

    companion object {
        const val DEFAULT_ENDPOINT = "https://z1.idlixku.com"
        const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
```

### 3.4 PlayerActivity (Media3)

- Receives via `Intent` extras: `url: String`, `type: String` (StreamType name), `headers: Bundle` (string→string), `title: String?`.
- Builds an HTTP data source with header replay:
  ```kotlin
  val httpFactory = DefaultHttpDataSource.Factory()
      .setUserAgent(headers["User-Agent"])
      .setDefaultRequestProperties(headers)          // Referer, Origin, Cookie
      .setAllowCrossProtocolRedirects(true)
  ```
- Picks a `MediaSource` by `type`: `HlsMediaSource` (HLS), `DashMediaSource` (DASH), `ProgressiveMediaSource` (PROGRESSIVE) — all using `httpFactory`.
- `PlayerView` fullscreen, `keepScreenOn`, landscape. Default Media3 controller is D-pad friendly; OK = play/pause, ◄/► = seek, Back = exit.
- `player.addListener` → on `onPlayerError` show a Toast ("Gagal play native, kembali ke WebView") and `finish()` (returns to the WebView, which is still on the movie page = automatic fallback).
- Lifecycle: create/prepare in `onStart`, `play` in `onResume`, `pause` in `onPause`, `release` in `onStop`/`onDestroy` (no leaks).

### 3.5 SettingsActivity

- Programmatic UI (matches the codebase — no XML layouts):
  - `EditText` endpoint URL (pre-filled with current).
  - `Switch` auto-sniff.
  - `EditText` custom User-Agent (optional/advanced).
  - Buttons: **Simpan** (validates + saves + toast), **Reset Default**.
- All controls `isFocusable = true` for D-pad. Invalid URL → toast, not saved.

### 3.6 MainActivity changes

- `HOME_URL` → `settingsManager.endpointUrl`.
- In `shouldInterceptRequest`: after the existing ad check, if `autoSniff`, call
  `sniffer.inspect(url, settingsManager.userAgent, currentUrl, CookieManager.getInstance().getCookie(url))`.
  If it returns non-null, `runOnUiThread { showPlayButton() }`.
- New **bottom-center pill** `▶ Putar tanpa iklan` (distinct from the draggable menu button). Visible only when `sniffer.latest != null`. Tap → `startActivity(PlayerActivity intent built from sniffer.latest)`.
- Call `sniffer.clear()` + hide pill in `onPageStarted` (new navigation = stale stream).
- `OverlayMenu`: add `⚙️ Settings` (opens `SettingsActivity`) and `▶ Putar tanpa iklan` (if `latest != null`).
- Remove multi-site logic: delete `switchSite`, `CHANNEL_UP/DOWN` handlers, swipe-left-switch-site, and the per-site search-URL branching (search now always targets `settingsManager.endpointUrl + "/?s=" + query`).

### 3.7 HomeActivity changes

- Remove the 3-site grid. Add:
  - **▶ Mulai Streaming** card → `MainActivity` with `settingsManager.endpointUrl`.
  - **⚙️ Settings** card → `SettingsActivity`.
  - **📋 Lanjut Nonton** (existing recent history) unchanged.

## 4. Error Handling / Fallback Matrix

| Situation | Behavior |
|---|---|
| No stream sniffed | Pill hidden; WebView plays normally (fallback). Optional hint toast. |
| `autoSniff` off | Sniffer not called; pure WebView. |
| ExoPlayer error (403/codec/DRM) | Toast + `finish()` → back to WebView on same page. |
| Invalid endpoint in Settings | Rejected with toast; keeps previous/default. |
| Multiple `.m3u8` seen | Keep the most recent non-ad manifest carrying the page Referer. |

## 5. Testing Strategy

- **Unit tests (primary, JVM):**
  - `StreamSnifferTest`: `.m3u8` → HLS; `.mpd` → DASH; `.mp4` → PROGRESSIVE; ad-domain URL → null; `.ts` segment → null; query strings handled (`x.m3u8?token=…` → HLS); headers populated (Referer/Origin/UA/Cookie); `clear()` resets `latest`.
  - `SettingsManagerTest`: default endpoint; set valid URL persists; invalid URL (`ftp://`, empty, no scheme) rejected; `resetToDefaults`.
- **Manual (device/emulator, Android TV + phone):**
  - Browse endpoint → open a movie → pill appears → native playback is ad-free → D-pad play/pause/seek → Back returns to WebView.
  - DRM/blob title → no pill → WebView fallback plays.
  - Change endpoint in Settings → new site loads.

`SettingsManager` is tested with a Robolectric-style or a thin interface around SharedPreferences; if Robolectric is not desired, abstract persistence behind a small `KeyValueStore` interface and unit-test the logic with an in-memory fake. **Decision: use an in-memory fake `KeyValueStore` to keep tests pure-JVM (no Android runtime needed).**

```kotlin
interface KeyValueStore {
    fun getString(key: String, def: String): String
    fun getBoolean(key: String, def: Boolean): Boolean
    fun putString(key: String, value: String)
    fun putBoolean(key: String, value: Boolean)
}
```
`SettingsManager` depends on `KeyValueStore`. Production wraps `SharedPreferences`; tests use an in-memory map.

## 6. Dependencies & Versioning

Add to `app/build.gradle.kts`:
- `androidx.media3:media3-exoplayer`
- `androidx.media3:media3-exoplayer-hls`
- `androidx.media3:media3-exoplayer-dash`
- `androidx.media3:media3-ui`
- `testImplementation("junit:junit:4.13.2")`

Media3 supports `minSdk 21` (current project min). Pin to the latest stable Media3 1.x at implementation time.

Version bump: **`versionName 2.1.0 → 3.0.0`**, **`versionCode 11 → 12`**.

## 7. Out of Scope (v3.0)

Not built now — captured as roadmap for later iterations:

1. **Remote config endpoint** (push new site URL without an APK update) — highest future value, since site domains change often.
2. Quality/resolution selector + subtitle (WebVTT) track selection.
3. Resume playback (persist position per title in `HistoryManager`).
4. Chromecast / Google Cast.
5. Picture-in-Picture (phone/tablet) + playback speed + multi-audio.
6. Offline download (Media3 `DownloadManager`).
7. UI migration to Jetpack Compose for TV (`androidx.tv:tv-material`).
8. TMDB metadata (posters/synopsis) + unified search + recommendations.

## 8. File Inventory

**New:**
- `app/src/main/kotlin/com/streamtv/app/stream/MediaStream.kt`
- `app/src/main/kotlin/com/streamtv/app/stream/StreamSniffer.kt`
- `app/src/main/kotlin/com/streamtv/app/data/KeyValueStore.kt`
- `app/src/main/kotlin/com/streamtv/app/data/SettingsManager.kt`
- `app/src/main/kotlin/com/streamtv/app/PlayerActivity.kt`
- `app/src/main/kotlin/com/streamtv/app/SettingsActivity.kt`
- `app/src/test/kotlin/com/streamtv/app/stream/StreamSnifferTest.kt`
- `app/src/test/kotlin/com/streamtv/app/data/SettingsManagerTest.kt`

**Modified:**
- `app/src/main/kotlin/com/streamtv/app/MainActivity.kt`
- `app/src/main/kotlin/com/streamtv/app/HomeActivity.kt`
- `app/src/main/kotlin/com/streamtv/app/ui/OverlayMenu.kt`
- `app/src/main/kotlin/com/streamtv/app/data/SiteManager.kt` (shrunk to a default-URL constant, or removed once unused)
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `README.md` (changelog v3.0.0)
