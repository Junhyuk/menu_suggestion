package com.dgy.menusuggestion.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Android GPS/네트워크를 이용한 위치 정보 제공자
 *
 * FusedLocationProviderClient를 사용해 배터리 효율적으로 위치를 가져옵니다.
 * 권한 체크 포함.
 */
class LocationProvider(private val context: Context) {

    companion object {
        private const val TAG = "LocationProvider"
        private const val LOCATION_TIMEOUT_MS = 10_000L  // 10초 타임아웃
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * 현재 위치 반환 (위도, 경도)
     *
     * - 위치 권한 없으면 null 반환
     * - 마지막으로 알려진 위치가 있으면 즉시 반환
     * - 없으면 최대 10초 대기 후 위치 요청
     *
     * @return Pair(latitude, longitude) 또는 null
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Pair<Double, Double>? {
        if (!hasLocationPermission()) {
            Log.w(TAG, "위치 권한 없음")
            return null
        }

        // 1. 마지막으로 알려진 위치 시도
        val lastLocation = getLastKnownLocation()
        if (lastLocation != null) {
            Log.d(TAG, "마지막 위치 사용: lat=${lastLocation.first}, lon=${lastLocation.second}")
            return lastLocation
        }

        // 2. 새 위치 요청 (타임아웃 10초)
        Log.d(TAG, "새 위치 요청 시작...")
        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            requestNewLocation()
        } ?: run {
            Log.w(TAG, "위치 요청 타임아웃 ($LOCATION_TIMEOUT_MS ms)")
            null
        }
    }

    /** 위치 권한 확인 */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 마지막으로 알려진 위치 가져오기 */
    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        cont.resume(Pair(location.latitude, location.longitude))
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "마지막 위치 조회 실패: ${e.message}")
                    cont.resume(null)
                }
        }

    /** 새 위치 요청 (콜백 기반 → 코루틴 변환) */
    @SuppressLint("MissingPermission")
    private suspend fun requestNewLocation(): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5_000L  // 5초 간격
            ).apply {
                setMaxUpdates(1)
                setWaitForAccurateLocation(false)
            }.build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation
                    if (location != null) {
                        Log.d(TAG, "새 위치 수신: lat=${location.latitude}, lon=${location.longitude}")
                        if (cont.isActive) {
                            cont.resume(Pair(location.latitude, location.longitude))
                        }
                    } else {
                        if (cont.isActive) cont.resume(null)
                    }
                    fusedLocationClient.removeLocationUpdates(this)
                }

                override fun onLocationAvailability(availability: LocationAvailability) {
                    if (!availability.isLocationAvailable) {
                        Log.w(TAG, "위치 서비스 사용 불가")
                        if (cont.isActive) cont.resume(null)
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            cont.invokeOnCancellation {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }
}

