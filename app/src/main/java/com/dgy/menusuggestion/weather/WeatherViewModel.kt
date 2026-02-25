package com.dgy.menusuggestion.weather

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 날씨 정보 ViewModel
 *
 * 위치 정보 조회 → 기상청 날씨 API 호출 → UI 상태 업데이트
 */
class WeatherViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "WeatherViewModel"
    }

    // ─── UI 상태 ─────────────────────────────────────────────────

    sealed class WeatherUiState {
        object Idle        : WeatherUiState()
        object Loading     : WeatherUiState()
        data class Success(val weather: WeatherInfo) : WeatherUiState()
        data class Error(val message: String)        : WeatherUiState()
        object PermissionDenied : WeatherUiState()
    }

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Idle)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    // ─── 의존성 ──────────────────────────────────────────────────

    private val locationProvider = LocationProvider(context)
    private val weatherRepository = WeatherRepository()

    // ─── 공개 API ────────────────────────────────────────────────

    /**
     * 위치 기반 날씨 조회
     * - 권한이 없으면 PermissionDenied 상태로 전환
     */
    fun fetchWeather() {
        if (!locationProvider.hasLocationPermission()) {
            Log.w(TAG, "위치 권한 없음 → PermissionDenied")
            _uiState.value = WeatherUiState.PermissionDenied
            return
        }

        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            Log.d(TAG, "날씨 조회 시작")

            // 1. 현재 위치 가져오기
            val location = locationProvider.getCurrentLocation()
            if (location == null) {
                Log.e(TAG, "위치 조회 실패")
                _uiState.value = WeatherUiState.Error("위치를 가져올 수 없습니다.\nGPS 또는 네트워크 위치를 활성화해 주세요.")
                return@launch
            }

            val (lat, lon) = location
            Log.d(TAG, "위치 조회 성공: lat=$lat, lon=$lon")

            // 2. 날씨 API 호출
            weatherRepository.getWeather(lat, lon)
                .onSuccess { weather ->
                    Log.d(TAG, "날씨 조회 성공: ${weather.temperature}, ${weather.skyCondition}")
                    _uiState.value = WeatherUiState.Success(weather)
                }
                .onFailure { e ->
                    Log.e(TAG, "날씨 조회 실패: ${e.message}", e)
                    _uiState.value = WeatherUiState.Error(
                        "날씨 정보를 불러오지 못했습니다.\n${e.message ?: "알 수 없는 오류"}"
                    )
                }
        }
    }

    /** 권한 허용 후 재시도 */
    fun onPermissionGranted() {
        _uiState.value = WeatherUiState.Idle
        fetchWeather()
    }
}

// ─── ViewModel Factory ────────────────────────────────────────────

class WeatherViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            return WeatherViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}

