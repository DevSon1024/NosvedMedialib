#include "dav1d_video_decoder.h"
#include "../common/logging.h"
#include <cstring>

namespace nosved {

Dav1dVideoDecoder::Dav1dVideoDecoder() {
    std::memset(&currentPicture_, 0, sizeof(currentPicture_));
}

Dav1dVideoDecoder::~Dav1dVideoDecoder() {
    close();
}

const char* Dav1dVideoDecoder::getVersion() {
    return dav1d_version();
}

bool Dav1dVideoDecoder::open(int width, int height, int nThreads) {
    std::lock_guard<std::mutex> lock(mutex_);
    releaseResources();

    Dav1dSettings settings;
    dav1d_default_settings(&settings);
    settings.n_threads = nThreads > 0 ? nThreads : 0;
    settings.max_frame_delay = 1;

    int ret = dav1d_open(&context_, &settings);
    if (ret < 0 || !context_) {
        LOGE("dav1d_open failed: %d", ret);
        return false;
    }

    LOGI("Dav1dVideoDecoder opened successfully (dav1d %s)", dav1d_version());
    return true;
}

int Dav1dVideoDecoder::decode(const uint8_t* packetData, int packetSize, int64_t ptsUs, bool isEos) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!context_) {
        return -EINVAL;
    }

    if (isEos) {
        releasePicture();
        int ret = dav1d_get_picture(context_, &currentPicture_);
        if (ret == 0) {
            hasPicture_ = true;
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

        releasePicture();
        int getRet = dav1d_get_picture(context_, &currentPicture_);
        if (getRet == 0) {
            hasPicture_ = true;
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

bool Dav1dVideoDecoder::getFrameInfo(VideoFrameInfo& outInfo) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!hasPicture_) {
        return false;
    }

    outInfo.width = currentPicture_.p.w;
    outInfo.height = currentPicture_.p.h;
    outInfo.pts = currentPicture_.m.timestamp;
    outInfo.bitDepth = currentPicture_.p.bpc;
    outInfo.format = static_cast<int>(currentPicture_.p.layout);
    outInfo.isKeyFrame = (currentPicture_.frame_hdr != nullptr) ? (currentPicture_.frame_hdr->frame_type == DAV1D_FRAME_TYPE_KEY) : false;
    return true;
}

bool Dav1dVideoDecoder::renderToSurface(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!hasPicture_ || !window) {
        return false;
    }

    int width = currentPicture_.p.w;
    int height = currentPicture_.p.h;
    int bpc = currentPicture_.p.bpc;

    if (width <= 0 || height <= 0) {
        return false;
    }

    ANativeWindow_setBuffersGeometry(window, width, height, WINDOW_FORMAT_RGBA_8888);

    ANativeWindow_Buffer winBuffer;
    if (ANativeWindow_lock(window, &winBuffer, nullptr) < 0) {
        LOGE("Failed to lock ANativeWindow for dav1d rendering");
        return false;
    }

    AVPixelFormat srcFmt = (bpc == 10) ? AV_PIX_FMT_YUV420P10LE : AV_PIX_FMT_YUV420P;

    swsContext_ = sws_getCachedContext(
        swsContext_,
        width, height, srcFmt,
        width, height, AV_PIX_FMT_RGBA,
        SWS_FAST_BILINEAR, nullptr, nullptr, nullptr
    );

    if (!swsContext_) {
        LOGE("Failed to create SwsContext for dav1d surface rendering");
        ANativeWindow_unlockAndPost(window);
        return false;
    }

    const uint8_t* srcPlanes[3] = {
        static_cast<const uint8_t*>(currentPicture_.data[0]),
        static_cast<const uint8_t*>(currentPicture_.data[1]),
        static_cast<const uint8_t*>(currentPicture_.data[2])
    };
    const int srcStrides[3] = {
        static_cast<int>(currentPicture_.stride[0]),
        static_cast<int>(currentPicture_.stride[1]),
        static_cast<int>(currentPicture_.stride[1])
    };

    uint8_t* dstPlanes[4] = { static_cast<uint8_t*>(winBuffer.bits), nullptr, nullptr, nullptr };
    int dstStrides[4] = { static_cast<int>(winBuffer.stride * 4), 0, 0, 0 };

    sws_scale(swsContext_, srcPlanes, srcStrides, 0, height, dstPlanes, dstStrides);

    ANativeWindow_unlockAndPost(window);
    return true;
}

void Dav1dVideoDecoder::flush() {
    std::lock_guard<std::mutex> lock(mutex_);
    releasePicture();
    if (context_) {
        dav1d_flush(context_);
    }
}

void Dav1dVideoDecoder::releaseResources() {
    releasePicture();
    releaseSws();
    if (context_) {
        dav1d_close(&context_);
        context_ = nullptr;
    }
}

void Dav1dVideoDecoder::close() {
    std::lock_guard<std::mutex> lock(mutex_);
    releaseResources();
}

void Dav1dVideoDecoder::releasePicture() {
    if (hasPicture_) {
        dav1d_picture_unref(&currentPicture_);
        std::memset(&currentPicture_, 0, sizeof(currentPicture_));
        hasPicture_ = false;
    }
}

void Dav1dVideoDecoder::releaseSws() {
    if (swsContext_) {
        sws_freeContext(swsContext_);
        swsContext_ = nullptr;
        swsSrcWidth_ = 0;
        swsSrcHeight_ = 0;
        swsSrcBpc_ = 0;
    }
}

} // namespace nosved

