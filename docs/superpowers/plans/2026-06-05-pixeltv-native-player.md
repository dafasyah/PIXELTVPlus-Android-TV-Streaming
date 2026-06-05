# PIXELTV v3.0 Native Player Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a native ad-free Media3 player fed by WebView stream-sniffing, plus a single configurable endpoint with a Settings screen, while keeping the WebView as an automatic fallback.

**Architecture:** Hybrid. WebView browses one configurable site; a pure-logic `StreamSniffer` captures `.m3u8`/`.mpd`/`.mp4` manifests + replay headers from WebView network requests; a separate `PlayerActivity` (ExoPlayer) plays them. If nothing is sniffed, the existing ad-blocked WebView keeps playing.

**Tech Stack:** Kotlin, Android (minSdk 21 / targetSdk 34), AndroidX, **Media3 1.x (ExoPlayer/HLS/DASH/UI)**, JUnit4. Programmatic UI (no XML layouts), SharedPreferences for persistence.

**Spec:** `docs/superpowers/specs/2026-06-05-pixeltv-native-player-design.md`

---

## File Structure

**New files & responsibilities**
- `app/src/main/kotlin/com/streamtv/app/stream/MediaStream.kt` — immutable stream descriptor (`url`, `type`, `headers`) + `StreamType` enum.
- `app/src/main/kotlin/com/streamtv/app/stream/StreamSniffer.kt` — pure classifier: request URL → playable `MediaStream?`; holds `latest`; rejects ad URLs & segments. JVM-testable.
- `app/src/main/kotlin/com/streamtv/app/data/KeyValueStore.kt` — tiny persistence interface (decouples `SettingsManager` from Android for testing) + `PrefsKeyValueStore` production impl.
- `app/src/main/kotlin/com/streamtv/app/data/SettingsManager.kt` — endpoint URL + flags, defaults, validation. Depends only on `KeyValueStore`.
- `app/src/main/kotlin/com/streamtv/app/PlayerActivity.kt` — Media3 player with header replay + D-pad + error→fallback.
- `app/src/main/kotlin/com/streamtv/app/SettingsActivity.kt` — programmatic TV form to edit endpoint/options.
- `app/src/test/kotlin/com/streamtv/app/stream/StreamSnifferTest.kt` — classifier unit tests.
- `app/src/test/kotlin/com/streamtv/app/data/SettingsManagerTest.kt` — settings logic unit tests (in-memory fake store).

**Modified files**
- `app/build.gradle.kts` — Media3 + JUnit deps, version bump 3.0.0 / code 12.
- `app/src/main/AndroidManifest.xml` — register `PlayerActivity` + `SettingsActivity`.
- `app/src/main/kotlin/com/streamtv/app/MainActivity.kt` — wire sniffer, pill button, endpoint from settings, drop multi-site.
- `app/src/main/kotlin/com/streamtv/app/HomeActivity.kt` — simplified launcher.
- `app/src/main/kotlin/com/streamtv/app/ui/OverlayMenu.kt` — add Settings + "Putar tanpa iklan" entries.
- `app/src/main/kotlin/com/streamtv/app/data/SiteManager.kt` — shrink to default-URL constant.
- `README.md` — v3.0.0 changelog.

**Commands (Windows PowerShell)**
- Unit tests: `.\gradlew.bat testDebugUnitTest`
- Single test class: `.\gradlew.bat testDebugUnitTest --tests "com.streamtv.app.stream.StreamSnifferTest"`
- Debug build: `.\gradlew.bat assembleDebug`

---

## Task 0: Dependencies, version bump, test wiring

**Files:**
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/kotlin/com/streamtv/app/SmokeTest.kt`

- [ ] **Step 1: Add Media3 + JUnit deps and bump version**

Replace the `defaultConfig` version lines and the `dependencies { }` block in `app/build.gradle.kts`:

```kotlin
    defaultConfig {
        applicationId = "com.streamtv.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 12
        versionName = "3.0.0"
    }
```

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("androidx.webkit:webkit:1.10.0")
    implementation("com.google.android.material:material:1.11.0")

    // Media3 (native player)
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    // Unit testing
    testImplementation("junit:junit:4.13.2")
}
```

> If `1.4.1` fails to resolve, use the latest stable `androidx.media3:media3-*` 1.x shown by `.\gradlew.bat app:dependencies` and keep all four artifacts on the same version.

- [ ] **Step 2: Write a smoke test to verify the test source set compiles & runs**

Create `app/src/test/kotlin/com/streamtv/app/SmokeTest.kt`:

```kotlin
package com.streamtv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun testRunnerWorks() {
        assertEquals(4, 2 + 2)
    }
}
```

