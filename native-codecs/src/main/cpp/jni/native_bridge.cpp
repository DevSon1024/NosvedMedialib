#include <jni.h>
#include <string>
#include "../core/ffmpeg_init.h"
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
Java_com_devson_native_1codecs_NativeLib_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string info = "NosvedMedia FFmpeg: " + nosved::FfmpegInit::getInstance().getVersion();
    return env->NewStringUTF(info.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_devson_native_1codecs_NativeLib_nativeInit(
        JNIEnv* /*env*/,
        jobject /* this */) {
    return static_cast<jboolean>(nosved::FfmpegInit::getInstance().initialize(g_vm));
}

JNIEXPORT jboolean JNICALL
Java_com_devson_native_1codecs_NativeLib_nativeIsInitialized(
        JNIEnv* /*env*/,
        jobject /* this */) {
    return static_cast<jboolean>(nosved::FfmpegInit::getInstance().isInitialized());
}

JNIEXPORT jstring JNICALL
Java_com_devson_native_1codecs_NativeLib_nativeGetFFmpegVersion(
        JNIEnv* env,
        jobject /* this */) {
    std::string ver = nosved::FfmpegInit::getInstance().getVersion();
    return env->NewStringUTF(ver.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_devson_native_1codecs_NativeLib_nativeGetFFmpegLicense(
        JNIEnv* env,
        jobject /* this */) {
    std::string lic = nosved::FfmpegInit::getInstance().getLicense();
    return env->NewStringUTF(lic.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_devson_native_1codecs_NativeLib_nativeGetFFmpegConfiguration(
        JNIEnv* env,
        jobject /* this */) {
    std::string config = nosved::FfmpegInit::getInstance().getConfiguration();
    return env->NewStringUTF(config.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_devson_native_1codecs_NativeLib_nativeGetErrorDescription(
        JNIEnv* env,
        jobject /* this */,
        jint errorCode) {
    std::string desc = nosved::FfmpegInit::getInstance().getErrorDescription(errorCode);
    return env->NewStringUTF(desc.c_str());
}

JNIEXPORT jint JNICALL
Java_com_devson_native_1codecs_NativeLib_nativeGetCodecVersion(
        JNIEnv* /*env*/,
        jobject /* this */) {
    return static_cast<jint>(nosved::FfmpegInit::getInstance().getCodecVersion());
}

JNIEXPORT jint JNICALL
Java_com_devson_native_1codecs_NativeLib_nativeGetFormatVersion(
        JNIEnv* /*env*/,
        jobject /* this */) {
    return static_cast<jint>(nosved::FfmpegInit::getInstance().getFormatVersion());
}

JNIEXPORT jint JNICALL
Java_com_devson_native_1codecs_NativeLib_nativeGetUtilVersion(
        JNIEnv* /*env*/,
        jobject /* this */) {
    return static_cast<jint>(nosved::FfmpegInit::getInstance().getUtilVersion());
}

} // extern "C"

