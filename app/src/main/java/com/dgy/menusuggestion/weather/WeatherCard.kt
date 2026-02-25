package com.dgy.menusuggestion.weather

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 날씨 정보 카드 Composable
 *
 * MenuSuggestionScreen 상단에 삽입해 날씨 정보를 표시합니다.
 * 위치 권한 요청 → 위치 조회 → 기상청 API 호출 흐름을 자동으로 처리합니다.
 */
@Composable
fun WeatherCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val weatherViewModel: WeatherViewModel = viewModel(
        factory = WeatherViewModelFactory(context)
    )
    val uiState by weatherViewModel.uiState.collectAsState()

    // 위치 권한 요청 런처
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        Log.d("WeatherCard", "위치 권한 결과: $granted")
        if (granted) {
            weatherViewModel.onPermissionGranted()
        }
    }

    // 최초 진입 시 날씨 조회 시도
    LaunchedEffect(Unit) {
        weatherViewModel.fetchWeather()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1565C0),
                        Color(0xFF1976D2)
                    )
                )
            )
            .padding(16.dp)
    ) {
        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "weather_anim"
        ) { state ->
            when (state) {
                is WeatherViewModel.WeatherUiState.Idle,
                is WeatherViewModel.WeatherUiState.Loading -> {
                    WeatherLoadingContent()
                }

                is WeatherViewModel.WeatherUiState.Success -> {
                    WeatherSuccessContent(
                        weather = state.weather,
                        onRefresh = { weatherViewModel.fetchWeather() }
                    )
                }

                is WeatherViewModel.WeatherUiState.Error -> {
                    WeatherErrorContent(
                        message = state.message,
                        onRetry = { weatherViewModel.fetchWeather() }
                    )
                }

                is WeatherViewModel.WeatherUiState.PermissionDenied -> {
                    WeatherPermissionContent(
                        onRequestPermission = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

// ─── 로딩 상태 ────────────────────────────────────────────────────

@Composable
private fun WeatherLoadingContent() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = Color.White,
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "날씨 정보를 불러오는 중...",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp
        )
    }
}

// ─── 성공 상태 ────────────────────────────────────────────────────

@Composable
private fun WeatherSuccessContent(
    weather: WeatherInfo,
    onRefresh: () -> Unit
) {
    Column {
        // 상단: 아이콘 + 기온 + 하늘상태
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = weather.skyEmoji,
                    fontSize = 40.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = weather.temperature,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    )
                    Text(
                        text = weather.skyCondition,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp
                    )
                }
            }

            // 새로고침 버튼
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "새로고침",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(10.dp))

        // 하단: 세부 정보 격자
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            WeatherDetailItem(
                emoji = "💧",
                label = "습도",
                value = weather.humidity
            )
            WeatherDetailItem(
                emoji = "🌂",
                label = "강수확률",
                value = weather.precipProbability
            )
            WeatherDetailItem(
                emoji = "💨",
                label = "풍속",
                value = weather.windSpeed
            )
            WeatherDetailItem(
                emoji = "🌡️",
                label = "체감온도",
                value = weather.feelsLike
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 최저/최고 기온
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "최저 ${weather.minTemp}",
                color = Color(0xFF90CAF9),
                fontSize = 12.sp
            )
            Text(
                text = " / ",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
            Text(
                text = "최고 ${weather.maxTemp}",
                color = Color(0xFFFF8A65),
                fontSize = 12.sp
            )
            if (weather.precipitationType != "없음") {
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = weather.precipitationType,
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherDetailItem(emoji: String, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = emoji, fontSize = 16.sp)
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp
        )
    }
}

// ─── 오류 상태 ────────────────────────────────────────────────────

@Composable
private fun WeatherErrorContent(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFFCC80),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp
            )
        }
        TextButton(onClick = onRetry) {
            Text(text = "재시도", color = Color(0xFFFFCC80), fontSize = 12.sp)
        }
    }
}

// ─── 권한 요청 상태 ───────────────────────────────────────────────

@Composable
private fun WeatherPermissionContent(onRequestPermission: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFFCC80),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "위치 권한을 허용하면\n현재 날씨를 확인할 수 있어요",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp
            )
        }
        TextButton(onClick = onRequestPermission) {
            Text(text = "권한 허용", color = Color(0xFFFFCC80), fontSize = 12.sp)
        }
    }
}