- [ ] **Step 3: Run the smoke test**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.streamtv.app.SmokeTest"`
Expected: `BUILD SUCCESSFUL`, 1 test passed. (Confirms `src/test/kotlin` is wired and deps resolve.)

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts app/src/test/kotlin/com/streamtv/app/SmokeTest.kt
git commit -m "chore: add Media3 + JUnit deps, bump to v3.0.0, wire unit tests"
```

---

## Task 1: MediaStream model

**Files:**
- Create: `app/src/main/kotlin/com/streamtv/app/stream/MediaStream.kt`

- [ ] **Step 1: Create the data model**

```kotlin
package com.streamtv.app.stream

/** Kind of media manifest detected on the page. */
enum class StreamType { HLS, DASH, PROGRESSIVE }

/**
 * A playable stream captured from the WebView, with the HTTP headers
 * required to replay it in the native player (Referer/UA/Origin/Cookie).
 */
data class MediaStream(
    val url: String,
    val type: StreamType,
    val headers: Map<String, String>
)
```

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/streamtv/app/stream/MediaStream.kt
git commit -m "feat: add MediaStream model for native playback"
```

---

## Task 2: StreamSniffer (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/streamtv/app/stream/StreamSniffer.kt`
- Test: `app/src/test/kotlin/com/streamtv/app/stream/StreamSnifferTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/com/streamtv/app/stream/StreamSnifferTest.kt`:

```kotlin
package com.streamtv.app.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StreamSnifferTest {

    private val ads = listOf("doubleclick.net", "popads.net", "ads.example")
    private lateinit var sniffer: StreamSniffer

    private val ua = "TestAgent/1.0"
    private val page = "https://site.example/movie/123"

    @Before
    fun setUp() { sniffer = StreamSniffer(ads) }

    @Test
    fun detectsHls() {
        val s = sniffer.inspect("https://cdn.example/master.m3u8", ua, page, null)
        assertEquals(StreamType.HLS, s?.type)
        assertEquals("https://cdn.example/master.m3u8", s?.url)
    }

    @Test
    fun detectsHlsWithQuery() {
        val s = sniffer.inspect("https://cdn.example/index.m3u8?token=abc123", ua, page, null)
        assertEquals(StreamType.HLS, s?.type)
    }

    @Test
    fun detectsDash() {
        val s = sniffer.inspect("https://cdn.example/manifest.mpd", ua, page, null)
        assertEquals(StreamType.DASH, s?.type)
    }

    @Test
    fun detectsProgressiveMp4() {
        val s = sniffer.inspect("https://cdn.example/file.mp4", ua, page, null)
        assertEquals(StreamType.PROGRESSIVE, s?.type)
    }

    @Test
    fun ignoresAdDomains() {
        assertNull(sniffer.inspect("https://ads.example/promo.m3u8", ua, page, null))
    }

    @Test
    fun ignoresTsSegments() {
        assertNull(sniffer.inspect("https://cdn.example/seg-00012.ts", ua, page, null))
        assertNull(sniffer.inspect("https://cdn.example/seg.ts?x=1", ua, page, null))
    }

    @Test
    fun ignoresNonMedia() {
        assertNull(sniffer.inspect("https://cdn.example/poster.jpg", ua, page, null))
    }

    @Test
    fun buildsReplayHeaders() {
        val s = sniffer.inspect("https://cdn.example/master.m3u8", ua, page, "sid=xyz")!!
        assertEquals(ua, s.headers["User-Agent"])
        assertEquals(page, s.headers["Referer"])
        assertEquals("https://site.example", s.headers["Origin"])
        assertEquals("sid=xyz", s.headers["Cookie"])
    }

    @Test
    fun omitsCookieWhenBlank() {
        val s = sniffer.inspect("https://cdn.example/master.m3u8", ua, page, "")!!
        assertTrue(!s.headers.containsKey("Cookie"))
    }

    @Test
    fun latestTracksMostRecentAndClears() {
        sniffer.inspect("https://cdn.example/a.m3u8", ua, page, null)
        sniffer.inspect("https://cdn.example/b.m3u8", ua, page, null)
        assertEquals("https://cdn.example/b.m3u8", sniffer.latest?.url)
        sniffer.clear()
        assertNull(sniffer.latest)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.streamtv.app.stream.StreamSnifferTest"`
Expected: FAIL — `StreamSniffer` unresolved / does not compile.

- [ ] **Step 3: Implement StreamSniffer**

Create `app/src/main/kotlin/com/streamtv/app/stream/StreamSniffer.kt`:

