#ifndef NOSVED_MEDIA_FFMPEG_VIDEO_DECODER_H
#define NOSVED_MEDIA_FFMPEG_VIDEO_DECODER_H

#include <string>
#include <vector>
#include <mutex>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include "video_frame.h"

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/imgutils.h>
#include <libswscale/swscale.h>
}

namespace nosved {

class FfmpegVideoDecoder {
public:
    FfmpegVideoDecoder();
    ~FfmpegVideoDecoder();

    bool open(const std::string& mimeType, const uint8_t* extraData, int extraDataSize, int width, int height);

    // Returns:
    // 0 = frame produced
    // 1 = input consumed, no frame ready (need more packets)
    // 2 = end of stream reached
    // < 0 = error code (FFmpeg AVERROR)
    int decode(const uint8_t* packetData, int packetSize, int64_t ptsUs, bool isEos);

    bool getLatestFrameInfo(VideoFrameInfo* outInfo);
    bool renderToSurface(ANativeWindow* window);

    void flush();
    void close();

private:
    void releaseResources();
    static enum AVCodecID getCodecIdForMimeType(const std::string& mimeType);

    std::mutex mutex_;
    AVCodecContext* codecContext_ = nullptr;
    AVPacket* packet_ = nullptr;
    AVFrame* frame_ = nullptr;
    SwsContext* swsContext_ = nullptr;
    enum AVCodecID codecId_ = AV_CODEC_ID_NONE;

    int cachedSwsWidth_ = 0;
    int cachedSwsHeight_ = 0;
    int cachedSwsFormat_ = -1;

    VideoFrameInfo latestFrameInfo_;
    bool hasLatestFrame_ = false;
    bool isEos_ = false;
};

} // namespace nosved

#endif // NOSVED_MEDIA_FFMPEG_VIDEO_DECODER_H

