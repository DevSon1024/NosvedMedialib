#ifndef NOSVED_MEDIA_FFMPEG_INIT_H
#define NOSVED_MEDIA_FFMPEG_INIT_H

#include <string>
#include <jni.h>
#include <atomic>

extern "C" {
#include <libavutil/avutil.h>
#include <libavutil/ffversion.h>
#include <libavutil/error.h>
#include <libavcodec/avcodec.h>
#include <libavcodec/jni.h>
#include <libavformat/avformat.h>
}

namespace nosved {

class FfmpegInit {
public:
    static FfmpegInit& getInstance();

    bool initialize(JavaVM* vm);
    bool isInitialized() const;

    std::string getVersion() const;
    std::string getLicense() const;
    std::string getConfiguration() const;
    std::string getErrorDescription(int errorCode) const;

    unsigned int getCodecVersion() const;
    unsigned int getFormatVersion() const;
    unsigned int getUtilVersion() const;

private:
    FfmpegInit();
    ~FfmpegInit() = default;
    FfmpegInit(const FfmpegInit&) = delete;
    FfmpegInit& operator=(const FfmpegInit&) = delete;

    std::atomic<bool> initialized_{false};
};

} // namespace nosved

#endif // NOSVED_MEDIA_FFMPEG_INIT_H

