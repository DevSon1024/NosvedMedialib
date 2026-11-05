#include "ffmpeg_video_decoder.h"
#include "../common/logging.h"
#include <cstring>
#include <algorithm>
#include <thread>

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

static void convertAvcOrHevcToAnnexB(uint8_t* data, int size) {
    if (!data || size < 4) return;

    // Check if already 4-byte Annex B
    if (data[0] == 0 && data[1] == 0 && data[2] == 0 && data[3] == 1) {
        return;
    }

    // Check if length-prefixed (AVCC/HVCC format)
    int offset = 0;
    bool isLengthPrefixed = false;
    while (offset + 4 <= size) {
        uint32_t nalLen = (static_cast<uint32_t>(data[offset]) << 24) |
                          (static_cast<uint32_t>(data[offset + 1]) << 16) |
                          (static_cast<uint32_t>(data[offset + 2]) << 8) |
                          static_cast<uint32_t>(data[offset + 3]);

        if (nalLen == 0 || offset + 4 + nalLen > static_cast<uint32_t>(size)) {
            break;
        }

        offset += 4 + nalLen;
        if (offset == size || (offset <= size && offset >= size - 4)) {
            isLengthPrefixed = true;
            break;
        }
    }

    if (!isLengthPrefixed) {
        uint32_t firstLen = (static_cast<uint32_t>(data[0]) << 24) |
                            (static_cast<uint32_t>(data[1]) << 16) |
                            (static_cast<uint32_t>(data[2]) << 8) |
                            static_cast<uint32_t>(data[3]);
        if (firstLen > 4 && firstLen <= static_cast<uint32_t>(size - 4)) {
            isLengthPrefixed = true;
        }
    }

    if (isLengthPrefixed) {
        offset = 0;
        while (offset + 4 <= size) {
            uint32_t nalLen = (static_cast<uint32_t>(data[offset]) << 24) |
                              (static_cast<uint32_t>(data[offset + 1]) << 16) |
                              (static_cast<uint32_t>(data[offset + 2]) << 8) |
                              static_cast<uint32_t>(data[offset + 3]);

            if (nalLen == 0 || offset + 4 + nalLen > static_cast<uint32_t>(size)) {
                break;
            }

            data[offset] = 0x00;
            data[offset + 1] = 0x00;
            data[offset + 2] = 0x00;
            data[offset + 3] = 0x01;

            offset += 4 + nalLen;
        }
    }
}

bool FfmpegVideoDecoder::open(const std::string& mimeType, const uint8_t* extraData, int extraDataSize, int width, int height) {
    releaseResources();

    std::lock_guard<std::mutex> codecLock(codecMutex_);

    enum AVCodecID codecId = getCodecIdForMimeType(mimeType);
    if (codecId == AV_CODEC_ID_NONE) {
        LOGE("Unsupported video MIME type: %s", mimeType.c_str());
        return false;
    }

    codecId_ = codecId;

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

    // Multi-threaded decoding: utilize available cores with frame & slice parallelization, zero pipeline delay
    int cpuCores = static_cast<int>(std::thread::hardware_concurrency());
    codecContext_->thread_count = (cpuCores > 0) ? std::min(4, std::max(2, cpuCores)) : 4;
    codecContext_->thread_type = FF_THREAD_FRAME | FF_THREAD_SLICE;
    codecContext_->delay = 0;
    codecContext_->flags |= AV_CODEC_FLAG_LOW_DELAY;
    codecContext_->flags2 |= AV_CODEC_FLAG2_FAST;

    int ret = avcodec_open2(codecContext_, codec, nullptr);
    if (ret < 0) {
        char errBuf[AV_ERROR_MAX_STRING_SIZE] = {0};
        av_strerror(ret, errBuf, sizeof(errBuf));
        LOGE("avcodec_open2 failed: %s (%d)", errBuf, ret);
        releaseResources();
        return false;
    }

    packet_ = av_packet_alloc();
    tempFrame_ = av_frame_alloc();

    if (!packet_ || !tempFrame_) {
        LOGE("Failed to allocate AVPacket or temp AVFrame");
        releaseResources();
        return false;
    }

    LOGI("FfmpegVideoDecoder opened successfully for %s (%s, threads=%d)", mimeType.c_str(), codec->name, codecContext_->thread_count);
    return true;
}

