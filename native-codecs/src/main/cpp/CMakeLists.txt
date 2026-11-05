cmake_minimum_required(VERSION 3.22.1)

project("native_codecs")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(FFMPEG_INCLUDE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/ffmpeg/include)
set(DAV1D_INCLUDE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/dav1d/include)
set(FFMPEG_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

include_directories(
    ${FFMPEG_INCLUDE_DIR}
    ${DAV1D_INCLUDE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
    ${CMAKE_CURRENT_SOURCE_DIR}/core
    ${CMAKE_CURRENT_SOURCE_DIR}/common
    ${CMAKE_CURRENT_SOURCE_DIR}/audio
    ${CMAKE_CURRENT_SOURCE_DIR}/video
)

# Imported FFmpeg shared libraries
add_library(avutil SHARED IMPORTED)
set_target_properties(avutil PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIB_DIR}/libavutil.so)

add_library(swresample SHARED IMPORTED)
set_target_properties(swresample PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIB_DIR}/libswresample.so)

add_library(swscale SHARED IMPORTED)
set_target_properties(swscale PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIB_DIR}/libswscale.so)

add_library(avcodec SHARED IMPORTED)
set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIB_DIR}/libavcodec.so)

add_library(avformat SHARED IMPORTED)
set_target_properties(avformat PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIB_DIR}/libavformat.so)

# Imported dav1d shared library
add_library(dav1d SHARED IMPORTED)
set_target_properties(dav1d PROPERTIES IMPORTED_LOCATION ${FFMPEG_LIB_DIR}/libdav1d.so)

# Native codecs library
add_library(${CMAKE_PROJECT_NAME} SHARED
    jni/native_bridge.cpp
    core/ffmpeg_init.cpp
    core/codec_inspector.cpp
    audio/ffmpeg_audio_decoder.cpp
    video/ffmpeg_video_decoder.cpp
    video/dav1d_video_decoder.cpp
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    avcodec
    avformat
    avutil
    swresample
    swscale
    dav1d
    android
    log
)
