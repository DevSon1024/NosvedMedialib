#ifndef NOSVED_FFMPEG_AUDIO_DECODER_H
#define NOSVED_FFMPEG_AUDIO_DECODER_H

#include <string>
#include <mutex>
#include <cstdint>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/channel_layout.h>
#include <libavutil/opt.h>
#include <libavutil/samplefmt.h>
#include <libswresample/swresample.h>
}

namespace nosved {

class FfmpegAudioDecoder {
public:
    FfmpegAudioDecoder();
    ~FfmpegAudioDecoder();

    bool open(const std::string& mimeType, const uint8_t* extraData, int extraDataSize, int sampleRate, int channelCount);
    int decode(const uint8_t* packetData, int packetSize, uint8_t* outPcmBuffer, int outPcmCapacity);
    void flush();
    void close();

    int getSampleRate() const { return outSampleRate_; }
    int getChannelCount() const { return outChannelCount_; }

    static enum AVCodecID getCodecIdForMimeType(const std::string& mimeType);
    static bool isMimeTypeSupported(const std::string& mimeType);

private:
    void releaseResources();
    bool initResampler(const AVFrame* frame);

    std::mutex mutex_;
    AVCodecContext* codecContext_{nullptr};
    AVPacket* packet_{nullptr};
    AVFrame* frame_{nullptr};
    SwrContext* swrContext_{nullptr};

    int outSampleRate_{48000};
    int outChannelCount_{2};
    AVChannelLayout inChannelLayout_{};
    enum AVSampleFormat inSampleFmt_{AV_SAMPLE_FMT_NONE};
    int inSampleRate_{0};
    bool isEos_{false};
};

} // namespace nosved

#endif // NOSVED_FFMPEG_AUDIO_DECODER_H

