package com.dgy.menusuggestion

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.dgy.menusuggestion.ui.MenuSuggestionScreen
import com.dgy.menusuggestion.ui.theme.MenusuggestionTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LlamaInference"
        private const val MODEL_FILENAME = "model.gguf"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "====== MainActivity onCreate ======")
        enableEdgeToEdge()

        setContent {
            MenusuggestionTheme {
                val context = LocalContext.current

                LaunchedEffect(Unit) {
                    // 1) 모델 파일 경로 결정 (우선순위: filesDir → /sdcard/Download)
                    val internalModel = File(context.filesDir, MODEL_FILENAME)
                    val sdcardModel   = File("/sdcard/Download/$MODEL_FILENAME")

                    val modelFile: File? = when {
                        internalModel.exists() && internalModel.length() > 0 -> {
                            Log.i(TAG, "[PATH] 내부 저장소 모델 사용: ${internalModel.absolutePath} (${internalModel.length()} bytes)")
                            internalModel
                        }
                        sdcardModel.exists() && sdcardModel.length() > 0 -> {
                            Log.i(TAG, "[PATH] SD카드 모델 사용: ${sdcardModel.absolutePath} (${sdcardModel.length()} bytes)")
                            sdcardModel
                        }
                        else -> null
                    }

                    if (modelFile == null) {
                        val msg = "모델 파일 없음. adb push 로 ${internalModel.absolutePath} 에 복사하세요."
                        Log.e(TAG, msg)
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                        return@LaunchedEffect
                    }

                    withContext(Dispatchers.IO) {
                        try {
                            val inference = LlamaInference()

                            // 2) 모델 로드
                            Log.i(TAG, "[STEP1] 모델 로드 시작...")
                            val t0 = System.currentTimeMillis()
                            val loaded = inference.loadModel(modelFile.absolutePath)
                            val loadMs = System.currentTimeMillis() - t0
                            Log.i(TAG, "[STEP1] 모델 로드 결과: $loaded  소요: ${loadMs}ms")

                            launch(Dispatchers.Main) {
                                Toast.makeText(context,
                                    "모델 로드: $loaded (${loadMs}ms)",
                                    Toast.LENGTH_SHORT).show()
                            }

                            if (!loaded) {
                                Log.e(TAG, "[STEP1] 모델 로드 실패 — 추론 중단")
                                return@withContext
                            }

                            // 3) infer_gguf.cpp 와 동일한 sample prompt 로 추론
                            //    (native-lib.cpp 에서 prompt 파라미터는 무시하고
                            //     INFER_USER_CONTENT 고정 사용)
                            val samplePrompt = "오늘 저녁 메뉴 추천해줘."
                            Log.i(TAG, "[STEP2] 추론 시작 (infer_gguf.cpp 동일 prompt)")

                            val t1 = System.currentTimeMillis()
                            val result = inference.generate(samplePrompt)
                            val genMs = System.currentTimeMillis() - t1
                            Log.i(TAG, "[STEP2] 추론 완료 — 소요: ${genMs}ms")
                            Log.i(TAG, "[STEP2] 결과 길이: ${result.length} chars")

                            // 결과를 로그에 분할 출력 (logcat 한 줄 제한 대응)
                            val chunkSize = 400
                            var offset = 0
                            while (offset < result.length) {
                                val end = minOf(offset + chunkSize, result.length)
                                Log.i(TAG, "[OUTPUT] ${result.substring(offset, end)}")
                                offset = end
                            }

                            // Toast 로도 결과 일부 표시
                            val preview = result.take(80).replace("\n", " ")
                            launch(Dispatchers.Main) {
                                Toast.makeText(context,
                                    "추론완료(${genMs}ms)\n$preview...",
                                    Toast.LENGTH_LONG).show()
                            }

                            inference.unloadModel()
                            Log.i(TAG, "[STEP3] 모델 언로드 완료")

                        } catch (e: Exception) {
                            Log.e(TAG, "[ERROR] 예외 발생: ${e.message}", e)
                            launch(Dispatchers.Main) {
                                Toast.makeText(context,
                                    "오류: ${e.message}",
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                MenuSuggestionScreen()
            }
        }
    }
}