```kotlin
package com.streamtv.app.stream

import java.net.URI

/**
 * Pure classifier for WebView network requests. Decides whether a URL is a
 * playable media manifest and, if so, produces a [MediaStream] with replay
 * headers. Holds the most recently detected stream in [latest].
 *
 * No Android dependencies → unit-testable on the JVM.
 */
class StreamSniffer(private val adDomains: List<String>) {

    @Volatile
    var latest: MediaStream? = null
        private set

    fun inspect(
        url: String,
        userAgent: String,
        pageUrl: String,
        cookie: String?
    ): MediaStream? {
        val lower = url.lowercase()

        // Reject ads and HLS segments (we want manifests, not chunks).
        if (adDomains.any { lower.contains(it) }) return null
        if (lower.endsWith(".ts") || lower.contains(".ts?")) return null

        val type = when {
            lower.contains(".m3u8") -> StreamType.HLS
            lower.contains(".mpd") -> StreamType.DASH
            lower.endsWith(".mp4") || lower.contains(".mp4?") ||
                lower.endsWith(".mkv") || lower.contains(".mkv?") -> StreamType.PROGRESSIVE
            else -> return null
        }

        val headers = buildMap {
            put("User-Agent", userAgent)
            put("Referer", pageUrl)
            originOf(pageUrl)?.let { put("Origin", it) }
            if (!cookie.isNullOrBlank()) put("Cookie", cookie)
        }

        return MediaStream(url, type, headers).also { latest = it }
    }

    fun clear() { latest = null }

    private fun originOf(pageUrl: String): String? = try {
        val u = URI(pageUrl)
        if (u.scheme != null && u.host != null) {
            buildString {
                append(u.scheme).append("://").append(u.host)
                if (u.port != -1) append(":").append(u.port)
            }
        } else null
    } catch (e: Exception) {
        null
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.streamtv.app.stream.StreamSnifferTest"`
Expected: PASS — all 11 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/streamtv/app/stream/StreamSniffer.kt app/src/test/kotlin/com/streamtv/app/stream/StreamSnifferTest.kt
git commit -m "feat: add StreamSniffer with classification tests"
```

---

## Task 3: KeyValueStore + SettingsManager (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/streamtv/app/data/KeyValueStore.kt`
- Create: `app/src/main/kotlin/com/streamtv/app/data/SettingsManager.kt`
- Test: `app/src/test/kotlin/com/streamtv/app/data/SettingsManagerTest.kt`

- [ ] **Step 1: Create the KeyValueStore interface + prefs impl**

Create `app/src/main/kotlin/com/streamtv/app/data/KeyValueStore.kt`:

```kotlin
package com.streamtv.app.data

import android.content.Context

/** Minimal persistence seam so SettingsManager can be unit-tested without Android. */
interface KeyValueStore {
    fun getString(key: String, def: String): String
    fun getBoolean(key: String, def: Boolean): Boolean
    fun putString(key: String, value: String)
    fun putBoolean(key: String, value: Boolean)
}

/** Production implementation backed by SharedPreferences. */
class PrefsKeyValueStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("pixeltv_prefs", Context.MODE_PRIVATE)
    override fun getString(key: String, def: String) = prefs.getString(key, def) ?: def
    override fun getBoolean(key: String, def: Boolean) = prefs.getBoolean(key, def)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
}
```

- [ ] **Step 2: Write the failing tests**

Create `app/src/test/kotlin/com/streamtv/app/data/SettingsManagerTest.kt`:

```kotlin
package com.streamtv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsManagerTest {

    /** In-memory KeyValueStore so the test stays pure-JVM. */
    private class FakeStore : KeyValueStore {
        val strings = HashMap<String, String>()
        val bools = HashMap<String, Boolean>()
        override fun getString(key: String, def: String) = strings[key] ?: def
        override fun getBoolean(key: String, def: Boolean) = bools[key] ?: def
        override fun putString(key: String, value: String) { strings[key] = value }
        override fun putBoolean(key: String, value: Boolean) { bools[key] = value }
    }

    private lateinit var settings: SettingsManager

    @Before
    fun setUp() { settings = SettingsManager(FakeStore()) }

    @Test
    fun defaultsAreApplied() {
        assertEquals(SettingsManager.DEFAULT_ENDPOINT, settings.endpointUrl)
        assertTrue(settings.autoSniff)
        assertEquals(SettingsManager.DEFAULT_UA, settings.userAgent)
    }

    @Test
    fun setValidHttpsEndpointPersists() {
        assertTrue(settings.setEndpoint("https://newsite.example"))
        assertEquals("https://newsite.example", settings.endpointUrl)
    }

    @Test
    fun setValidHttpEndpointPersists() {
        assertTrue(settings.setEndpoint("http://newsite.example"))
        assertEquals("http://newsite.example", settings.endpointUrl)
    }

    @Test
    fun trimsEndpointWhitespace() {
        assertTrue(settings.setEndpoint("  https://trim.example  "))
        assertEquals("https://trim.example", settings.endpointUrl)
    }

    @Test
    fun rejectsInvalidEndpoint() {
        assertFalse(settings.setEndpoint("ftp://bad.example"))
        assertFalse(settings.setEndpoint("notaurl"))
        assertFalse(settings.setEndpoint(""))
        assertEquals(SettingsManager.DEFAULT_ENDPOINT, settings.endpointUrl) // unchanged
    }

    @Test
    fun autoSniffTogglePersists() {
        settings.autoSniff = false
        assertFalse(settings.autoSniff)
    }

    @Test
    fun resetRestoresDefaults() {
        settings.setEndpoint("https://other.example")
        settings.autoSniff = false
        settings.resetToDefaults()
        assertEquals(SettingsManager.DEFAULT_ENDPOINT, settings.endpointUrl)
        assertTrue(settings.autoSniff)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.streamtv.app.data.SettingsManagerTest"`
