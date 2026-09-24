# SPDX-License-Identifier: MIT
cmake_minimum_required(VERSION 3.10.2)

project(whisper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_definitions(-DWHISPER_BUILD)

# Compiler optimization flags
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -fPIC")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fPIC")

# ARM NEON SIMD optimizations
if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -mfpu=neon")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon")
endif()

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/src
)

add_library(whisper SHARED
    jni_whisper.cpp
    src/whisper.cpp
    src/ggml.c
    src/ggml-alloc.c
    src/ggml-backend.c
    src/ggml-quants.c
)

find_library(log-lib log)

target_link_libraries(whisper
    ${log-lib}
)
