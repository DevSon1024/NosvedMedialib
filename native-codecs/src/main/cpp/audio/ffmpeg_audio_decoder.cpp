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
    if (mimeType == "audio/true-hd" || mimeType == "audio/truehd") return AV_CODEC_ID_TRUEHD;
    if (mimeType == "audio/vnd.dts" || mimeType == "audio/vnd.dts.hd" || mimeType == "audio/vnd.dts.uhd" || mimeType == "audio/dts") return AV_CODEC_ID_DTS;
    if (mimeType == "audio/flac" || mimeType == "audio/x-flac") return AV_CODEC_ID_FLAC;
    if (mimeType == "audio/alac") return AV_CODEC_ID_ALAC;
    if (mimeType == "audio/opus") return AV_CODEC_ID_OPUS;
    if (mimeType == "audio/vorbis") return AV_CODEC_ID_VORBIS;
    if (mimeType == "audio/mp4a-latm" || mimeType == "audio/aac") return AV_CODEC_ID_AAC;
    if (mimeType == "audio/mpeg" || mimeType == "audio/mp3") return AV_CODEC_ID_MP3;
    if (mimeType == "audio/mpeg-L1") return AV_CODEC_ID_MP1;
    if (mimeType == "audio/mpeg-L2") return AV_CODEC_ID_MP2;
    if (mimeType == "audio/wav" || mimeType == "audio/raw" || mimeType == "audio/x-wav") return AV_CODEC_ID_PCM_S16LE;
    if (mimeType == "audio/x-ms-wma" || mimeType == "audio/wma") return AV_CODEC_ID_WMAV2;
    if (mimeType == "audio/x-ms-wmapro") return AV_CODEC_ID_WMAPRO;
    if (mimeType == "audio/x-ms-wmalossless") return AV_CODEC_ID_WMALOSSLESS;
    if (mimeType == "audio/mlp") return AV_CODEC_ID_MLP;
    return AV_CODEC_ID_NONE;
}

bool FfmpegAudioDecoder::isMimeTypeSupported(const std::string& mimeType) {
    enum AVCodecID codecId = getCodecIdForMimeType(mimeType);
    if (codecId == AV_CODEC_ID_NONE) return false;
    const AVCodec* codec = avcodec_find_decoder(codecId);
    return codec != nullptr;
}

bool FfmpegAudioDecoder::open(const std::string& mimeType,
                              const uint8_t* extraData,
                              int extraDataSize,
                              int sampleRate,
                              int channelCount,
                              int pcmEncoding,
                              int channelRouting,
                              int maxOutputChannels) {
    std::lock_guard<std::mutex> lock(mutex_);
    releaseResources();

    pcmEncoding_ = (pcmEncoding == 4) ? 4 : 2;
    outSampleFmt_ = (pcmEncoding_ == 4) ? AV_SAMPLE_FMT_FLT : AV_SAMPLE_FMT_S16;
    channelRouting_ = channelRouting;
    maxOutputChannels_ = (maxOutputChannels > 0) ? maxOutputChannels : 8;

    enum AVCodecID codecId = getCodecIdForMimeType(mimeType);
    if (codecId == AV_CODEC_ID_NONE) {
        lastErrorMessage_ = "Unsupported audio MIME type: " + mimeType;
        LOGE("%s", lastErrorMessage_.c_str());
        return false;
    }

    const AVCodec* codec = avcodec_find_decoder(codecId);
    if (!codec) {
        lastErrorMessage_ = "No audio decoder found for codec ID: " + std::to_string(codecId);
        LOGE("%s", lastErrorMessage_.c_str());
        return false;
    }

    codecContext_ = avcodec_alloc_context3(codec);
    if (!codecContext_) {
        lastErrorMessage_ = "Failed to allocate audio AVCodecContext";
        LOGE("%s", lastErrorMessage_.c_str());
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
        if (channelRouting_ == AUDIO_ROUTING_FORCE_MONO) {
            outChannelCount_ = 1;
        } else if (channelRouting_ == AUDIO_ROUTING_FORCE_STEREO) {
            outChannelCount_ = (channelCount == 1) ? 1 : 2;
        } else {
            outChannelCount_ = (channelCount <= maxOutputChannels_) ? channelCount : 2;
        }
    } else {
        outChannelCount_ = 2;
    }
    bytesPerSample_ = outChannelCount_ * (pcmEncoding_ == 4 ? 4 : 2);

    int ret = avcodec_open2(codecContext_, codec, nullptr);
    if (ret < 0) {
        char errBuf[AV_ERROR_MAX_STRING_SIZE] = {0};
        av_strerror(ret, errBuf, sizeof(errBuf));
        lastErrorMessage_ = "avcodec_open2 failed for audio " + mimeType + ": " + errBuf;
        LOGE("%s", lastErrorMessage_.c_str());
        releaseResources();
        return false;
    }

    packet_ = av_packet_alloc();
    frame_ = av_frame_alloc();
    if (!packet_ || !frame_) {
        lastErrorMessage_ = "Failed to allocate audio packet or frame";
        LOGE("%s", lastErrorMessage_.c_str());
        releaseResources();
        return false;
    }

    pcmQueue_.reserve(65536);
    queueReadOffset_ = 0;
    isEos_ = false;
    isCodecEof_ = false;
    isDrained_ = false;
    currentPtsUs_ = -1;

    LOGI("FfmpegAudioDecoder opened for %s (%s) [sampleRate=%d, channels=%d, pcmEncoding=%d]",
         mimeType.c_str(), codec->name, outSampleRate_, outChannelCount_, pcmEncoding_);
    return true;
}