Expected: FAIL — `SettingsManager` unresolved.

- [ ] **Step 4: Implement SettingsManager**

Create `app/src/main/kotlin/com/streamtv/app/data/SettingsManager.kt`:

```kotlin
package com.streamtv.app.data

import android.content.Context

/**
 * Single source of truth for the configurable endpoint and player options.
 * Logic depends only on [KeyValueStore] for testability.
 */
class SettingsManager(private val store: KeyValueStore) {

    var endpointUrl: String
        get() = store.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT)
        set(value) = store.putString(KEY_ENDPOINT, value)

    var autoSniff: Boolean
        get() = store.getBoolean(KEY_AUTOSNIFF, true)
        set(value) = store.putBoolean(KEY_AUTOSNIFF, value)

    var userAgent: String
        get() = store.getString(KEY_UA, DEFAULT_UA)
        set(value) = store.putString(KEY_UA, value)

    /** Validates and stores the endpoint. Returns false (and stores nothing) if invalid. */
    fun setEndpoint(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return false
        if (trimmed.length <= "https://".length) return false
        endpointUrl = trimmed
        return true
    }

    fun resetToDefaults() {
        endpointUrl = DEFAULT_ENDPOINT
        autoSniff = true
        userAgent = DEFAULT_UA
    }

    companion object {
        private const val KEY_ENDPOINT = "endpoint_url"
        private const val KEY_AUTOSNIFF = "auto_sniff"
        private const val KEY_UA = "user_agent"

        const val DEFAULT_ENDPOINT = "https://z1.idlixku.com"
        const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /** Convenience factory for activities. */
        fun from(context: Context): SettingsManager =
            SettingsManager(PrefsKeyValueStore(context))
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.streamtv.app.data.SettingsManagerTest"`
Expected: PASS — all 7 tests green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/streamtv/app/data/KeyValueStore.kt app/src/main/kotlin/com/streamtv/app/data/SettingsManager.kt app/src/test/kotlin/com/streamtv/app/data/SettingsManagerTest.kt
git commit -m "feat: add SettingsManager with validation tests"
```

---

## Task 4: PlayerActivity (Media3) + manifest

**Files:**
- Create: `app/src/main/kotlin/com/streamtv/app/PlayerActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create PlayerActivity**

Create `app/src/main/kotlin/com/streamtv/app/PlayerActivity.kt`:

```kotlin
package com.streamtv.app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.streamtv.app.stream.StreamType

/**
 * Native fullscreen player. Receives a sniffed stream + replay headers and
 * plays it ad-free. On any error it finishes, returning the user to the
 * WebView (automatic fallback).
 */
@UnstableApi
class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    private lateinit var streamUrl: String
    private lateinit var streamType: StreamType
    private lateinit var headers: Map<String, String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        streamUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        streamType = runCatching {
            StreamType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: StreamType.HLS.name)
        }.getOrDefault(StreamType.HLS)
        headers = readHeaders(intent.getBundleExtra(EXTRA_HEADERS))

        if (streamUrl.isBlank()) {
            Toast.makeText(this, "Stream tidak valid", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        playerView = PlayerView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        }
        setContentView(playerView)
    }

    private fun readHeaders(bundle: Bundle?): Map<String, String> {
        if (bundle == null) return emptyMap()
        return bundle.keySet().associateWith { bundle.getString(it).orEmpty() }
    }

    private fun initPlayer() {
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        playerView.player = exo

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(headers["User-Agent"])
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)

        val item = MediaItem.fromUri(streamUrl)
        val source: MediaSource = when (streamType) {
            StreamType.HLS -> HlsMediaSource.Factory(httpFactory).createMediaSource(item)
            StreamType.DASH -> DashMediaSource.Factory(httpFactory).createMediaSource(item)
            StreamType.PROGRESSIVE -> ProgressiveMediaSource.Factory(httpFactory).createMediaSource(item)
        }

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    "Gagal play native, kembali ke WebView",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        })

        exo.setMediaSource(source)
        exo.playWhenReady = true
        exo.prepare()
    }

    override fun onStart() {
        super.onStart()
        if (player == null) initPlayer()
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_URL = "stream_url"
        const val EXTRA_TYPE = "stream_type"
        const val EXTRA_HEADERS = "stream_headers"
    }
}
```

