#include "ffmpeg_video_decoder.h"
#include "../common/logging.h"
#include <cstring>
#include <algorithm>
#include <thread>
#include <cmath>

namespace nosved {

static uint8_t s_pqToSdrLut[256];
static uint8_t s_hlgToSdrLut[256];
static std::once_flag s_lutInitOnce;

void FfmpegVideoDecoder::initToneMapLuts() {
    std::call_once(s_lutInitOnce, []() {
        for (int i = 0; i < 256; ++i) {
            float v = i / 255.0f;
            float mapped = std::pow(v, 0.65f) * 1.35f;
            if (mapped > 0.85f) {
                float over = mapped - 0.85f;
                mapped = 0.85f + (over / (1.0f + over * 1.5f)) * 0.15f;
            }
            int val = static_cast<int>(mapped * 255.0f + 0.5f);
            s_pqToSdrLut[i] = static_cast<uint8_t>(std::min(255, std::max(0, val)));
        }

        for (int i = 0; i < 256; ++i) {
            float v = i / 255.0f;
            float mapped = std::pow(v, 0.82f) * 1.18f;
            if (mapped > 0.90f) {
                float over = mapped - 0.90f;
                mapped = 0.90f + (over / (1.0f + over * 1.2f)) * 0.10f;
            }
            int val = static_cast<int>(mapped * 255.0f + 0.5f);
            s_hlgToSdrLut[i] = static_cast<uint8_t>(std::min(255, std::max(0, val)));
        }
    });
}

void FfmpegVideoDecoder::applyToneMapping(ANativeWindow_Buffer& windowBuffer, int colorTransfer) {
    initToneMapLuts();
    const uint8_t* lut = (colorTransfer == AVCOL_TRC_ARIB_STD_B67) ? s_hlgToSdrLut : s_pqToSdrLut;

    uint8_t* row = static_cast<uint8_t*>(windowBuffer.bits);
    const int width = windowBuffer.width;
    const int height = windowBuffer.height;
    const int strideBytes = windowBuffer.stride * 4;

    for (int y = 0; y < height; ++y) {
        uint8_t* px = row;
        for (int x = 0; x < width; ++x) {
            px[0] = lut[px[0]];
            px[1] = lut[px[1]];
            px[2] = lut[px[2]];
            px += 4;
        }
        row += strideBytes;
    }
}

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

std::vector<uint8_t> FfmpegVideoDecoder::normalizeExtraData(enum AVCodecID codecId, const uint8_t* extraData, int extraDataSize) {
    if (!extraData || extraDataSize <= 0) {
        return {};
    }

    // If already Annex B start code (0x00000001 or 0x000001), use directly
    if (extraDataSize >= 4 && extraData[0] == 0 && extraData[1] == 0 &&
        (extraData[2] == 1 || (extraData[2] == 0 && extraData[3] == 1))) {
        return std::vector<uint8_t>(extraData, extraData + extraDataSize);
    }

    if (codecId == AV_CODEC_ID_H264) {
        // avcC format: starts with configurationVersion == 1 and at least 7 bytes
        if (extraDataSize >= 7 && extraData[0] == 1) {
            std::vector<uint8_t> out;
            int numSps = extraData[5] & 0x1F;
            size_t offset = 6;
            bool valid = true;

            for (int i = 0; i < numSps && offset + 2 <= static_cast<size_t>(extraDataSize); ++i) {
                uint16_t spsLen = (static_cast<uint16_t>(extraData[offset]) << 8) | extraData[offset + 1];
                offset += 2;
                if (offset + spsLen > static_cast<size_t>(extraDataSize)) {
                    valid = false;
                    break;
                }
                out.push_back(0);
                out.push_back(0);
                out.push_back(0);
                out.push_back(1);
                out.insert(out.end(), extraData + offset, extraData + offset + spsLen);
                offset += spsLen;
            }

            if (valid && offset < static_cast<size_t>(extraDataSize)) {
                int numPps = extraData[offset++];
                for (int i = 0; i < numPps && offset + 2 <= static_cast<size_t>(extraDataSize); ++i) {
                    uint16_t ppsLen = (static_cast<uint16_t>(extraData[offset]) << 8) | extraData[offset + 1];
                    offset += 2;
                    if (offset + ppsLen > static_cast<size_t>(extraDataSize)) {
                        valid = false;
                        break;
                    }
                    out.push_back(0);
                    out.push_back(0);
                    out.push_back(0);
                    out.push_back(1);
                    out.insert(out.end(), extraData + offset, extraData + offset + ppsLen);
                    offset += ppsLen;
                }
            }

            if (valid && !out.empty()) {
                return out;
            }
        }
    } else if (codecId == AV_CODEC_ID_HEVC) {
        // hvcC format: starts with configurationVersion == 1 and at least 23 bytes
        if (extraDataSize >= 23 && extraData[0] == 1) {
            std::vector<uint8_t> out;
            size_t offset = 22;
            int numArrays = extraData[offset++];
            bool valid = true;

            for (int i = 0; i < numArrays && offset + 3 <= static_cast<size_t>(extraDataSize); ++i) {
                offset++; // skip array_completeness and NAL unit type
                uint16_t numNalus = (static_cast<uint16_t>(extraData[offset]) << 8) | extraData[offset + 1];
                offset += 2;

                for (int j = 0; j < numNalus && offset + 2 <= static_cast<size_t>(extraDataSize); ++j) {
                    uint16_t naluLen = (static_cast<uint16_t>(extraData[offset]) << 8) | extraData[offset + 1];
                    offset += 2;
                    if (offset + naluLen > static_cast<size_t>(extraDataSize)) {
                        valid = false;
                        break;
                    }
                    out.push_back(0);
                    out.push_back(0);
                    out.push_back(0);
                    out.push_back(1);
                    out.insert(out.end(), extraData + offset, extraData + offset + naluLen);
                    offset += naluLen;
                }
            }

            if (valid && !out.empty()) {
                return out;
            }
        }
    }

    return std::vector<uint8_t>(extraData, extraData + extraDataSize);
}

void FfmpegVideoDecoder::convertAvcOrHevcToAnnexB(uint8_t* data, int size) {
    if (!data || size < 4) return;

    // Check if already Annex B start code
    if (data[0] == 0 && data[1] == 0 && (data[2] == 1 || (data[2] == 0 && data[3] == 1))) {
        return;
    }

    int offset = 0;
    while (offset + 4 <= size) {
        uint32_t nalLen = (static_cast<uint32_t>(data[offset]) << 24) |
                          (static_cast<uint32_t>(data[offset + 1]) << 16) |
                          (static_cast<uint32_t>(data[offset + 2]) << 8) |
                          static_cast<uint32_t>(data[offset + 3]);

        if (nalLen > 0 && offset + 4 + nalLen <= static_cast<uint32_t>(size)) {
            data[offset] = 0;
            data[offset + 1] = 0;
            data[offset + 2] = 0;
            data[offset + 3] = 1;
            offset += 4 + nalLen;
        } else {
            break;
        }
    }
}

bool FfmpegVideoDecoder::open(
    const std::string& mimeType,
    const uint8_t* extraData,
    int extraDataSize,
    int width,
    int height,
    int threadCount,
    VideoScalingMode scalingMode,
    bool enableToneMapping
) {
    releaseResources();

    scalingMode_ = scalingMode;
    enableToneMapping_ = enableToneMapping;

    std::lock_guard<std::mutex> codecLock(codecMutex_);

    enum AVCodecID codecId = getCodecIdForMimeType(mimeType);
    if (codecId == AV_CODEC_ID_NONE) {
        lastErrorMessage_ = "Unsupported video MIME type: " + mimeType;
        LOGE("%s", lastErrorMessage_.c_str());
        return false;
    }

    codecId_ = codecId;

    const AVCodec* codec = avcodec_find_decoder(codecId);
    if (!codec) {
        lastErrorMessage_ = "No decoder found for codec ID: " + std::to_string(codecId);
        LOGE("%s", lastErrorMessage_.c_str());
        return false;
    }

    codecContext_ = avcodec_alloc_context3(codec);
    if (!codecContext_) {
        lastErrorMessage_ = "Failed to allocate AVCodecContext";
        LOGE("%s", lastErrorMessage_.c_str());
        return false;
    }

    if (extraData && extraDataSize > 0) {
        std::vector<uint8_t> normalizedExtra = normalizeExtraData(codecId, extraData, extraDataSize);
        if (!normalizedExtra.empty()) {
            codecContext_->extradata = static_cast<uint8_t*>(av_mallocz(normalizedExtra.size() + AV_INPUT_BUFFER_PADDING_SIZE));
            if (codecContext_->extradata) {
                std::memcpy(codecContext_->extradata, normalizedExtra.data(), normalizedExtra.size());
                codecContext_->extradata_size = static_cast<int>(normalizedExtra.size());
            }
        }
    }

    if (width > 0) codecContext_->width = width;
    if (height > 0) codecContext_->height = height;

    // Use microsecond timebase matching Media3 timeUs
    codecContext_->pkt_timebase = {1, 1000000};
    codecContext_->time_base = {1, 1000000};

    // Multi-threaded decoding
    if (threadCount > 0) {
        codecContext_->thread_count = threadCount;
    } else {
        int cpuCores = static_cast<int>(std::thread::hardware_concurrency());
        codecContext_->thread_count = (cpuCores > 0) ? std::min(4, std::max(2, cpuCores)) : 4;
    }
    codecContext_->thread_type = FF_THREAD_FRAME | FF_THREAD_SLICE;

    int ret = avcodec_open2(codecContext_, codec, nullptr);
    if (ret < 0) {
        char errBuf[AV_ERROR_MAX_STRING_SIZE] = {0};
        av_strerror(ret, errBuf, sizeof(errBuf));
        lastErrorMessage_ = std::string("avcodec_open2 failed: ") + errBuf;
        LOGE("%s (%d)", lastErrorMessage_.c_str(), ret);
        releaseResources();
        return false;
    }

    packet_ = av_packet_alloc();
    tempFrame_ = av_frame_alloc();

    if (!packet_ || !tempFrame_) {
        lastErrorMessage_ = "Failed to allocate AVPacket or temp AVFrame";
        LOGE("%s", lastErrorMessage_.c_str());
        releaseResources();
        return false;
    }

    std::lock_guard<std::mutex> renderLock(renderMutex_);
    for (int i = 0; i < MAX_OUTPUT_BUFFERS; ++i) {
        slots_[i].frame = av_frame_alloc();
        slots_[i].state = SlotState::FREE;
        slots_[i].generation = 0;
    }

    isEosSent_ = false;
    isEosReached_ = false;
    lastPts_ = 0;

    LOGI("FfmpegVideoDecoder opened successfully for %s (%s, threads=%d)",
         mimeType.c_str(), codec->name, codecContext_->thread_count);
    return true;
}

int FfmpegVideoDecoder::sendPacket(const uint8_t* packetData, int packetSize, int64_t ptsUs, int64_t dtsUs) {
    std::lock_guard<std::mutex> codecLock(codecMutex_);
    if (!codecContext_ || !packet_) {
        lastErrorMessage_ = "Decoder not initialized";
        return DECODE_ERROR_INVALID_STATE;
    }

    if (!packetData || packetSize <= 0) {
        return DECODE_OK;
    }

    av_packet_unref(packet_);
    int ret = av_new_packet(packet_, packetSize);
    if (ret < 0) {
        char errBuf[AV_ERROR_MAX_STRING_SIZE] = {0};
        av_strerror(ret, errBuf, sizeof(errBuf));
        lastErrorMessage_ = std::string("av_new_packet failed: ") + errBuf;
        LOGE("%s", lastErrorMessage_.c_str());
        return DECODE_ERROR_NO_MEMORY;
    }

    std::memcpy(packet_->data, packetData, packetSize);

    if (codecId_ == AV_CODEC_ID_H264 || codecId_ == AV_CODEC_ID_HEVC) {
        convertAvcOrHevcToAnnexB(packet_->data, packetSize);
    }

    packet_->pts = ptsUs;
    packet_->dts = (dtsUs != AV_NOPTS_VALUE) ? dtsUs : ptsUs;

    ret = avcodec_send_packet(codecContext_, packet_);
    if (ret == 0) {
        return DECODE_OK;
    }
    if (ret == AVERROR(EAGAIN)) {
        return DECODE_BUFFER_FULL;
    }
    if (ret == AVERROR_EOF) {
        return DECODE_END_OF_STREAM;
    }
    if (ret == AVERROR_INVALIDDATA) {
        char errBuf[AV_ERROR_MAX_STRING_SIZE] = {0};
        av_strerror(ret, errBuf, sizeof(errBuf));
        lastErrorMessage_ = std::string("Corrupted packet: ") + errBuf;
        LOGW("%s", lastErrorMessage_.c_str());
        return DECODE_ERROR_CORRUPTED_PACKET;
    }

    char errBuf[AV_ERROR_MAX_STRING_SIZE] = {0};
    av_strerror(ret, errBuf, sizeof(errBuf));
    lastErrorMessage_ = std::string("avcodec_send_packet error: ") + errBuf;
    LOGE("%s (%d)", lastErrorMessage_.c_str(), ret);
    return DECODE_ERROR_FATAL;
}

int FfmpegVideoDecoder::sendEos() {
    std::lock_guard<std::mutex> codecLock(codecMutex_);
    if (!codecContext_) {
        lastErrorMessage_ = "Decoder not initialized";
        return DECODE_ERROR_INVALID_STATE;
    }

    isEosSent_ = true;
    int ret = avcodec_send_packet(codecContext_, nullptr);
    if (ret == 0 || ret == AVERROR_EOF || ret == AVERROR(EAGAIN)) {
        return DECODE_OK;
    }

    char errBuf[AV_ERROR_MAX_STRING_SIZE] = {0};
    av_strerror(ret, errBuf, sizeof(errBuf));
    lastErrorMessage_ = std::string("avcodec_send_packet(null) error: ") + errBuf;
    LOGE("%s (%d)", lastErrorMessage_.c_str(), ret);
    return DECODE_ERROR_FATAL;
}

int FfmpegVideoDecoder::receiveFrame(int& outSlotIndex, VideoFrameInfo* outInfo) {
    std::lock_guard<std::mutex> codecLock(codecMutex_);
    if (!codecContext_ || !tempFrame_) {
        lastErrorMessage_ = "Decoder not initialized";
        return DECODE_ERROR_INVALID_STATE;
    }

    if (isEosReached_) {
        return DECODE_END_OF_STREAM;
    }

    int ret = avcodec_receive_frame(codecContext_, tempFrame_);
    if (ret == AVERROR(EAGAIN)) {
        if (isEosSent_) {
            isEosReached_ = true;
            return DECODE_END_OF_STREAM;
        }
        return DECODE_NEED_MORE_INPUT;
    }
    if (ret == AVERROR_EOF) {
        isEosReached_ = true;
        return DECODE_END_OF_STREAM;
    }
    if (ret < 0) {
        char errBuf[AV_ERROR_MAX_STRING_SIZE] = {0};
        av_strerror(ret, errBuf, sizeof(errBuf));
        lastErrorMessage_ = std::string("avcodec_receive_frame error: ") + errBuf;
        LOGE("%s (%d)", lastErrorMessage_.c_str(), ret);
        return DECODE_ERROR_FATAL;
    }

    std::lock_guard<std::mutex> renderLock(renderMutex_);
    int slotIndex = findFreeSlotLocked();
    if (slotIndex < 0) {
        av_frame_unref(tempFrame_);
        return DECODE_BUFFER_FULL;
    }

    if (!slots_[slotIndex].frame) {
        slots_[slotIndex].frame = av_frame_alloc();
        if (!slots_[slotIndex].frame) {
            av_frame_unref(tempFrame_);
            return DECODE_ERROR_NO_MEMORY;
        }
    } else {
        av_frame_unref(slots_[slotIndex].frame);
    }

    av_frame_move_ref(slots_[slotIndex].frame, tempFrame_);

    int64_t pts = slots_[slotIndex].frame->best_effort_timestamp;
    if (pts == AV_NOPTS_VALUE) {
        pts = slots_[slotIndex].frame->pts;
    }
    if (pts == AV_NOPTS_VALUE) {
        pts = lastPts_;
    } else {
        lastPts_ = pts;
    }

    const AVPixFmtDescriptor* desc = av_pix_fmt_desc_get(static_cast<AVPixelFormat>(slots_[slotIndex].frame->format));
    int bitDepth = desc ? desc->comp[0].depth : 8;

    VideoColorInfo colorInfo;
    colorInfo.colorSpace = slots_[slotIndex].frame->colorspace;
    colorInfo.colorRange = slots_[slotIndex].frame->color_range;
    colorInfo.colorPrimaries = slots_[slotIndex].frame->color_primaries;
    colorInfo.colorTransfer = slots_[slotIndex].frame->color_trc;
    colorInfo.bitDepth = bitDepth;
    colorInfo.isHdr = (slots_[slotIndex].frame->color_trc == AVCOL_TRC_SMPTE2084 ||
                       slots_[slotIndex].frame->color_trc == AVCOL_TRC_ARIB_STD_B67 ||
                       (slots_[slotIndex].frame->color_primaries == AVCOL_PRI_BT2020 && bitDepth >= 10));

    slots_[slotIndex].info.width = slots_[slotIndex].frame->width;
    slots_[slotIndex].info.height = slots_[slotIndex].frame->height;
    slots_[slotIndex].info.pts = pts;
    slots_[slotIndex].info.format = slots_[slotIndex].frame->format;
    slots_[slotIndex].info.bitDepth = bitDepth;
    slots_[slotIndex].info.isKeyFrame = (slots_[slotIndex].frame->flags & AV_FRAME_FLAG_KEY) != 0;
    slots_[slotIndex].info.colorInfo = colorInfo;
    slots_[slotIndex].state = SlotState::READY;

    outSlotIndex = slotIndex;
    if (outInfo) {
        *outInfo = slots_[slotIndex].info;
    }

    return DECODE_OK;
}

int FfmpegVideoDecoder::decode(int bufferIndex, const uint8_t* packetData, int packetSize, int64_t ptsUs, bool isEos) {
    if (isEos) {
        sendEos();
    } else if (packetData && packetSize > 0) {
        int s = sendPacket(packetData, packetSize, ptsUs, ptsUs);
        if (s < 0 && s != DECODE_BUFFER_FULL) {
            return s;
        }
    }

    int outSlot = -1;
    VideoFrameInfo info;
    int rec = receiveFrame(outSlot, &info);
    if (rec == DECODE_OK) {
        return 0;
    } else if (rec == DECODE_NEED_MORE_INPUT) {
        return 1;
    } else if (rec == DECODE_END_OF_STREAM) {
        return 2;
    } else {
        return rec;
    }
}

bool FfmpegVideoDecoder::getFrameInfo(int slotIndex, VideoFrameInfo* outInfo) {
    if (!outInfo || slotIndex < 0 || slotIndex >= MAX_OUTPUT_BUFFERS) return false;
    std::lock_guard<std::mutex> renderLock(renderMutex_);
    if (slots_[slotIndex].state == SlotState::FREE) {
        return false;
    }
    *outInfo = slots_[slotIndex].info;
    return true;
}

bool FfmpegVideoDecoder::renderToSurface(int slotIndex, ANativeWindow* window) {
    if (!window || slotIndex < 0 || slotIndex >= MAX_OUTPUT_BUFFERS) return false;

    std::lock_guard<std::mutex> renderLock(renderMutex_);
    if (slots_[slotIndex].state == SlotState::FREE) {
        LOGW("Attempted to render free slot %d", slotIndex);
        return false;
    }

    AVFrame* frame = slots_[slotIndex].frame;
    if (!frame || frame->width <= 0 || frame->height <= 0) {
        return false;
    }

    slots_[slotIndex].state = SlotState::IN_USE;

    int frameW = frame->width;
    int frameH = frame->height;
    enum AVPixelFormat srcFormat = static_cast<enum AVPixelFormat>(frame->format);

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

    int swsFlags = SWS_BILINEAR;
    if (scalingMode_ == VideoScalingMode::FAST_BILINEAR) {
        swsFlags = SWS_FAST_BILINEAR;
    } else if (scalingMode_ == VideoScalingMode::BICUBIC) {
        swsFlags = SWS_BICUBIC;
    }

    swsContext_ = sws_getCachedContext(
        swsContext_,
        frameW, frameH, srcFormat,
        windowBuffer.width, windowBuffer.height, AV_PIX_FMT_RGBA,
        swsFlags,
        nullptr, nullptr, nullptr
    );

    if (!swsContext_) {
        LOGE("sws_getCachedContext failed to create scaler");
        ANativeWindow_unlockAndPost(window);
        return false;
    }

    int srcColorSpace = frame->colorspace;
    if (srcColorSpace == AVCOL_SPC_UNSPECIFIED || srcColorSpace == AVCOL_SPC_RESERVED) {
        srcColorSpace = (frameW >= 1280 || frameH >= 720) ? AVCOL_SPC_BT709 : AVCOL_SPC_SMPTE170M;
    }

    int swsSrcCS = SWS_CS_ITU709;
    if (srcColorSpace == AVCOL_SPC_BT2020_NCL || srcColorSpace == AVCOL_SPC_BT2020_CL) {
        swsSrcCS = SWS_CS_BT2020;
    } else if (srcColorSpace == AVCOL_SPC_SMPTE170M || srcColorSpace == AVCOL_SPC_BT470BG) {
        swsSrcCS = SWS_CS_ITU601;
    } else if (srcColorSpace == AVCOL_SPC_SMPTE240M) {
        swsSrcCS = SWS_CS_SMPTE240M;
    }

    int srcRange = (frame->color_range == AVCOL_RANGE_JPEG) ? 1 : 0;
    int dstRange = 1; // Full range RGB

    const int* invTable = sws_getCoefficients(swsSrcCS);
    const int* table = sws_getCoefficients(SWS_CS_DEFAULT);

    if (invTable && table) {
        sws_setColorspaceDetails(
            swsContext_,
            invTable,
            srcRange,
            table,
            dstRange,
            0,
            1 << 16,
            1 << 16
        );
    }

    uint8_t* dstData[4] = { static_cast<uint8_t*>(windowBuffer.bits), nullptr, nullptr, nullptr };
    int dstLinesize[4] = { windowBuffer.stride * 4, 0, 0, 0 };

    sws_scale(swsContext_, frame->data, frame->linesize, 0, frameH, dstData, dstLinesize);

    if (enableToneMapping_ && (frame->color_trc == AVCOL_TRC_SMPTE2084 || frame->color_trc == AVCOL_TRC_ARIB_STD_B67)) {
        applyToneMapping(windowBuffer, frame->color_trc);
    }

    ANativeWindow_unlockAndPost(window);
    return true;
}

void FfmpegVideoDecoder::releaseFrame(int slotIndex) {
    if (slotIndex < 0 || slotIndex >= MAX_OUTPUT_BUFFERS) return;
    std::lock_guard<std::mutex> renderLock(renderMutex_);
    if (slots_[slotIndex].frame) {
        av_frame_unref(slots_[slotIndex].frame);
    }
    slots_[slotIndex].state = SlotState::FREE;
    slots_[slotIndex].generation++;
}

int FfmpegVideoDecoder::findFreeSlotLocked() const {
    for (int i = 0; i < MAX_OUTPUT_BUFFERS; ++i) {
        if (slots_[i].state == SlotState::FREE) {
            return i;
        }
    }
    return -1;
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
        isEosSent_ = false;
        isEosReached_ = false;
        lastPts_ = 0;
    }
    {
        std::lock_guard<std::mutex> renderLock(renderMutex_);
        for (int i = 0; i < MAX_OUTPUT_BUFFERS; ++i) {
            if (slots_[i].frame) {
                av_frame_unref(slots_[i].frame);
            }
            slots_[i].state = SlotState::FREE;
            slots_[i].generation++;
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
        isEosSent_ = false;
        isEosReached_ = false;
        lastPts_ = 0;
    }
    {
        std::lock_guard<std::mutex> renderLock(renderMutex_);
        for (int i = 0; i < MAX_OUTPUT_BUFFERS; ++i) {
            if (slots_[i].frame) {
                av_frame_free(&slots_[i].frame);
                slots_[i].frame = nullptr;
            }
            slots_[i].state = SlotState::FREE;
            slots_[i].generation++;
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

std::string FfmpegVideoDecoder::getLastErrorMessage() const {
    std::lock_guard<std::mutex> codecLock(codecMutex_);
    return lastErrorMessage_;
}

} // namespace nosved
