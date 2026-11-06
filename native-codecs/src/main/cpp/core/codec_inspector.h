#ifndef NOSVED_MEDIA_CODEC_INSPECTOR_H
#define NOSVED_MEDIA_CODEC_INSPECTOR_H

#include <string>
#include <vector>

namespace nosved {

class CodecInspector {
public:
    static bool isDecoderCompiled(const std::string& codecName);
    static bool isCodecIdCompiled(int codecId);
    static std::string getCompiledDecodersJson(int mediaType); // 0=all, 1=video, 2=audio
    static std::string getDecoderDetailsJson(const std::string& codecName);
    static std::string getDav1dCapabilitiesJson();
    static bool testDecoderInitialization(const std::string& codecName);
};

} // namespace nosved

#endif // NOSVED_MEDIA_CODEC_INSPECTOR_H