int FfmpegVideoDecoder::decode(int bufferIndex, const uint8_t* packetData, int packetSize, int64_t ptsUs, bool isEos) {
    std::lock_guard<std::mutex> codecLock(codecMutex_);
    if (!codecContext_ || !packet_ || !tempFrame_) {
        return AVERROR(EINVAL);
    }

    int idx = (bufferIndex >= 0 && bufferIndex < MAX_OUTPUT_BUFFERS) ? bufferIndex : (std::abs(bufferIndex) % MAX_OUTPUT_BUFFERS);

    if (isEos) {
        int ret = avcodec_send_packet(codecContext_, nullptr);
        if (ret < 0 && ret != AVERROR_EOF) {
            return ret;
        }
    } else if (packetData && packetSize > 0) {
        av_packet_unref(packet_);
        int allocRet = av_new_packet(packet_, packetSize);
        if (allocRet < 0) {
            LOGE("av_new_packet failed: %d", allocRet);
            return allocRet;
        }
        std::memcpy(packet_->data, packetData, packetSize);

        if (codecId_ == AV_CODEC_ID_H264 || codecId_ == AV_CODEC_ID_HEVC) {
            convertAvcOrHevcToAnnexB(packet_->data, packetSize);
        }

        packet_->pts = ptsUs;
        packet_->dts = ptsUs;

        int ret = avcodec_send_packet(codecContext_, packet_);
        if (ret < 0 && ret != AVERROR(EAGAIN)) {
            return ret;
        }
    }

    int rec = avcodec_receive_frame(codecContext_, tempFrame_);
    if (rec == 0) {
        std::lock_guard<std::mutex> renderLock(renderMutex_);
        if (!outputFrames_[idx]) {
            outputFrames_[idx] = av_frame_alloc();
        } else {
            av_frame_unref(outputFrames_[idx]);
        }

        av_frame_move_ref(outputFrames_[idx], tempFrame_);

        frameInfos_[idx].width = outputFrames_[idx]->width;
        frameInfos_[idx].height = outputFrames_[idx]->height;
        frameInfos_[idx].pts = (outputFrames_[idx]->pts != AV_NOPTS_VALUE) ? outputFrames_[idx]->pts : ptsUs;
        frameInfos_[idx].format = outputFrames_[idx]->format;
        frameInfos_[idx].isKeyFrame = (outputFrames_[idx]->flags & AV_FRAME_FLAG_KEY) != 0;
        frameValid_[idx] = true;

        return 0; // Frame ready
    } else if (rec == AVERROR(EAGAIN)) {
        return 1; // Needs more packets
    } else if (rec == AVERROR_EOF) {
        isEos_ = true;
        return 2; // End of stream
    }

    return rec; // Decode error
}

bool FfmpegVideoDecoder::getFrameInfo(int bufferIndex, VideoFrameInfo* outInfo) {
    if (!outInfo) return false;
    std::lock_guard<std::mutex> renderLock(renderMutex_);
    int idx = (bufferIndex >= 0 && bufferIndex < MAX_OUTPUT_BUFFERS) ? bufferIndex : (std::abs(bufferIndex) % MAX_OUTPUT_BUFFERS);
    if (!frameValid_[idx]) {
        return false;
    }
    *outInfo = frameInfos_[idx];
    return true;
}

