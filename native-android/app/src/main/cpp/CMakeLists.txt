cmake_minimum_required(VERSION 3.22.1)
project(roadstr_voice_jni LANGUAGES CXX)

add_library(roadstr_voice_jni SHARED native_voice_phonemizer.cpp)
target_compile_features(roadstr_voice_jni PRIVATE cxx_std_17)
target_compile_options(roadstr_voice_jni PRIVATE -Wall -Wextra -Werror)
target_link_libraries(roadstr_voice_jni PRIVATE dl log)
# 16 KiB page sizes: segments aligned to 4 KiB are refused or warned about on Android 15+.
target_link_options(roadstr_voice_jni PRIVATE -Wl,-z,max-page-size=16384)