- [ ] **Step 2: Register PlayerActivity in the manifest**

In `app/src/main/AndroidManifest.xml`, add inside `<application>` after the `MainActivity` block:

```xml
        <!-- Native Media3 Player -->
        <activity
            android:name=".PlayerActivity"
            android:exported="false"
            android:screenOrientation="landscape"
            android:configChanges="orientation|screenSize|keyboardHidden|smallestScreenSize" />
```

- [ ] **Step 3: Verify it compiles**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` (the `@UnstableApi` annotation silences Media3 unstable-API warnings).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/streamtv/app/PlayerActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add native Media3 PlayerActivity with header replay"
```

---

## Task 5: SettingsActivity + manifest

**Files:**
- Create: `app/src/main/kotlin/com/streamtv/app/SettingsActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create SettingsActivity**

Create `app/src/main/kotlin/com/streamtv/app/SettingsActivity.kt`:

```kotlin
package com.streamtv.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.streamtv.app.data.SettingsManager

/** TV-friendly settings: edit the single endpoint URL and player options. */
class SettingsActivity : Activity() {

    private lateinit var settings: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        settings = SettingsManager.from(this)

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0D0D1A"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(40), dp(32), dp(32))
        }

        root.addView(TextView(this).apply {
            text = "⚙️ Settings"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setPadding(0, 0, 0, dp(24))
        })

        // Endpoint URL
        root.addView(label("URL Endpoint Streaming", dp(8)))
        val endpointInput = field(settings.endpointUrl, dp(16))
        root.addView(endpointInput)

        // Auto-sniff toggle
        val sniffRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }
        sniffRow.addView(TextView(this).apply {
            text = "Auto-deteksi stream (player native)"
            textSize = 14f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val sniffSwitch = Switch(this).apply {
            isChecked = settings.autoSniff
            isFocusable = true
        }
        sniffRow.addView(sniffSwitch)
        root.addView(sniffRow)

        // User-Agent (advanced)
        root.addView(label("User-Agent (lanjutan)", dp(8)))
        val uaInput = field(settings.userAgent, dp(16))
        root.addView(uaInput)

        // Buttons
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        buttonRow.addView(button("Simpan", "#7C4DFF") {
            val ok = settings.setEndpoint(endpointInput.text.toString())
            if (!ok) {
                Toast.makeText(this, "URL harus diawali http:// atau https://", Toast.LENGTH_LONG).show()
                return@button
            }
            settings.autoSniff = sniffSwitch.isChecked
            val ua = uaInput.text.toString().trim()
            if (ua.isNotEmpty()) settings.userAgent = ua
            Toast.makeText(this, "✅ Tersimpan", Toast.LENGTH_SHORT).show()
            finish()
        })
        buttonRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), 1)
        })
        buttonRow.addView(button("Reset Default", "#444466") {
            settings.resetToDefaults()
            endpointInput.setText(settings.endpointUrl)
            uaInput.setText(settings.userAgent)
            sniffSwitch.isChecked = settings.autoSniff
            Toast.makeText(this, "Direset ke default", Toast.LENGTH_SHORT).show()
        })
        root.addView(buttonRow)

        scroll.addView(root)
        setContentView(scroll)
        endpointInput.requestFocus()
    }

    private fun label(text: String, bottom: Int) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor("#AAFFFFFF"))
        setPadding(0, 0, 0, bottom)
    }

    private fun field(value: String, bottom: Int) = EditText(this).apply {
        setText(value)
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#66FFFFFF"))
        setBackgroundColor(Color.parseColor("#22FFFFFF"))
        setSingleLine(true)
        isFocusable = true
        isFocusableInTouchMode = true
        val density = resources.displayMetrics.density
        setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
        (layoutParams as? ViewGroup.MarginLayoutParams)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = bottom }
    }

    private fun button(text: String, color: String, onClick: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            isFocusable = true
            isClickable = true
            setPadding((20 * density).toInt(), (14 * density).toInt(), (20 * density).toInt(), (14 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 14f
                setColor(Color.parseColor(color))
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, has ->
                v.scaleX = if (has) 1.05f else 1f
                v.scaleY = if (has) 1.05f else 1f
            }
        }
    }
}
```

- [ ] **Step 2: Register SettingsActivity in the manifest**

In `app/src/main/AndroidManifest.xml`, add inside `<application>` after the `PlayerActivity` block:

```xml
        <!-- Settings -->
        <activity
            android:name=".SettingsActivity"
            android:exported="false"
            android:screenOrientation="landscape"
            android:configChanges="orientation|screenSize|keyboardHidden" />
