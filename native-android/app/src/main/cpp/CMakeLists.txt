cmake_minimum_required(VERSION 3.22.1)
project(roadstr_voice_jni LANGUAGES CXX)

add_library(roadstr_voice_jni SHARED native_voice_phonemizer.cpp)
target_compile_features(roadstr_voice_jni PRIVATE cxx_std_17)
target_compile_options(roadstr_voice_jni PRIVATE -Wall -Wextra -Werror)
target_link_libraries(roadstr_voice_jni PRIVATE dl log)
