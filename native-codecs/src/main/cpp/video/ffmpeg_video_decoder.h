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
#include <libavutil/time.h>
#include <libavutil/pixdesc.h>
#include <libswscale/swscale.h>
}

namespace nosved {

constexpr int MAX_OUTPUT_BUFFERS = 16;

enum VideoDecodeResult {
    DECODE_OK = 0,
    DECODE_NEED_MORE_INPUT = 1,
    DECODE_END_OF_STREAM = 2,
    DECODE_BUFFER_FULL = 3,
    DECODE_ERROR_INVALID_STATE = -1,
    DECODE_ERROR_INVALID_EXTRADATA = -2,
    DECODE_ERROR_INIT_FAILED = -3,
    DECODE_ERROR_CORRUPTED_PACKET = -4,
    DECODE_ERROR_UNSUPPORTED_PIXEL_FORMAT = -5,
    DECODE_ERROR_NO_MEMORY = -6,
    DECODE_ERROR_FATAL = -7
};

enum class SlotState {
    FREE,
    READY,
    IN_USE
};

struct OutputSlot {
    AVFrame* frame = nullptr;
    VideoFrameInfo info = {};
    SlotState state = SlotState::FREE;
    uint32_t generation = 0;
};

class FfmpegVideoDecoder {
public:
    FfmpegVideoDecoder();
    ~FfmpegVideoDecoder();

    bool open(
        const std::string& mimeType,
        const uint8_t* extraData,
        int extraDataSize,
        int width,
        int height,
        int threadCount = 0,
        int threadType = 0,
        VideoScalingMode scalingMode = VideoScalingMode::BILINEAR,
        bool enableToneMapping = true
    );

    // Decoupled send/receive architecture
    int sendPacket(const uint8_t* packetData, int packetSize, int64_t ptsUs, int64_t dtsUs);
    int sendEos();
    int receiveFrame(int& outSlotIndex, VideoFrameInfo* outInfo);

    // Legacy unified entry point for backward compatibility
    int decode(int bufferIndex, const uint8_t* packetData, int packetSize, int64_t ptsUs, bool isEos);

    bool getFrameInfo(int slotIndex, VideoFrameInfo* outInfo);
    bool renderToSurface(int slotIndex, ANativeWindow* window);
    void releaseFrame(int slotIndex);

    void flush();
    void close();

    std::string getLastErrorMessage() const;

    static bool isMimeTypeSupported(const std::string& mimeType);

private:
    void releaseResources();
    int findFreeSlotLocked() const;
    static enum AVCodecID getCodecIdForMimeType(const std::string& mimeType);
    static std::vector<uint8_t> normalizeExtraData(enum AVCodecID codecId, const uint8_t* extraData, int extraDataSize);
    static void convertAvcOrHevcToAnnexB(uint8_t* data, int size);
    static void initToneMapLuts();
    static void applyToneMapping(ANativeWindow_Buffer& windowBuffer, int colorTransfer);

    mutable std::mutex codecMutex_;
    AVCodecContext* codecContext_ = nullptr;
    AVPacket* packet_ = nullptr;
    AVFrame* tempFrame_ = nullptr;
    enum AVCodecID codecId_ = AV_CODEC_ID_NONE;
    bool isEosSent_ = false;
    bool isEosReached_ = false;
    int64_t lastPts_ = 0;
    std::string lastErrorMessage_;
    VideoScalingMode scalingMode_ = VideoScalingMode::BILINEAR;
    bool enableToneMapping_ = true;

    // Explicit bounded buffer slot management
    mutable std::mutex renderMutex_;
    OutputSlot slots_[MAX_OUTPUT_BUFFERS];

    SwsContext* swsContext_ = nullptr;
    int cachedWidth_ = 0;
    int cachedHeight_ = 0;
    int cachedFormat_ = -1;
    int cachedColorSpace_ = -1;
    int cachedColorRange_ = -1;
};

} // namespace nosved

#endif // NOSVED_MEDIA_FFMPEG_VIDEO_DECODER_H
