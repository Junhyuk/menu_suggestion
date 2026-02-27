package com.dgy.menusuggestion.data

import com.dgy.menusuggestion.R

// 식사 시간대 탭
enum class MealTimeTab(val label: String, val emoji: String) {
    BREAKFAST("아침", "🌅"),
    LUNCH("점심", "☀️"),
    DINNER("저녁", "🌙"),
    SNACK("안주", "🍺"),
    LATE_NIGHT("야식", "🌃"),
    DESSERT("간식", "🍡");

    companion object {
        fun fromIndex(index: Int) = values()[index]
    }
}

// ★ 카테고리 순서: 전체 → 건강식 → 한식 → 일식 → 양식 → 중식 → 분식
val sampleCategories = listOf(
    Category(0, "전체",      "🍽️"),
    Category(6, "건강식",    "🥗"),   // ★ 전체 바로 다음
    Category(7, "한국건강식","🌿"),   // ★ 한국식 건강 메뉴
    Category(8, "글로벌건강","🌍"),   // ★ 글로벌 건강 메뉴
    Category(1, "한식",      "🍚"),
    Category(2, "일식",      "🍣"),
    Category(3, "양식",      "🍝"),
    Category(4, "중식",      "🥢"),
    Category(5, "분식",      "🌶️"),
)

// 탭별 추천 카테고리
val mealTimeCategoryMap = mapOf(
    MealTimeTab.BREAKFAST  to listOf("한국건강식", "글로벌건강", "건강식", "한식"),
    MealTimeTab.LUNCH      to listOf("건강식", "한국건강식", "글로벌건강", "한식", "일식", "양식", "중식", "분식"),
    MealTimeTab.DINNER     to listOf("한식", "한국건강식", "글로벌건강", "일식", "양식", "중식"),
    MealTimeTab.SNACK      to listOf("양식", "분식", "한식"),
    MealTimeTab.LATE_NIGHT to listOf("분식", "한식", "양식"),
    MealTimeTab.DESSERT    to listOf("분식", "건강식", "글로벌건강", "양식")
)