bool FfmpegVideoDecoder::renderToSurface(int bufferIndex, ANativeWindow* window) {
    if (!window) return false;

    std::lock_guard<std::mutex> renderLock(renderMutex_);
    int idx = (bufferIndex >= 0 && bufferIndex < MAX_OUTPUT_BUFFERS) ? bufferIndex : (std::abs(bufferIndex) % MAX_OUTPUT_BUFFERS);
    AVFrame* frame = outputFrames_[idx];

    if (!frameValid_[idx] || !frame || frame->width <= 0 || frame->height <= 0) {
        return false;
    }

    int frameW = frame->width;
    int frameH = frame->height;
    enum AVPixelFormat srcFormat = static_cast<enum AVPixelFormat>(frame->format);

    // Set buffer geometry only when dimensions change to avoid SurfaceFlinger buffer reallocations
    if (cachedWidth_ != frameW || cachedHeight_ != frameH || cachedFormat_ != WINDOW_FORMAT_RGBA_8888) {
        int setGeomRet = ANativeWindow_setBuffersGeometry(window, frameW, frameH, WINDOW_FORMAT_RGBA_8888);
        if (setGeomRet >= 0) {
            cachedWidth_ = frameW;
            cachedHeight_ = frameH;
            cachedFormat_ = WINDOW_FORMAT_RGBA_8888;
        } else {
            LOGE("ANativeWindow_setBuffersGeometry failed: %d", setGeomRet);
            return false;
        }
    }

    ANativeWindow_Buffer windowBuffer;
    if (ANativeWindow_lock(window, &windowBuffer, nullptr) < 0) {
        LOGE("ANativeWindow_lock failed");
        return false;
    }

    swsContext_ = sws_getCachedContext(
        swsContext_,
        frameW, frameH, srcFormat,
        windowBuffer.width, windowBuffer.height, AV_PIX_FMT_RGBA,
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

    sws_scale(swsContext_, frame->data, frame->linesize, 0, frameH, dstData, dstLinesize);

    ANativeWindow_unlockAndPost(window);
    return true;
}

void FfmpegVideoDecoder::releaseFrame(int bufferIndex) {
    std::lock_guard<std::mutex> renderLock(renderMutex_);
    int idx = (bufferIndex >= 0 && bufferIndex < MAX_OUTPUT_BUFFERS) ? bufferIndex : (std::abs(bufferIndex) % MAX_OUTPUT_BUFFERS);
    if (outputFrames_[idx]) {
        av_frame_unref(outputFrames_[idx]);
    }
    frameValid_[idx] = false;
}

void FfmpegVideoDecoder::flush() {
    {
        std::lock_guard<std::mutex> codecLock(codecMutex_);
        if (codecContext_) {
            avcodec_flush_buffers(codecContext_);
        }
        if (tempFrame_) {
            av_frame_unref(tempFrame_);
        }
        isEos_ = false;
    }
    {
        std::lock_guard<std::mutex> renderLock(renderMutex_);
        for (int i = 0; i < MAX_OUTPUT_BUFFERS; ++i) {
            if (outputFrames_[i]) {
                av_frame_unref(outputFrames_[i]);
            }
            frameValid_[i] = false;
        }
    }
}

void FfmpegVideoDecoder::releaseResources() {
    {
        std::lock_guard<std::mutex> codecLock(codecMutex_);
        if (tempFrame_) {
            av_frame_free(&tempFrame_);
            tempFrame_ = nullptr;
        }
        if (packet_) {
            av_packet_free(&packet_);
            packet_ = nullptr;
        }
        if (codecContext_) {
            avcodec_free_context(&codecContext_);
            codecContext_ = nullptr;
        }
        codecId_ = AV_CODEC_ID_NONE;
        isEos_ = false;
    }
    {
        std::lock_guard<std::mutex> renderLock(renderMutex_);
        for (int i = 0; i < MAX_OUTPUT_BUFFERS; ++i) {
            if (outputFrames_[i]) {
                av_frame_free(&outputFrames_[i]);
                outputFrames_[i] = nullptr;
            }
            frameValid_[i] = false;
        }
        if (swsContext_) {
            sws_freeContext(swsContext_);
            swsContext_ = nullptr;
        }
        cachedWidth_ = 0;
        cachedHeight_ = 0;
        cachedFormat_ = -1;
    }
}

void FfmpegVideoDecoder::close() {
    releaseResources();
}

} // namespace nosved
