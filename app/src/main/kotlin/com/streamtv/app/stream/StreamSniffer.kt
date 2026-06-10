package com.streamtv.app.stream

import java.net.URI

/**
 * Classifier murni untuk request WebView. Jika request mengarah ke manifest
 * atau file media yang bisa diputar, sniffer membangun [MediaStream] dengan
 * replay header yang dibutuhkan native player.
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
        val lowerUrl = url.lowercase()

        if (adDomains.any { lowerUrl.contains(it.lowercase()) }) return null
        if (lowerUrl.endsWith(".ts") || lowerUrl.contains(".ts?")) return null

        val type = when {
            lowerUrl.contains(".m3u8") -> StreamType.HLS
            lowerUrl.contains(".mpd") -> StreamType.DASH
            lowerUrl.endsWith(".mp4") || lowerUrl.contains(".mp4?") -> StreamType.PROGRESSIVE
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

    fun clear() {
        latest = null
    }

    private fun originOf(pageUrl: String): String? = try {
        val uri = URI(pageUrl)
        if (uri.scheme != null && uri.host != null) {
            buildString {
                append(uri.scheme).append("://").append(uri.host)
                if (uri.port != -1) append(":").append(uri.port)
            }
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