void FfmpegAudioDecoder::drainResamplerToQueue() {
    if (!swrContext_) return;

    int rate = (inSampleRate_ > 0) ? inSampleRate_ : outSampleRate_;
    int64_t delay = swr_get_delay(swrContext_, rate);
    while (delay > 0) {
        int drainSamples = std::min(static_cast<int>(delay), 4096);
        int bufferBytes = drainSamples * bytesPerSample_;
        std::vector<uint8_t> tempBuf(bufferBytes);
        uint8_t* outPtr = tempBuf.data();
        int converted = swr_convert(swrContext_, &outPtr, drainSamples, nullptr, 0);
        if (converted <= 0) break;

        if (queueReadOffset_ > 65536) {
            pcmQueue_.erase(pcmQueue_.begin(), pcmQueue_.begin() + queueReadOffset_);
            queueReadOffset_ = 0;
        }
        pcmQueue_.insert(pcmQueue_.end(), tempBuf.data(), tempBuf.data() + (converted * bytesPerSample_));
        delay = swr_get_delay(swrContext_, rate);
    }
}

bool FfmpegAudioDecoder::initResampler(const AVFrame* frame) {
    if (!frame) return false;

    if (swrContext_) {
        // Drain pending samples from existing resampler before destroying to avoid sample loss
        drainResamplerToQueue();
        swr_free(&swrContext_);
        swrContext_ = nullptr;
    }

    inSampleFmt_ = static_cast<enum AVSampleFormat>(frame->format);
    inSampleRate_ = (frame->sample_rate > 0) ? frame->sample_rate : outSampleRate_;
    av_channel_layout_uninit(&inChannelLayout_);
    av_channel_layout_copy(&inChannelLayout_, &frame->ch_layout);

    outSampleRate_ = inSampleRate_;
    int inChannels = frame->ch_layout.nb_channels;
    if (channelRouting_ == AUDIO_ROUTING_FORCE_MONO) {
        outChannelCount_ = 1;
    } else if (channelRouting_ == AUDIO_ROUTING_FORCE_STEREO) {
        outChannelCount_ = (inChannels == 1) ? 1 : 2;
    } else {
        outChannelCount_ = (inChannels <= maxOutputChannels_) ? inChannels : 2;
    }
    bytesPerSample_ = outChannelCount_ * (pcmEncoding_ == 4 ? 4 : 2);

    AVChannelLayout outLayout{};
    if (outChannelCount_ == inChannels && av_channel_layout_check(&frame->ch_layout)) {
        av_channel_layout_copy(&outLayout, &frame->ch_layout);
    } else {
        av_channel_layout_default(&outLayout, outChannelCount_);
    }

    int ret = swr_alloc_set_opts2(
        &swrContext_,
        &outLayout,
        outSampleFmt_,
        outSampleRate_,
        &inChannelLayout_,
        inSampleFmt_,
        inSampleRate_,
        0,
        nullptr
    );

    if (ret < 0 || !swrContext_) {
        LOGE("swr_alloc_set_opts2 failed: %d", ret);
        av_channel_layout_uninit(&outLayout);
        return false;
    }

    // Configure safe downmixing with rematrix volume protection to avoid clipping
    if (inChannels > outChannelCount_) {
        av_opt_set_double(swrContext_, "rematrix_maxval", 1.0, 0);
    }

    ret = swr_init(swrContext_);
    av_channel_layout_uninit(&outLayout);

    if (ret < 0) {
        LOGE("swr_init failed: %d", ret);
        swr_free(&swrContext_);
        swrContext_ = nullptr;
        return false;
    }

    return true;
}

int FfmpegAudioDecoder::sendPacket(const uint8_t* packetData, int packetSize, int64_t ptsUs) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!codecContext_ || !packet_) {
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
        packet_->pts = ptsUs;

        ret = avcodec_send_packet(codecContext_, packet_);
        if (ret < 0 && ret != AVERROR(EAGAIN)) {
            char errBuf[AV_ERROR_MAX_STRING_SIZE] = {0};
            av_strerror(ret, errBuf, sizeof(errBuf));
            lastErrorMessage_ = "avcodec_send_packet audio warning: " + std::string(errBuf);
            LOGW("%s", lastErrorMessage_.c_str());
        }
        return ret;
    }

    return 0;
}

