package com.nosved.nosvedmedia.media3

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class ReliabilityAndStressTest {

    @Test
    fun testDecoderLifecycleChurn() {
        val iterations = 50
        val formatH264 = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setWidth(1280)
            .setHeight(720)
            .build()

        val formatAv1 = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_AV1)
            .setWidth(1280)
            .setHeight(720)
            .build()

        for (i in 0 until iterations) {
            val ffmpegDecoder = FFmpegVideoDecoder(formatH264)
            ffmpegDecoder.flush()
            ffmpegDecoder.release()

            val dav1dDecoder = Dav1dVideoDecoder(formatAv1)
            dav1dDecoder.flush()
            dav1dDecoder.release()
        }
    }

    @Test
    fun testRapidSeekAndFlushStress() {
        val formatH264 = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setWidth(640)
            .setHeight(360)
            .build()

        val decoder = FFmpegVideoDecoder(formatH264)
        try {
            val inputBuffer = decoder.dequeueInputBuffer()
            assertNotNull(inputBuffer)
            val buffer = inputBuffer?.data
            if (buffer != null) {
                buffer.put(byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x67, 0x42, 0x00, 0x1E))
                buffer.flip()
            }
            if (inputBuffer != null) {
                decoder.queueInputBuffer(inputBuffer)
            }

            for (i in 0 until 100) {
                decoder.flush()
            }

            val nextInput = decoder.dequeueInputBuffer()
            assertNotNull(nextInput)
        } finally {
            decoder.release()
        }
    }

    @Test
    fun testCorruptedPacketTolerance() {
        val formatH264 = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setWidth(640)
            .setHeight(360)
            .build()

        val decoder = FFmpegVideoDecoder(formatH264)
        val random = Random(42)

        try {
            for (i in 0 until 20) {
                try {
                    var inputBuffer = decoder.dequeueInputBuffer()
                    var retries = 0
                    while (inputBuffer == null && retries < 10) {
                        Thread.sleep(10)
                        decoder.dequeueOutputBuffer()?.release()
                        inputBuffer = decoder.dequeueInputBuffer()
                        retries++
                    }

                    if (inputBuffer != null) {
                        val garbage = ByteArray(512)
                        random.nextBytes(garbage)

                        val data = inputBuffer.data
                        if (data != null) {
                            data.clear()
                            data.put(garbage)
                            data.flip()
                        }

                        inputBuffer.timeUs = i * 33333L
                        decoder.queueInputBuffer(inputBuffer)
                    }

                    val outputBuffer = decoder.dequeueOutputBuffer()
                    outputBuffer?.release()
                } catch (e: FFmpegDecoderException) {
                    // Graceful decoder exception is acceptable for corrupted data
                    assertNotNull(e.message)
                    decoder.flush() // Reset decoder error state to resume
                }
            }
        } finally {
            decoder.release()
        }
    }

    @Test
    fun testDav1dCorruptedPacketTolerance() {
        val formatAv1 = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_AV1)
            .setWidth(640)
            .setHeight(360)
            .build()

        val decoder = Dav1dVideoDecoder(formatAv1)
        val random = Random(99)

        try {
            for (i in 0 until 20) {
                try {
                    var inputBuffer = decoder.dequeueInputBuffer()
                    var retries = 0
                    while (inputBuffer == null && retries < 10) {
                        Thread.sleep(10)
                        decoder.dequeueOutputBuffer()?.release()
                        inputBuffer = decoder.dequeueInputBuffer()
                        retries++
                    }

                    if (inputBuffer != null) {
                        val garbage = ByteArray(256)
                        random.nextBytes(garbage)

                        val data = inputBuffer.data
                        if (data != null) {
                            data.clear()
                            data.put(garbage)
                            data.flip()
                        }

                        inputBuffer.timeUs = i * 33333L
                        decoder.queueInputBuffer(inputBuffer)
                    }

                    val outputBuffer = decoder.dequeueOutputBuffer()
                    outputBuffer?.release()
                } catch (e: FFmpegDecoderException) {
                    assertNotNull(e.message)
                    decoder.flush() // Reset decoder error state to resume
                }
            }
        } finally {
            decoder.release()
        }
    }

    @Test
    fun testUnsupportedFormatRejection() {
        val invalidFormat = Format.Builder()
            .setSampleMimeType("video/unsupported-dummy-format")
            .build()

        try {
            FFmpegVideoDecoder(invalidFormat)
            fail("Expected FFmpegDecoderException for unsupported format")
        } catch (e: FFmpegDecoderException) {
            assertTrue(e.message?.contains("unsupported-dummy-format") == true || e.message?.contains("Failed to initialize") == true)
        }

        try {
            Dav1dVideoDecoder(invalidFormat)
            fail("Expected FFmpegDecoderException for non-AV1 format in Dav1d")
        } catch (e: FFmpegDecoderException) {
            assertTrue(e.message?.contains("only supports") == true)
        }
    }

    @Test
    fun testMultithreadedConcurrentStress() {
        val threadCount = 4
        val iterationsPerThread = 25
        val latch = CountDownLatch(threadCount)
        val errorDetected = AtomicBoolean(false)
        val operationsCount = AtomicInteger(0)

        for (t in 0 until threadCount) {
            val isAv1 = (t % 2 == 1)
            Thread {
                try {
                    val format = if (isAv1) {
                        Format.Builder()
                            .setSampleMimeType(MimeTypes.VIDEO_AV1)
                            .setWidth(640)
                            .setHeight(360)
                            .build()
                    } else {
                        Format.Builder()
                            .setSampleMimeType(MimeTypes.VIDEO_H264)
                            .setWidth(640)
                            .setHeight(360)
                            .build()
                    }

                    val decoder = if (isAv1) Dav1dVideoDecoder(format) else FFmpegVideoDecoder(format)
                    try {
                        for (i in 0 until iterationsPerThread) {
                            if (i % 5 == 0) {
                                decoder.flush()
                            } else {
                                val inBuf = decoder.dequeueInputBuffer()
                                if (inBuf != null) {
                                    inBuf.data?.clear()
                                    inBuf.data?.put(byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x67, 0x42))
                                    inBuf.data?.flip()
                                    inBuf.timeUs = i * 1000L
                                    decoder.queueInputBuffer(inBuf)
                                }
                                try {
                                    val outBuf = decoder.dequeueOutputBuffer()
                                    outBuf?.release()
                                } catch (e: FFmpegDecoderException) {
                                    // Handled gracefully
                                }
                            }
                            operationsCount.incrementAndGet()
                        }
                    } finally {
                        decoder.release()
                    }
                } catch (e: Throwable) {
                    errorDetected.set(true)
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        assertTrue("Concurrent stress test timed out", latch.await(10, TimeUnit.SECONDS))
        assertFalse("Unexpected exception occurred during multithreaded test", errorDetected.get())
        assertTrue("Expected operations to execute", operationsCount.get() >= threadCount * 10)
    }

    @Test
    fun testPerformanceMonitorTracking() {
        val monitor = PerformanceMonitor(sampleWindowFrames = 10)

        for (i in 1..20) {
            monitor.recordDecodeTime(i * 1_000_000L) // 1ms, 2ms, ... 20ms
        }
        monitor.recordDroppedFrame()
        monitor.recordDroppedFrame()

        val snapshot = monitor.getSnapshot()
        assertEquals(20L, snapshot.totalFramesDecoded)
        assertEquals(2L, snapshot.totalFramesDropped)
        assertEquals(1.0, snapshot.minDecodeTimeMs, 0.001)
        assertEquals(20.0, snapshot.maxDecodeTimeMs, 0.001)
        assertEquals(10.5, snapshot.averageDecodeTimeMs, 0.001)
        assertTrue(snapshot.memoryUsageMb > 0.0)

        monitor.reset()
        val resetSnapshot = monitor.getSnapshot()
        assertEquals(0L, resetSnapshot.totalFramesDecoded)
        assertEquals(0L, resetSnapshot.totalFramesDropped)
    }

    @Test
    fun testDiagnosticsReporting() {
        var initializedReport: DecoderDiagnostics? = null
        var metricsReport: DecodingPerformanceMetrics? = null
        var errorReport: DecodingError? = null

        val listener = object : PlaybackDiagnosticsListener {
            override fun onDecoderInitialized(diagnostics: DecoderDiagnostics) {
                initializedReport = diagnostics
            }

            override fun onPerformanceMetricsUpdated(metrics: DecodingPerformanceMetrics) {
                metricsReport = metrics
            }

            override fun onDecodingError(error: DecodingError) {
                errorReport = error
            }
        }

        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setWidth(1920)
            .setHeight(1080)
            .build()

        val monitor = PerformanceMonitor(listener)
        val decoder = FFmpegVideoDecoder(
            format = format,
            performanceMonitor = monitor,
            diagnosticsListener = listener
        )

        try {
            assertNotNull(initializedReport)
            assertEquals("FFmpegVideoDecoder", initializedReport?.decoderName)
            assertEquals(MimeTypes.VIDEO_H264, initializedReport?.mimeType)
            assertEquals(1920, initializedReport?.width)
            assertEquals(1080, initializedReport?.height)
            assertFalse(initializedReport?.isHardware == true)

            // Verify performance update
            monitor.recordDecodeTime(5_000_000L)
            assertNotNull(metricsReport)
            assertEquals(1L, metricsReport?.totalFramesDecoded)
            assertEquals(5.0, metricsReport?.maxDecodeTimeMs ?: 0.0, 0.001)
        } finally {
            decoder.release()
        }
    }
}
