package com.dgy.menusuggestion.weather

import kotlin.math.*

/**
 * 위경도(WGS84) → 기상청 격자 좌표(nx, ny) 변환 유틸리티
 *
 * 기상청 공식 제공 Lambert Conformal Conic 변환 알고리즘을 Kotlin으로 구현
 * 참고: 기상청 기술문서 "기상청_동네예보 격자 변환" (2021)
 */
object LatLonToGrid {

    private const val RE = 6371.00877      // 지구 반경 (km)
    private const val GRID = 5.0           // 격자 간격 (km)
    private const val SLAT1 = 30.0        // 투영 위도1 (도)
    private const val SLAT2 = 60.0        // 투영 위도2 (도)
    private const val OLON = 126.0        // 기준점 경도 (도)
    private const val OLAT = 38.0         // 기준점 위도 (도)
    private const val XO = 43.0           // 기준점 X 격자 좌표
    private const val YO = 136.0          // 기준점 Y 격자 좌표

    private val DEGRAD = PI / 180.0
    private val RADDEG = 180.0 / PI

    /**
     * 위도/경도를 기상청 격자 좌표로 변환
     *
     * @param lat 위도 (예: 37.5665)
     * @param lon 경도 (예: 126.9780)
     * @return GridCoordinate(nx, ny)
     */
    fun convert(lat: Double, lon: Double): GridCoordinate {
        val re = RE / GRID
        val slat1 = SLAT1 * DEGRAD
        val slat2 = SLAT2 * DEGRAD
        val olon = OLON * DEGRAD
        val olat = OLAT * DEGRAD

        var sn = tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5)
        sn = ln(cos(slat1) / cos(slat2)) / ln(sn)

        var sf = tan(PI * 0.25 + slat1 * 0.5)
        sf = sf.pow(sn) * cos(slat1) / sn

        var ro = tan(PI * 0.25 + olat * 0.5)
        ro = re * sf / ro.pow(sn)

        val ra = tan(PI * 0.25 + lat * DEGRAD * 0.5)
            .let { re * sf / it.pow(sn) }

        var theta = lon * DEGRAD - olon
        if (theta > PI) theta -= 2.0 * PI
        if (theta < -PI) theta += 2.0 * PI
        theta *= sn

        val nx = (ra * sin(theta) + XO + 0.5).toInt()
        val ny = (ro - ra * cos(theta) + YO + 0.5).toInt()

        return GridCoordinate(nx, ny)
    }
}

