cmake_minimum_required(VERSION 3.22.1)

project("menusuggestion")

# Define the path to the llama.cpp source code
# Since the user provided an absolute path, we try to use it.
# However, usually it's better to have it inside the project.
# We will assume we can access it.
set(LLAMA_ROOT "/home/joon/work/coding/android/project/llama_cpp/llama.cpp")

# Check if the directory exists
if(NOT EXISTS ${LLAMA_ROOT})
    message(FATAL_ERROR "llama.cpp not found at ${LLAMA_ROOT}")
endif()

# ── 빌드 타입 강제 Release ──────────────────────────────────────────
# Android Studio가 Debug 빌드로 실행해도 llama.cpp는 Release 최적화 적용
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# ── 컴파일러 최적화 플래그 ────────────────────────────────────────────
# 벤치마크(llama-bench)와 동일한 조건: -O3 + NEON i8mm + dotprod
# 주의: -ffast-math는 -ffinite-math-only 를 내포하여 llama.cpp 빌드 실패 →
#       llama.cpp는 명시적으로 -fno-finite-math-only 를 요구함
set(OPT_FLAGS "-O3 -march=armv8.4-a+dotprod+i8mm -fno-finite-math-only -DNDEBUG")
set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}   ${OPT_FLAGS}")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${OPT_FLAGS}")
message(STATUS "[APP] Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "[APP] C_FLAGS: ${CMAKE_C_FLAGS}")

# ── llama.cpp 빌드 설정 ───────────────────────────────────────────────
set(LLAMA_BUILD_COMMON ON)
set(LLAMA_BUILD_TESTS OFF)
set(LLAMA_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_SERVER OFF)

# OpenMP (멀티스레드 병렬화)
set(GGML_OPENMP ON CACHE BOOL "Enable OpenMP" FORCE)

# ── KleidiAI (arm64 전용 최적화 커널) ────────────────────────────────
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "Enable KleidiAI" FORCE)
    # kleidiai는 llama.cpp 내부 서브모듈 경로 사용
    if(EXISTS "${LLAMA_ROOT}/kleidiai")
        set(KLEIDIAI_LOCAL_SRC "${LLAMA_ROOT}/kleidiai")
        message(STATUS "[APP] KleidiAI path: ${KLEIDIAI_LOCAL_SRC}")
    endif()
else()
    set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "Enable KleidiAI" FORCE)
endif()

# Add llama.cpp as subdirectory
# Note: Since it is outside the source tree, we need to specify the binary directory
add_subdirectory(${LLAMA_ROOT} ${CMAKE_BINARY_DIR}/llama_cpp_build)

# Create our shared library
add_library(${CMAKE_PROJECT_NAME} SHARED
    native-lib.cpp
)

# Link against llama and ggml
target_link_libraries(${CMAKE_PROJECT_NAME}
    PRIVATE
    llama
    ggml
    ggml-base
    android
    log
)

