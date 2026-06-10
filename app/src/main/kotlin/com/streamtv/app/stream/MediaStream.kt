package com.streamtv.app.stream

/** Jenis manifest atau file media yang terdeteksi dari halaman WebView. */
enum class StreamType { HLS, DASH, PROGRESSIVE }

/**
 * Stream yang bisa diputar ulang di native player, lengkap dengan header HTTP
 * yang dibutuhkan agar request player tidak ditolak server.
 */
data class MediaStream(
    val url: String,
    val type: StreamType,
    val headers: Map<String, String>
)
