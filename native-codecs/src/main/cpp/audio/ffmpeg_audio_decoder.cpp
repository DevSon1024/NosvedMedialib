#include "ffmpeg_audio_decoder.h"
#include "../common/logging.h"
#include <cstring>
#include <algorithm>

namespace nosved {

FfmpegAudioDecoder::FfmpegAudioDecoder() = default;

FfmpegAudioDecoder::~FfmpegAudioDecoder() {
    close();
}

enum AVCodecID FfmpegAudioDecoder::getCodecIdForMimeType(const std::string& mimeType) {
    if (mimeType == "audio/eac3" || mimeType == "audio/eac3-joc") return AV_CODEC_ID_EAC3;
    if (mimeType == "audio/ac3") return AV_CODEC_ID_AC3;
    if (mimeType == "audio/ac4") return AV_CODEC_ID_AC4;
    if (mimeType == "audio/true-hd") return AV_CODEC_ID_TRUEHD;
    if (mimeType == "audio/vnd.dts" || mimeType == "audio/vnd.dts.hd" || mimeType == "audio/vnd.dts.uhd") return AV_CODEC_ID_DTS;
    if (mimeType == "audio/flac") return AV_CODEC_ID_FLAC;
    if (mimeType == "audio/alac") return AV_CODEC_ID_ALAC;
    if (mimeType == "audio/opus") return AV_CODEC_ID_OPUS;
    if (mimeType == "audio/vorbis") return AV_CODEC_ID_VORBIS;
    if (mimeType == "audio/mp4a-latm" || mimeType == "audio/aac") return AV_CODEC_ID_AAC;
    if (mimeType == "audio/mpeg") return AV_CODEC_ID_MP3;
    if (mimeType == "audio/mpeg-L1") return AV_CODEC_ID_MP1;
    if (mimeType == "audio/mpeg-L2") return AV_CODEC_ID_MP2;
    if (mimeType == "audio/wav") return AV_CODEC_ID_PCM_S16LE;
    if (mimeType == "audio/x-ms-wma" || mimeType == "audio/wma") return AV_CODEC_ID_WMAV2;
    if (mimeType == "audio/x-ms-wmapro") return AV_CODEC_ID_WMAPRO;
    if (mimeType == "audio/mlp") return AV_CODEC_ID_MLP;
    return AV_CODEC_ID_NONE;
}

bool FfmpegAudioDecoder::isMimeTypeSupported(const std::string& mimeType) {
    enum AVCodecID codecId = getCodecIdForMimeType(mimeType);
    if (codecId == AV_CODEC_ID_NONE) return false;
    const AVCodec* codec = avcodec_find_decoder(codecId);
    return codec != nullptr;
}

bool FfmpegAudioDecoder::open(const std::string& mimeType, const uint8_t* extraData, int extraDataSize, int sampleRate, int channelCount) {
    std::lock_guard<std::mutex> lock(mutex_);
    releaseResources();

    enum AVCodecID codecId = getCodecIdForMimeType(mimeType);
    if (codecId == AV_CODEC_ID_NONE) {
        LOGE("Unsupported audio MIME type: %s", mimeType.c_str());
        return false;
    }

    const AVCodec* codec = avcodec_find_decoder(codecId);
    if (!codec) {
        LOGE("No audio decoder found for codec ID: %d", codecId);
        return false;
    }

    codecContext_ = avcodec_alloc_context3(codec);
    if (!codecContext_) {
        LOGE("Failed to allocate audio AVCodecContext");
        return false;
    }

    if (extraData && extraDataSize > 0) {
        codecContext_->extradata = static_cast<uint8_t*>(av_mallocz(extraDataSize + AV_INPUT_BUFFER_PADDING_SIZE));
        if (codecContext_->extradata) {
            std::memcpy(codecContext_->extradata, extraData, extraDataSize);
            codecContext_->extradata_size = extraDataSize;
        }
    }

    if (sampleRate > 0) {
        codecContext_->sample_rate = sampleRate;
        outSampleRate_ = sampleRate;
    } else {
        outSampleRate_ = 48000;
    }

    if (channelCount > 0) {
        av_channel_layout_default(&codecContext_->ch_layout, channelCount);
        // Default to clean stereo output for 2+ channels
        outChannelCount_ = (channelCount == 1) ? 1 : 2;
    } else {
        outChannelCount_ = 2;
    }

    codecContext_->request_sample_fmt = AV_SAMPLE_FMT_S16;

    int ret = avcodec_open2(codecContext_, codec, nullptr);
    if (ret < 0) {
        char errBuf[AV_ERROR_MAX_STRING_SIZE] = {0};
        av_strerror(ret, errBuf, sizeof(errBuf));
        LOGE("avcodec_open2 failed for audio %s: %s (%d)", mimeType.c_str(), errBuf, ret);
        releaseResources();
        return false;
    }

    packet_ = av_packet_alloc();
    frame_ = av_frame_alloc();
    if (!packet_ || !frame_) {
        LOGE("Failed to allocate audio packet or frame");
        releaseResources();
        return false;
    }

    LOGI("FfmpegAudioDecoder opened for %s (%s)", mimeType.c_str(), codec->name);
    return true;
}

bool FfmpegAudioDecoder::initResampler(const AVFrame* frame) {
    if (!frame) return false;

    if (swrContext_) {
        swr_free(&swrContext_);
        swrContext_ = nullptr;
    }

    inSampleFmt_ = static_cast<enum AVSampleFormat>(frame->format);
    inSampleRate_ = frame->sample_rate > 0 ? frame->sample_rate : outSampleRate_;
    av_channel_layout_uninit(&inChannelLayout_);
    av_channel_layout_copy(&inChannelLayout_, &frame->ch_layout);

    outSampleRate_ = inSampleRate_;
    int inChannels = frame->ch_layout.nb_channels;
    outChannelCount_ = (inChannels == 1) ? 1 : 2;

    AVChannelLayout outLayout;
    if (outChannelCount_ == 1) {
        outLayout = (AVChannelLayout)AV_CHANNEL_LAYOUT_MONO;
    } else {
        outLayout = (AVChannelLayout)AV_CHANNEL_LAYOUT_STEREO;
    }

    int ret = swr_alloc_set_opts2(
        &swrContext_,
        &outLayout,
        AV_SAMPLE_FMT_S16,
        outSampleRate_,
        &inChannelLayout_,
        inSampleFmt_,
        inSampleRate_,
        0,
        nullptr
    );

    if (ret < 0 || !swrContext_) {
        LOGE("swr_alloc_set_opts2 failed: %d", ret);
        return false;
    }

    ret = swr_init(swrContext_);
    if (ret < 0) {
        LOGE("swr_init failed: %d", ret);
        swr_free(&swrContext_);
        swrContext_ = nullptr;
        return false;
    }

    return true;
}

int FfmpegAudioDecoder::decode(const uint8_t* packetData, int packetSize, uint8_t* outPcmBuffer, int outPcmCapacity) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!codecContext_ || !packet_ || !frame_ || !outPcmBuffer || outPcmCapacity <= 0) {
        return -1;
    }

    if (packetData && packetSize > 0) {
        av_packet_unref(packet_);
        int ret = av_new_packet(packet_, packetSize);
        if (ret < 0) {
            LOGE("av_new_packet audio failed: %d", ret);
            return ret;
        }
        std::memcpy(packet_->data, packetData, packetSize);

        ret = avcodec_send_packet(codecContext_, packet_);
        if (ret < 0 && ret != AVERROR(EAGAIN)) {
            LOGW("avcodec_send_packet audio non-fatal: %d", ret);
        }
    } else if (packetSize == 0) {
        // Drain / EOF
        avcodec_send_packet(codecContext_, nullptr);
    }

    int bytesWritten = 0;
    while (true) {
        int rec = avcodec_receive_frame(codecContext_, frame_);
        if (rec == AVERROR(EAGAIN) || rec == AVERROR_EOF) {
            break;
        }
        if (rec < 0) {
            LOGW("avcodec_receive_frame audio error: %d", rec);
            break;
        }

        // Check if resampler needs initialization or reconfiguration
        bool needReinit = (!swrContext_) ||
                          (inSampleFmt_ != frame_->format) ||
                          (inSampleRate_ != frame_->sample_rate) ||
                          (av_channel_layout_compare(&inChannelLayout_, &frame_->ch_layout) != 0);

        if (needReinit) {
            if (!initResampler(frame_)) {
                LOGE("Failed to re-initialize audio resampler");
                break;
            }
        }

        int bytesPerSample = sizeof(int16_t) * outChannelCount_;
        int remainingBytes = outPcmCapacity - bytesWritten;
        int maxOutSamples = remainingBytes / bytesPerSample;
        if (maxOutSamples <= 0) {
            break;
        }

        uint8_t* outPtr = outPcmBuffer + bytesWritten;
        int convertedSamples = swr_convert(
            swrContext_,
            &outPtr,
            maxOutSamples,
            const_cast<const uint8_t**>(frame_->data),
            frame_->nb_samples
        );

        if (convertedSamples > 0) {
            bytesWritten += convertedSamples * bytesPerSample;
        }
    }

    return bytesWritten;
}

void FfmpegAudioDecoder::flush() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (codecContext_) {
        avcodec_flush_buffers(codecContext_);
    }
    if (swrContext_) {
        swr_close(swrContext_);
        swr_init(swrContext_);
    }
    isEos_ = false;
}

void FfmpegAudioDecoder::close() {
    std::lock_guard<std::mutex> lock(mutex_);
    releaseResources();
}

void FfmpegAudioDecoder::releaseResources() {
    if (swrContext_) {
        swr_free(&swrContext_);
        swrContext_ = nullptr;
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
        if (codecContext_->extradata) {
            av_freep(&codecContext_->extradata);
        }
        avcodec_free_context(&codecContext_);
        codecContext_ = nullptr;
    }
    av_channel_layout_uninit(&inChannelLayout_);
    std::memset(&inChannelLayout_, 0, sizeof(inChannelLayout_));
    inSampleFmt_ = AV_SAMPLE_FMT_NONE;
    inSampleRate_ = 0;
    isEos_ = false;
}

} // namespace nosved

