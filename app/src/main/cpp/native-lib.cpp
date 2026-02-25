/**
 * native-lib.cpp
 *
 * infer_gguf.cpp 와 동일한 sample prompt / sampler 설정을 사용하는 JNI 래퍼.
 * 성능 측정(load time, prefill time, decode time, tok/s)을 logcat 으로 출력한다.
 */

#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <cstdlib>
#include <ctime>
#include <android/log.h>
#include "llama.h"

#define TAG "LlamaInference"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ── 나노초 타이머 헬퍼 ────────────────────────────────────────
static inline int64_t now_ns() {
    struct timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

static llama_model * g_model = nullptr;
static const llama_vocab * g_vocab = nullptr;

// ── infer_gguf.cpp 와 동일한 system / user 메시지 ────────────
static const char * INFER_SYS_CONTENT =
    "너는 오늘의 저녁 메뉴 추천 어시스턴트다.\n"
    "규칙:\n"
    "- 입력에 주어진 '날씨/날짜/요일/월(계절)/시간대(저녁)' 정보를 반드시 반영해 추천한다.\n"
    "- 정보가 부족해도 일단 추천을 제공하되, 추가 질문은 딱 1개만 한다.\n"
    "- 출력은 다음 순서 고정(마크다운 금지):\n"
    "1) 추천 메뉴 1개\n"
    "2) 20~30분 레시피(5단계)\n"
    "3) 장보기 목록(필요 시)\n"
    "4) 추가 질문 1개\n";

static const char * INFER_USER_CONTENT =
    "오늘 저녁 메뉴 추천해줘.\n"
    "참고 정보:\n"
    "- 날짜/요일: 2026-02-20 (금요일)\n"
    "- 월/계절: 2월(겨울)\n"
    "- 날씨: 흐릿/뿌연 느낌, 기온은 낮에 약 14°C / 밤에 0°C 근처로 쌀쌀함\n"
    "- 시간대: 저녁\n"
    "요청:\n"
    "- 이런 날씨/계절/금요일 분위기에 어울리는 '따뜻한 느낌' 메뉴를 우선 추천해줘.\n"
    "- 맵지 않게(아이도 먹기 좋게) 추천해줘.\n";

// ════════════════════════════════════════════════════════════
//  loadModel
// ════════════════════════════════════════════════════════════
extern "C" JNIEXPORT jboolean JNICALL
Java_com_dgy_menusuggestion_LlamaInference_loadModel(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPathStr) {

    const char * model_path = env->GetStringUTFChars(modelPathStr, nullptr);
    LOGI("============================================================");
    LOGI("[LOAD] 모델 경로: %s", model_path);

    // OpenMP spin-wait 크래시 방지
    setenv("KMP_BLOCKTIME",  "0",       1);
    setenv("OMP_WAIT_POLICY","passive", 1);
    setenv("KMP_SETTINGS",   "0",       1);

    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
    }

    ggml_backend_load_all();

    // llama log 를 Android logcat 으로 리다이렉트
    llama_log_set([](enum ggml_log_level level, const char * text, void *) {
        if (level == GGML_LOG_LEVEL_ERROR) {
            LOGE("[llama] %s", text);
        } else if (level == GGML_LOG_LEVEL_WARN) {
            LOGD("[llama] %s", text);
        }
        // INFO/DEBUG 는 생략 (너무 많음)
    }, nullptr);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;  // Android CPU only

    int64_t t0 = now_ns();
    g_model = llama_model_load_from_file(model_path, mparams);
    int64_t t1 = now_ns();
    env->ReleaseStringUTFChars(modelPathStr, model_path);

    if (!g_model) {
        LOGE("[LOAD] 모델 로드 실패");
        return JNI_FALSE;
    }

    g_vocab = llama_model_get_vocab(g_model);
    double load_ms = (t1 - t0) / 1e6;
    LOGI("[LOAD] 모델 로드 완료 — 소요시간: %.1f ms", load_ms);
    LOGI("[LOAD] vocab ptr: %p", (void*)g_vocab);
    LOGI("============================================================");
    return JNI_TRUE;
}

