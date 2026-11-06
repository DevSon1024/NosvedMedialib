#ifndef NOSVED_MEDIA_DAV1D_VIDEO_DECODER_H
#define NOSVED_MEDIA_DAV1D_VIDEO_DECODER_H

#include <string>
#include <mutex>
#include <thread>
#include <algorithm>
#include <android/native_window.h>
#include "video_frame.h"

extern "C" {
#include <dav1d/dav1d.h>
#include <libswscale/swscale.h>
#include <libavutil/pixfmt.h>
}

namespace nosved {

constexpr int MAX_DAV1D_OUTPUT_BUFFERS = 16;

class Dav1dVideoDecoder {
public:
    Dav1dVideoDecoder();
    ~Dav1dVideoDecoder();

    Dav1dVideoDecoder(const Dav1dVideoDecoder&) = delete;
    Dav1dVideoDecoder& operator=(const Dav1dVideoDecoder&) = delete;

    bool open(
        int width = 0,
        int height = 0,
        int nThreads = 0,
        VideoScalingMode scalingMode = VideoScalingMode::BILINEAR,
        bool enableToneMapping = true
    );
    int decode(int bufferIndex, const uint8_t* packetData, int packetSize, int64_t ptsUs, bool isEos);
    bool getFrameInfo(int bufferIndex, VideoFrameInfo& outInfo);
    bool renderToSurface(int bufferIndex, ANativeWindow* window);
    void releaseFrame(int bufferIndex);
    void flush();
    void close();

    static const char* getVersion();

private:
    void releaseResources();

    std::mutex codecMutex_;
    Dav1dContext* context_ = nullptr;

    std::mutex renderMutex_;
    Dav1dPicture outputPictures_[MAX_DAV1D_OUTPUT_BUFFERS];
    bool pictureValid_[MAX_DAV1D_OUTPUT_BUFFERS] = { false };
    VideoFrameInfo frameInfos_[MAX_DAV1D_OUTPUT_BUFFERS] = {};

    SwsContext* swsContext_ = nullptr;
    int cachedWidth_ = 0;
    int cachedHeight_ = 0;
    int cachedFormat_ = -1;
    VideoScalingMode scalingMode_ = VideoScalingMode::BILINEAR;
    bool enableToneMapping_ = true;
    static void applyToneMapping(ANativeWindow_Buffer& windowBuffer, int colorTransfer);
};

} // namespace nosved

#endif // NOSVED_MEDIA_DAV1D_VIDEO_DECODER_H
