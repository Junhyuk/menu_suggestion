package com.dgy.menusuggestion.weather

import com.google.gson.annotations.SerializedName

// ─── Open-Meteo API 응답 모델 ──────────────────────────────────────

data class OpenMeteoResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: OpenMeteoCurrent?,
    val daily: OpenMeteoDaily?
)

data class OpenMeteoCurrent(
    @SerializedName("temperature_2m")       val temperature: Double?,
    @SerializedName("apparent_temperature") val apparentTemperature: Double?,
    @SerializedName("relative_humidity_2m") val relativeHumidity: Int?,
    @SerializedName("weather_code")         val weatherCode: Int?,
    @SerializedName("wind_speed_10m")       val windSpeed: Double?,
    @SerializedName("wind_direction_10m")   val windDirection: Int?,
    @SerializedName("precipitation")        val precipitation: Double?
)

data class OpenMeteoDaily(
    @SerializedName("temperature_2m_max")            val tempMax: List<Double>?,
    @SerializedName("temperature_2m_min")            val tempMin: List<Double>?,
    @SerializedName("precipitation_probability_max") val precipProbMax: List<Int>?
)

// ─── 앱 내부 날씨 정보 모델 ───────────────────────────────────────

data class WeatherInfo(
    val temperature: String,
    val feelsLike: String,
    val humidity: String,
    val skyCondition: String,
    val skyEmoji: String,
    val precipitationType: String,
    val precipitation: String,
    val windSpeed: String,
    val windDirection: String,
    val precipProbability: String,
    val minTemp: String,
    val maxTemp: String
) {
    companion object {
        fun empty() = WeatherInfo(
            temperature = "--", feelsLike = "--", humidity = "--",
            skyCondition = "알 수 없음", skyEmoji = "🌡️",
            precipitationType = "없음", precipitation = "0mm",
            windSpeed = "--", windDirection = "--",
            precipProbability = "--", minTemp = "--", maxTemp = "--"
        )
    }
}

// ─── 격자 좌표 (이전 코드 호환용) ─────────────────────────────────

data class GridCoordinate(val nx: Int, val ny: Int)
