package com.dgy.menusuggestion

import com.dgy.menusuggestion.data.MealTimeTab
import com.dgy.menusuggestion.weather.WeatherInfo
import java.util.Calendar
import java.util.Locale

object LlamaPromptBuilder {

    data class DateTimeInfo(
        val date: String,
        val weekday: String,
        val month: Int,
        val season: String,
        val timePeriod: String,
        val hour: Int,
        val isWeekend: Boolean,      // 토/일
        val isHoliday: Boolean,      // 단순히 주말 여부 (공휴일 API 없으므로 주말=휴일)
        val dayType: String          // "월요일(평일)", "금요일(주말 전날)", "토요일(휴일)" 등
    )

    fun getDateTimeInfo(): DateTimeInfo {
        val cal    = Calendar.getInstance()
        val year   = cal.get(Calendar.YEAR)
        val month  = cal.get(Calendar.MONTH) + 1
        val day    = cal.get(Calendar.DAY_OF_MONTH)
        val hour   = cal.get(Calendar.HOUR_OF_DAY)
        val dow    = cal.get(Calendar.DAY_OF_WEEK)
        val weekday = when (dow) {
            Calendar.MONDAY    -> "월요일"
            Calendar.TUESDAY   -> "화요일"
            Calendar.WEDNESDAY -> "수요일"
            Calendar.THURSDAY  -> "목요일"
            Calendar.FRIDAY    -> "금요일"
            Calendar.SATURDAY  -> "토요일"
            else               -> "일요일"
        }
        val isWeekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        val dayType = when (dow) {
            Calendar.MONDAY    -> "월요일(주초 평일)"
            Calendar.FRIDAY    -> "금요일(주말 전날)"
            Calendar.SATURDAY  -> "토요일(휴일)"
            Calendar.SUNDAY    -> "일요일(휴일)"
            else               -> "$weekday(평일)"
        }
        val season = when (month) { 12, 1, 2 -> "겨울"; 3, 4, 5 -> "봄"; 6, 7, 8 -> "여름"; else -> "가을" }
        val timePeriod = when { hour < 10 -> "아침"; hour < 14 -> "점심"; hour < 18 -> "오후"; hour < 22 -> "저녁"; else -> "야식" }
        return DateTimeInfo(
            date       = String.format(Locale.US, "%04d-%02d-%02d", year, month, day),
            weekday    = weekday, month = month, season = season,
            timePeriod = timePeriod, hour = hour,
            isWeekend  = isWeekend, isHoliday = isWeekend, dayType = dayType
        )
    }

    // ── 1차 prompt: 메뉴 추천 ──────────────────────────────────────
    fun buildMenuRecommendPrompt(
        dt: DateTimeInfo,
        weather: WeatherInfo?,
        mealTab: MealTimeTab,
        categoryName: String = "전체",
        extraPrefs: String = "",
        likedMenuHistory: List<String> = emptyList(),
        eatAlone: Boolean = true,             // ★ 혼밥 여부
        healthyMode: Boolean = false          // ★ 건강식 선호
    ): String {
        val weatherDesc = if (weather != null)
            "기온 ${weather.temperature}℃(체감 ${weather.feelsLike}℃), " +
            "날씨 ${weather.skyCondition}, 습도 ${weather.humidity}%, 강수확률 ${weather.precipProbability}"
        else "날씨 정보 없음"

        val categoryHint = if (categoryName == "전체") "" else "\n- 음식 종류: $categoryName"
        val prefHint     = if (extraPrefs.isNotBlank()) "\n- 선호 조건: $extraPrefs" else ""
        val likedHint    = if (likedMenuHistory.isNotEmpty())
            "\n- 좋아요 이력 메뉴: ${likedMenuHistory.take(5).joinToString(", ")} (유사 계열 우선 추천)"
        else ""
        val eatStyle     = if (eatAlone) "혼밥(1인식사, 1인분 기준)" else "함께 먹기(2인 이상, 같이 먹기 좋은 메뉴)"
        val holidayHint  = if (dt.isHoliday) "오늘은 휴일이므로 여유롭고 특별한 메뉴도 가능" else "평일이므로 빠르고 간편한 메뉴 우선"
        val healthyHint  = if (healthyMode) "\n- 건강식 선호: 저칼로리·고영양·담백한 메뉴 우선 추천 (튀김/고칼로리 지양)" else ""

        val systemMsg = """너는 ${mealTab.label} 메뉴 추천 어시스턴트다.
규칙:
- 날씨/날짜/요일/계절/시간대를 반드시 반영한다.
- 혼밥/함께 여부에 맞는 메뉴를 추천한다.
- 평일/휴일 분위기에 맞게 추천한다.
- 좋아요 이력이 있으면 유사 계열 우선 추천한다.
- 출력 형식(마크다운 금지):
1) 추천 메뉴명: [메뉴명] - [한 줄 설명]
2) 추천 이유: [날씨/요일/식사 스타일 반영한 이유]
3) 핵심 태그: #태그1 #태그2 #태그3 (3~5개, 메뉴 특징)
4) 한 줄 질문: [추가 취향 확인 질문 1개]"""

        val userMsg = """오늘 ${mealTab.label} 메뉴 추천해줘.
참고 정보:
- 날짜/요일: ${dt.date} (${dt.dayType})
- 월/계절: ${dt.month}월(${dt.season})
- 시간대: ${dt.timePeriod}
- 날씨: $weatherDesc
- 식사 스타일: $eatStyle
- 오늘 분위기: $holidayHint$categoryHint$prefHint$likedHint$healthyHint
요청:
- ${dt.season} / ${dt.dayType} / $eatStyle 에 어울리는 ${mealTab.label} 메뉴 1개를 추천해줘."""

        return buildChatPrompt(systemMsg, userMsg)
    }

    // ── 2차 prompt: 칼로리 + 먹는 방법 ────────────────────────────
    fun buildMenuDetailPrompt(menuName: String, firstResponse: String): String {
        val systemMsg = """너는 음식 정보 어시스턴트다.
출력 형식(마크다운 금지):
1) 칼로리 정보
   - 1인분: XXX kcal
   - 주요 영양소: 탄수화물/단백질/지방
2) 맛있게 먹는 팁 (3가지)
3) 궁합 음식 (1~2가지, 이유 포함)"""

        val userMsg = """"$menuName"의 영양·섭취 정보를 알려줘."""
        return buildChatPrompt(systemMsg, userMsg)
    }

    // ── 3차 prompt: 반찬 + 차/디저트 ───────────────────────────────
    fun buildSideDishAndDessertPrompt(menuName: String): String {
        val systemMsg = """너는 식단 구성 어시스턴트다.
출력 형식(마크다운 금지):
1) 곁들일 반찬 추천 (2~3가지, 각각 한 줄 설명)
2) 식후 차 추천 (1가지, 이유 포함)
3) 식후 디저트 추천 (1가지, 이유 포함)"""

        val userMsg = """"$menuName"에 어울리는 반찬, 차, 디저트를 추천해줘."""
        return buildChatPrompt(systemMsg, userMsg)
    }

    // ── HyperCLOVA-X SEED chat template ──────────────────────────
    private fun buildChatPrompt(system: String, user: String): String =
        "<|im_start|>system\n$system<|im_end|>\n" +
        "<|im_start|>user\n$user<|im_end|>\n" +
        "<|im_start|>assistant\n"
}



