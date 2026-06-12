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
