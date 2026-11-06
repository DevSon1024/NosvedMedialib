#include <jni.h>
#include <string>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include "../core/ffmpeg_init.h"
#include "../audio/ffmpeg_audio_decoder.h"
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

    bool ok = decoder->open(mimeType, extraDataPtr, extraDataSize, width, height, 0);

    if (byteElements) {
        env->ReleaseByteArrayElements(jExtraData, byteElements, JNI_ABORT);
    }

    if (!ok) {
        delete decoder;
        return 0L;
    }

    return reinterpret_cast<jlong>(decoder);
}

JNIEXPORT jlong JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeCreateVideoDecoderConfigured(
        JNIEnv* env,
        jobject /* this */,
        jstring jMimeType,
        jbyteArray jExtraData,
        jint width,
        jint height,
        jint threadCount,
        jint scalingMode,
        jboolean enableToneMapping) {
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

    nosved::VideoScalingMode mode = static_cast<nosved::VideoScalingMode>(scalingMode);
    bool ok = decoder->open(mimeType, extraDataPtr, extraDataSize, width, height, threadCount, mode, enableToneMapping == JNI_TRUE);

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
Java_com_nosved_native_1codecs_NativeLib_nativeSendVideoPacket(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jobject jByteBuffer,
        jint offset,
        jint length,
        jlong ptsUs,
        jlong dtsUs) {
    if (handle == 0L) return nosved::DECODE_ERROR_INVALID_STATE;
    auto* decoder = reinterpret_cast<nosved::FfmpegVideoDecoder*>(handle);

    const uint8_t* data = nullptr;
    if (jByteBuffer && length > 0) {
        auto* directAddress = static_cast<uint8_t*>(env->GetDirectBufferAddress(jByteBuffer));
        if (directAddress) {
            data = directAddress + offset;
        }
    }

    return decoder->sendPacket(data, length, ptsUs, dtsUs);
}

JNIEXPORT jint JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeSendVideoEos(
        JNIEnv* /*env*/,
        jobject /* this */,
        jlong handle) {
    if (handle == 0L) return nosved::DECODE_ERROR_INVALID_STATE;
    auto* decoder = reinterpret_cast<nosved::FfmpegVideoDecoder*>(handle);
    return decoder->sendEos();
}

JNIEXPORT jint JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeReceiveVideoFrame(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jintArray jOutInfo) {
    if (handle == 0L || !jOutInfo) return nosved::DECODE_ERROR_INVALID_STATE;
    auto* decoder = reinterpret_cast<nosved::FfmpegVideoDecoder*>(handle);

    int slotIndex = -1;
    nosved::VideoFrameInfo info;
    int res = decoder->receiveFrame(slotIndex, &info);
    if (res == nosved::DECODE_OK) {
        jsize len = env->GetArrayLength(jOutInfo);
        if (len >= 10) {
            jint temp[10] = {
                slotIndex,
                info.width,
                info.height,
                info.isKeyFrame ? 1 : 0,
                info.format,
                info.bitDepth,
                info.colorInfo.colorSpace,
                info.colorInfo.colorRange,
                info.colorInfo.colorTransfer,
                info.colorInfo.isHdr ? 1 : 0
            };
            env->SetIntArrayRegion(jOutInfo, 0, 10, temp);
        } else if (len >= 5) {
            jint temp[5] = {
                slotIndex,
                info.width,
                info.height,
                info.isKeyFrame ? 1 : 0,
                info.format
            };
            env->SetIntArrayRegion(jOutInfo, 0, 5, temp);
        }
    }
    return res;
}

JNIEXPORT jstring JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetVideoLastErrorMessage(
        JNIEnv* env,
        jobject /* this */,
        jlong handle) {
    if (handle == 0L) return env->NewStringUTF("Decoder handle is null");
    auto* decoder = reinterpret_cast<nosved::FfmpegVideoDecoder*>(handle);
    std::string msg = decoder->getLastErrorMessage();
    return env->NewStringUTF(msg.c_str());
}

JNIEXPORT jint JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeDecodeVideo(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jint bufferIndex,
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

    return decoder->decode(bufferIndex, data, length, timeUs, isEos == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetVideoFrameInfo(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jint bufferIndex,
        jintArray jOutInfo) {
    if (handle == 0L || !jOutInfo) return JNI_FALSE;
    auto* decoder = reinterpret_cast<nosved::FfmpegVideoDecoder*>(handle);

    nosved::VideoFrameInfo info;
    if (!decoder->getFrameInfo(bufferIndex, &info)) {
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

JNIEXPORT jlong JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetVideoFramePts(
        JNIEnv* /*env*/,
        jobject /* this */,
        jlong handle,
        jint bufferIndex) {
    if (handle == 0L) return -1LL;
    auto* decoder = reinterpret_cast<nosved::FfmpegVideoDecoder*>(handle);
    nosved::VideoFrameInfo info;
    if (!decoder->getFrameInfo(bufferIndex, &info)) {
        return -1LL;
    }
    return static_cast<jlong>(info.pts);
}

JNIEXPORT jboolean JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeRenderVideoFrameToSurface(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jint bufferIndex,
        jobject jSurface) {
    if (handle == 0L || !jSurface) return JNI_FALSE;
    auto* decoder = reinterpret_cast<nosved::FfmpegVideoDecoder*>(handle);

    ANativeWindow* window = ANativeWindow_fromSurface(env, jSurface);
    if (!window) {
        LOGE("Failed to obtain ANativeWindow from Surface");
        return JNI_FALSE;
    }

    bool success = decoder->renderToSurface(bufferIndex, window);
    ANativeWindow_release(window);
    return static_cast<jboolean>(success);
}

JNIEXPORT void JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeReleaseVideoFrame(
        JNIEnv* /*env*/,
        jobject /* this */,
        jlong handle,
        jint bufferIndex) {
    if (handle == 0L) return;
    auto* decoder = reinterpret_cast<nosved::FfmpegVideoDecoder*>(handle);
    decoder->releaseFrame(bufferIndex);
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

JNIEXPORT jlong JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeCreateDav1dDecoderConfigured(
        JNIEnv* /*env*/,
        jobject /* this */,
        jint width,
        jint height,
        jint nThreads,
        jint scalingMode,
        jboolean enableToneMapping) {
    auto decoder = std::make_unique<nosved::Dav1dVideoDecoder>();
    nosved::VideoScalingMode mode = static_cast<nosved::VideoScalingMode>(scalingMode);
    if (!decoder->open(width, height, nThreads, mode, enableToneMapping == JNI_TRUE)) {
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
        jint bufferIndex,
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

    return decoder->decode(bufferIndex, packetData, length, ptsUs, static_cast<bool>(isEos));
}

JNIEXPORT jboolean JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetDav1dFrameInfo(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jint bufferIndex,
        jintArray jOutInfo) {
    if (handle == 0L || !jOutInfo) return JNI_FALSE;
    auto* decoder = reinterpret_cast<nosved::Dav1dVideoDecoder*>(handle);

    nosved::VideoFrameInfo info;
    if (!decoder->getFrameInfo(bufferIndex, info)) {
        return JNI_FALSE;
    }

    jsize len = env->GetArrayLength(jOutInfo);
    if (len >= 9) {
        jint temp[9] = {
            info.width,
            info.height,
            info.isKeyFrame ? 1 : 0,
            info.format,
            info.bitDepth,
            info.colorInfo.colorSpace,
            info.colorInfo.colorRange,
            info.colorInfo.colorTransfer,
            info.colorInfo.isHdr ? 1 : 0
        };
        env->SetIntArrayRegion(jOutInfo, 0, 9, temp);
    } else if (len >= 5) {
        jint temp[5] = {
            info.width,
            info.height,
            info.isKeyFrame ? 1 : 0,
            info.format,
            info.bitDepth
        };
        env->SetIntArrayRegion(jOutInfo, 0, 5, temp);
    }
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetDav1dFramePts(
        JNIEnv* /*env*/,
        jobject /* this */,
        jlong handle,
        jint bufferIndex) {
    if (handle == 0L) return -1LL;
    auto* decoder = reinterpret_cast<nosved::Dav1dVideoDecoder*>(handle);
    nosved::VideoFrameInfo info;
    if (!decoder->getFrameInfo(bufferIndex, info)) {
        return -1LL;
    }
    return static_cast<jlong>(info.pts);
}

JNIEXPORT jboolean JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeRenderDav1dFrameToSurface(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jint bufferIndex,
        jobject jSurface) {
    if (handle == 0L || !jSurface) return JNI_FALSE;
    auto* decoder = reinterpret_cast<nosved::Dav1dVideoDecoder*>(handle);

    ANativeWindow* window = ANativeWindow_fromSurface(env, jSurface);
    if (!window) {
        LOGE("Failed to obtain ANativeWindow from Surface for dav1d");
        return JNI_FALSE;
    }

    bool success = decoder->renderToSurface(bufferIndex, window);
    ANativeWindow_release(window);
    return static_cast<jboolean>(success);
}

JNIEXPORT void JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeReleaseDav1dFrame(
        JNIEnv* /*env*/,
        jobject /* this */,
        jlong handle,
        jint bufferIndex) {
    if (handle == 0L) return;
    auto* decoder = reinterpret_cast<nosved::Dav1dVideoDecoder*>(handle);
    decoder->releaseFrame(bufferIndex);
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

// Audio Decoder JNI methods

JNIEXPORT jboolean JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeIsAudioMimeTypeSupported(
        JNIEnv* env,
        jobject /* this */,
        jstring jMimeType) {
    if (!jMimeType) return JNI_FALSE;
    const char* chars = env->GetStringUTFChars(jMimeType, nullptr);
    if (!chars) return JNI_FALSE;
    std::string mime(chars);
    env->ReleaseStringUTFChars(jMimeType, chars);
    return static_cast<jboolean>(nosved::FfmpegAudioDecoder::isMimeTypeSupported(mime));
}

JNIEXPORT jlong JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeCreateAudioDecoderConfigured(
        JNIEnv* env,
        jobject /* this */,
        jstring jMimeType,
        jbyteArray jExtraData,
        jint sampleRate,
        jint channelCount,
        jint pcmEncoding,
        jint channelRouting,
        jint maxOutputChannels) {
    if (!jMimeType) return 0L;
    const char* chars = env->GetStringUTFChars(jMimeType, nullptr);
    if (!chars) return 0L;
    std::string mime(chars);
    env->ReleaseStringUTFChars(jMimeType, chars);

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

    auto* decoder = new (std::nothrow) nosved::FfmpegAudioDecoder();
    if (!decoder) {
        if (byteElements) env->ReleaseByteArrayElements(jExtraData, byteElements, JNI_ABORT);
        return 0L;
    }

    bool ok = decoder->open(mime, extraDataPtr, extraDataSize, sampleRate, channelCount,
                            pcmEncoding, channelRouting, maxOutputChannels);
    if (byteElements) {
        env->ReleaseByteArrayElements(jExtraData, byteElements, JNI_ABORT);
    }

    if (!ok) {
        delete decoder;
        return 0L;
    }

    return reinterpret_cast<jlong>(decoder);
}

JNIEXPORT jlong JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeCreateAudioDecoder(
        JNIEnv* env,
        jobject thiz,
        jstring jMimeType,
        jbyteArray jExtraData,
        jint sampleRate,
        jint channelCount) {
    return Java_com_nosved_native_1codecs_NativeLib_nativeCreateAudioDecoderConfigured(
            env, thiz, jMimeType, jExtraData, sampleRate, channelCount, 2, 0, 8);
}

JNIEXPORT jint JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeSendAudioPacket(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jobject jInputBuffer,
        jint offset,
        jint length,
        jlong ptsUs) {
    if (handle == 0L) return -1;
    auto* decoder = reinterpret_cast<nosved::FfmpegAudioDecoder*>(handle);

    const uint8_t* inData = nullptr;
    if (jInputBuffer && length > 0) {
        auto* directAddress = static_cast<uint8_t*>(env->GetDirectBufferAddress(jInputBuffer));
        if (directAddress) {
            inData = directAddress + offset;
        }
    }

    return decoder->sendPacket(inData, length, ptsUs);
}

JNIEXPORT jint JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeSendAudioEos(
        JNIEnv* /*env*/,
        jobject /* this */,
        jlong handle) {
    if (handle == 0L) return -1;
    auto* decoder = reinterpret_cast<nosved::FfmpegAudioDecoder*>(handle);
    return decoder->sendEos();
}

JNIEXPORT jint JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeReceiveAudioPcm(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jobject jOutputBuffer,
        jint outCapacity,
        jlongArray jOutPtsUs) {
    if (handle == 0L || !jOutputBuffer || outCapacity <= 0) return -1;
    auto* decoder = reinterpret_cast<nosved::FfmpegAudioDecoder*>(handle);

    auto* outAddress = static_cast<uint8_t*>(env->GetDirectBufferAddress(jOutputBuffer));
    if (!outAddress) {
        LOGE("Direct buffer address for audio output buffer is null");
        return -1;
    }

    int64_t pts = -1;
    int bytesRead = decoder->receivePcm(outAddress, outCapacity, &pts);

    if (jOutPtsUs && env->GetArrayLength(jOutPtsUs) > 0) {
        jlong ptsVal = static_cast<jlong>(pts);
        env->SetLongArrayRegion(jOutPtsUs, 0, 1, &ptsVal);
    }

    return bytesRead;
}

JNIEXPORT jint JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetAudioPcmEncoding(
        JNIEnv* /*env*/,
        jobject /* this */,
        jlong handle) {
    if (handle == 0L) return 2;
    auto* decoder = reinterpret_cast<nosved::FfmpegAudioDecoder*>(handle);
    return decoder->getPcmEncoding();
}

JNIEXPORT jstring JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetAudioLastErrorMessage(
        JNIEnv* env,
        jobject /* this */,
        jlong handle) {
    if (handle == 0L) return env->NewStringUTF("");
    auto* decoder = reinterpret_cast<nosved::FfmpegAudioDecoder*>(handle);
    std::string msg = decoder->getLastErrorMessage();
    return env->NewStringUTF(msg.c_str());
}

JNIEXPORT jint JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeDecodeAudio(
        JNIEnv* env,
        jobject /* this */,
        jlong handle,
        jobject jInputBuffer,
        jint offset,
        jint length,
        jobject jOutputBuffer,
        jint outCapacity) {
    if (handle == 0L || !jOutputBuffer || outCapacity <= 0) return -1;
    auto* decoder = reinterpret_cast<nosved::FfmpegAudioDecoder*>(handle);

    const uint8_t* inData = nullptr;
    if (jInputBuffer && length > 0) {
        auto* directAddress = static_cast<uint8_t*>(env->GetDirectBufferAddress(jInputBuffer));
        if (directAddress) {
            inData = directAddress + offset;
        }
    }

    auto* outAddress = static_cast<uint8_t*>(env->GetDirectBufferAddress(jOutputBuffer));
    if (!outAddress) {
        LOGE("Direct buffer address for audio output buffer is null");
        return -1;
    }

    return decoder->decode(inData, length, outAddress, outCapacity);
}

JNIEXPORT jint JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetAudioSampleRate(
        JNIEnv* /*env*/,
        jobject /* this */,
        jlong handle) {
    if (handle == 0L) return 0;
    auto* decoder = reinterpret_cast<nosved::FfmpegAudioDecoder*>(handle);
    return decoder->getSampleRate();
}

JNIEXPORT jint JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeGetAudioChannelCount(
        JNIEnv* /*env*/,
        jobject /* this */,
        jlong handle) {
    if (handle == 0L) return 0;
    auto* decoder = reinterpret_cast<nosved::FfmpegAudioDecoder*>(handle);
    return decoder->getChannelCount();
}

JNIEXPORT void JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeFlushAudioDecoder(
        JNIEnv* /*env*/,
        jobject /* this */,
        jlong handle) {
    if (handle == 0L) return;
    auto* decoder = reinterpret_cast<nosved::FfmpegAudioDecoder*>(handle);
    decoder->flush();
}

JNIEXPORT void JNICALL
Java_com_nosved_native_1codecs_NativeLib_nativeReleaseAudioDecoder(
        JNIEnv* /*env*/,
        jobject /* this */,
        jlong handle) {
    if (handle == 0L) return;
    auto* decoder = reinterpret_cast<nosved::FfmpegAudioDecoder*>(handle);
    delete decoder;
}

} // extern "C"
