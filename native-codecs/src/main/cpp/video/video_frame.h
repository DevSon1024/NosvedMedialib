#ifndef NOSVED_MEDIA_VIDEO_FRAME_H
#define NOSVED_MEDIA_VIDEO_FRAME_H

#include <cstdint>
#include <memory>

extern "C" {
#include <libavutil/frame.h>
}

namespace nosved {

struct VideoFrameInfo {
    int width = 0;
    int height = 0;
    int64_t pts = 0;
    int format = -1;
    int bitDepth = 8;
    bool isKeyFrame = false;
};

} // namespace nosved

#endif // NOSVED_MEDIA_VIDEO_FRAME_H

