package com.nosved.nosvedmedia.media3

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nosved.native_codecs.NativeLib
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class Dav1dDecoderTest {

    private val nativeLib = NativeLib()

    @Test
    fun testDav1dVersionRetrieval() {
        val version = nativeLib.getDav1dVersion()
        assertNotNull(version)
        assertTrue("dav1d version should start with 1, was: $version", version.startsWith("1."))
    }

    @Test
    fun testDav1dDecoderCapabilities() {
        assertTrue(VideoDecoderCapabilities.isMimeTypeSupported(MimeTypes.VIDEO_AV1))
        assertTrue(CodecCapabilities.getSoftwareDecoderAvailability(MimeTypes.VIDEO_AV1))

        val report = CodecCapabilities.getCodecReport(MimeTypes.VIDEO_AV1)
        assertEquals(MimeTypes.VIDEO_AV1, report.mimeType)
        assertTrue(report.isSoftwareFallbackAvailable)
        assertNotNull(report.softwareEngine)
        assertTrue(report.softwareEngine?.contains("dav1d") == true)

        assertTrue(report.supportedBitDepths.contains(8))
        assertTrue(report.supportedBitDepths.contains(10))

        assertTrue(report.supportedProfiles.any { it.contains("Main") })
        assertTrue(report.supportedProfiles.any { it.contains("High") })
        assertTrue(report.supportedProfiles.any { it.contains("Professional") })

        assertTrue(report.supportedOutputFormats.contains("RGBA_8888"))
    }

    @Test
    fun testDav1dVideoDecoderInstantiation() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_AV1)
            .setWidth(1920)
            .setHeight(1080)
            .build()

        val decoder = Dav1dVideoDecoder(format)
        try {
            assertEquals("Dav1dVideoDecoder", decoder.name)
        } finally {
            decoder.release()
        }
    }

    @Test
    fun testDav1dVideoDecoderUnsupportedMimeType() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setWidth(1280)
            .setHeight(720)
            .build()

        try {
            Dav1dVideoDecoder(format)
            fail("Expected FFmpegDecoderException when passing non-AV1 format to Dav1dVideoDecoder")
        } catch (expected: FFmpegDecoderException) {
            assertTrue(expected.message?.contains("only supports") == true)
        }
    }

    @Test
    fun testDav1dVideoDecoderFlushAndReset() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_AV1)
            .setWidth(1280)
            .setHeight(720)
            .build()

        val decoder = Dav1dVideoDecoder(format)
        try {
            decoder.flush()

            val inputBuffer = decoder.dequeueInputBuffer()
            assertNotNull("Input buffer should not be null", inputBuffer)

            if (inputBuffer != null) {
                inputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM)
                decoder.queueInputBuffer(inputBuffer)

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
    fun testDav1dRepeatedLifecycleMemorySafety() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_AV1)
            .setWidth(1920)
            .setHeight(1080)
            .build()

        for (i in 0 until 15) {
            val decoder = Dav1dVideoDecoder(format)
            decoder.flush()
            decoder.release()
        }
    }

    @Test
    fun testDav1dRendererSupportsFormat() {
        val renderer = Dav1dVideoRenderer()

        val av1Format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_AV1)
            .setWidth(1920)
            .setHeight(1080)
            .build()
        val av1Support = renderer.supportsFormat(av1Format)
        assertEquals(C.FORMAT_HANDLED, RendererCapabilities.getFormatSupport(av1Support))

        val h264Format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setWidth(1920)
            .setHeight(1080)
            .build()
        val h264Support = renderer.supportsFormat(h264Format)
        assertEquals(C.FORMAT_UNSUPPORTED_TYPE, RendererCapabilities.getFormatSupport(h264Support))
    }

    @Test
    fun testRenderersFactoryIncludesDav1dRenderer() {
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
        val hasDav1dFallback = fallbackRenderers.any { it is Dav1dVideoRenderer }
        assertTrue("Fallback renderers must include Dav1dVideoRenderer", hasDav1dFallback)

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
        val firstVideoRenderer = preferRenderers.firstOrNull { it.trackType == C.TRACK_TYPE_VIDEO }
        assertTrue("Preferred video renderer should be software decoder", firstVideoRenderer is Dav1dVideoRenderer || firstVideoRenderer is FFmpegVideoRenderer)

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
        assertFalse("Disabled priority must not include Dav1dVideoRenderer", disabledRenderers.any { it is Dav1dVideoRenderer })
    }

    @Test
    fun testNativeDirectDav1dLifecycle() {
        val handle = nativeLib.createDav1dDecoder(1920, 1080, 2)
        assertTrue("Native dav1d handle must be non-zero", handle != 0L)

        nativeLib.flushDav1dDecoder(handle)
        nativeLib.releaseDav1dDecoder(handle)
    }
}

