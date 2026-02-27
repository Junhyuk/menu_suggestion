package com.dgy.menusuggestion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dgy.menusuggestion.LlamaUiState
import com.dgy.menusuggestion.LlamaViewModel
import com.dgy.menusuggestion.LlamaPromptBuilder
import com.dgy.menusuggestion.data.MealTimeTab
import com.dgy.menusuggestion.data.PreferenceRepository
import com.dgy.menusuggestion.data.sampleMenuItems
import com.dgy.menusuggestion.weather.WeatherInfo

// ─────────────────────────────────────────────────────────────────
// LLM 추천 결과 전체 뷰
// ─────────────────────────────────────────────────────────────────
@Composable
fun LlmRecommendView(
    viewModel: LlamaViewModel,
    currentTab: MealTimeTab,
    selectedCategoryName: String,
    weather: WeatherInfo?,
    prefRepo: PreferenceRepository,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showExtraPrefsDialog by remember { mutableStateOf(false) }
    var extraPrefs by remember { mutableStateOf("") }
    var eatAlone by remember { mutableStateOf(true) }          // ★ 혼밥/함께
    var healthyMode by remember { mutableStateOf(false) }      // ★ 건강식 선호

    val likedMenus by prefRepo.likedMenusFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // 현재 날짜/시간/요일 정보
    val dt = remember { LlamaPromptBuilder.getDateTimeInfo() }

    Column(modifier = modifier.fillMaxWidth()) {

        // ── 컨텍스트 배지 바 (요일/날씨/혼밥) ───────────────────
        ContextBadgeBar(
            dt = dt, weather = weather,
            eatAlone = eatAlone, onEatAloneChange = { eatAlone = it },
            healthyMode = healthyMode, onHealthyModeChange = { healthyMode = it }
        )

        // ── 좋아요 이력 힌트 ─────────────────────────────────────
        if (likedMenus.isNotEmpty()) LlmLikedHistoryHint(likedMenus = likedMenus.take(3))

        // ── 요청 버튼 바 ─────────────────────────────────────────
        LlmRequestBar(
            tab = currentTab, categoryName = selectedCategoryName,
            isRunning = state is LlamaUiState.Inferring,
            onRequestClick = {
                viewModel.runMenuRecommendation(
                    weather = weather, mealTab = currentTab,
                    categoryName = selectedCategoryName,
                    extraPrefs = extraPrefs,
                    likedMenuHistory = likedMenus,
                    eatAlone = eatAlone,
                    healthyMode = healthyMode
                )
            },
            onPrefsClick = { showExtraPrefsDialog = true }
        )

        // ── 상태별 콘텐츠 ────────────────────────────────────────
        when (val s = state) {
            is LlamaUiState.Idle         -> LlmIdleHint(tab = currentTab)
            is LlamaUiState.LoadingModel -> LlmLoadingCard(message = "모델 로딩 중...")
            is LlamaUiState.Inferring -> {
                LlmLoadingCard(message = s.step)
                if (s.partial.isNotBlank())
                    LlmStep1Card(menuName = "추론 중...", text = s.partial, isLoading = true)
            }
            is LlamaUiState.Step1Done -> {
                AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically()) {
                    LlmStep1Card(menuName = s.menuName, text = s.fullText, isLoading = false)
                }
            }
            is LlamaUiState.Step2Done -> {
                AnimatedVisibility(visible = true, enter = fadeIn()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LlmMenuImageCard(menuName = s.menuName)
                        LlmStep1Card(menuName = s.menuName, text = s.step1Text, isLoading = false)
                        LlmStep2Card(text = s.step2Text)
                    }
                }
            }
            is LlamaUiState.Step3Done -> {
                AnimatedVisibility(visible = true, enter = fadeIn()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LlmMenuImageCard(menuName = s.menuName)
                        // 메뉴 추천 카드 (태그 파싱 포함)
                        LlmMenuRecommendCard(
                            menuName = s.menuName,
                            text = s.step1Text,
                            eatAlone = eatAlone,
                            dt = dt
                        )
                        // 칼로리/영양 표 카드
                        LlmNutritionTableCard(text = s.step2Text)
                        // 반찬/차/디저트 카드
                        LlmStep3Card(text = s.step3Text)
                        // 피드백 카드
                        LlmFeedbackCard(
                            menuName = s.menuName, isLiked = s.isLiked, rating = s.rating,
                            onLike = { viewModel.toggleLike(s.menuName, s.isLiked) },
                            onRate = { r -> viewModel.saveRating(s.menuName, r) }
                        )
                    }
                }
            }
            is LlamaUiState.Error -> LlmErrorCard(message = s.message)
        }
    }

    if (showExtraPrefsDialog) {
        ExtraPrefsDialog(
            current = extraPrefs,
            onConfirm = { prefs -> extraPrefs = prefs; showExtraPrefsDialog = false },
            onDismiss = { showExtraPrefsDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// ★ 컨텍스트 배지 바 (요일/평일휴일/날씨 + 혼밥 스위치)
// ─────────────────────────────────────────────────────────────────
@Composable
fun ContextBadgeBar(
    dt: LlamaPromptBuilder.DateTimeInfo,
    weather: WeatherInfo?,
    eatAlone: Boolean,
    onEatAloneChange: (Boolean) -> Unit,
    healthyMode: Boolean = false,
    onHealthyModeChange: (Boolean) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // 배지 행 1: 요일/평일휴일/날씨
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                val (dayColor, dayBg) = if (dt.isHoliday)
                    Pair(Color(0xFFE53935), Color(0xFFFFEBEE))
                else Pair(Color(0xFF1565C0), Color(0xFFE3F2FD))
                Surface(shape = RoundedCornerShape(20.dp), color = dayBg) {
                    Text(dt.dayType, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = dayColor)
                }
                val holidayLabel = if (dt.isHoliday) "🏖 휴일" else "💼 평일"
                val holidayBg = if (dt.isHoliday) Color(0xFFFFF3E0) else Color(0xFFF3F4F6)
                val holidayColor = if (dt.isHoliday) Color(0xFFE65100) else Color(0xFF455A64)
                Surface(shape = RoundedCornerShape(20.dp), color = holidayBg) {
                    Text(holidayLabel, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp, fontWeight = FontWeight.Medium, color = holidayColor)
                }
                if (weather != null) {
                    Surface(shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                        Text("${weather.skyEmoji} ${weather.temperature}℃",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            // 배지 행 2: 혼밥/함께 스위치
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (eatAlone) "🍱 혼밥 식사?" else "👥 함께",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (eatAlone) Color(0xFF7B1FA2) else Color(0xFF1976D2))
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("혼밥 식사?", fontSize = 12.sp,
                        color = if (eatAlone) Color(0xFF7B1FA2) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (eatAlone) FontWeight.Bold else FontWeight.Normal)
                    Switch(
                        checked = !eatAlone,
                        onCheckedChange = { onEatAloneChange(!it) },
                        modifier = Modifier.height(24.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF1976D2),
                            uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF7B1FA2))
                    )
                    Text("함께", fontSize = 12.sp,
                        color = if (!eatAlone) Color(0xFF1976D2) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (!eatAlone) FontWeight.Bold else FontWeight.Normal)
                }
            }

            // ★ 배지 행 3: 건강식/일반 스위치
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (healthyMode) "🥗 건강식" else "🍖 일반식",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (healthyMode) Color(0xFF2E7D32) else Color(0xFF6D4C41))
                    Text(if (healthyMode) "저칼로리·담백 우선" else "맛있는 메뉴 추천",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("일반식", fontSize = 12.sp,
                        color = if (!healthyMode) Color(0xFF6D4C41) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (!healthyMode) FontWeight.Bold else FontWeight.Normal)
                    Switch(
                        checked = healthyMode,
                        onCheckedChange = { onHealthyModeChange(it) },
                        modifier = Modifier.height(24.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF2E7D32),
                            uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF6D4C41))
                    )
                    Text("건강식", fontSize = 12.sp,
                        color = if (healthyMode) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (healthyMode) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// ★ 메뉴 추천 결과 카드 (태그 파싱 + 표 형식 요약)
// ─────────────────────────────────────────────────────────────────
@Composable
fun LlmMenuRecommendCard(
    menuName: String,
    text: String,
    eatAlone: Boolean,
    dt: LlamaPromptBuilder.DateTimeInfo
) {
    // 텍스트에서 #태그 파싱
    val tags = remember(text) { parseHashTags(text) }
    // 태그 없으면 텍스트에서 키워드 자동 추출
    val displayTags = if (tags.isNotEmpty()) tags else extractKeywords(text, menuName)

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // 헤더
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null,
                            modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("AI 메뉴 추천", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── 메뉴 요약 표 ─────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TableRow(label = "🍽 추천 메뉴", value = menuName, valueWeight = FontWeight.Bold,
                        valueColor = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp)
                    TableRow(label = "🗓 오늘", value = "${dt.date} ${dt.dayType}")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp)
                    TableRow(label = "👤 식사 스타일",
                        value = if (eatAlone) "혼밥 (1인)" else "함께 먹기 (2인+)")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp)
                    TableRow(label = "🌤 날씨", value = dt.season)
                }
            }

            // ── AI 추천 이유 텍스트 ──────────────────────────────
            Text(text.lines().drop(1).take(4).joinToString("\n").trim(),
                fontSize = 13.sp, lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            // ── 인스타 스타일 태그 ───────────────────────────────
            if (displayTags.isNotEmpty()) {
                InstagramTagRow(tags = displayTags)
            }
        }
    }
}

