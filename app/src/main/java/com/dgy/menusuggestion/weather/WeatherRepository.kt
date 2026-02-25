package com.dgy.menusuggestion.weather

import android.util.Log
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Open-Meteo 기반 날씨 레포지토리
 * - 완전 무료, 인증키 없음 (401 문제 없음)
 * - https://open-meteo.com/en/docs
 */
class WeatherRepository {

    companion object {
        private const val TAG = "WeatherRepository"
        private const val BASE_URL = "https://api.open-meteo.com/"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor { Log.d(TAG, it) }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiService: WeatherApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
        .build()
        .create(WeatherApiService::class.java)

    // ── 공개 API ─────────────────────────────────────────────────

    suspend fun getWeather(lat: Double, lon: Double): Result<WeatherInfo> {
        return try {
            Log.d(TAG, "Open-Meteo 호출: lat=$lat, lon=$lon")
            val resp = apiService.getForecast(latitude = lat, longitude = lon)
            val current = resp.current
                ?: return Result.failure(Exception("현재 날씨 데이터 없음"))
            val info = mapToWeatherInfo(current, resp.daily)
            Log.d(TAG, "날씨 파싱 완료: ${info.temperature}, ${info.skyCondition}")
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "날씨 조회 실패: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── 내부 파싱 ────────────────────────────────────────────────

    private fun mapToWeatherInfo(c: OpenMeteoCurrent, d: OpenMeteoDaily?): WeatherInfo {
        val (skyText, skyEmoji, precipType) = wmoToDescription(c.weatherCode ?: 0)
        return WeatherInfo(
            temperature       = c.temperature?.let { "%.1f℃".format(it) } ?: "--",
            feelsLike         = c.apparentTemperature?.let { "%.1f℃".format(it) } ?: "--",
            humidity          = c.relativeHumidity?.let { "$it%" } ?: "--",
            skyCondition      = skyText,
            skyEmoji          = skyEmoji,
            precipitationType = precipType,
            precipitation     = if ((c.precipitation ?: 0.0) > 0)
                                    "%.1fmm".format(c.precipitation) else "0mm",
            windSpeed         = c.windSpeed?.let { "%.1fm/s".format(it) } ?: "--",
            windDirection     = windDirText(c.windDirection),
            precipProbability = "${d?.precipProbMax?.firstOrNull() ?: "--"}%",
            minTemp           = d?.tempMin?.firstOrNull()?.let { "%.1f℃".format(it) } ?: "--",
            maxTemp           = d?.tempMax?.firstOrNull()?.let { "%.1f℃".format(it) } ?: "--"
        )
    }

    /**
     * WMO Weather Interpretation Code → (하늘상태, 이모지, 강수형태)
     * https://open-meteo.com/en/docs#weathervariables
     */
    private fun wmoToDescription(code: Int): Triple<String, String, String> = when (code) {
        0          -> Triple("맑음",          "☀️",  "없음")
        1          -> Triple("대체로 맑음",    "🌤️",  "없음")
        2          -> Triple("구름많음",       "⛅",  "없음")
        3          -> Triple("흐림",           "☁️",  "없음")
        45, 48     -> Triple("안개",           "🌫️",  "없음")
        51, 53, 55 -> Triple("이슬비",         "🌦️",  "이슬비")
        61, 63, 65 -> Triple("비",             "🌧️",  "비")
        66, 67     -> Triple("얼어붙는 비",    "🌨️",  "비/눈")
        71, 73, 75 -> Triple("눈",             "❄️",  "눈")
        77         -> Triple("눈알갱이",       "🌨️",  "눈")
        80, 81, 82 -> Triple("소나기",         "🌦️",  "소나기")
        85, 86     -> Triple("눈소나기",       "🌨️",  "눈")
        95         -> Triple("뇌우",           "⛈️",  "비")
        96, 99     -> Triple("우박 동반 뇌우", "⛈️",  "비/우박")
        else       -> Triple("알 수 없음",     "🌡️",  "없음")
    }

    /** 풍향 각도 → 16방위 텍스트 */
    private fun windDirText(deg: Int?): String {
        deg ?: return "--"
        val dirs = listOf("N","NNE","NE","ENE","E","ESE","SE","SSE",
                          "S","SSW","SW","WSW","W","WNW","NW","NNW")
        return dirs[((deg + 11) / 23) % 16]
    }
}
