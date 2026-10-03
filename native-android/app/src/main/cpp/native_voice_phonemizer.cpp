#include <jni.h>

#include <dlfcn.h>
#include <cstdint>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
using EspeakInitialize = int (*)(int, int, const char*, int);
using EspeakSetVoiceByName = int (*)(const char*);
using EspeakTextToPhonemes = const char* (*)(const void**, int, int);

std::mutex voice_mutex;
void* espeak_handle = nullptr;
EspeakInitialize espeak_initialize = nullptr;
EspeakSetVoiceByName espeak_set_voice = nullptr;
EspeakTextToPhonemes espeak_text_to_phonemes = nullptr;
bool initialized = false;

constexpr int kAudioOutputSynchronous = 2;
constexpr int kCharsUtf8 = 1;
constexpr int kPhonemesIpa = 2;
constexpr int kMaxIterations = 2048;
constexpr std::size_t kMaxOutputBytes = 16000;

std::vector<char> bytes(JNIEnv* env, jbyteArray input) {
    const jsize length = env->GetArrayLength(input);
    std::vector<char> output(static_cast<std::size_t>(length) + 1U, '\0');
    env->GetByteArrayRegion(input, 0, length, reinterpret_cast<jbyte*>(output.data()));
    return output;
}

void throw_illegal_state(JNIEnv* env, const char* message) {
    const jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) env->ThrowNew(type, message);
}

template <typename T>
T symbol(const char* name) {
    void* value = dlsym(espeak_handle, name);
    if (value == nullptr) throw std::runtime_error(name);
    return reinterpret_cast<T>(value);
}
}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_app_roadstr_roadtest_NativeEspeakBridge_nativeInitialize(
    JNIEnv* env,
    jobject,
    jbyteArray data_parent
) {
    std::lock_guard<std::mutex> guard(voice_mutex);
    if (initialized) return 0;
    try {
        espeak_handle = dlopen("libespeak-ng.so", RTLD_NOW | RTLD_LOCAL);
        if (espeak_handle == nullptr) throw std::runtime_error("libespeak-ng.so");
        espeak_initialize = symbol<EspeakInitialize>("espeak_Initialize");
        espeak_set_voice = symbol<EspeakSetVoiceByName>("espeak_SetVoiceByName");
        espeak_text_to_phonemes = symbol<EspeakTextToPhonemes>("espeak_TextToPhonemes");
        const auto parent = bytes(env, data_parent);
        const int result = espeak_initialize(kAudioOutputSynchronous, 0, parent.data(), 0);
        if (result < 0) throw std::runtime_error("espeak_Initialize");
        initialized = true;
        return result;
    } catch (const std::exception&) {
        throw_illegal_state(env, "Native eSpeak initialization failed");
        return -1;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_app_roadstr_roadtest_NativeEspeakBridge_nativePhonemize(
    JNIEnv* env,
    jobject,
    jbyteArray input_text,
    jbyteArray input_voice
) {
    std::lock_guard<std::mutex> guard(voice_mutex);
    if (!initialized) {
        throw_illegal_state(env, "Native eSpeak is not initialized");
        return nullptr;
    }
    const auto text = bytes(env, input_text);
    const auto voice = bytes(env, input_voice);
    if (espeak_set_voice(voice.data()) < 0) {
        throw_illegal_state(env, "Requested eSpeak voice is unavailable");
        return nullptr;
    }
    const void* cursor = text.data();
    std::string output;
    for (int iteration = 0; cursor != nullptr; ++iteration) {
        if (iteration >= kMaxIterations) {
            throw_illegal_state(env, "Native eSpeak did not finish phonemization");
            return nullptr;
        }
        const void* before = cursor;
        const char* phonemes = espeak_text_to_phonemes(&cursor, kCharsUtf8, kPhonemesIpa);
        if (phonemes != nullptr) output.append(phonemes);
        if (cursor != nullptr && cursor == before) {
            throw_illegal_state(env, "Native eSpeak did not advance its input");
            return nullptr;
        }
        if (output.size() > kMaxOutputBytes) {
            throw_illegal_state(env, "Native eSpeak output exceeded its bound");
            return nullptr;
        }
    }
    jbyteArray result = env->NewByteArray(static_cast<jsize>(output.size()));
    if (result != nullptr && !output.empty()) {
        env->SetByteArrayRegion(
            result,
            0,
            static_cast<jsize>(output.size()),
            reinterpret_cast<const jbyte*>(output.data())
        );
    }
    return result;
}