@Composable
private fun TableRow(
    label: String,
    value: String,
    valueWeight: FontWeight = FontWeight.Normal,
    valueColor: Color = Color.Unspecified
) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f))
        Text(value, fontSize = 13.sp, fontWeight = valueWeight,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
            modifier = Modifier.weight(0.58f), textAlign = TextAlign.End)
    }
}

// ─────────────────────────────────────────────────────────────────
// ★ 인스타그램 스타일 해시태그 행
// ─────────────────────────────────────────────────────────────────
@Composable
fun InstagramTagRow(tags: List<String>) {
    val tagColors = listOf(
        Color(0xFF7B1FA2), Color(0xFF1565C0), Color(0xFF2E7D32),
        Color(0xFFE65100), Color(0xFF00695C)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tags.take(5).forEachIndexed { idx, tag ->
            val color = tagColors[idx % tagColors.size]
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = color.copy(alpha = 0.1f)
            ) {
                Text(
                    text = if (tag.startsWith("#")) tag else "#$tag",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = color
                )
            }
        }
    }
}

// ── 태그 파싱 유틸 ──────────────────────────────────────────────
private fun parseHashTags(text: String): List<String> {
    return Regex("""#[\w가-힣]+""").findAll(text).map { it.value }.toList()
}

private fun extractKeywords(text: String, menuName: String): List<String> {
    val keywords = mutableListOf<String>()
    // 메뉴명 첫 단어
    menuName.split(" ").firstOrNull()?.let { keywords.add(it) }
    // 자주 나오는 음식 특성 키워드
    val featureWords = listOf("담백", "든든", "칼칼", "고소", "쫄깃", "바삭", "매콤", "따뜻",
        "시원", "깔끔", "풍부", "부드러운", "건강", "고단백", "저칼로리", "면", "밥", "국물")
    featureWords.forEach { w -> if (text.contains(w)) keywords.add(w) }
    return keywords.take(5)
}

