#include "ffmpeg_init.h"
#include "../common/logging.h"

namespace nosved {

FfmpegInit::FfmpegInit() : initialized_(false) {}

FfmpegInit& FfmpegInit::getInstance() {
    static FfmpegInit instance;
    return instance;
}

bool FfmpegInit::initialize(JavaVM* vm) {
    if (initialized_.load()) {
        LOGD("FfmpegInit: already initialized");
        return true;
    }

    if (vm != nullptr) {
        int result = av_jni_set_java_vm(vm, nullptr);
        if (result < 0) {
            LOGW("av_jni_set_java_vm failed with code %d", result);
        } else {
            LOGI("av_jni_set_java_vm registered successfully");
        }
    }

    av_log_set_level(AV_LOG_INFO);

    initialized_.store(true);
    LOGI("NosvedMedia FFmpeg initialized successfully. Version: %s", av_version_info());
    return true;
}

bool FfmpegInit::isInitialized() const {
    return initialized_.load();
}

std::string FfmpegInit::getVersion() const {
    const char* version = av_version_info();
    return version ? std::string(version) : std::string("unknown");
}

std::string FfmpegInit::getLicense() const {
    const char* license = avutil_license();
    return license ? std::string(license) : std::string("unknown");
}

std::string FfmpegInit::getConfiguration() const {
    const char* config = avutil_configuration();
    return config ? std::string(config) : std::string("");
}

std::string FfmpegInit::getErrorDescription(int errorCode) const {
    char errBuf[AV_ERROR_MAX_STRING_SIZE] = {0};
    if (av_strerror(errorCode, errBuf, sizeof(errBuf)) == 0) {
        return std::string(errBuf);
    }
    return "Unknown FFmpeg error (" + std::to_string(errorCode) + ")";
}

unsigned int FfmpegInit::getCodecVersion() const {
    return avcodec_version();
}

unsigned int FfmpegInit::getFormatVersion() const {
    return avformat_version();
}

unsigned int FfmpegInit::getUtilVersion() const {
    return avutil_version();
}

} // namespace nosved

