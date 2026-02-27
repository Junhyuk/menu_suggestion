package com.dgy.menusuggestion

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dgy.menusuggestion.data.MealTimeTab
import com.dgy.menusuggestion.data.PreferenceRepository
import com.dgy.menusuggestion.weather.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "LlamaInference"

// ── UI 상태 ──────────────────────────────────────────────────────
sealed class LlamaUiState {
    object Idle : LlamaUiState()
    object LoadingModel : LlamaUiState()
    data class Inferring(val step: String, val partial: String = "") : LlamaUiState()
    data class Step1Done(val menuName: String, val fullText: String) : LlamaUiState()
    data class Step2Done(val menuName: String, val step1Text: String, val step2Text: String) : LlamaUiState()
    data class Step3Done(                    // 3차: 반찬 + 디저트 완료
        val menuName: String,
        val step1Text: String,
        val step2Text: String,
        val step3Text: String,               // 반찬/차/디저트
        val isLiked: Boolean = false,        // 현재 좋아요 여부
        val rating: Int = 0                  // 현재 평점 (0 = 미평가)
    ) : LlamaUiState()
    data class Error(val message: String) : LlamaUiState()
}

class LlamaViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<LlamaUiState>(LlamaUiState.Idle)
    val state: StateFlow<LlamaUiState> = _state.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val inference = LlamaInference()
    private var modelLoaded = false

    private val repo by lazy { PreferenceRepository.getInstance(application) }

    // ── 모델 로드 ─────────────────────────────────────────────────
    fun loadModelIfNeeded() {
        if (modelLoaded) return
        viewModelScope.launch {
            _state.value = LlamaUiState.LoadingModel
            withContext(Dispatchers.IO) {
                try {
                    val ctx = getApplication<Application>()
                    val modelFile = File(ctx.filesDir, "model.gguf")
                    if (!modelFile.exists()) {
                        _state.value = LlamaUiState.Error("모델 파일 없음: ${modelFile.absolutePath}")
                        return@withContext
                    }
                    val ok = inference.loadModel(modelFile.absolutePath)
                    modelLoaded = ok
                    _state.value = if (ok) LlamaUiState.Idle else LlamaUiState.Error("모델 로드 실패")
                    Log.i(TAG, "[LlamaVM] 모델 로드: $ok")
                } catch (e: Exception) {
                    _state.value = LlamaUiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    // ── 메뉴 추천 (1차 → 2차 → 3차 연속 추론) ─────────────────────
    fun runMenuRecommendation(
        weather: WeatherInfo?,
        mealTab: MealTimeTab,
        categoryName: String = "전체",
        extraPrefs: String = "",
        likedMenuHistory: List<String> = emptyList(),
        eatAlone: Boolean = true,
        healthyMode: Boolean = false           // ★ 건강식 선호
    ) {
        if (!modelLoaded) {
            _state.value = LlamaUiState.Error("모델이 로드되지 않았습니다")
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val dt = LlamaPromptBuilder.getDateTimeInfo()

                    // 1차: 메뉴 추천
                    val prompt1 = LlamaPromptBuilder.buildMenuRecommendPrompt(
                        dt, weather, mealTab, categoryName, extraPrefs, likedMenuHistory, eatAlone, healthyMode)
                    Log.i(TAG, "[LlamaVM] 1차 prompt 길이: ${prompt1.length}")
                    _state.value = LlamaUiState.Inferring("${mealTab.label} 메뉴 추천 중...")
                    val result1 = inference.generate(prompt1)
                    Log.i(TAG, "[LlamaVM] 1차 결과: $result1")

                    val menuName = parseMenuName(result1)
                    Log.i(TAG, "[LlamaVM] 파싱된 메뉴명: $menuName")
                    _state.value = LlamaUiState.Step1Done(menuName, result1)

                    // 2차: 칼로리 + 먹는 방법
                    val prompt2 = LlamaPromptBuilder.buildMenuDetailPrompt(menuName, result1)
                    _state.value = LlamaUiState.Inferring("\"$menuName\" 칼로리·먹는법 생성 중...", result1)
                    val result2 = inference.generate(prompt2)
                    Log.i(TAG, "[LlamaVM] 2차 결과: $result2")
                    _state.value = LlamaUiState.Step2Done(menuName, result1, result2)

                    // 3차: 반찬 + 차 + 디저트
                    val prompt3 = LlamaPromptBuilder.buildSideDishAndDessertPrompt(menuName)
                    _state.value = LlamaUiState.Inferring("\"$menuName\" 반찬·디저트 추천 중...", result1)
                    val result3 = inference.generate(prompt3)
                    Log.i(TAG, "[LlamaVM] 3차 결과: $result3")

                    _state.value = LlamaUiState.Step3Done(
                        menuName   = menuName,
                        step1Text  = result1,
                        step2Text  = result2,
                        step3Text  = result3,
                        isLiked    = false,
                        rating     = 0
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "[LlamaVM] 오류: ${e.message}", e)
                    _state.value = LlamaUiState.Error(e.message ?: "추론 오류")
                }
            }
        }
    }

    // ── 좋아요 토글 ───────────────────────────────────────────────
    fun toggleLike(menuName: String, currentlyLiked: Boolean) {
        val s = _state.value
        if (s is LlamaUiState.Step3Done) {
            _state.value = s.copy(isLiked = !currentlyLiked)
        }
        viewModelScope.launch {
            if (currentlyLiked) repo.removeLikedMenu(menuName)
            else repo.addLikedMenu(menuName)
        }
    }

    // ── 평점 저장 ─────────────────────────────────────────────────
    fun saveRating(menuName: String, rating: Int) {
        val s = _state.value
        if (s is LlamaUiState.Step3Done) {
            _state.value = s.copy(rating = rating)
        }
        viewModelScope.launch { repo.saveRating(menuName, rating) }
        Log.i(TAG, "[LlamaVM] 평점 저장: $menuName → $rating 점")
    }

    // ── 메뉴명 파싱 ───────────────────────────────────────────────
    private fun parseMenuName(text: String): String {
        val lines = text.lines()
        for (line in lines) {
            val match = Regex("""^1[).]\s*(.+)""").find(line.trim()) ?: continue
            var name = match.groupValues[1].trim()
            name = name.replace(Regex("""\s*[(\[].+[)\]]"""), "").trim()
            if (name.contains(":")) name = name.substringAfter(":").trim()
            if (name.isNotBlank()) return name
        }
        return lines.firstOrNull { it.isNotBlank() }?.take(20)?.trim() ?: "추천 메뉴"
    }

    override fun onCleared() {
        super.onCleared()
        if (modelLoaded) { inference.unloadModel(); modelLoaded = false }
    }
}