// ─────────────────────────────────────────────────────────────────
// ★ 칼로리/영양소 표 카드 (2차 결과)
// ─────────────────────────────────────────────────────────────────
@Composable
fun LlmNutritionTableCard(text: String) {
    // 칼로리 숫자 파싱
    val kcalMatch = Regex("""(\d{2,4})\s*kcal""").find(text)
    val kcal = kcalMatch?.groupValues?.get(1)?.toIntOrNull()

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // 헤더
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFF7043).copy(alpha = 0.15f)) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Whatshot, contentDescription = null,
                            modifier = Modifier.size(14.dp), tint = Color(0xFFFF7043))
                        Text("칼로리 · 영양 정보", fontSize = 12.sp,
                            color = Color(0xFFFF7043), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 칼로리 시각화 바
            if (kcal != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("1인분 칼로리", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$kcal kcal", fontSize = 14.sp,
                            fontWeight = FontWeight.Bold, color = Color(0xFFFF7043))
                    }
                    // 칼로리 바 (2000kcal 기준)
                    val progress = (kcal / 2000f).coerceIn(0f, 1f)
                    val barColor = when {
                        kcal < 400  -> Color(0xFF4CAF50)
                        kcal < 700  -> Color(0xFFFFA726)
                        else        -> Color(0xFFEF5350)
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = barColor,
                        trackColor = barColor.copy(alpha = 0.2f)
                    )
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("저칼로리", fontSize = 10.sp, color = Color(0xFF4CAF50))
                        Text("일일권장량 기준 ${(progress * 100).toInt()}%", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("고칼로리", fontSize = 10.sp, color = Color(0xFFEF5350))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // 전체 텍스트
            Text(text, fontSize = 13.sp, lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 3차 결과 카드 (반찬 + 차 + 디저트)
// ─────────────────────────────────────────────────────────────────
@Composable
fun LlmStep3Card(text: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.08f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF4CAF50).copy(alpha = 0.15f)) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("🥗", fontSize = 13.sp)
                    Text("반찬 · 차 · 디저트 추천", fontSize = 12.sp,
                        color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                }
            }
            // 섹션 아이콘 파싱해서 보여주기
            text.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotBlank()) {
                    val icon = when {
                        trimmed.contains("반찬") || trimmed.startsWith("1)") -> "🥬"
                        trimmed.contains("차") || trimmed.contains("tea", true) -> "☕"
                        trimmed.contains("디저트") || trimmed.startsWith("3)") -> "🍮"
                        else -> ""
                    }
                    if (icon.isNotEmpty() && (trimmed.startsWith("1)") || trimmed.startsWith("2)") || trimmed.startsWith("3)"))) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(icon, fontSize = 16.sp)
                            Text(trimmed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1B5E20))
                        }
                    } else {
                        Text("  $trimmed", fontSize = 13.sp, lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 좋아요 이력 힌트
// ─────────────────────────────────────────────────────────────────
@Composable
fun LlmLikedHistoryHint(likedMenus: List<String>) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Favorite, contentDescription = null,
            tint = Color.Red, modifier = Modifier.size(12.dp))
        Text("좋아요 이력 반영:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        likedMenus.forEach { name ->
            Surface(shape = RoundedCornerShape(20.dp), color = Color.Red.copy(alpha = 0.1f)) {
                Text(name, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 11.sp, color = Color.Red)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 요청 버튼 바
// ─────────────────────────────────────────────────────────────────
@Composable
fun LlmRequestBar(
    tab: MealTimeTab, categoryName: String, isRunning: Boolean,
    onRequestClick: () -> Unit, onPrefsClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onRequestClick, enabled = !isRunning,
            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
        ) {
            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("추론 중...", fontSize = 13.sp)
            } else {
                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                val label = if (categoryName == "전체") tab.label else "$categoryName ${tab.label}"
                Text("AI $label 추천", fontSize = 13.sp)
            }
        }
        OutlinedButton(onClick = onPrefsClick, shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Icon(Icons.Default.Tune, contentDescription = "선호 설정", modifier = Modifier.size(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 대기 힌트
// ─────────────────────────────────────────────────────────────────
@Composable
fun LlmIdleHint(tab: MealTimeTab) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Stars, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text("위 버튼을 누르면 현재 날씨·날짜·${tab.label} 정보를 반영하여 AI가 메뉴를 추천해드려요",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─────────────────────────────────────────────────────────────────
// 로딩 카드
// ─────────────────────────────────────────────────────────────────
@Composable
fun LlmLoadingCard(message: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f, label = "alpha",
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse))
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(message, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = alpha))
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 메뉴 이미지 카드
// ─────────────────────────────────────────────────────────────────
@Composable
fun LlmMenuImageCard(menuName: String) {
    val matchedMenu = sampleMenuItems.find {
        menuName.contains(it.name) || it.name.contains(menuName.take(3))
    }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(4.dp)) {
        Box {
            if (matchedMenu != null) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = matchedMenu.imageRes),
                    contentDescription = menuName,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentScale = ContentScale.Crop)
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp)
                    .background(Brush.verticalGradient(colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer))),
                    contentAlignment = Alignment.Center) { Text("🍽️", fontSize = 64.sp) }
            }
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)
                .background(Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(0.7f)))))
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Stars, contentDescription = null,
                        tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                    Text("AI 추천", color = Color(0xFFFFD700), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(menuName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
        }
    }
}

