#ifndef NOSVED_FFMPEG_AUDIO_DECODER_H
#define NOSVED_FFMPEG_AUDIO_DECODER_H

#include <string>
#include <mutex>
#include <vector>
#include <cstdint>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/channel_layout.h>
#include <libavutil/opt.h>
#include <libavutil/samplefmt.h>
#include <libswresample/swresample.h>
}

namespace nosved {

enum AudioChannelRouting {
    AUDIO_ROUTING_AUTO = 0,
    AUDIO_ROUTING_FORCE_STEREO = 1,
    AUDIO_ROUTING_FORCE_MONO = 2
};

class FfmpegAudioDecoder {
public:
    FfmpegAudioDecoder();
    ~FfmpegAudioDecoder();

    bool open(const std::string& mimeType,
              const uint8_t* extraData,
              int extraDataSize,
              int sampleRate,
              int channelCount,
              int pcmEncoding = 2,
              int channelRouting = AUDIO_ROUTING_AUTO,
              int maxOutputChannels = 8);

    int sendPacket(const uint8_t* packetData, int packetSize, int64_t ptsUs);
    int sendEos();
    int receivePcm(uint8_t* outPcmBuffer, int outPcmCapacity, int64_t* outPtsUs);
    int decode(const uint8_t* packetData, int packetSize, uint8_t* outPcmBuffer, int outPcmCapacity);

    void flush();
    void close();

    int getSampleRate() const { return outSampleRate_; }
    int getChannelCount() const { return outChannelCount_; }
    int getPcmEncoding() const { return pcmEncoding_; }
    std::string getLastErrorMessage() const;

    static enum AVCodecID getCodecIdForMimeType(const std::string& mimeType);
    static bool isMimeTypeSupported(const std::string& mimeType);

private:
    void releaseResources();
    bool initResampler(const AVFrame* frame);
    void drainResamplerToQueue();

    std::mutex mutex_;
    AVCodecContext* codecContext_{nullptr};
    AVPacket* packet_{nullptr};
    AVFrame* frame_{nullptr};
    SwrContext* swrContext_{nullptr};

    int pcmEncoding_{2}; // 2 = ENCODING_PCM_16BIT, 4 = ENCODING_PCM_FLOAT
    int channelRouting_{AUDIO_ROUTING_AUTO};
    int maxOutputChannels_{8};

    enum AVSampleFormat outSampleFmt_{AV_SAMPLE_FMT_S16};
    int outSampleRate_{48000};
    int outChannelCount_{2};
    int bytesPerSample_{4}; // outChannelCount_ * (pcmEncoding_ == 4 ? 4 : 2)

    AVChannelLayout inChannelLayout_{};
    enum AVSampleFormat inSampleFmt_{AV_SAMPLE_FMT_NONE};
    int inSampleRate_{0};

    bool isEos_{false};
    bool isCodecEof_{false};
    bool isDrained_{false};

    int64_t currentPtsUs_{-1};

    std::vector<uint8_t> pcmQueue_;
    size_t queueReadOffset_{0};
    std::string lastErrorMessage_;
};

} // namespace nosved

#endif // NOSVED_FFMPEG_AUDIO_DECODER_H
