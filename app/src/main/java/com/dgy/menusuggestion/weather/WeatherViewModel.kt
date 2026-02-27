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
        /** 날씨 캐시 유효 시간: 4시간(밀리초) */
        private const val CACHE_VALID_MS = 4L * 60 * 60 * 1000
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

    /** 마지막으로 날씨를 성공적으로 가져온 시각 (epoch ms). 0이면 미조회. */
    private var lastFetchedAt: Long = 0L

    /** 캐시된 날씨 데이터 */
    private var cachedWeather: WeatherInfo? = null

    private val locationProvider = LocationProvider(context)
    private val weatherRepository = WeatherRepository()

    // ─── 공개 API ────────────────────────────────────────────────

    /**
     * 앱 시작 시 호출: 캐시가 유효하면 재사용, 6시간 경과하면 재요청.
     */
    fun fetchWeatherOnAppStart() {
        val cached = cachedWeather
        if (cached != null && !isCacheExpired()) {
            Log.d(TAG, "캐시 재사용 (${(System.currentTimeMillis() - lastFetchedAt) / 60000}분 경과)")
            _uiState.value = WeatherUiState.Success(cached)
            return
        }
        fetchWeather()
    }

    /**
     * 수동 새로고침(pull-to-refresh): 6시간이 지난 경우에만 실제 요청.
     * @return true = 실제 요청 허용됨, false = 아직 캐시 유효 (요청 차단)
     */
    fun refreshIfExpired(): Boolean {
        return if (isCacheExpired()) {
            Log.d(TAG, "캐시 만료 → 새로고침 허용")
            fetchWeather()
            true
        } else {
            val remainMin = (CACHE_VALID_MS - (System.currentTimeMillis() - lastFetchedAt)) / 60000
            Log.d(TAG, "캐시 유효 → 새로고침 차단 (${remainMin}분 남음)")
            false
        }
    }

    /** 캐시 만료 여부 */
    fun isCacheExpired(): Boolean =
        lastFetchedAt == 0L || (System.currentTimeMillis() - lastFetchedAt) >= CACHE_VALID_MS

    /** 남은 캐시 시간 (분 단위, 이미 만료면 0) */
    fun remainingCacheMinutes(): Long {
        if (isCacheExpired()) return 0L
        return (CACHE_VALID_MS - (System.currentTimeMillis() - lastFetchedAt)) / 60000
    }

    /**
     * 실제 날씨 API 요청 (내부 + 수동 새로고침용)
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
                // 캐시가 있으면 캐시 유지
                val cached = cachedWeather
                if (cached != null) {
                    _uiState.value = WeatherUiState.Success(cached)
                } else {
                    _uiState.value = WeatherUiState.Error("위치를 가져올 수 없습니다.\nGPS 또는 네트워크 위치를 활성화해 주세요.")
                }
                return@launch
            }

            val (lat, lon) = location
            Log.d(TAG, "위치 조회 성공: lat=$lat, lon=$lon")

            // 2. 날씨 API 호출
            weatherRepository.getWeather(lat, lon)
                .onSuccess { weather ->
                    Log.d(TAG, "날씨 조회 성공: ${weather.temperature}, ${weather.skyCondition}")
                    cachedWeather = weather
                    lastFetchedAt = System.currentTimeMillis()
                    _uiState.value = WeatherUiState.Success(weather)
                }
                .onFailure { e ->
                    Log.e(TAG, "날씨 조회 실패: ${e.message}", e)
                    // 실패해도 캐시가 있으면 캐시 표시
                    val cached = cachedWeather
                    if (cached != null) {
                        _uiState.value = WeatherUiState.Success(cached)
                    } else {
                        _uiState.value = WeatherUiState.Error(
                            "날씨 정보를 불러오지 못했습니다.\n${e.message ?: "알 수 없는 오류"}"
                        )
                    }
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