// ════════════════════════════════════════════════════════════
//  generate  — infer_gguf.cpp 동일 sample prompt 사용
// ════════════════════════════════════════════════════════════
extern "C" JNIEXPORT jstring JNICALL
Java_com_dgy_menusuggestion_LlamaInference_generate(
        JNIEnv* env,
        jobject /* this */,
        jstring /* promptStr — ignored, using infer_gguf sample */) {

    if (!g_model || !g_vocab) {
        LOGE("[GEN] 모델/vocab 미로드");
        return env->NewStringUTF("Error: Model not loaded");
    }

    LOGI("============================================================");
    LOGI("[GEN] infer_gguf.cpp 와 동일한 sample prompt 로 추론 시작");

    // ── Chat messages (infer_gguf.cpp 와 동일) ────────────────
    std::vector<llama_chat_message> messages;
    messages.push_back({"system", INFER_SYS_CONTENT});
    messages.push_back({"user",   INFER_USER_CONTENT});

    // ── Chat template 적용 ────────────────────────────────────
    const char * tmpl = llama_model_chat_template(g_model, nullptr);
    LOGD("[GEN] chat template: %.80s", tmpl ? tmpl : "(null)");

    std::vector<char> formatted(8192);
    int fmt_len = llama_chat_apply_template(
        tmpl, messages.data(), messages.size(),
        /*add_ass=*/true,
        formatted.data(), (int)formatted.size());

    if (fmt_len < 0) {
        LOGE("[GEN] chat template 적용 실패");
        return env->NewStringUTF("Error: chat template failed");
    }
    if (fmt_len > (int)formatted.size()) {
        formatted.resize(fmt_len + 1);
        fmt_len = llama_chat_apply_template(
            tmpl, messages.data(), messages.size(),
            true, formatted.data(), (int)formatted.size());
    }
    std::string prompt_str(formatted.begin(), formatted.begin() + fmt_len);
    LOGI("[GEN] 포맷된 프롬프트 길이: %d chars", fmt_len);
    // 프롬프트 앞부분만 출력
    LOGD("[GEN] prompt[:200]: %.200s", prompt_str.c_str());

    // ── Context 생성 ─────────────────────────────────────────
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = 1024; // 메모리 절약: 프롬프트(~256) + 생성(512) 여유
    cparams.n_batch         = 512;  // batch 크기도 n_ctx 절반으로
    cparams.n_threads       = 4;    // 추론 스레드 4개
    cparams.n_threads_batch = 4;

    int64_t tc0 = now_ns();
    llama_context * ctx = llama_init_from_model(g_model, cparams);
    int64_t tc1 = now_ns();

    if (!ctx) {
        LOGE("[GEN] Context 생성 실패");
        return env->NewStringUTF("Error: context creation failed");
    }
    LOGI("[GEN] Context 생성 완료 — %.1f ms", (tc1 - tc0) / 1e6);

    // ── Sampler (infer_gguf.cpp 와 동일: top_p=0.9, temp=0.7) ─
    llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // ── 토크나이징 ───────────────────────────────────────────
    int n_prompt_tokens = -llama_tokenize(
        g_vocab, prompt_str.c_str(), (int)prompt_str.size(),
        nullptr, 0, /*add_special=*/true, /*parse_special=*/true);

    if (n_prompt_tokens <= 0) {
        LOGE("[GEN] 토크나이징 카운트 실패");
        llama_sampler_free(smpl);
        llama_free(ctx);
        return env->NewStringUTF("Error: tokenization failed");
    }

    std::vector<llama_token> prompt_tokens(n_prompt_tokens);
    int tok_ret = llama_tokenize(
        g_vocab, prompt_str.c_str(), (int)prompt_str.size(),
        prompt_tokens.data(), (int)prompt_tokens.size(), true, true);
    if (tok_ret < 0) {
        LOGE("[GEN] 토크나이징 실패: %d", tok_ret);
        llama_sampler_free(smpl);
        llama_free(ctx);
        return env->NewStringUTF("Error: tokenization failed");
    }
    LOGI("[GEN] 프롬프트 토큰 수: %d", n_prompt_tokens);

    // ── Prefill (배치 디코드) ─────────────────────────────────
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), (int)prompt_tokens.size());
    int64_t tp0 = now_ns();
    int decode_ret = llama_decode(ctx, batch);
    int64_t tp1 = now_ns();

    if (decode_ret != 0) {
        LOGE("[GEN] prefill llama_decode 실패: %d", decode_ret);
        llama_sampler_free(smpl);
        llama_free(ctx);
        return env->NewStringUTF("Error: prefill failed");
    }
    double prefill_ms = (tp1 - tp0) / 1e6;
    double prefill_tps = n_prompt_tokens / (prefill_ms / 1000.0);
    LOGI("[PERF] Prefill: %d tokens / %.1f ms = %.2f tok/s", n_prompt_tokens, prefill_ms, prefill_tps);

    // ── 생성 루프 ────────────────────────────────────────────
    std::string result_str;
    result_str.reserve(4096);
    int n_generated = 0;
    const int max_new_tokens = 512; // infer_gguf.cpp DEFAULT_MAX_NEW_TOKENS

    LOGI("[GEN] 텍스트 생성 시작 (max=%d tokens)", max_new_tokens);
    int64_t tg0 = now_ns();

    while (n_generated < max_new_tokens) {
        // 컨텍스트 공간 확인 (infer_gguf.cpp 와 동일)
        int n_ctx_used = (int)(llama_memory_seq_pos_max(llama_get_memory(ctx), 0) + 1);
        if (n_ctx_used + 1 > (int)cparams.n_ctx) {
            LOGI("[GEN] 컨텍스트 초과, 생성 종료 (used=%d)", n_ctx_used);
            break;
        }

        llama_token new_token_id = llama_sampler_sample(smpl, ctx, -1);

        if (llama_vocab_is_eog(g_vocab, new_token_id)) {
            LOGI("[GEN] EOS 감지 — 생성 종료 (token=%d)", n_generated);
            break;
        }

        char buf[256] = {0};
        int n = llama_token_to_piece(g_vocab, new_token_id, buf, sizeof(buf) - 1, 0, true);
        if (n > 0) result_str.append(buf, n);

        batch = llama_batch_get_one(&new_token_id, 1);
        int dr = llama_decode(ctx, batch);
        if (dr != 0) {
            LOGE("[GEN] llama_decode 실패 (ret=%d, token=%d)", dr, n_generated);
            break;
        }

        n_generated++;

        // 10 토큰마다 중간 로그
        if (n_generated % 10 == 0) {
            int64_t now = now_ns();
            double elapsed = (now - tg0) / 1e6;
            double tps = n_generated / (elapsed / 1000.0);
            LOGI("[GEN] %3d tokens | %.1f ms | %.2f tok/s | ...%s",
                 n_generated, elapsed, tps,
                 result_str.size() > 60 ? result_str.c_str() + result_str.size() - 60 : result_str.c_str());
        }
    }

    int64_t tg1 = now_ns();
    double decode_ms  = (tg1 - tg0) / 1e6;
    double decode_tps = n_generated > 0 ? n_generated / (decode_ms / 1000.0) : 0.0;

    // ── 성능 요약 ─────────────────────────────────────────────
    LOGI("============================================================");
    LOGI("[RESULT] 생성된 텍스트:");
    // logcat 한 줄 최대 ~4000자 → 청크로 출력
    const int chunk = 300;
    for (int off = 0; off < (int)result_str.size(); off += chunk) {
        LOGI("[RESULT] %s", result_str.substr(off, chunk).c_str());
    }
    LOGI("------------------------------------------------------------");
    LOGI("[PERF] Prefill  : %d tokens / %.1f ms  = %.2f tok/s", n_prompt_tokens, prefill_ms, prefill_tps);
    LOGI("[PERF] Decode   : %d tokens / %.1f ms  = %.2f tok/s", n_generated,     decode_ms,  decode_tps);
    LOGI("[PERF] Total    : %.1f ms", prefill_ms + decode_ms);
    LOGI("============================================================");

    llama_sampler_free(smpl);
    llama_free(ctx);

    // Kotlin 쪽으로 성능 정보도 함께 반환
    char perf_buf[512];
    snprintf(perf_buf, sizeof(perf_buf),
             "\n\n--- 성능 ---\nPrefill : %d tok / %.0f ms / %.1f tok/s\nDecode  : %d tok / %.0f ms / %.1f tok/s",
             n_prompt_tokens, prefill_ms, prefill_tps,
             n_generated,     decode_ms,  decode_tps);
    result_str += perf_buf;

    return env->NewStringUTF(result_str.c_str());
}

// ════════════════════════════════════════════════════════════
//  unloadModel
// ════════════════════════════════════════════════════════════
extern "C" JNIEXPORT void JNICALL
Java_com_dgy_menusuggestion_LlamaInference_unloadModel(
        JNIEnv* env,
        jobject /* this */) {
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        LOGI("[UNLOAD] 모델 해제 완료");
    }
}