// 구버전 호환 (Step2Done에서 사용)
@Composable
fun LlmStep1Card(menuName: String, text: String, isLoading: Boolean) {
    LlmMenuRecommendCard(
        menuName = menuName, text = text, eatAlone = true,
        dt = LlamaPromptBuilder.getDateTimeInfo()
    )
}

@Composable
fun LlmStep2Card(text: String) { LlmNutritionTableCard(text = text) }

// ─────────────────────────────────────────────────────────────────
// ★ 피드백 카드 (좋아요 + 별점)
// ─────────────────────────────────────────────────────────────────
@Composable
fun LlmFeedbackCard(
    menuName: String, isLiked: Boolean, rating: Int,
    onLike: () -> Unit, onRate: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("이 추천이 마음에 드셨나요?", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val heartScale by animateFloatAsState(
                    targetValue = if (isLiked) 1.2f else 1f, animationSpec = tween(200), label = "heart")
                IconButton(onClick = onLike,
                    modifier = Modifier.size(40.dp).scale(heartScale).clip(CircleShape)
                        .background(if (isLiked) Color.Red.copy(0.1f) else MaterialTheme.colorScheme.surface)) {
                    Icon(imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "좋아요",
                        tint = if (isLiked) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp))
                }
                Text(text = if (isLiked) "좋아요! 다음 추천에 반영됩니다 😊" else "좋아요를 누르면 다음 추천에 반영돼요",
                    fontSize = 12.sp,
                    color = if (isLiked) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("맛 평점 (별점)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { star ->
                        val filled = star <= rating
                        Icon(imageVector = if (filled) Icons.Default.Star else Icons.Outlined.Star,
                            contentDescription = "$star 점",
                            tint = if (filled) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(32.dp).clickable { onRate(star) })
                    }
                    if (rating > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text("$rating 점", fontSize = 13.sp, color = Color(0xFFFFB300),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
                if (rating > 0) {
                    Text(when (rating) { 5->"최고예요! 😍"; 4->"맛있었어요! 😋"; 3->"괜찮았어요 😊"; 2->"아쉬웠어요 😐"; else->"별로였어요 😔" },
                        fontSize = 12.sp, color = Color(0xFFFFB300))
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

// ─────────────────────────────────────────────────────────────────
// 에러 카드
// ─────────────────────────────────────────────────────────────────
@Composable
fun LlmErrorCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text("⚠️ $message", modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
    }
}

// ─────────────────────────────────────────────────────────────────
// 선호 조건 다이얼로그
// ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtraPrefsDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    val presets = listOf("맵지 않게", "아이도 먹기 좋게", "담백하게", "든든하게", "가볍게", "고단백")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("선호 조건 설정", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AI 추천에 반영할 선호 조건을 선택하거나 직접 입력하세요",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { preset ->
                        val selected = text.contains(preset)
                        FilterChip(selected = selected,
                            onClick = {
                                text = if (selected) text.replace(preset,"").replace("  "," ").trim()
                                       else if (text.isBlank()) preset else "$text, $preset"
                            },
                            label = { Text(preset, fontSize = 12.sp) })
                    }
                }
                OutlinedTextField(value = text, onValueChange = { text = it },
                    label = { Text("직접 입력", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(), maxLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("적용") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
