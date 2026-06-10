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
    fun setUp() {
        sniffer = StreamSniffer(ads)
    }

    @Test
    fun detectsHls() {
        val stream = sniffer.inspect("https://cdn.example/master.m3u8", ua, page, null)

        assertEquals(StreamType.HLS, stream?.type)
        assertEquals("https://cdn.example/master.m3u8", stream?.url)
    }

    @Test
    fun detectsHlsWithQuery() {
        val stream = sniffer.inspect("https://cdn.example/index.m3u8?token=abc123", ua, page, null)

        assertEquals(StreamType.HLS, stream?.type)
    }

    @Test
    fun detectsDash() {
        val stream = sniffer.inspect("https://cdn.example/manifest.mpd", ua, page, null)

        assertEquals(StreamType.DASH, stream?.type)
    }

    @Test
    fun detectsProgressiveMp4() {
        val stream = sniffer.inspect("https://cdn.example/file.mp4", ua, page, null)

        assertEquals(StreamType.PROGRESSIVE, stream?.type)
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
        val stream = sniffer.inspect("https://cdn.example/master.m3u8", ua, page, "sid=xyz")!!

        assertEquals(ua, stream.headers["User-Agent"])
        assertEquals(page, stream.headers["Referer"])
        assertEquals("https://site.example", stream.headers["Origin"])
        assertEquals("sid=xyz", stream.headers["Cookie"])
    }

    @Test
    fun originKeepsPagePort() {
        val stream = sniffer.inspect(
            "https://cdn.example/master.m3u8",
            ua,
            "https://site.example:8443/movie/123",
            null
        )!!

        assertEquals("https://site.example:8443", stream.headers["Origin"])
    }

    @Test
    fun omitsCookieWhenBlank() {
        val stream = sniffer.inspect("https://cdn.example/master.m3u8", ua, page, "")!!

        assertTrue(!stream.headers.containsKey("Cookie"))
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