// 탭별 메뉴 ID
val mealTimeMenuMap = mapOf(
    MealTimeTab.BREAKFAST  to listOf(701, 702, 703, 704, 705, 801, 802, 803, 101, 104, 106, 107, 201, 209),
    MealTimeTab.LUNCH      to listOf(101,102,103,105,201,202,203,204,205,206,207,208,209,
                                     301,302,303,401,402,403,501,502,503,601,602,606,
                                     701,702,703,704,705,706,707,708,709,
                                     801,802,803,804,805,806,807,808,809,810),
    MealTimeTab.DINNER     to listOf(101,102,103,105,201,202,203,204,206,208,209,
                                     301,302,303,401,402,403,501,502,503,
                                     703,705,706,801,803,806,809),
    MealTimeTab.SNACK      to listOf(103,105,204,401,501,502,503,601,602,606),
    MealTimeTab.LATE_NIGHT to listOf(601,602,603,604,606,501,502,401,403),
    MealTimeTab.DESSERT    to listOf(604,605,106,201,203,804,808)
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

data class Category(val id: Int, val name: String, val emoji: String)

// ═══════════════════════════════════════════════════════
//  전체 메뉴 데이터 (카테고리별)
// ═══════════════════════════════════════════════════════
val sampleMenuItems: List<MenuItem> = listOf(

    // ── 건강식 (id: 100번대) ─────────────────────────────
    MenuItem(101, "샐러드",     "건강식", "신선한 채소와 드레싱의 저칼로리 샐러드",     "250 kcal", R.drawable.food_salad,
        listOf("건강", "다이어트", "채식", "저칼로리")),
    MenuItem(102, "나물비빔밥", "건강식", "제철 나물과 현미밥으로 만든 영양 비빔밥",    "480 kcal", R.drawable.food_bibimbap,
        listOf("건강", "채식", "균형식", "고단백")),
    MenuItem(103, "된장찌개",   "건강식", "된장과 두부·채소를 끓인 발효 건강 찌개",     "350 kcal", R.drawable.food_doenjang_jjigae,
        listOf("발효식품", "건강", "국물", "담백")),
    MenuItem(104, "순두부찌개", "건강식", "부드러운 두부와 해물의 얼큰한 찌개",         "400 kcal", R.drawable.food_sundubu_jjigae,
        listOf("단백질", "건강", "국물", "따뜻한")),
    MenuItem(105, "잡채",       "건강식", "당면·채소·고기를 볶아낸 영양 잡채",         "500 kcal", R.drawable.food_japchae,
        listOf("건강", "채소", "명절", "고소")),
    MenuItem(106, "건강 한 상", "건강식", "다양한 채소와 현미밥의 건강 정식",           "320 kcal", R.drawable.food_healthy,
        listOf("건강", "채식", "비타민", "저칼로리")),
    MenuItem(107, "설렁탕",     "건강식", "사골을 오래 끓인 담백하고 진한 국물",        "600 kcal", R.drawable.food_seolleongtang,
        listOf("단백질", "콜라겐", "국물", "담백")),

    // ── 한식 (id: 200번대) ──────────────────────────────
    MenuItem(201, "김치찌개",   "한식", "잘 익은 김치와 돼지고기의 얼큰한 찌개",       "450 kcal", R.drawable.food_kimchi_jjigae,
        listOf("매운맛", "따뜻한", "국물", "한국전통")),
    MenuItem(202, "비빔밥",     "한식", "밥 위에 나물·고기·계란을 올린 한 그릇",      "650 kcal", R.drawable.food_bibimbap,
        listOf("한식", "영양", "고추장", "다양한")),
    MenuItem(203, "불고기",     "한식", "간장 양념에 재운 달짝지근한 소고기 볶음",     "550 kcal", R.drawable.food_bulgogi,
        listOf("고기", "달콤", "간장양념", "인기")),
    MenuItem(204, "삼겹살",     "한식", "두툼한 돼지고기를 구워 쌈과 함께",           "900 kcal", R.drawable.food_samgyeopsal,
        listOf("고기", "구이", "쌈", "술안주")),
    MenuItem(205, "김밥",       "한식", "밥과 다양한 속재료를 김에 말아낸 간편 메뉴", "450 kcal", R.drawable.food_kimbap,
        listOf("간편", "소풍", "한입", "다양한")),
    MenuItem(206, "갈비탕",     "한식", "소갈비를 오래 고아 맑고 깊은 맛이 나는 국물 요리", "700 kcal", R.drawable.food_galbitang,
        listOf("고기", "국물", "든든", "보양식")),
    MenuItem(207, "냉면",       "한식", "시원한 육수에 쫄깃한 면의 냉면",             "550 kcal", R.drawable.food_naengmyeon,
        listOf("시원한", "면요리", "여름", "담백")),
    MenuItem(208, "부대찌개",   "한식", "햄·소시지·라면이 어우러진 얼큰한 찌개",     "750 kcal", R.drawable.food_budae_jjigae,
        listOf("매운맛", "든든", "국물", "퓨전")),
    MenuItem(209, "된장찌개",   "한식", "된장과 채소·두부를 넣어 끓인 구수한 찌개",   "350 kcal", R.drawable.food_doenjang_jjigae,
        listOf("발효식품", "구수", "국물", "한국전통")),

    // ── 일식 (id: 300번대) ──────────────────────────────
    MenuItem(301, "초밥",       "일식", "신선한 재료로 만든 정통 일본식 초밥",        "420 kcal", R.drawable.food_sushi,
        listOf("생선", "담백", "고급", "신선")),
    MenuItem(302, "라멘",       "일식", "진한 육수와 쫄깃한 면의 일본식 라멘",        "520 kcal", R.drawable.food_ramen,
        listOf("국물", "면요리", "따뜻한", "농후")),
    MenuItem(303, "돈가스",     "일식", "빵가루를 입혀 바삭하게 튀긴 돼지고기 ��틀릿", "950 kcal", R.drawable.food_donkatsu,
        listOf("바삭", "튀김", "고기", "든든")),

    // ── 양식 (id: 400번대) ──────────────────────────────
    MenuItem(401, "피자",       "양식", "바삭한 도우와 풍부한 토핑의 이탈리안 피자", "950 kcal", R.drawable.food_pizza,
        listOf("치즈", "간식", "패밀리", "이탈리안")),
    MenuItem(402, "파스타",     "양식", "알단테로 삶은 면에 소스와 즐기는 이탈리안", "700 kcal", R.drawable.food_pasta,
        listOf("면요리", "이탈리안", "크리미", "양식")),
    MenuItem(403, "햄버거",     "양식", "두툼한 패티와 신선한 채소의 수제 버거",     "700 kcal", R.drawable.food_burger,
        listOf("패스트푸드", "간식", "든든", "양식")),

    // ── 중식 (id: 500번대) ──────────────────────────────
    MenuItem(501, "짜장면",     "중식", "춘장 소스의 고소하고 달콤한 면요리",        "800 kcal", R.drawable.food_jjajangmyeon,
        listOf("달콤", "면요리", "중식", "인기")),
    MenuItem(502, "짬뽕",       "중식", "해물과 채소의 얼큰한 국물 면요리",          "750 kcal", R.drawable.food_jjamppong,
        listOf("매운맛", "해물", "국물", "중식")),
    MenuItem(503, "탕수육",     "중식", "바삭하게 튀긴 고기에 새콤달콤한 소스",     "900 kcal", R.drawable.food_tangsuyuk,
        listOf("바삭", "달콤", "안주", "중식")),

    // ── 분식 (id: 600번대) ──────────────────────────────
    MenuItem(601, "떡볶이",     "분식", "매콤달콤한 소스에 쫄깃한 떡의 국민 간식",  "600 kcal", R.drawable.food_tteokbokki,
        listOf("매운맛", "간식", "분식", "인기")),
    MenuItem(602, "모듬튀김",   "분식", "다양한 재료를 바삭하게 튀겨낸 튀김",       "620 kcal", R.drawable.food_twigim,
        listOf("바삭", "간식", "분식", "고소")),
    MenuItem(603, "순대",       "분식", "내장과 함께 즐기는 고소하고 쫄깃한 순대",  "510 kcal", R.drawable.food_sundae,
        listOf("고소", "든든", "분식", "쫄깃")),
    MenuItem(604, "라면",       "분식", "꼬들꼬들한 면발과 얼큰한 국물의 조화",     "500 kcal", R.drawable.food_ramyeon,
        listOf("매운맛", "간편", "야식", "면요리")),
    MenuItem(605, "호떡",       "분식", "달콤한 흑설탕 소를 넣은 따뜻한 호떡",     "300 kcal", R.drawable.food_hotteok,
        listOf("달콤", "간식", "겨울", "길거리")),
    MenuItem(606, "치킨",       "분식", "바삭한 튀김옷과 촉촉한 속살이 특징인 대표 야식", "1100 kcal", R.drawable.food_fried_chicken,
        listOf("바삭", "치킨", "야식", "인기")),

    // ── 한국식 건강식 (id: 700번대) ─────────────────────────
    MenuItem(701, "콩국수",     "한국건강식", "콩을 갈아 만든 고소한 콩국물에 면을 말아 먹는 여름 건강식", "600 kcal",
        R.drawable.food_kh_kongguksu, listOf("단백질", "여름", "담백", "건강")),
    MenuItem(702, "미역국",     "한국건강식", "깔끔한 국물에 미역을 끓인 저칼로리 건강 국",              "180 kcal",
        R.drawable.food_kh_miyeokguk, listOf("저칼로리", "미역", "담백", "건강")),
    MenuItem(703, "된장찌개",   "한국건강식", "된장 베이스로 채소·두부를 넣어 끓인 발효 건강 찌개",      "320 kcal",
        R.drawable.food_kh_doenjang_jjigae, listOf("발효", "두부", "건강", "담백")),
    MenuItem(704, "청국장찌개", "한국건강식", "발효 콩으로 만든 단백질 풍부 찌개",                      "350 kcal",
        R.drawable.food_kh_cheonggukjang, listOf("발효", "단백질", "건강", "구수")),
    MenuItem(705, "콩나물국밥", "한국건강식", "콩나물국에 밥을 말아 먹는 담백하고 따뜻한 한 그릇",      "500 kcal",
        R.drawable.food_kh_kongnamul_gukbap, listOf("담백", "국밥", "건강", "따뜻")),
    MenuItem(706, "두부조림",   "한국건강식", "두부를 양념에 졸인 고단백 저칼로리 메뉴",                "280 kcal",
        R.drawable.food_kh_dubu_jorim, listOf("두부", "단백질", "건강", "저칼로리")),
    MenuItem(707, "도토리묵",   "한국건강식", "담백한 도토리묵에 채소를 곁들인 건강 무침",              "250 kcal",
        R.drawable.food_kh_dotorimuk, listOf("저칼로리", "담백", "채식", "건강")),
    MenuItem(708, "전복죽",     "한국건강식", "부드럽고 소화가 편한 영양 전복죽",                       "420 kcal",
        R.drawable.food_kh_jeonbokjuk, listOf("보양식", "부드러운", "건강", "회복")),
    MenuItem(709, "물회",       "한국건강식", "회와 채소, 육수를 시원하게 즐기는 여름 건강식",          "430 kcal",
        R.drawable.food_kh_mulhoe, listOf("시원한", "여름", "담백", "건강")),

    // ── 글로벌 건강식 (id: 800번대) ─────────────────────────
    MenuItem(801, "그릭 샐러드",      "글로벌건강", "토마토·오이·올리브·치즈가 어우러진 지중해 샐러드", "350 kcal",
        R.drawable.food_gh_greek_salad, listOf("지중해", "샐러드", "채식", "건강")),
    MenuItem(802, "퀴노아 샐러드",    "글로벌건강", "퀴노아에 채소를 더한 포만감 높은 건강 샐러드",     "420 kcal",
        R.drawable.food_gh_quinoa_salad, listOf("퀴노아", "고단백", "건강", "샐러드")),
    MenuItem(803, "렌틸 수프",        "글로벌건강", "렌틸콩을 끓인 단백질·식이섬유 풍부한 수프",        "320 kcal",
        R.drawable.food_gh_lentil_soup, listOf("식이섬유", "단백질", "수프", "건강")),
    MenuItem(804, "미소된장국",       "글로벌건강", "된장(미소) 베이스의 따뜻한 일본식 국",             "80 kcal",
        R.drawable.food_gh_miso_soup, listOf("저칼로리", "발효", "담백", "건강")),
    MenuItem(805, "연어 사시미",      "글로벌건강", "오메가3 풍부한 담백한 연어 회 메뉴",               "300 kcal",
        R.drawable.food_gh_salmon_sashimi, listOf("오메가3", "단백질", "건강", "담백")),
    MenuItem(806, "포케 보울",        "글로벌건강", "곡물·채소·단백질을 한 그릇에 담은 하와이식 건강식", "650 kcal",
        R.drawable.food_gh_poke_bowl, listOf("균형식", "고단백", "건강", "하와이")),
    MenuItem(807, "두부 샐러드",      "글로벌건강", "두부를 샐러드 토핑으로 활용한 저칼로리 메뉴",      "380 kcal",
        R.drawable.food_gh_tofu_salad, listOf("두부", "저칼로리", "채식", "건강")),
    MenuItem(808, "그릴드 닭가슴살",  "글로벌건강", "단백질 중심 그릴드 닭가슴살, 채소와 함께",        "300 kcal",
        R.drawable.food_gh_grilled_chicken_breast, listOf("고단백", "다이어트", "건강", "담백")),
    MenuItem(809, "콘지(쌀죽)",       "글로벌건강", "쌀을 푹 끓인 부드러운 중국식 건강 죽",             "350 kcal",
        R.drawable.food_gh_congee, listOf("소화", "부드러운", "건강", "회복")),
    MenuItem(810, "마파두부(채식)",   "글로벌건강", "두부 중심의 담백한 채식 마파두부",                  "450 kcal",
        R.drawable.food_gh_mapo_tofu_veg, listOf("두부", "채식", "건강", "담백"))
)
