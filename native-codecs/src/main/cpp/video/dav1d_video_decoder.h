#ifndef NOSVED_MEDIA_DAV1D_VIDEO_DECODER_H
#define NOSVED_MEDIA_DAV1D_VIDEO_DECODER_H

#include <string>
#include <mutex>
#include <android/native_window.h>
#include "video_frame.h"

extern "C" {
#include <dav1d/dav1d.h>
#include <libswscale/swscale.h>
#include <libavutil/pixfmt.h>
}

namespace nosved {

class Dav1dVideoDecoder {
public:
    Dav1dVideoDecoder();
    ~Dav1dVideoDecoder();

    Dav1dVideoDecoder(const Dav1dVideoDecoder&) = delete;
    Dav1dVideoDecoder& operator=(const Dav1dVideoDecoder&) = delete;

    bool open(int width = 0, int height = 0, int nThreads = 0);
    int decode(const uint8_t* packetData, int packetSize, int64_t ptsUs, bool isEos);
    bool getFrameInfo(VideoFrameInfo& outInfo);
    bool renderToSurface(ANativeWindow* window);
    void flush();
    void close();

    static const char* getVersion();

private:
    void releaseResources();
    void releasePicture();
    void releaseSws();

    std::mutex mutex_;
    Dav1dContext* context_ = nullptr;
    Dav1dPicture currentPicture_;
    bool hasPicture_ = false;

    SwsContext* swsContext_ = nullptr;
    int swsSrcWidth_ = 0;
    int swsSrcHeight_ = 0;
    int swsSrcBpc_ = 0;
};

} // namespace nosved

#endif // NOSVED_MEDIA_DAV1D_VIDEO_DECODER_H

