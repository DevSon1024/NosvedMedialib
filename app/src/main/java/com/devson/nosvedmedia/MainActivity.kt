package com.devson.nosvedmedia

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.devson.native_codecs.NativeLib
import com.devson.nosvedmedia.media3.FFmpegRenderersFactory
import com.devson.nosvedmedia.media3.SoftwareDecoderPriority

@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var statusText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        statusText = findViewById(R.id.statusText)

        val nativeLib = NativeLib()
        val ffmpegVersion = nativeLib.getFFmpegVersion() ?: "Unknown"
        statusText?.text = "NosvedMedia FFmpeg $ffmpegVersion | Ready"

        initializePlayer()
    }

    private fun initializePlayer() {
        val renderersFactory = FFmpegRenderersFactory(
            context = this,
            softwareDecoderPriority = SoftwareDecoderPriority.FALLBACK
        )

        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .build()

        playerView?.player = exoPlayer
        this.player = exoPlayer

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateStr = when (playbackState) {
                    Player.STATE_IDLE -> "Idle"
                    Player.STATE_BUFFERING -> "Buffering"
                    Player.STATE_READY -> "Ready"
                    Player.STATE_ENDED -> "Ended"
                    else -> "Unknown"
                }
                statusText?.text = "Playback State: $stateStr"
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (player == null) {
            initializePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.let { p ->
            p.release()
            player = null
        }
        playerView?.player = null
    }
}

