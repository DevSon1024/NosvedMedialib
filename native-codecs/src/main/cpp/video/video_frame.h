#ifndef NOSVED_MEDIA_VIDEO_FRAME_H
#define NOSVED_MEDIA_VIDEO_FRAME_H

#include <cstdint>
#include <memory>

extern "C" {
#include <libavutil/frame.h>
#include <libavutil/pixdesc.h>
}

namespace nosved {

enum class VideoScalingMode {
    FAST_BILINEAR = 0,
    BILINEAR = 1,
    BICUBIC = 2
};

struct VideoColorInfo {
    int colorSpace = 0;     // AVColorSpace
    int colorRange = 0;     // AVColorRange (0 = unspecified, 1 = limited/mpeg, 2 = full/jpeg)
    int colorPrimaries = 0; // AVColorPrimaries
    int colorTransfer = 0;  // AVColorTransferCharacteristic
    int bitDepth = 8;
    bool isHdr = false;
};

struct VideoFrameInfo {
    int width = 0;
    int height = 0;
    int64_t pts = 0;
    int format = -1;
    int bitDepth = 8;
    bool isKeyFrame = false;
    VideoColorInfo colorInfo = {};
};

} // namespace nosved

#endif // NOSVED_MEDIA_VIDEO_FRAME_H
