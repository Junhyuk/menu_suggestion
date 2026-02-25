package com.dgy.menusuggestion.data

import com.dgy.menusuggestion.R

// 식사 시간대 탭
enum class MealTimeTab(val label: String, val emoji: String) {
    LUNCH("점심", "☀️"),
    DINNER("저녁", "🌙"),
    SNACK("안주", "🍺"),
    LATE_NIGHT("야식", "🌃");

    companion object {
        fun fromIndex(index: Int) = values()[index]
    }
}

// 탭별 추천 카테고리 매핑
val mealTimeCategoryMap = mapOf(
    MealTimeTab.LUNCH    to listOf("한식", "일식", "양식", "중식", "분식", "건강식"),
    MealTimeTab.DINNER   to listOf("한식", "일식", "양식", "중식"),
    MealTimeTab.SNACK    to listOf("양식", "분식", "한식"),
    MealTimeTab.LATE_NIGHT to listOf("분식", "한식", "양식")
)

// 탭별 추천 메뉴 ID 세트
val mealTimeMenuMap = mapOf(
    MealTimeTab.LUNCH      to listOf(1, 2, 3, 4, 7, 8, 9, 10),
    MealTimeTab.DINNER     to listOf(1, 2, 3, 5, 7, 8, 11),
    MealTimeTab.SNACK      to listOf(3, 5, 9, 10, 11),
    MealTimeTab.LATE_NIGHT to listOf(9, 10, 11, 12, 1, 5)
)

data class MenuItem(
    val id: Int,
    val name: String,
    val category: String,
    val description: String,
    val calories: String,
    val imageRes: Int,
    val tags: List<String> = emptyList()
)

data class Category(
    val id: Int,
    val name: String,
    val emoji: String
)

val sampleCategories = listOf(
    Category(0, "전체", "🍽️"),
    Category(1, "한식", "🍚"),
    Category(2, "일식", "🍣"),
    Category(3, "양식", "🍝"),
    Category(4, "중식", "🥢"),
    Category(5, "분식", "🌶️"),
    Category(6, "건강식", "🥗"),
)

val sampleMenuItems = listOf(
    MenuItem(
        id = 1,
        name = "김치찌개",
        category = "한식",
        description = "얼큰하고 깊은 맛의 한국 전통 찌개",
        calories = "350 kcal",
        imageRes = R.drawable.food_korean,
        tags = listOf("매운맛", "따뜻한", "국물")
    ),
    MenuItem(
        id = 2,
        name = "라멘",
        category = "일식",
        description = "진한 육수와 쫄깃한 면의 일본식 라멘",
        calories = "520 kcal",
        imageRes = R.drawable.food_ramen,
        tags = listOf("국물", "면요리", "따뜻한")
    ),
    MenuItem(
        id = 3,
        name = "피자",
        category = "양식",
        description = "바삭한 도우와 풍부한 토핑의 이탈리안 피자",
        calories = "680 kcal",
        imageRes = R.drawable.food_pizza,
        tags = listOf("치즈", "간식", "패밀리")
    ),
    MenuItem(
        id = 4,
        name = "샐러드",
        category = "건강식",
        description = "신선한 채소와 드레싱의 건강한 샐러드",
        calories = "180 kcal",
        imageRes = R.drawable.food_salad,
        tags = listOf("건강", "다이어트", "채식")
    ),
    MenuItem(
        id = 5,
        name = "버거",
        category = "양식",
        description = "두툼한 패티와 신선한 채소의 수제 버거",
        calories = "590 kcal",
        imageRes = R.drawable.food_burger,
        tags = listOf("간식", "패스트푸드", "든든한")
    ),
    MenuItem(
        id = 6,
        name = "건강 한 상",
        category = "건강식",
        description = "다양한 채소와 과일로 구성된 건강 정식",
        calories = "320 kcal",
        imageRes = R.drawable.food_healthy,
        tags = listOf("건강", "채식", "비타민")
    ),
    MenuItem(
        id = 7,
        name = "스시",
        category = "일식",
        description = "신선한 재료로 만든 정통 일본 스시",
        calories = "420 kcal",
        imageRes = R.drawable.food_sushi,
        tags = listOf("생선", "담백한", "고급")
    ),
    MenuItem(
        id = 8,
        name = "파스타",
        category = "양식",
        description = "알단테로 삶은 면에 진한 소스의 파스타",
        calories = "560 kcal",
        imageRes = R.drawable.food_pasta,
        tags = listOf("면요리", "이탈리안", "크리미")
    ),
    MenuItem(
        id = 9,
        name = "떡볶이",
        category = "분식",
        description = "매콤달콤한 소스에 쫄깃한 떡이 어우러진 국민 간식",
        calories = "480 kcal",
        imageRes = R.drawable.food_tteokbokki,
        tags = listOf("매운맛", "간식", "인기")
    ),
    MenuItem(
        id = 10,
        name = "모듬튀김",
        category = "분식",
        description = "다양한 재료를 바삭하게 튀겨낸 모듬 튀김",
        calories = "620 kcal",
        imageRes = R.drawable.food_twigim,
        tags = listOf("바삭한", "간식", "분식")
    ),
    MenuItem(
        id = 11,
        name = "순대",
        category = "분식",
        description = "내장과 함께 즐기는 고소하고 쫄깃한 순대",
        calories = "510 kcal",
        imageRes = R.drawable.food_sundae,
        tags = listOf("고소한", "든든한", "분식")
    ),
    MenuItem(
        id = 12,
        name = "라면",
        category = "분식",
        description = "꼬들꼬들한 면발과 얼큰한 국물의 조화",
        calories = "500 kcal",
        imageRes = R.drawable.food_ramen, // Reusing ramen image for now as per instruction to use web search but I can't do that effectively for binary, so reusing existing or placeholder
        tags = listOf("국물", "매운맛", "간편식")
    ),
)