int FfmpegAudioDecoder::sendEos() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!codecContext_) return -1;

    isEos_ = true;
    int ret = avcodec_send_packet(codecContext_, nullptr);
    if (ret < 0 && ret != AVERROR_EOF) {
        char errBuf[AV_ERROR_MAX_STRING_SIZE] = {0};
        av_strerror(ret, errBuf, sizeof(errBuf));
        LOGW("avcodec_send_packet EOS audio error: %s", errBuf);
    }
    return ret;
}

int FfmpegAudioDecoder::receivePcm(uint8_t* outPcmBuffer, int outPcmCapacity, int64_t* outPtsUs) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!codecContext_ || !frame_ || !outPcmBuffer || outPcmCapacity <= 0) {
        return -1;
    }

    // Attempt to decode frames while queue has insufficient data and codec is not at EOF
    while ((pcmQueue_.size() - queueReadOffset_) < static_cast<size_t>(outPcmCapacity) && !isCodecEof_) {
        int rec = avcodec_receive_frame(codecContext_, frame_);
        if (rec == 0) {
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

            int64_t delay = swr_get_delay(swrContext_, inSampleRate_);
            int64_t maxOutSamples = av_rescale_rnd(delay + frame_->nb_samples, outSampleRate_, inSampleRate_, AV_ROUND_UP);
            if (maxOutSamples <= 0) maxOutSamples = frame_->nb_samples;

            int bufferBytes = static_cast<int>(maxOutSamples) * bytesPerSample_;
            std::vector<uint8_t> tempBuf(bufferBytes);
            uint8_t* outPtr = tempBuf.data();
            int converted = swr_convert(
                swrContext_,
                &outPtr,
                static_cast<int>(maxOutSamples),
                const_cast<const uint8_t**>(frame_->data),
                frame_->nb_samples
            );

            if (converted > 0) {
                if (queueReadOffset_ > 65536) {
                    pcmQueue_.erase(pcmQueue_.begin(), pcmQueue_.begin() + queueReadOffset_);
                    queueReadOffset_ = 0;
                }
                pcmQueue_.insert(pcmQueue_.end(), tempBuf.data(), tempBuf.data() + (converted * bytesPerSample_));
            }

            if (currentPtsUs_ < 0 && frame_->pts != AV_NOPTS_VALUE) {
                currentPtsUs_ = frame_->pts;
            }
        } else if (rec == AVERROR(EAGAIN)) {
            break;
        } else if (rec == AVERROR_EOF) {
            isCodecEof_ = true;
            drainResamplerToQueue();
            break;
        } else {
            char errBuf[AV_ERROR_MAX_STRING_SIZE] = {0};
            av_strerror(rec, errBuf, sizeof(errBuf));
            LOGW("avcodec_receive_frame audio error: %s", errBuf);
            break;
        }
    }

    size_t availableBytes = pcmQueue_.size() - queueReadOffset_;
    if (availableBytes == 0) {
        if (isCodecEof_) {
            isDrained_ = true;
            return -1; // Complete EOF
        }
        return 0; // Need more input packets
    }

    int bytesToRead = std::min(outPcmCapacity, static_cast<int>(availableBytes));
    std::memcpy(outPcmBuffer, pcmQueue_.data() + queueReadOffset_, bytesToRead);
    queueReadOffset_ += bytesToRead;

    if (queueReadOffset_ >= pcmQueue_.size()) {
        pcmQueue_.clear();
        queueReadOffset_ = 0;
    }

    if (outPtsUs) {
        *outPtsUs = currentPtsUs_;
    }
    if (currentPtsUs_ >= 0) {
        int samplesRead = bytesToRead / bytesPerSample_;
        currentPtsUs_ += (static_cast<int64_t>(samplesRead) * 1000000LL) / outSampleRate_;
    }

    return bytesToRead;
}

int FfmpegAudioDecoder::decode(const uint8_t* packetData, int packetSize, uint8_t* outPcmBuffer, int outPcmCapacity) {
    if (packetData && packetSize > 0) {
        sendPacket(packetData, packetSize, 0);
    } else if (packetSize == 0) {
        sendEos();
    }
    int64_t pts = 0;
    return receivePcm(outPcmBuffer, outPcmCapacity, &pts);
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
    pcmQueue_.clear();
    queueReadOffset_ = 0;
    isEos_ = false;
    isCodecEof_ = false;
    isDrained_ = false;
    currentPtsUs_ = -1;
}

void FfmpegAudioDecoder::close() {
    std::lock_guard<std::mutex> lock(mutex_);
    releaseResources();
}

std::string FfmpegAudioDecoder::getLastErrorMessage() const {
    return lastErrorMessage_;
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
    pcmQueue_.clear();
    queueReadOffset_ = 0;
    isEos_ = false;
    isCodecEof_ = false;
    isDrained_ = false;
    currentPtsUs_ = -1;
}

} // namespace nosved
