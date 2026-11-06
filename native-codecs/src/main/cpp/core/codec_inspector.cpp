#include "codec_inspector.h"
#include "../common/logging.h"
#include <sstream>
#include <set>
#include <vector>
#include <algorithm>

#if defined(__clang__)
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
#endif

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/pixdesc.h>
#include <libavutil/samplefmt.h>
#include <libavutil/avutil.h>
#include <dav1d/dav1d.h>
#include <dav1d/version.h>
}

namespace nosved {

static std::string escapeJson(const std::string& input) {
    std::ostringstream ss;
    for (char c : input) {
        switch (c) {
            case '"': ss << "\\\""; break;
            case '\\': ss << "\\\\"; break;
            case '\b': ss << "\\b"; break;
            case '\f': ss << "\\f"; break;
            case '\n': ss << "\\n"; break;
            case '\r': ss << "\\r"; break;
            case '\t': ss << "\\t"; break;
            default:
                if (static_cast<unsigned char>(c) < ' ') {
                    // Control char
                } else {
                    ss << c;
                }
                break;
        }
    }
    return ss.str();
}

bool CodecInspector::isDecoderCompiled(const std::string& codecName) {
    if (codecName.empty()) return false;
    const AVCodec* codec = avcodec_find_decoder_by_name(codecName.c_str());
    return codec != nullptr;
}

bool CodecInspector::isCodecIdCompiled(int codecId) {
    if (codecId <= 0) return false;
    const AVCodec* codec = avcodec_find_decoder(static_cast<AVCodecID>(codecId));
    return codec != nullptr;
}

bool CodecInspector::testDecoderInitialization(const std::string& codecName) {
    if (codecName.empty()) return false;
    const AVCodec* codec = avcodec_find_decoder_by_name(codecName.c_str());
    if (!codec) return false;

    AVCodecContext* ctx = avcodec_alloc_context3(codec);
    if (!ctx) return false;

    avcodec_free_context(&ctx);
    return true;
}

std::string CodecInspector::getCompiledDecodersJson(int mediaType) {
    std::ostringstream ss;
    ss << "[";

    void* opaque = nullptr;
    const AVCodec* codec = nullptr;
    bool first = true;

    while ((codec = av_codec_iterate(&opaque)) != nullptr) {
        if (!av_codec_is_decoder(codec)) {
            continue;
        }

        if (mediaType == 1 && codec->type != AVMEDIA_TYPE_VIDEO) {
            continue;
        }
        if (mediaType == 2 && codec->type != AVMEDIA_TYPE_AUDIO) {
            continue;
        }

        if (!first) {
            ss << ",";
        }
        first = false;

        ss << "{";
        ss << "\"name\":\"" << escapeJson(codec->name ? codec->name : "") << "\",";
        ss << "\"long_name\":\"" << escapeJson(codec->long_name ? codec->long_name : "") << "\",";
        ss << "\"id\":" << static_cast<int>(codec->id) << ",";
        ss << "\"type\":\"" << (codec->type == AVMEDIA_TYPE_VIDEO ? "video" : (codec->type == AVMEDIA_TYPE_AUDIO ? "audio" : "other")) << "\",";

        // Profiles
        ss << "\"profiles\":[";
        if (codec->profiles) {
            bool firstP = true;
            for (const AVProfile* p = codec->profiles; p->profile != AV_PROFILE_UNKNOWN; ++p) {
                if (!firstP) ss << ",";
                firstP = false;
                ss << "{\"id\":" << p->profile << ",\"name\":\"" << escapeJson(p->name ? p->name : "") << "\"}";
            }
        }
        ss << "],";

        // Formats and bit depths
        if (codec->type == AVMEDIA_TYPE_VIDEO) {
            ss << "\"pixel_formats\":[";
            std::set<int> bitDepths;
            if (codec->pix_fmts) {
                bool firstF = true;
                for (const enum AVPixelFormat* pf = codec->pix_fmts; *pf != AV_PIX_FMT_NONE; ++pf) {
                    const char* fmtName = av_get_pix_fmt_name(*pf);
                    if (fmtName) {
                        if (!firstF) ss << ",";
                        firstF = false;
                        ss << "\"" << escapeJson(fmtName) << "\"";
                    }
                    const AVPixFmtDescriptor* desc = av_pix_fmt_desc_get(*pf);
                    if (desc && desc->comp[0].depth > 0) {
                        bitDepths.insert(desc->comp[0].depth);
                    }
                }
            }
            ss << "],";

            ss << "\"bit_depths\":[";
            bool firstD = true;
            for (int depth : bitDepths) {
                if (!firstD) ss << ",";
                firstD = false;
                ss << depth;
            }
            ss << "]";
        } else if (codec->type == AVMEDIA_TYPE_AUDIO) {
            ss << "\"sample_formats\":[";
            if (codec->sample_fmts) {
                bool firstF = true;
                for (const enum AVSampleFormat* sf = codec->sample_fmts; *sf != AV_SAMPLE_FMT_NONE; ++sf) {
                    const char* fmtName = av_get_sample_fmt_name(*sf);
                    if (fmtName) {
                        if (!firstF) ss << ",";
                        firstF = false;
                        ss << "\"" << escapeJson(fmtName) << "\"";
                    }
                }
            }
            ss << "],";

            ss << "\"supported_sample_rates\":[";
            if (codec->supported_samplerates) {
                bool firstR = true;
                for (const int* sr = codec->supported_samplerates; *sr != 0; ++sr) {
                    if (!firstR) ss << ",";
                    firstR = false;
                    ss << *sr;
                }
            }
            ss << "]";
        } else {
            ss << "\"pixel_formats\":[],\"bit_depths\":[]";
        }

        ss << "}";
    }

    ss << "]";
    return ss.str();
}

std::string CodecInspector::getDecoderDetailsJson(const std::string& codecName) {
    if (codecName.empty()) return "{}";
    const AVCodec* codec = avcodec_find_decoder_by_name(codecName.c_str());
    if (!codec) return "{}";

    std::ostringstream ss;
    ss << "{";
    ss << "\"name\":\"" << escapeJson(codec->name ? codec->name : "") << "\",";
    ss << "\"long_name\":\"" << escapeJson(codec->long_name ? codec->long_name : "") << "\",";
    ss << "\"id\":" << static_cast<int>(codec->id) << ",";
    ss << "\"type\":\"" << (codec->type == AVMEDIA_TYPE_VIDEO ? "video" : (codec->type == AVMEDIA_TYPE_AUDIO ? "audio" : "other")) << "\",";

    ss << "\"profiles\":[";
    if (codec->profiles) {
        bool firstP = true;
        for (const AVProfile* p = codec->profiles; p->profile != AV_PROFILE_UNKNOWN; ++p) {
            if (!firstP) ss << ",";
            firstP = false;
            ss << "{\"id\":" << p->profile << ",\"name\":\"" << escapeJson(p->name ? p->name : "") << "\"}";
        }
    }
    ss << "],";

    if (codec->type == AVMEDIA_TYPE_VIDEO) {
        ss << "\"pixel_formats\":[";
        std::set<int> bitDepths;
        if (codec->pix_fmts) {
            bool firstF = true;
            for (const enum AVPixelFormat* pf = codec->pix_fmts; *pf != AV_PIX_FMT_NONE; ++pf) {
                const char* fmtName = av_get_pix_fmt_name(*pf);
                if (fmtName) {
                    if (!firstF) ss << ",";
                    firstF = false;
                    ss << "\"" << escapeJson(fmtName) << "\"";
                }
                const AVPixFmtDescriptor* desc = av_pix_fmt_desc_get(*pf);
                if (desc && desc->comp[0].depth > 0) {
                    bitDepths.insert(desc->comp[0].depth);
                }
            }
        }
        ss << "],";

        ss << "\"bit_depths\":[";
        bool firstD = true;
        for (int depth : bitDepths) {
            if (!firstD) ss << ",";
            firstD = false;
            ss << depth;
        }
        ss << "],";
    } else {
        ss << "\"pixel_formats\":[],\"bit_depths\":[],";
    }

    AVCodecContext* testCtx = avcodec_alloc_context3(codec);
    bool initializable = (testCtx != nullptr);
    if (testCtx) {
        avcodec_free_context(&testCtx);
    }
    ss << "\"initializable\":" << (initializable ? "true" : "false");

    ss << "}";
    return ss.str();
}

std::string CodecInspector::getDav1dCapabilitiesJson() {
    std::ostringstream ss;
    ss << "{";
    ss << "\"engine\":\"dav1d\",";
    ss << "\"version\":\"" << escapeJson(dav1d_version()) << "\",";
    ss << "\"supported_profiles\":[\"Main (Profile 0)\",\"High (Profile 1)\",\"Professional (Profile 2)\"],";
    ss << "\"supported_bit_depths\":[8,10],";
    ss << "\"supported_pixel_layouts\":[\"I400 (Monochrome)\",\"I420 (4:2:0)\",\"I422 (4:2:2)\",\"I444 (4:4:4)\"],";
    ss << "\"max_software_width\":4096,";
    ss << "\"max_software_height\":2304,";
    ss << "\"supports_10bit_hdr\":true,";
    ss << "\"supports_film_grain\":true,";
    ss << "\"supports_multithreading\":true";
    ss << "}";
    return ss.str();
}

} // namespace nosved

#if defined(__clang__)
#pragma clang diagnostic pop
#endif
