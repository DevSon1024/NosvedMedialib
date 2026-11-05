#include "dav1d_video_decoder.h"
#include "../common/logging.h"
#include <cstring>
#include <thread>
#include <algorithm>
#include <cmath>

namespace nosved {

static uint8_t s_dav1dPqLut[256];
static uint8_t s_dav1dHlgLut[256];
static std::once_flag s_dav1dLutOnce;

static void initDav1dToneMapLuts() {
    std::call_once(s_dav1dLutOnce, []() {
        for (int i = 0; i < 256; ++i) {
            float v = i / 255.0f;
            float mapped = std::pow(v, 0.65f) * 1.35f;
            if (mapped > 0.85f) {
                float over = mapped - 0.85f;
                mapped = 0.85f + (over / (1.0f + over * 1.5f)) * 0.15f;
            }
            int val = static_cast<int>(mapped * 255.0f + 0.5f);
            s_dav1dPqLut[i] = static_cast<uint8_t>(std::min(255, std::max(0, val)));
        }

        for (int i = 0; i < 256; ++i) {
            float v = i / 255.0f;
            float mapped = std::pow(v, 0.82f) * 1.18f;
            if (mapped > 0.90f) {
                float over = mapped - 0.90f;
                mapped = 0.90f + (over / (1.0f + over * 1.2f)) * 0.10f;
            }
            int val = static_cast<int>(mapped * 255.0f + 0.5f);
            s_dav1dHlgLut[i] = static_cast<uint8_t>(std::min(255, std::max(0, val)));
        }
    });
}

void Dav1dVideoDecoder::applyToneMapping(ANativeWindow_Buffer& windowBuffer, int colorTransfer) {
    initDav1dToneMapLuts();
    const uint8_t* lut = (colorTransfer == 18) ? s_dav1dHlgLut : s_dav1dPqLut;

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

Dav1dVideoDecoder::Dav1dVideoDecoder() {
    for (int i = 0; i < MAX_DAV1D_OUTPUT_BUFFERS; ++i) {
        std::memset(&outputPictures_[i], 0, sizeof(Dav1dPicture));
        pictureValid_[i] = false;
    }
}

Dav1dVideoDecoder::~Dav1dVideoDecoder() {
    close();
}

const char* Dav1dVideoDecoder::getVersion() {
    return dav1d_version();
}

bool Dav1dVideoDecoder::open(int width, int height, int nThreads, VideoScalingMode scalingMode, bool enableToneMapping) {
    releaseResources();

    scalingMode_ = scalingMode;
    enableToneMapping_ = enableToneMapping;

    std::lock_guard<std::mutex> codecLock(codecMutex_);

    Dav1dSettings settings;
    dav1d_default_settings(&settings);
    int cpuCores = static_cast<int>(std::thread::hardware_concurrency());
    if (nThreads > 0) {
        settings.n_threads = nThreads;
    } else {
        if (width >= 3840 || height >= 2160) {
            settings.n_threads = (cpuCores > 0) ? std::min(6, std::max(4, cpuCores - 2)) : 4;
        } else if (width >= 1920 || height >= 1080) {
            settings.n_threads = (cpuCores > 0) ? std::min(4, std::max(2, cpuCores / 2)) : 3;
        } else {
            settings.n_threads = (cpuCores > 0) ? std::min(2, std::max(1, cpuCores / 4)) : 2;
        }
    }
    settings.max_frame_delay = (settings.n_threads > 2) ? 2 : 1;

    int ret = dav1d_open(&context_, &settings);
    if (ret < 0 || !context_) {
        LOGE("dav1d_open failed: %d", ret);
        return false;
    }

    LOGI("Dav1dVideoDecoder opened successfully (dav1d %s, threads=%d)", dav1d_version(), settings.n_threads);
    return true;
}

int Dav1dVideoDecoder::decode(int bufferIndex, const uint8_t* packetData, int packetSize, int64_t ptsUs, bool isEos) {
    std::lock_guard<std::mutex> codecLock(codecMutex_);
    if (!context_) {
        return -EINVAL;
    }

    int idx = (bufferIndex >= 0 && bufferIndex < MAX_DAV1D_OUTPUT_BUFFERS) ? bufferIndex : (std::abs(bufferIndex) % MAX_DAV1D_OUTPUT_BUFFERS);

    if (isEos) {
        Dav1dPicture pic;
        std::memset(&pic, 0, sizeof(pic));
        int ret = dav1d_get_picture(context_, &pic);
        if (ret == 0) {
            std::lock_guard<std::mutex> renderLock(renderMutex_);
            if (pictureValid_[idx]) {
                dav1d_picture_unref(&outputPictures_[idx]);
            }
            outputPictures_[idx] = pic;
            pictureValid_[idx] = true;

            VideoColorInfo colorInfo;
            if (pic.seq_hdr) {
                colorInfo.colorPrimaries = pic.seq_hdr->pri;
                colorInfo.colorTransfer = pic.seq_hdr->trc;
                colorInfo.colorSpace = pic.seq_hdr->mtrx;
                colorInfo.colorRange = pic.seq_hdr->color_range;
            } else {
                colorInfo.colorPrimaries = 1;
                colorInfo.colorTransfer = 1;
                colorInfo.colorSpace = (pic.p.w >= 1280 || pic.p.h >= 720) ? 1 : 6;
                colorInfo.colorRange = 0;
            }
            colorInfo.bitDepth = pic.p.bpc;
            colorInfo.isHdr = (colorInfo.colorTransfer == 16 || colorInfo.colorTransfer == 18 ||
                              (colorInfo.colorPrimaries == 9 && pic.p.bpc >= 10));

            frameInfos_[idx].width = pic.p.w;
            frameInfos_[idx].height = pic.p.h;
            frameInfos_[idx].pts = pic.m.timestamp;
            frameInfos_[idx].bitDepth = pic.p.bpc;
            frameInfos_[idx].format = static_cast<int>(pic.p.layout);
            frameInfos_[idx].isKeyFrame = (pic.frame_hdr != nullptr) ? (pic.frame_hdr->frame_type == DAV1D_FRAME_TYPE_KEY) : false;
            frameInfos_[idx].colorInfo = colorInfo;

            return 0; // Decoded picture drained
        } else if (ret == DAV1D_ERR(EAGAIN)) {
            return 2; // All frames drained, end of stream
        } else {
            return ret;
        }
    }

    if (packetData && packetSize > 0) {
        Dav1dData data;
        std::memset(&data, 0, sizeof(data));
        uint8_t* buf = dav1d_data_create(&data, packetSize);
        if (!buf) {
            LOGE("dav1d_data_create failed for size %d", packetSize);
            return -ENOMEM;
        }
        std::memcpy(buf, packetData, packetSize);
        data.m.timestamp = ptsUs;

        int sendRet = dav1d_send_data(context_, &data);
        if (sendRet < 0 && sendRet != DAV1D_ERR(EAGAIN)) {
            dav1d_data_unref(&data);
            LOGE("dav1d_send_data failed: %d", sendRet);
            return sendRet;
        }

        Dav1dPicture pic;
        std::memset(&pic, 0, sizeof(pic));
        int getRet = dav1d_get_picture(context_, &pic);
        if (getRet == 0) {
            std::lock_guard<std::mutex> renderLock(renderMutex_);
            if (pictureValid_[idx]) {
                dav1d_picture_unref(&outputPictures_[idx]);
            }
            outputPictures_[idx] = pic;
            pictureValid_[idx] = true;

            VideoColorInfo colorInfo;
            if (pic.seq_hdr) {
                colorInfo.colorPrimaries = pic.seq_hdr->pri;
                colorInfo.colorTransfer = pic.seq_hdr->trc;
                colorInfo.colorSpace = pic.seq_hdr->mtrx;
                colorInfo.colorRange = pic.seq_hdr->color_range;
            } else {
                colorInfo.colorPrimaries = 1;
                colorInfo.colorTransfer = 1;
                colorInfo.colorSpace = (pic.p.w >= 1280 || pic.p.h >= 720) ? 1 : 6;
                colorInfo.colorRange = 0;
            }
            colorInfo.bitDepth = pic.p.bpc;
            colorInfo.isHdr = (colorInfo.colorTransfer == 16 || colorInfo.colorTransfer == 18 ||
                              (colorInfo.colorPrimaries == 9 && pic.p.bpc >= 10));

            frameInfos_[idx].width = pic.p.w;
            frameInfos_[idx].height = pic.p.h;
            frameInfos_[idx].pts = pic.m.timestamp;
            frameInfos_[idx].bitDepth = pic.p.bpc;
            frameInfos_[idx].format = static_cast<int>(pic.p.layout);
            frameInfos_[idx].isKeyFrame = (pic.frame_hdr != nullptr) ? (pic.frame_hdr->frame_type == DAV1D_FRAME_TYPE_KEY) : false;
            frameInfos_[idx].colorInfo = colorInfo;

            return 0; // Picture successfully decoded
        } else if (getRet == DAV1D_ERR(EAGAIN)) {
            return 1; // More data needed
        } else {
            LOGE("dav1d_get_picture failed: %d", getRet);
            return getRet;
        }
    }

    return 1;
}

bool Dav1dVideoDecoder::getFrameInfo(int bufferIndex, VideoFrameInfo& outInfo) {
    std::lock_guard<std::mutex> renderLock(renderMutex_);
    int idx = (bufferIndex >= 0 && bufferIndex < MAX_DAV1D_OUTPUT_BUFFERS) ? bufferIndex : (std::abs(bufferIndex) % MAX_DAV1D_OUTPUT_BUFFERS);
    if (!pictureValid_[idx]) {
        return false;
    }
    outInfo = frameInfos_[idx];
    return true;
}

bool Dav1dVideoDecoder::renderToSurface(int bufferIndex, ANativeWindow* window) {
    if (!window) return false;

    std::lock_guard<std::mutex> renderLock(renderMutex_);
    int idx = (bufferIndex >= 0 && bufferIndex < MAX_DAV1D_OUTPUT_BUFFERS) ? bufferIndex : (std::abs(bufferIndex) % MAX_DAV1D_OUTPUT_BUFFERS);

    if (!pictureValid_[idx]) {
        return false;
    }

    Dav1dPicture& pic = outputPictures_[idx];
    int width = pic.p.w;
    int height = pic.p.h;
    int bpc = pic.p.bpc;

    if (width <= 0 || height <= 0) {
        return false;
    }

    if (cachedWidth_ != width || cachedHeight_ != height || cachedFormat_ != WINDOW_FORMAT_RGBA_8888) {
        int setGeomRet = ANativeWindow_setBuffersGeometry(window, width, height, WINDOW_FORMAT_RGBA_8888);
        if (setGeomRet >= 0) {
            cachedWidth_ = width;
            cachedHeight_ = height;
            cachedFormat_ = WINDOW_FORMAT_RGBA_8888;
        } else {
            LOGE("ANativeWindow_setBuffersGeometry failed for dav1d: %d", setGeomRet);
            return false;
        }
    }

    ANativeWindow_Buffer winBuffer;
    if (ANativeWindow_lock(window, &winBuffer, nullptr) < 0) {
        LOGE("Failed to lock ANativeWindow for dav1d rendering");
        return false;
    }

    AVPixelFormat srcFmt = (bpc == 10) ? AV_PIX_FMT_YUV420P10LE : AV_PIX_FMT_YUV420P;

    int swsFlags = SWS_BILINEAR;
    if (scalingMode_ == VideoScalingMode::FAST_BILINEAR) {
        swsFlags = SWS_FAST_BILINEAR;
    } else if (scalingMode_ == VideoScalingMode::BICUBIC) {
        swsFlags = SWS_BICUBIC;
    }

    swsContext_ = sws_getCachedContext(
        swsContext_,
        width, height, srcFmt,
        winBuffer.width, winBuffer.height, AV_PIX_FMT_RGBA,
        swsFlags, nullptr, nullptr, nullptr
    );

    if (!swsContext_) {
        LOGE("Failed to create SwsContext for dav1d surface rendering");
        ANativeWindow_unlockAndPost(window);
        return false;
    }

    int av1Matrix = pic.seq_hdr ? pic.seq_hdr->mtrx : 1;
    int swsSrcCS = SWS_CS_ITU709;
    if (av1Matrix == 9) { // DAV1D_MC_BT2020_NCL
        swsSrcCS = SWS_CS_BT2020;
    } else if (av1Matrix == 5 || av1Matrix == 6) { // DAV1D_MC_BT470BG / BT601
        swsSrcCS = SWS_CS_ITU601;
    } else if (av1Matrix == 7) { // DAV1D_MC_SMPTE240
        swsSrcCS = SWS_CS_SMPTE240M;
    } else {
        swsSrcCS = (width >= 1280 || height >= 720) ? SWS_CS_ITU709 : SWS_CS_ITU601;
    }

    int srcRange = pic.seq_hdr ? (pic.seq_hdr->color_range ? 1 : 0) : 0;
    int dstRange = 1;

    if (cachedColorSpace_ != swsSrcCS || cachedColorRange_ != srcRange) {
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
            cachedColorSpace_ = swsSrcCS;
            cachedColorRange_ = srcRange;
        }
    }

    const uint8_t* srcPlanes[3] = {
        static_cast<const uint8_t*>(pic.data[0]),
        static_cast<const uint8_t*>(pic.data[1]),
        static_cast<const uint8_t*>(pic.data[2])
    };
    const int srcStrides[3] = {
        static_cast<int>(pic.stride[0]),
        static_cast<int>(pic.stride[1]),
        static_cast<int>(pic.stride[1])
    };

    uint8_t* dstPlanes[4] = { static_cast<uint8_t*>(winBuffer.bits), nullptr, nullptr, nullptr };
    int dstStrides[4] = { static_cast<int>(winBuffer.stride * 4), 0, 0, 0 };

    sws_scale(swsContext_, srcPlanes, srcStrides, 0, height, dstPlanes, dstStrides);

    int trc = pic.seq_hdr ? pic.seq_hdr->trc : 1;
    if (enableToneMapping_ && (trc == 16 || trc == 18)) {
        applyToneMapping(winBuffer, trc);
    }

    ANativeWindow_unlockAndPost(window);
    return true;
}

void Dav1dVideoDecoder::releaseFrame(int bufferIndex) {
    std::lock_guard<std::mutex> renderLock(renderMutex_);
    int idx = (bufferIndex >= 0 && bufferIndex < MAX_DAV1D_OUTPUT_BUFFERS) ? bufferIndex : (std::abs(bufferIndex) % MAX_DAV1D_OUTPUT_BUFFERS);
    if (pictureValid_[idx]) {
        dav1d_picture_unref(&outputPictures_[idx]);
        pictureValid_[idx] = false;
    }
}

void Dav1dVideoDecoder::flush() {
    {
        std::lock_guard<std::mutex> codecLock(codecMutex_);
        if (context_) {
            dav1d_flush(context_);
        }
    }
    {
        std::lock_guard<std::mutex> renderLock(renderMutex_);
        for (int i = 0; i < MAX_DAV1D_OUTPUT_BUFFERS; ++i) {
            if (pictureValid_[i]) {
                dav1d_picture_unref(&outputPictures_[i]);
                pictureValid_[i] = false;
            }
        }
        cachedColorSpace_ = -1;
        cachedColorRange_ = -1;
    }
}

void Dav1dVideoDecoder::releaseResources() {
    {
        std::lock_guard<std::mutex> codecLock(codecMutex_);
        if (context_) {
            dav1d_close(&context_);
            context_ = nullptr;
        }
    }
    {
        std::lock_guard<std::mutex> renderLock(renderMutex_);
        for (int i = 0; i < MAX_DAV1D_OUTPUT_BUFFERS; ++i) {
            if (pictureValid_[i]) {
                dav1d_picture_unref(&outputPictures_[i]);
                pictureValid_[i] = false;
            }
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

void Dav1dVideoDecoder::close() {
    releaseResources();
}

} // namespace nosved
