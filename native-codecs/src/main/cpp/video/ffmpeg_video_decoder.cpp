#include "ffmpeg_video_decoder.h"
#include "../common/logging.h"
#include <cstring>

namespace nosved {

FfmpegVideoDecoder::FfmpegVideoDecoder() = default;

FfmpegVideoDecoder::~FfmpegVideoDecoder() {
    close();
}

enum AVCodecID FfmpegVideoDecoder::getCodecIdForMimeType(const std::string& mimeType) {
    if (mimeType == "video/avc") return AV_CODEC_ID_H264;
    if (mimeType == "video/hevc") return AV_CODEC_ID_HEVC;
    if (mimeType == "video/x-vnd.on2.vp8") return AV_CODEC_ID_VP8;
    if (mimeType == "video/x-vnd.on2.vp9") return AV_CODEC_ID_VP9;
    if (mimeType == "video/mp4v-es") return AV_CODEC_ID_MPEG4;
    if (mimeType == "video/mpeg2") return AV_CODEC_ID_MPEG2VIDEO;
    if (mimeType == "video/wvc1") return AV_CODEC_ID_VC1;
    return AV_CODEC_ID_NONE;
}

bool FfmpegVideoDecoder::open(const std::string& mimeType, const uint8_t* extraData, int extraDataSize, int width, int height) {
    std::lock_guard<std::mutex> lock(mutex_);
    releaseResources();

    enum AVCodecID codecId = getCodecIdForMimeType(mimeType);
    if (codecId == AV_CODEC_ID_NONE) {
        LOGE("Unsupported video MIME type: %s", mimeType.c_str());
        return false;
    }

    const AVCodec* codec = avcodec_find_decoder(codecId);
    if (!codec) {
        LOGE("No decoder found for codec ID: %d", codecId);
        return false;
    }

    codecContext_ = avcodec_alloc_context3(codec);
    if (!codecContext_) {
        LOGE("Failed to allocate AVCodecContext");
        return false;
    }

    if (extraData && extraDataSize > 0) {
        codecContext_->extradata = static_cast<uint8_t*>(av_mallocz(extraDataSize + AV_INPUT_BUFFER_PADDING_SIZE));
        if (codecContext_->extradata) {
            std::memcpy(codecContext_->extradata, extraData, extraDataSize);
            codecContext_->extradata_size = extraDataSize;
        }
    }

    if (width > 0) codecContext_->width = width;
    if (height > 0) codecContext_->height = height;

    // Enable multithreaded decoding automatically based on available cores
    codecContext_->thread_count = 0;

    int ret = avcodec_open2(codecContext_, codec, nullptr);
    if (ret < 0) {
        char errBuf[AV_ERROR_MAX_STRING_SIZE] = {0};
        av_strerror(ret, errBuf, sizeof(errBuf));
        LOGE("avcodec_open2 failed: %s (%d)", errBuf, ret);
        releaseResources();
        return false;
    }

    packet_ = av_packet_alloc();
    frame_ = av_frame_alloc();

    if (!packet_ || !frame_) {
        LOGE("Failed to allocate AVPacket or AVFrame");
        releaseResources();
        return false;
    }

    LOGI("FfmpegVideoDecoder opened successfully for %s (%s)", mimeType.c_str(), codec->name);
    return true;
}

int FfmpegVideoDecoder::decode(const uint8_t* packetData, int packetSize, int64_t ptsUs, bool isEos) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!codecContext_ || !packet_ || !frame_) {
        return AVERROR(EINVAL);
    }

    if (isEos) {
        int ret = avcodec_send_packet(codecContext_, nullptr);
        if (ret < 0 && ret != AVERROR_EOF) {
            return ret;
        }
    } else if (packetData && packetSize > 0) {
        av_packet_unref(packet_);
        packet_->data = const_cast<uint8_t*>(packetData);
        packet_->size = packetSize;
        packet_->pts = ptsUs;
        packet_->dts = ptsUs;

        int ret = avcodec_send_packet(codecContext_, packet_);
        if (ret < 0 && ret != AVERROR(EAGAIN)) {
            return ret;
        }
    }

    int rec = avcodec_receive_frame(codecContext_, frame_);
    if (rec == 0) {
        latestFrameInfo_.width = frame_->width;
        latestFrameInfo_.height = frame_->height;
        latestFrameInfo_.pts = frame_->pts;
        latestFrameInfo_.format = frame_->format;
        latestFrameInfo_.isKeyFrame = (frame_->flags & AV_FRAME_FLAG_KEY) != 0;
        hasLatestFrame_ = true;
        return 0; // Frame ready
    } else if (rec == AVERROR(EAGAIN)) {
        return 1; // Needs more packets
    } else if (rec == AVERROR_EOF) {
        isEos_ = true;
        return 2; // End of stream
    }

    return rec; // Decode error
}

