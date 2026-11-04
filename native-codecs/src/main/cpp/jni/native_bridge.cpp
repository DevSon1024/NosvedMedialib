#include <jni.h>
#include <string>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include "../core/ffmpeg_init.h"
#include "../video/ffmpeg_video_decoder.h"
#include "../video/dav1d_video_decoder.h"
#include "../common/logging.h"

static JavaVM* g_vm = nullptr;

jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_vm = vm;
    LOGI("JNI_OnLoad called for NosvedMedia native-codecs");

    if (!nosved::FfmpegInit::getInstance().initialize(vm)) {
        LOGE("Failed to initialize FFmpeg subsystem in JNI_OnLoad");
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM* /*vm*/, void* /*reserved*/) {
    LOGI("JNI_OnUnload called for NosvedMedia native-codecs");
    g_vm = nullptr;
}

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_nosved_native_1codecs_NativeLib_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string info = "NosvedMedia FFmpeg: " + nosved::FfmpegInit::getInstance().getVersion();
    return env->NewStringUTF(info.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeInit(
        JNIEnv* /*env*/,
        jobject /* this */) {
    return static_cast<jboolean>(nosved::FfmpegInit::getInstance().initialize(g_vm));
}

JNIEXPORT jboolean JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeIsInitialized(
        JNIEnv* /*env*/,
        jobject /* this */) {
    return static_cast<jboolean>(nosved::FfmpegInit::getInstance().isInitialized());
}

JNIEXPORT jstring JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetFFmpegVersion(
        JNIEnv* env,
        jobject /* this */) {
    std::string ver = nosved::FfmpegInit::getInstance().getVersion();
    return env->NewStringUTF(ver.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetFFmpegLicense(
        JNIEnv* env,
        jobject /* this */) {
    std::string lic = nosved::FfmpegInit::getInstance().getLicense();
    return env->NewStringUTF(lic.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetFFmpegConfiguration(
        JNIEnv* env,
        jobject /* this */) {
    std::string config = nosved::FfmpegInit::getInstance().getConfiguration();
    return env->NewStringUTF(config.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetErrorDescription(
        JNIEnv* env,
        jobject /* this */,
        jint errorCode) {
    std::string desc = nosved::FfmpegInit::getInstance().getErrorDescription(errorCode);
    return env->NewStringUTF(desc.c_str());
}

JNIEXPORT jint JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetCodecVersion(
        JNIEnv* /*env*/,
        jobject /* this */) {
    return static_cast<jint>(nosved::FfmpegInit::getInstance().getCodecVersion());
}

JNIEXPORT jint JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetFormatVersion(
        JNIEnv* /*env*/,
        jobject /* this */) {
    return static_cast<jint>(nosved::FfmpegInit::getInstance().getFormatVersion());
}

JNIEXPORT jint JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetUtilVersion(
        JNIEnv* /*env*/,
        jobject /* this */) {
    return static_cast<jint>(nosved::FfmpegInit::getInstance().getUtilVersion());
}

// Video Decoder JNI methods

JNIEXPORT jlong JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeCreateVideoDecoder(
        JNIEnv* env,
        jobject /* this */,
        jstring jMimeType,
        jbyteArray jExtraData,
        jint width,
        jint height) {
    if (!jMimeType) return 0L;

    const char* mimeChars = env->GetStringUTFChars(jMimeType, nullptr);
    if (!mimeChars) return 0L;
    std::string mimeType(mimeChars);
    env->ReleaseStringUTFChars(jMimeType, mimeChars);

    uint8_t* extraDataPtr = nullptr;
    int extraDataSize = 0;
    jbyte* byteElements = nullptr;

    if (jExtraData) {
        extraDataSize = env->GetArrayLength(jExtraData);
        if (extraDataSize > 0) {
            byteElements = env->GetByteArrayElements(jExtraData, nullptr);
            extraDataPtr = reinterpret_cast<uint8_t*>(byteElements);
        }
    }

    auto* decoder = new (std::nothrow) nosved::FfmpegVideoDecoder();
    if (!decoder) {
        if (byteElements) env->ReleaseByteArrayElements(jExtraData, byteElements, JNI_ABORT);
        return 0L;
    }

    bool ok = decoder->open(mimeType, extraDataPtr, extraDataSize, width, height);

    if (byteElements) {
        env->ReleaseByteArrayElements(jExtraData, byteElements, JNI_ABORT);
    }

    if (!ok) {
        delete decoder;
        return 0L;
    }

    return reinterpret_cast<jlong>(decoder);
}

JNIEXPORT jint JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeDecodeVideo(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jobject jByteBuffer,
        jint offset,
        jint length,
        jlong timeUs,
        jboolean isEos) {
    if (handle == 0L) return -1;
    auto* decoder = reinterpret_cast<nosved::FfmpegVideoDecoder*>(handle);

    const uint8_t* data = nullptr;
    if (jByteBuffer && length > 0) {
        auto* directAddress = static_cast<uint8_t*>(env->GetDirectBufferAddress(jByteBuffer));
        if (directAddress) {
            data = directAddress + offset;
        }
    }

    return decoder->decode(data, length, timeUs, isEos == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetVideoFrameInfo(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jintArray jOutInfo) {
    if (handle == 0L || !jOutInfo) return JNI_FALSE;
    auto* decoder = reinterpret_cast<nosved::FfmpegVideoDecoder*>(handle);

    nosved::VideoFrameInfo info;
    if (!decoder->getLatestFrameInfo(&info)) {
        return JNI_FALSE;
    }

    jint temp[4] = {
        info.width,
        info.height,
        info.isKeyFrame ? 1 : 0,
        info.format
    };
    env->SetIntArrayRegion(jOutInfo, 0, 4, temp);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeRenderVideoFrameToSurface(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jobject jSurface) {
    if (handle == 0L || !jSurface) return JNI_FALSE;
    auto* decoder = reinterpret_cast<nosved::FfmpegVideoDecoder*>(handle);

    ANativeWindow* window = ANativeWindow_fromSurface(env, jSurface);
    if (!window) {
        LOGE("Failed to obtain ANativeWindow from Surface");
        return JNI_FALSE;
    }

    bool success = decoder->renderToSurface(window);
    ANativeWindow_release(window);
    return static_cast<jboolean>(success);
}

JNIEXPORT void JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeFlushVideoDecoder(
        JNIEnv* /*env*/,
        jobject /* this */,
        jlong handle) {
    if (handle == 0L) return;
    auto* decoder = reinterpret_cast<nosved::FfmpegVideoDecoder*>(handle);
    decoder->flush();
}

JNIEXPORT void JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeReleaseVideoDecoder(
        JNIEnv* /*env*/,
        jobject /* this */,
        jlong handle) {
    if (handle == 0L) return;
    auto* decoder = reinterpret_cast<nosved::FfmpegVideoDecoder*>(handle);
    delete decoder;
}

JNIEXPORT jstring JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetDav1dVersion(
        JNIEnv* env,
        jobject /* this */) {
    const char* ver = nosved::Dav1dVideoDecoder::getVersion();
    return env->NewStringUTF(ver ? ver : "unknown");
}

JNIEXPORT jlong JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeCreateDav1dDecoder(
        JNIEnv* /*env*/,
        jobject /* this */,
        jint width,
        jint height,
        jint nThreads) {
    auto decoder = std::make_unique<nosved::Dav1dVideoDecoder>();
    if (!decoder->open(width, height, nThreads)) {
        LOGE("Failed to open dav1d decoder");
        return 0L;
    }
    return reinterpret_cast<jlong>(decoder.release());
}

JNIEXPORT jint JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeDecodeDav1d(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jobject jByteBuffer,
        jint offset,
        jint length,
        jlong ptsUs,
        jboolean isEos) {
    if (handle == 0L) return -EINVAL;
    auto* decoder = reinterpret_cast<nosved::Dav1dVideoDecoder*>(handle);

    const uint8_t* packetData = nullptr;
    if (jByteBuffer && length > 0) {
        auto* directBuffer = static_cast<uint8_t*>(env->GetDirectBufferAddress(jByteBuffer));
        if (directBuffer) {
            packetData = directBuffer + offset;
        }
    }

    return decoder->decode(packetData, length, ptsUs, static_cast<bool>(isEos));
}

JNIEXPORT jboolean JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetDav1dFrameInfo(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jintArray jOutInfo) {
    if (handle == 0L || !jOutInfo) return JNI_FALSE;
    auto* decoder = reinterpret_cast<nosved::Dav1dVideoDecoder*>(handle);

    nosved::VideoFrameInfo info;
    if (!decoder->getFrameInfo(info)) {
        return JNI_FALSE;
    }

    jint temp[5] = {
        info.width,
        info.height,
        info.isKeyFrame ? 1 : 0,
        info.format,
        info.bitDepth
    };
    env->SetIntArrayRegion(jOutInfo, 0, 5, temp);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeRenderDav1dFrameToSurface(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jobject jSurface) {
    if (handle == 0L || !jSurface) return JNI_FALSE;
    auto* decoder = reinterpret_cast<nosved::Dav1dVideoDecoder*>(handle);

    ANativeWindow* window = ANativeWindow_fromSurface(env, jSurface);
    if (!window) {
        LOGE("Failed to obtain ANativeWindow from Surface for dav1d");
        return JNI_FALSE;
    }

    bool success = decoder->renderToSurface(window);
    ANativeWindow_release(window);
    return static_cast<jboolean>(success);
}

JNIEXPORT void JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeFlushDav1dDecoder(
        JNIEnv* /*env*/,
        jobject /* this */,
        jlong handle) {
    if (handle == 0L) return;
    auto* decoder = reinterpret_cast<nosved::Dav1dVideoDecoder*>(handle);
    decoder->flush();
}

JNIEXPORT void JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeReleaseDav1dDecoder(
        JNIEnv* /*env*/,
        jobject /* this */,
        jlong handle) {
    if (handle == 0L) return;
    auto* decoder = reinterpret_cast<nosved::Dav1dVideoDecoder*>(handle);
    delete decoder;
}

} // extern "C"