```

- [ ] **Step 3: Verify it compiles**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/streamtv/app/SettingsActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add SettingsActivity for configurable endpoint"
```

---

## Task 6: Wire sniffer + pill button + endpoint into MainActivity

**Files:**
- Modify: `app/src/main/kotlin/com/streamtv/app/MainActivity.kt`

- [ ] **Step 1: Add imports and fields**

At the top of `MainActivity.kt`, add these imports near the other `com.streamtv.app` imports:

```kotlin
import android.content.Intent
import android.os.Bundle as AndroidBundle
import com.streamtv.app.data.SettingsManager
import com.streamtv.app.stream.MediaStream
import com.streamtv.app.stream.StreamSniffer
```

> Note: `android.os.Bundle` is already imported; do not duplicate it. Only add the imports above that are not already present (`Intent`, `SettingsManager`, `MediaStream`, `StreamSniffer`).

Add these fields in the class body next to the other `private lateinit var` declarations:

```kotlin
    private lateinit var settingsManager: SettingsManager
    private lateinit var sniffer: StreamSniffer
    private var playButton: TextView? = null
```

- [ ] **Step 2: Initialize settings + sniffer and use the configured endpoint**

In `onCreate`, replace the line:

```kotlin
        prefs = getSharedPreferences("pixeltv_prefs", MODE_PRIVATE)
```

with:

```kotlin
        prefs = getSharedPreferences("pixeltv_prefs", MODE_PRIVATE)
        settingsManager = SettingsManager.from(this)
        sniffer = StreamSniffer(AD_BLOCK_LIST)
```

In `onCreate`, replace the final URL-loading lines:

```kotlin
        // Load URL from intent or default
        val url = intent.getStringExtra("url") ?: HOME_URL
        webView.loadUrl(url)
```

with:

```kotlin
        // Load URL from intent or the configured endpoint
        val url = intent.getStringExtra("url") ?: settingsManager.endpointUrl
        webView.loadUrl(url)
```

- [ ] **Step 3: Add the play-button pill and wire it into the layout**

In `onCreate`, immediately after `rootLayout.addView(menuButton)`, add:

```kotlin
        playButton = createPlayButton()
        rootLayout.addView(playButton)
```

Add these three methods to the class:

```kotlin
    private fun createPlayButton(): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            text = "▶  Putar tanpa iklan"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            visibility = View.GONE
            isFocusable = true
            isClickable = true
            elevation = 999f
            setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 28f
                colors = intArrayOf(Color.parseColor("#7C4DFF"), Color.parseColor("#6C3FC7"))
                gradientType = GradientDrawable.LINEAR_GRADIENT
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (28 * density).toInt()
            }
            setOnClickListener { launchNativePlayer() }
        }
    }

    private fun showPlayButton() {
        playButton?.visibility = View.VISIBLE
    }

    private fun launchNativePlayer() {
        val stream: MediaStream = sniffer.latest ?: run {
            Toast.makeText(this, "Stream belum terdeteksi", Toast.LENGTH_SHORT).show()
            return
        }
        val headerBundle = android.os.Bundle().apply {
            stream.headers.forEach { (k, v) -> putString(k, v) }
        }
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URL, stream.url)
            putExtra(PlayerActivity.EXTRA_TYPE, stream.type.name)
            putExtra(PlayerActivity.EXTRA_HEADERS, headerBundle)
        }
        startActivity(intent)
    }
```

- [ ] **Step 4: Sniff inside shouldInterceptRequest and reset on navigation**

In `setupWebView`, replace the existing `shouldInterceptRequest` override with:

```kotlin
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (isAdUrl(url)) return WebResourceResponse("text/plain", "UTF-8", "".byteInputStream())
                if (settingsManager.autoSniff) {
                    val cookie = try { CookieManager.getInstance().getCookie(url) } catch (e: Exception) { null }
                    val stream = sniffer.inspect(url, settingsManager.userAgent, currentUrl, cookie)
                    if (stream != null) runOnUiThread { showPlayButton() }
                }
                return null
            }
```

In `setupWebView`, in the `onPageStarted` override, add these two lines at the end of the method body:

```kotlin
                sniffer.clear()
                playButton?.visibility = View.GONE
```

- [ ] **Step 5: Use the configured User-Agent**

In `setupWebView`, replace the hardcoded `userAgentString = "..."` line with:

```kotlin
            userAgentString = settingsManager.userAgent
```

- [ ] **Step 6: Remove multi-site logic**

Delete the `switchSite` function and the `private var currentSiteIndex = 0` line.

In `onKeyDown`, delete these two lines:

```kotlin
            KeyEvent.KEYCODE_CHANNEL_UP -> { switchSite(1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { switchSite(-1); return true }
```

In the gesture detector `onFling`, replace the swipe-left branch:

```kotlin
                        } else {
                            // Swipe left — switch site
                            switchSite(1)
                        }
```

with:

```kotlin
                        }
                        // swipe-left no longer switches site (single-endpoint app)
```

In `showSearchDialog`, replace the `searchUrl` block:

```kotlin
                    val searchUrl = when {
                        currentUrl.contains("idlix") -> "https://z1.idlixku.com/?s=$query"
                        currentUrl.contains("lk21") -> "https://tv10.lk21official.cc/?s=$query"
                        currentUrl.contains("rebahin") -> "https://rebahinxxi3.beauty/?s=$query"
                        else -> "https://z1.idlixku.com/?s=$query"
                    }
                    webView.loadUrl(searchUrl)
```

with:

```kotlin
                    val base = settingsManager.endpointUrl.trimEnd('/')
                    webView.loadUrl("$base/?s=$query")
```

Also remove the now-unused `HOME_URL` companion line:

```kotlin
        private val HOME_URL = SiteManager.sites[0].url
```

and remove the unused `import com.streamtv.app.data.SiteManager` if no other reference remains.

- [ ] **Step 7: Build and run unit tests (no regressions)**

Run: `.\gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all existing unit tests still pass.

- [ ] **Step 8: Manual smoke test (device/emulator)**

1. Launch app → browse the endpoint → open a movie.
2. When the player loads, the `▶ Putar tanpa iklan` pill appears at bottom-center.
3. Tap it → `PlayerActivity` opens and plays the stream ad-free.
4. Press Back → returns to the WebView on the same page.
5. Open a DRM/blob title → no pill → WebView plays (fallback). No crash.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/streamtv/app/MainActivity.kt
git commit -m "feat: wire StreamSniffer + native-player pill into MainActivity"
```

---

## Task 7: Simplify HomeActivity + add Settings to OverlayMenu

**Files:**
- Modify: `app/src/main/kotlin/com/streamtv/app/HomeActivity.kt`
- Modify: `app/src/main/kotlin/com/streamtv/app/ui/OverlayMenu.kt`

- [ ] **Step 1: Replace the 3-site grid with a single launcher + Settings**

In `HomeActivity.kt`, add the import:

```kotlin
import com.streamtv.app.data.SettingsManager
```

Replace the block that builds the site section (from the `"🌐  Situs Streaming"` TextView through `root.addView(siteGrid)`) with:

```kotlin
        val settings = SettingsManager.from(this)

        // Primary actions
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, (28 * density).toInt())
        }
        actionRow.addView(createActionCard("▶", "Mulai Streaming", density) {
            openSite(settings.endpointUrl)
        })
        actionRow.addView(createActionCard("⚙️", "Settings", density) {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        root.addView(actionRow)
```

- [ ] **Step 2: Add the `createActionCard` helper**

Add to `HomeActivity`:

```kotlin
    private fun createActionCard(icon: String, title: String, density: Float, onClick: () -> Unit): FrameLayout {
        val card = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, (120 * density).toInt(), 1f).apply {
                marginEnd = (12 * density).toInt()
            }
            background = GradientDrawable().apply {
                cornerRadius = 16f
                colors = intArrayOf(Color.parseColor("#1A1A2E"), Color.parseColor("#16213E"))
                gradientType = GradientDrawable.LINEAR_GRADIENT
                setStroke(1, Color.parseColor("#33FFFFFF"))
            }
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            isFocusable = true
            isClickable = true
            setOnFocusChangeListener { v, hasFocus ->
                val bg = v.background as? GradientDrawable
                if (hasFocus) { bg?.setStroke(2, Color.parseColor("#7C4DFF")); v.scaleX = 1.05f; v.scaleY = 1.05f }
                else { bg?.setStroke(1, Color.parseColor("#33FFFFFF")); v.scaleX = 1f; v.scaleY = 1f }
            }
            setOnClickListener { onClick() }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        content.addView(TextView(this).apply {
            text = icon; textSize = 32f; gravity = Gravity.CENTER
            setPadding(0, 0, 0, (8 * density).toInt())
        })
        content.addView(TextView(this).apply {
            text = title; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        card.addView(content)
        return card
    }
```

Add the import at the top of `HomeActivity.kt` if not present:

```kotlin
import android.content.Intent
```

