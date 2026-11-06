package com.nosved.nosvedmedia.media3

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FFmpegVideoDecoderTest {

    @Test
    fun getExtraData_h264_prependsAnnexBStartCodesWhenMissing() {
        val sps = byteArrayOf(0x67, 0x42, 0x00, 0x1E)
        val pps = byteArrayOf(0x68, 0xCE.toByte(), 0x38, 0x80.toByte())
        val initData = listOf(sps, pps)

        val result = FFmpegVideoDecoder.getExtraData(MimeTypes.VIDEO_H264, initData)
        assertNotNull(result)

        val expected = byteArrayOf(
            0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1E,
            0, 0, 0, 1, 0x68, 0xCE.toByte(), 0x38, 0x80.toByte()
        )
        assertArrayEquals(expected, result)
    }

    @Test
    fun getExtraData_h264_preservesExistingAnnexBStartCodes() {
        val sps = byteArrayOf(0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1E)
        val pps = byteArrayOf(0, 0, 0, 1, 0x68, 0xCE.toByte())
        val initData = listOf(sps, pps)

        val result = FFmpegVideoDecoder.getExtraData(MimeTypes.VIDEO_H264, initData)
        assertNotNull(result)

        val expected = byteArrayOf(
            0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1E,
            0, 0, 0, 1, 0x68, 0xCE.toByte()
        )
        assertArrayEquals(expected, result)
    }

    @Test
    fun getExtraData_hevc_extractsFirstBuffer() {
        val hevcConfig = byteArrayOf(1, 2, 3, 4, 5)
        val initData = listOf(hevcConfig)

        val result = FFmpegVideoDecoder.getExtraData(MimeTypes.VIDEO_H265, initData)
        assertNotNull(result)
        assertArrayEquals(hevcConfig, result)
    }

    @Test
    fun getExtraData_emptyList_returnsNull() {
        val result = FFmpegVideoDecoder.getExtraData(MimeTypes.VIDEO_H264, emptyList())
        assertNull(result)
    }

    @Test
    fun videoDecoderConfig_defaultAndCustomValues() {
        val defaultConfig = VideoDecoderConfig()
        assertEquals(12, defaultConfig.numInputBuffers)
        assertEquals(12, defaultConfig.numOutputBuffers)
        assertEquals(1024 * 1024, defaultConfig.initialInputBufferSize)
        assertEquals(3840, defaultConfig.maxOutputWidth)
        assertEquals(2160, defaultConfig.maxOutputHeight)
        assertEquals(0, defaultConfig.threadCount)

        val customConfig = VideoDecoderConfig(
            numInputBuffers = 16,
            numOutputBuffers = 16,
            initialInputBufferSize = 2 * 1024 * 1024,
            maxOutputWidth = 1920,
            maxOutputHeight = 1080,
            threadCount = 4
        )
        assertEquals(16, customConfig.numInputBuffers)
        assertEquals(16, customConfig.numOutputBuffers)
        assertEquals(2 * 1024 * 1024, customConfig.initialInputBufferSize)
        assertEquals(1920, customConfig.maxOutputWidth)
        assertEquals(1080, customConfig.maxOutputHeight)
        assertEquals(4, customConfig.threadCount)
    }
}
