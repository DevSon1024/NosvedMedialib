#ifndef NOSVED_MEDIA_FFMPEG_VIDEO_DECODER_H
#define NOSVED_MEDIA_FFMPEG_VIDEO_DECODER_H

#include <string>
#include <vector>
#include <deque>
#include <mutex>
#include <thread>
#include <algorithm>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include "video_frame.h"

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/imgutils.h>
#include <libswscale/swscale.h>
}

namespace nosved {

constexpr int MAX_OUTPUT_BUFFERS = 16;

class FfmpegVideoDecoder {
public:
    FfmpegVideoDecoder();
    ~FfmpegVideoDecoder();

    bool open(const std::string& mimeType, const uint8_t* extraData, int extraDataSize, int width, int height);

    // bufferIndex: 0 .. MAX_OUTPUT_BUFFERS-1
    int decode(int bufferIndex, const uint8_t* packetData, int packetSize, int64_t ptsUs, bool isEos);

    bool getFrameInfo(int bufferIndex, VideoFrameInfo* outInfo);
    bool renderToSurface(int bufferIndex, ANativeWindow* window);
    void releaseFrame(int bufferIndex);

    void flush();
    void close();

private:
    void releaseResources();
    void clearPendingFrames();
    static enum AVCodecID getCodecIdForMimeType(const std::string& mimeType);

    std::mutex codecMutex_;
    AVCodecContext* codecContext_ = nullptr;
    AVPacket* packet_ = nullptr;
    AVFrame* tempFrame_ = nullptr;
    std::deque<AVFrame*> pendingFrames_;
    enum AVCodecID codecId_ = AV_CODEC_ID_NONE;
    bool isEos_ = false;

    // Buffer-indexed frame storage (decoupled from decoding)
    std::mutex renderMutex_;
    AVFrame* outputFrames_[MAX_OUTPUT_BUFFERS] = { nullptr };
    VideoFrameInfo frameInfos_[MAX_OUTPUT_BUFFERS] = {};
    bool frameValid_[MAX_OUTPUT_BUFFERS] = { false };

    SwsContext* swsContext_ = nullptr;
    int cachedWidth_ = 0;
    int cachedHeight_ = 0;
    int cachedFormat_ = -1;
};

} // namespace nosved

#endif // NOSVED_MEDIA_FFMPEG_VIDEO_DECODER_H
