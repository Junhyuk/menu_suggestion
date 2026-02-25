package com.dgy.menusuggestion.weather

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo 날씨 API — 무료, 인증키 없음
 * https://open-meteo.com/en/docs
 *
 * 예시: https://api.open-meteo.com/v1/forecast
 *       ?latitude=37.5665&longitude=126.9780
 *       &current=temperature_2m,relative_humidity_2m,weather_code,
 *                wind_speed_10m,wind_direction_10m,apparent_temperature,precipitation
 *       &daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max
 *       &timezone=Asia/Seoul
 */
interface WeatherApiService {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude")                      latitude: Double,
        @Query("longitude")                     longitude: Double,
        @Query("current")                       current: String =
            "temperature_2m,relative_humidity_2m,weather_code," +
            "wind_speed_10m,wind_direction_10m,apparent_temperature,precipitation",
        @Query("daily")                         daily: String =
            "temperature_2m_max,temperature_2m_min,precipitation_probability_max",
        @Query("timezone")                      timezone: String = "Asia/Seoul",
        @Query("forecast_days")                 forecastDays: Int = 1
    ): OpenMeteoResponse
}
