package com.devson.nosvedmedia.media3

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class FFmpegVideoDecoderTest {

    @Test
    fun testDecoderCapabilities() {
        assertTrue(VideoDecoderCapabilities.isMimeTypeSupported(MimeTypes.VIDEO_H264))
        assertTrue(VideoDecoderCapabilities.isMimeTypeSupported(MimeTypes.VIDEO_H265))
        assertTrue(VideoDecoderCapabilities.isMimeTypeSupported(MimeTypes.VIDEO_VP8))
        assertTrue(VideoDecoderCapabilities.isMimeTypeSupported(MimeTypes.VIDEO_VP9))
        assertTrue(VideoDecoderCapabilities.isMimeTypeSupported(MimeTypes.VIDEO_MP4V))
        assertTrue(VideoDecoderCapabilities.isMimeTypeSupported(MimeTypes.VIDEO_MPEG2))

        assertTrue(VideoDecoderCapabilities.isMimeTypeSupported(MimeTypes.VIDEO_AV1))
        assertFalse(VideoDecoderCapabilities.isMimeTypeSupported("video/unknown-codec"))
    }

    @Test
    fun testDecoderInstantiationH264() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setWidth(1280)
            .setHeight(720)
            .build()

        val decoder = FFmpegVideoDecoder(format)
        try {
            assertEquals("FFmpegVideoDecoder", decoder.name)
        } finally {
            decoder.release()
        }
    }

    @Test
    fun testDecoderInstantiationHEVC() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setWidth(1920)
            .setHeight(1080)
            .build()

        val decoder = FFmpegVideoDecoder(format)
        try {
            assertEquals("FFmpegVideoDecoder", decoder.name)
        } finally {
            decoder.release()
        }
    }

    @Test
    fun testDecoderInstantiationVP8AndVP9() {
        val vp8Format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_VP8)
            .setWidth(640)
            .setHeight(480)
            .build()
        val vp8Decoder = FFmpegVideoDecoder(vp8Format)
        vp8Decoder.release()

        val vp9Format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_VP9)
            .setWidth(1280)
            .setHeight(720)
            .build()
        val vp9Decoder = FFmpegVideoDecoder(vp9Format)
        vp9Decoder.release()
    }

    @Test
    fun testUnsupportedCodecThrowsException() {
        val format = Format.Builder()
            .setSampleMimeType("video/nonexistent-codec")
            .setWidth(640)
            .setHeight(480)
            .build()

        try {
            FFmpegVideoDecoder(format)
            fail("Expected FFmpegDecoderException for unsupported codec")
        } catch (expected: FFmpegDecoderException) {
            assertTrue(expected.message?.contains("Failed to initialize") == true)
        }
    }

    @Test
    fun testMissingMimeTypeThrowsException() {
        val format = Format.Builder()
            .setWidth(640)
            .setHeight(480)
            .build()

        try {
            FFmpegVideoDecoder(format)
            fail("Expected FFmpegDecoderException for missing MIME type")
        } catch (expected: FFmpegDecoderException) {
            assertTrue(expected.message?.contains("MIME type not specified") == true)
        }
    }

    @Test
    fun testDecoderFlushAndReset() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setWidth(1280)
            .setHeight(720)
            .build()

        val decoder = FFmpegVideoDecoder(format)
        try {
            decoder.flush()

            val inputBuffer = decoder.dequeueInputBuffer()
            assertNotNull("Input buffer should not be null", inputBuffer)

            if (inputBuffer != null) {
                inputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM)
                decoder.queueInputBuffer(inputBuffer)

                // SimpleDecoder decodes asynchronously on a background thread; poll with timeout
                var outputBuffer = decoder.dequeueOutputBuffer()
                val deadline = System.currentTimeMillis() + 2000L
                while (outputBuffer == null && System.currentTimeMillis() < deadline) {
                    Thread.sleep(10)
                    outputBuffer = decoder.dequeueOutputBuffer()
                }

                assertNotNull("Output buffer should become available after EOS", outputBuffer)
                if (outputBuffer != null) {
                    assertTrue("Output buffer must have EOS flag set", outputBuffer.isEndOfStream)
                    outputBuffer.release()
                }
            }
        } finally {
            decoder.release()
        }
    }

    @Test
    fun testRepeatedDecoderLifecycleMemoryRelease() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setWidth(1920)
            .setHeight(1080)
            .build()

        for (i in 0 until 15) {
            val decoder = FFmpegVideoDecoder(format)
            decoder.flush()
            decoder.release()
        }
    }

    @Test
    fun testRendererSupportsFormat() {
        val renderer = FFmpegVideoRenderer()

        val h264Format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setWidth(1920)
            .setHeight(1080)
            .build()
        val h264Support = renderer.supportsFormat(h264Format)
        assertEquals(C.FORMAT_HANDLED, RendererCapabilities.getFormatSupport(h264Support))

        val hevcFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setWidth(3840)
            .setHeight(2160)
            .build()
        val hevcSupport = renderer.supportsFormat(hevcFormat)
        assertEquals(C.FORMAT_HANDLED, RendererCapabilities.getFormatSupport(hevcSupport))

        val unsupportedFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_AV1)
            .setWidth(1920)
            .setHeight(1080)
            .build()
        val unsupportedSupport = renderer.supportsFormat(unsupportedFormat)
        assertEquals(C.FORMAT_UNSUPPORTED_TYPE, RendererCapabilities.getFormatSupport(unsupportedSupport))
    }

    @Test
    fun testRenderersFactoryPriorityModes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val handler = Handler(Looper.getMainLooper())
        val dummyVideoListener = object : VideoRendererEventListener {}
        val dummyAudioListener = object : AudioRendererEventListener {}
        val dummyTextListener = TextOutput {}
        val dummyMetadataListener = MetadataOutput {}

        val fallbackFactory = FFmpegRenderersFactory(
            context = context,
            softwareDecoderPriority = SoftwareDecoderPriority.FALLBACK
        )
        val fallbackRenderers = fallbackFactory.createRenderers(
            handler,
            dummyVideoListener,
            dummyAudioListener,
            dummyTextListener,
            dummyMetadataListener
        )
        val fallbackVideoRenderers = fallbackRenderers.filter { it.trackType == C.TRACK_TYPE_VIDEO }
        assertTrue(fallbackVideoRenderers.isNotEmpty())
        val lastVideoRenderer = fallbackVideoRenderers.lastOrNull()
        assertTrue("Last video renderer should be FFmpegVideoRenderer for FALLBACK", lastVideoRenderer is FFmpegVideoRenderer)

        val preferFactory = FFmpegRenderersFactory(
            context = context,
            softwareDecoderPriority = SoftwareDecoderPriority.PREFER
        )
        val preferRenderers = preferFactory.createRenderers(
            handler,
            dummyVideoListener,
            dummyAudioListener,
            dummyTextListener,
            dummyMetadataListener
        )
        val preferVideoRenderers = preferRenderers.filter { it.trackType == C.TRACK_TYPE_VIDEO }
        assertTrue(preferVideoRenderers.isNotEmpty())
        val firstVideoRenderer = preferVideoRenderers.firstOrNull()
        val isSoftwareDecoder = firstVideoRenderer is FFmpegVideoRenderer || firstVideoRenderer is Dav1dVideoRenderer
        assertTrue("First video renderer should be a software decoder for PREFER", isSoftwareDecoder)

        val disabledFactory = FFmpegRenderersFactory(
            context = context,
            softwareDecoderPriority = SoftwareDecoderPriority.DISABLED
        )
        val disabledRenderers = disabledFactory.createRenderers(
            handler,
            dummyVideoListener,
            dummyAudioListener,
            dummyTextListener,
            dummyMetadataListener
        )
        assertFalse(
            "Disabled priority should not include FFmpegVideoRenderer",
            disabledRenderers.any { it is FFmpegVideoRenderer }
        )
    }
}