> Leave `createSiteCard` in place only if still referenced; otherwise delete it. After this change it is unused — delete `createSiteCard` and the `import com.streamtv.app.data.SiteManager` line.

Update the version string near the bottom of `HomeActivity` (`"v2.0 • Build by Buildbox Studio"`) to:

```kotlin
            text = "v3.0.0 • Build by Buildbox Studio"
```

- [ ] **Step 3: Add a Settings entry to the overlay menu**

In `ui/OverlayMenu.kt`, add this import:

```kotlin
import com.streamtv.app.SettingsActivity
```

Bump the version constant:

```kotlin
        const val APP_VERSION = "3.0.0"
```

In the `showMainMenu()` method, find where menu items are added (the `createMenuItem(...)` calls) and add a Settings item alongside the existing ones:

```kotlin
        menuContainer.addView(createMenuItem("⚙️", "Settings") {
            context.startActivity(
                Intent(context, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            dialog?.dismiss()
        })
```

> `Intent` and `dialog` are already used in this file (the menu already opens the credit URL and manages a dialog). Match the existing variable name used for the dialog reference in `showMainMenu()`; if the method holds the dialog in a local `val dialog`, dismiss that instead.

- [ ] **Step 4: Build**

Run: `.\gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual check**

1. Launch → Home shows `▶ Mulai Streaming` + `⚙️ Settings` (no 3-site grid).
2. `Settings` opens the form; change URL → Save → `Mulai Streaming` loads the new site.
3. In-player overlay menu shows `⚙️ Settings`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/streamtv/app/HomeActivity.kt app/src/main/kotlin/com/streamtv/app/ui/OverlayMenu.kt
git commit -m "feat: single-endpoint Home + Settings entry in overlay menu"
```

---

## Task 8: Shrink SiteManager, update README, final verify

**Files:**
- Modify: `app/src/main/kotlin/com/streamtv/app/data/SiteManager.kt`
- Modify: `README.md`

- [ ] **Step 1: Reduce SiteManager to a default-URL constant**

Replace the entire contents of `app/src/main/kotlin/com/streamtv/app/data/SiteManager.kt` with:

```kotlin
package com.streamtv.app.data

/**
 * Legacy default endpoint. The active endpoint is now managed by
 * [SettingsManager]; this only provides the first-run default value.
 */
object SiteManager {
    const val DEFAULT_URL = SettingsManager.DEFAULT_ENDPOINT
}
```

> If any file still imports `SiteManager.sites`, that reference must already have been removed in Tasks 6–7. A compile error here means a leftover reference — fix it by switching to `SettingsManager`.

- [ ] **Step 2: Add a v3.0.0 changelog entry to README**

In `README.md`, under `## Changelog`, add at the top:

```markdown
### v3.0.0
- NEW: **Native player (Media3/ExoPlayer)** — sniffs the `.m3u8`/`.mpd`/`.mp4` stream from the page and plays it ad-free in a built-in player with full D-pad control.
- NEW: **"Putar tanpa iklan" button** appears when a stream is detected.
- NEW: **Settings screen** — configure a single streaming endpoint URL, auto-sniff toggle, and User-Agent.
- CHANGED: **Single configurable endpoint** replaces the 3 hardcoded sites; simplified Home screen.
- KEPT: WebView remains as an automatic fallback when a stream can't be sniffed (DRM/blob).
```

Also update the `**Version:**` line near the top of the README to `3.0.0`.

- [ ] **Step 3: Full build + all unit tests**

Run: `.\gradlew.bat clean assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; `StreamSnifferTest`, `SettingsManagerTest`, `SmokeTest` all pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/streamtv/app/data/SiteManager.kt README.md
git commit -m "chore: shrink SiteManager to default constant, document v3.0.0"
```

---

## Self-Review Checklist (done while writing)

- **Spec coverage:** single endpoint (Task 3/6/7) ✓; Settings URL (Task 5) ✓; sniff → native player (Tasks 2/4/6) ✓; fallback (Task 4 error→finish, Task 6 no-pill path) ✓; easier UX (Task 7) ✓; version bump (Task 0/8) ✓.
- **Type consistency:** `MediaStream(url,type,headers)`, `StreamType{HLS,DASH,PROGRESSIVE}`, `StreamSniffer.inspect(url,userAgent,pageUrl,cookie)` + `latest` + `clear()`, `SettingsManager.{endpointUrl,autoSniff,userAgent,setEndpoint,resetToDefaults,from}`, `PlayerActivity.{EXTRA_URL,EXTRA_TYPE,EXTRA_HEADERS}` — used identically across tasks.
- **No placeholders:** every code step contains real, compilable code; Media3 version has a documented fallback path.
```
