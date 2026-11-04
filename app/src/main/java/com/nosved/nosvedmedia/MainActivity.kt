package com.nosved.nosvedmedia

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.nosved.nosvedmedia.media3.DecoderDiagnostics
import com.nosved.nosvedmedia.media3.DecodingError
import com.nosved.nosvedmedia.media3.DecodingPerformanceMetrics
import com.nosved.nosvedmedia.media3.PlaybackDiagnosticsListener
import com.nosved.nosvedmedia.media3.SoftwareDecoderPriority

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var statusText: TextView? = null
    private var diagnosticsText: TextView? = null

    private var currentPriority: SoftwareDecoderPriority = SoftwareDecoderPriority.FALLBACK
    private var activeDecoderName: String = "Unknown"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        statusText = findViewById(R.id.statusText)
        diagnosticsText = findViewById(R.id.diagnosticsText)

        val initialized = NosvedMedia.initialize(this)
        val ffmpegVersion = NosvedMedia.getFFmpegVersion()
        val dav1dVersion = NosvedMedia.getDav1dVersion()

        statusText?.text = if (initialized) {
            "NosvedMedia Ready | FFmpeg: $ffmpegVersion | dav1d: $dav1dVersion"
        } else {
            "NosvedMedia Native Init Failed"
        }

        findViewById<Button>(R.id.btnHwOnly).setOnClickListener {
            switchPriority(SoftwareDecoderPriority.DISABLED)
        }
        findViewById<Button>(R.id.btnFallback).setOnClickListener {
            switchPriority(SoftwareDecoderPriority.FALLBACK)
        }
        findViewById<Button>(R.id.btnPreferSw).setOnClickListener {
            switchPriority(SoftwareDecoderPriority.PREFER)
        }
        findViewById<Button>(R.id.btnSeekBack).setOnClickListener {
            player?.let { p ->
                val target = (p.currentPosition - 10_000L).coerceAtLeast(0L)
                p.seekTo(target)
            }
        }
        findViewById<Button>(R.id.btnSeekForward).setOnClickListener {
            player?.let { p ->
                val target = (p.currentPosition + 10_000L).coerceAtMost(p.duration.coerceAtLeast(0L))
                p.seekTo(target)
            }
        }

        initializePlayer(currentPriority)
    }

    private fun switchPriority(newPriority: SoftwareDecoderPriority) {
        if (currentPriority == newPriority && player != null) return
        currentPriority = newPriority
        val currentPos = player?.currentPosition ?: 0L
        val wasPlaying = player?.isPlaying ?: false
        releasePlayer()
        initializePlayer(newPriority)
        player?.let { p ->
            p.seekTo(currentPos)
            if (wasPlaying) p.play()
        }
    }

    private fun initializePlayer(priority: SoftwareDecoderPriority) {
        val diagnosticsListener = object : PlaybackDiagnosticsListener {
            override fun onDecoderInitialized(diagnostics: DecoderDiagnostics) {
                activeDecoderName = diagnostics.decoderName
                runOnUiThread {
                    diagnosticsText?.text = "Decoder: ${diagnostics.decoderName} (${diagnostics.width}x${diagnostics.height}, ${diagnostics.bitDepth}-bit, HW=${diagnostics.isHardware})"
                }
            }

            override fun onPerformanceMetricsUpdated(metrics: DecodingPerformanceMetrics) {
                runOnUiThread {
                    diagnosticsText?.text = "Decoder: $activeDecoderName | FPS: ${"%.1f".format(metrics.currentFps)} | Avg: ${"%.1f".format(metrics.averageDecodeTimeMs)}ms | Dec: ${metrics.totalFramesDecoded} | Drop: ${metrics.totalFramesDropped}"
                }
            }

            override fun onDecodingError(error: DecodingError) {
                runOnUiThread {
                    diagnosticsText?.text = "Error in ${error.decoderName}: [${error.errorCode}] ${error.errorMessage}"
                }
            }
        }

        val renderersFactory = NosvedMedia.createRenderersFactory(
            context = this,
            softwareDecoderPriority = priority,
            selectionListener = { rendererName, isSoftware, format ->
                runOnUiThread {
                    val codec = format.sampleMimeType ?: "unknown"
                    statusText?.text = "Mode: $priority | Selected: $rendererName (SW=$isSoftware) for $codec"
                }
            },
            diagnosticsListener = diagnosticsListener
        )

        val exoPlayer = ExoPlayer.Builder(this, renderersFactory).build()
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
                statusText?.text = "Mode: $currentPriority | State: $stateStr"
            }

            override fun onPlayerError(error: PlaybackException) {
                diagnosticsText?.text = "Playback Error: ${error.errorCodeName} - ${error.message}"
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (player == null) {
            initializePlayer(currentPriority)
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