bool FfmpegVideoDecoder::getLatestFrameInfo(VideoFrameInfo* outInfo) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!outInfo || !hasLatestFrame_) {
        return false;
    }
    *outInfo = latestFrameInfo_;
    return true;
}

bool FfmpegVideoDecoder::renderToSurface(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!window || !hasLatestFrame_ || !frame_ || frame_->width <= 0 || frame_->height <= 0) {
        return false;
    }

    int frameW = frame_->width;
    int frameH = frame_->height;
    enum AVPixelFormat srcFormat = static_cast<enum AVPixelFormat>(frame_->format);

    int setGeomRet = ANativeWindow_setBuffersGeometry(window, frameW, frameH, WINDOW_FORMAT_RGBA_8888);
    if (setGeomRet < 0) {
        LOGE("ANativeWindow_setBuffersGeometry failed: %d", setGeomRet);
        return false;
    }

    ANativeWindow_Buffer windowBuffer;
    if (ANativeWindow_lock(window, &windowBuffer, nullptr) < 0) {
        LOGE("ANativeWindow_lock failed");
        return false;
    }

    swsContext_ = sws_getCachedContext(
        swsContext_,
        frameW, frameH, srcFormat,
        frameW, frameH, AV_PIX_FMT_RGBA,
        SWS_FAST_BILINEAR,
        nullptr, nullptr, nullptr
    );

    if (!swsContext_) {
        LOGE("sws_getCachedContext failed to create scaler");
        ANativeWindow_unlockAndPost(window);
        return false;
    }

    uint8_t* dstData[4] = { static_cast<uint8_t*>(windowBuffer.bits), nullptr, nullptr, nullptr };
    int dstLinesize[4] = { windowBuffer.stride * 4, 0, 0, 0 };

    sws_scale(swsContext_, frame_->data, frame_->linesize, 0, frameH, dstData, dstLinesize);

    ANativeWindow_unlockAndPost(window);
    return true;
}

void FfmpegVideoDecoder::flush() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (codecContext_) {
        avcodec_flush_buffers(codecContext_);
    }
    if (packet_) {
        av_packet_unref(packet_);
    }
    if (frame_) {
        av_frame_unref(frame_);
    }
    hasLatestFrame_ = false;
    isEos_ = false;
}

void FfmpegVideoDecoder::releaseResources() {
    if (swsContext_) {
        sws_freeContext(swsContext_);
        swsContext_ = nullptr;
    }
    if (frame_) {
        av_frame_free(&frame_);
        frame_ = nullptr;
    }
    if (packet_) {
        av_packet_free(&packet_);
        packet_ = nullptr;
    }
    if (codecContext_) {
        avcodec_free_context(&codecContext_);
        codecContext_ = nullptr;
    }
    hasLatestFrame_ = false;
    isEos_ = false;
}

void FfmpegVideoDecoder::close() {
    std::lock_guard<std::mutex> lock(mutex_);
    releaseResources();
}

} // namespace nosved

