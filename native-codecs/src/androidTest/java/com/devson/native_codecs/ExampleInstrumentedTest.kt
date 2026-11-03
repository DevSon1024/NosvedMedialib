package com.devson.native_codecs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun testNativeLibraryLoaded() {
        assertTrue("Native library must be successfully loaded", NativeLib.isLibraryLoaded)
    }

    @Test
    fun testNativeInitialization() {
        val nativeLib = NativeLib()
        val initSuccess = nativeLib.init()
        assertTrue("Native initialization should succeed", initSuccess)
        assertTrue("Native isInitialized() should return true", nativeLib.isInitialized())
    }

    @Test
    fun testFFmpegVersionRetrieval() {
        val nativeLib = NativeLib()
        val version = nativeLib.getFFmpegVersion()
        assertNotNull(version)
        assertTrue("FFmpeg version should start with 7, was: $version", version.startsWith("7"))
    }

    @Test
    fun testFFmpegLicenseIsLGPL() {
        val nativeLib = NativeLib()
        val license = nativeLib.getFFmpegLicense()
        assertNotNull(license)
        assertTrue("License must be LGPL, was: $license", license.contains("LGPL"))
    }

    @Test
    fun testComponentVersions() {
        val nativeLib = NativeLib()
        assertTrue("AVCodec version should be positive", nativeLib.getCodecVersion() > 0)
        assertTrue("AVFormat version should be positive", nativeLib.getFormatVersion() > 0)
        assertTrue("AVUtil version should be positive", nativeLib.getUtilVersion() > 0)
    }

    @Test
    fun testStringFromJNI() {
        val nativeLib = NativeLib()
        val result = nativeLib.stringFromJNI()
        assertNotNull(result)
        assertTrue("stringFromJNI should contain FFmpeg version", result.contains("NosvedMedia FFmpeg: 7"))
    }

    @Test
    fun testErrorDescription() {
        val nativeLib = NativeLib()
        val desc = nativeLib.getErrorDescription(0)
        assertNotNull(desc)
    }
}
