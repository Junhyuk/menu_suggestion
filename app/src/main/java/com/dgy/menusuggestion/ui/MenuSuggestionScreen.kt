package com.dgy.menusuggestion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dgy.menusuggestion.LlamaViewModel
import com.dgy.menusuggestion.data.*
import com.dgy.menusuggestion.weather.WeatherCard
import com.dgy.menusuggestion.weather.WeatherInfo
import com.dgy.menusuggestion.weather.WeatherViewModel
import com.dgy.menusuggestion.weather.WeatherViewModelFactory
import kotlinx.coroutines.launch
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────
// 이미지 로드 실패 시 placeholder 표시 헬퍼
// ─────────────────────────────────────────────────────────────
@Composable
fun MenuItemImage(
    imageRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    // 이미지 로드를 시도하고, 실패하면 색상 박스 placeholder를 표시
    val painter = runCatching { painterResource(id = imageRes) }.getOrNull()
    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        // placeholder: 카테고리별 색상 구분 박스
        val placeholderColors = listOf(
            Color(0xFFE8F5E9), Color(0xFFE3F2FD), Color(0xFFFFF3E0),
            Color(0xFFF3E5F5), Color(0xFFE0F7FA), Color(0xFFFCE4EC)
        )
        val colorIndex = (imageRes % placeholderColors.size).let { if (it < 0) 0 else it }
        Box(
            modifier = modifier.background(placeholderColors[colorIndex]),
            contentAlignment = Alignment.Center
        ) {
            Text("🍽️", fontSize = 28.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 메뉴 이미지 안전 로더 (이미지 없을 때 컬러 placeholder)
// ─────────────────────────────────────────────────────────────
@Composable
fun SafeMenuImage(
    imageRes: Int,
    menuName: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val placeholderColors = listOf(
        Color(0xFF6C8EBF), Color(0xFF82B366), Color(0xFFD6B656),
        Color(0xFFAE4132), Color(0xFF9673A6), Color(0xFF5D7B8A)
    )
    val colorIndex = menuName.hashCode().and(0x7FFFFFFF) % placeholderColors.size

    // 1KB 미만 플레이스홀더 여부를 리소스 ID 유효성으로 판단
    val isPlaceholder = try {
        // 실제 이미지 시도 — 실패하면 placeholder 표시
        false
    } catch (e: Exception) { true }

    if (!isPlaceholder) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = menuName,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier = modifier.background(placeholderColors[colorIndex]),
            contentAlignment = Alignment.Center) {
            Text(menuName.take(1), color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = 22.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 메인 화면
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSuggestionScreen(
    llamaViewModel: LlamaViewModel = viewModel()
) {
    val context = LocalContext.current
    // ★ WeatherViewModel을 화면 레벨에서 한 번만 생성
    //    탭이 바뀌어도 이 인스턴스를 재사용 → 재조회 없음
    val weatherViewModel: WeatherViewModel = viewModel(factory = WeatherViewModelFactory(context))
    val prefRepo = remember { PreferenceRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { llamaViewModel.loadModelIfNeeded() }

    val favorites by prefRepo.favoritesFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    val savedTabIndex by prefRepo.selectedTabFlow.collectAsStateWithLifecycle(initialValue = 0)
    val pagerState = rememberPagerState(initialPage = savedTabIndex) { MealTimeTab.values().size }

    val weatherState by weatherViewModel.uiState.collectAsStateWithLifecycle()
    val currentWeather = (weatherState as? WeatherViewModel.WeatherUiState.Success)?.weather

    // ★ 탭이 바뀔 때는 날씨 재조회하지 않음 (WeatherCard 내부의 LaunchedEffect(Unit)이 담당)
    LaunchedEffect(pagerState.currentPage) {
        scope.launch { prefRepo.saveSelectedTab(pagerState.currentPage) }
    }

    val selectedCategories = remember { mutableStateMapOf<Int, Int>() }
    var randomMenu by remember { mutableStateOf<MenuItem?>(null) }
    var showRandom by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("오늘 뭐 먹지? 🍽️", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("메뉴를 추천해드려요", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface)
                )
                MealTimeTabRow(pagerState = pagerState,
                    onTabClick = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } })
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val tab = MealTimeTab.fromIndex(pagerState.currentPage)
                    val pool = mealTimeMenuMap[tab]?.mapNotNull { id ->
                        sampleMenuItems.find { it.id == id }
                    } ?: sampleMenuItems
                    randomMenu = pool[Random.nextInt(pool.size)]
                    showRandom = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Casino, contentDescription = "랜덤 메뉴", tint = Color.White)
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) { pageIndex ->
            val tab = MealTimeTab.fromIndex(pageIndex)
            val catId = selectedCategories[pageIndex] ?: 0
            val catName = if (catId == 0) "전체" else sampleCategories.find { it.id == catId }?.name ?: "전체"
            MealTimePage(
                tab = tab,
                favorites = favorites,
                selectedCategory = catId,
                onCategorySelected = { selectedCategories[pageIndex] = it },
                onFavoriteToggle = { id -> scope.launch { prefRepo.toggleFavorite(id) } },
                showRandom = showRandom && pageIndex == pagerState.currentPage,
                randomMenu = randomMenu,
                onCloseRandom = { showRandom = false },
                llamaViewModel = llamaViewModel,
                weather = currentWeather,
                categoryName = catName,
                prefRepo = prefRepo
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 식사 시간 탭 Row (점심/저녁/안주/야식)
// ─────────────────────────────────────────────────────────────
@Composable
fun MealTimeTabRow(
    pagerState: androidx.compose.foundation.pager.PagerState,
    onTabClick: (Int) -> Unit
) {
    val tabs = MealTimeTab.values()
    TabRow(
        selectedTabIndex = pagerState.currentPage,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = { onTabClick(index) },
                text = {
                    Text(
                        text = "${tab.emoji} ${tab.label}",
                        fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 각 탭 페이지
// ─────────────────────────────────────────────────────────────
@Composable
fun MealTimePage(
    tab: MealTimeTab,
    favorites: Set<Int>,
    selectedCategory: Int,
    onCategorySelected: (Int) -> Unit,
    onFavoriteToggle: (Int) -> Unit,
    showRandom: Boolean,
    randomMenu: MenuItem?,
    onCloseRandom: () -> Unit,
    llamaViewModel: LlamaViewModel? = null,
    weather: com.dgy.menusuggestion.weather.WeatherInfo? = null,
    categoryName: String = "전체",
    prefRepo: PreferenceRepository? = null
) {
    val tabMenuIds = mealTimeMenuMap[tab] ?: sampleMenuItems.map { it.id }

    // ★ id 기반 카테고리 필터링 (순서 변경에 안전)
    val selectedCategoryObj = sampleCategories.find { it.id == selectedCategory }
    val filteredMenus = remember(selectedCategory, tab) {
        val tabMenus = sampleMenuItems.filter { it.id in tabMenuIds }
        when (selectedCategory) {
            0    -> tabMenus   // 전체
            6    -> tabMenus.filter { it.category in listOf("건강식","한국건강식","글로벌건강") }
            7    -> tabMenus.filter { it.category == "한국건강식" }
            8    -> tabMenus.filter { it.category == "글로벌건강" }
            else -> {
                val catName = sampleCategories.find { it.id == selectedCategory }?.name ?: ""
                tabMenus.filter { it.category == catName }
            }
        }
    }

    val favMenusInTab = remember(favorites, tab) {
        sampleMenuItems.filter { it.id in tabMenuIds && it.id in favorites }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { WeatherCard() }

        // ── AI 메뉴 추천 뷰 ────────────────────────────────────
        if (llamaViewModel != null && prefRepo != null) {
            item {
                LlmRecommendView(
                    viewModel = llamaViewModel,
                    currentTab = tab,
                    selectedCategoryName = categoryName,
                    weather = weather,
                    prefRepo = prefRepo
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        item {
            AnimatedVisibility(visible = showRandom && randomMenu != null,
                enter = fadeIn() + scaleIn(), exit = fadeOut()) {
                randomMenu?.let { RandomMenuBanner(menu = it, onClose = onCloseRandom) }
            }
        }

        if (favMenusInTab.isNotEmpty()) {
            item {
                FavoriteRecommendSection(
                    menus = favMenusInTab, favorites = favorites, onFavoriteToggle = onFavoriteToggle)
            }
        }

        item {
            CategorySection(categories = sampleCategories, selectedId = selectedCategory,
                onCategorySelected = onCategorySelected)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (selectedCategory == 0) "${tab.label} 추천 메뉴"
                           else "${selectedCategoryObj?.name ?: ""} 메뉴",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${filteredMenus.size}개", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary)
            }
        }

        item {
            FeaturedSection(menus = filteredMenus.take(2), favorites = favorites,
                onFavoriteToggle = onFavoriteToggle)
        }

        items(filteredMenus.drop(2)) { menu ->
            MenuListItem(menu = menu, isFavorite = menu.id in favorites,
                onFavoriteToggle = { onFavoriteToggle(menu.id) })
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant)
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────
// 즐겨찾기 재추천 섹션
// ─────────────────────────────────────────────────────────────
@Composable
fun FavoriteRecommendSection(
    menus: List<MenuItem>,
    favorites: Set<Int>,
    onFavoriteToggle: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Star, contentDescription = null,
                tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
            Text("즐겨찾기 메뉴", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Surface(shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFFB300).copy(alpha = 0.15f)) {
                Text("${menus.size}개",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 11.sp, color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(menus) { menu ->
                FavoriteMenuChip(menu = menu,
                    onFavoriteToggle = { onFavoriteToggle(menu.id) })
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
fun FavoriteMenuChip(menu: MenuItem, onFavoriteToggle: () -> Unit) {
    Card(modifier = Modifier.width(140.dp), shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Box {
            SafeMenuImage(
                imageRes = menu.imageRes,
                menuName = menu.name,
                modifier = Modifier.fillMaxWidth().height(80.dp),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxWidth().height(80.dp)
                .background(Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(0.6f)))))
            IconButton(onClick = onFavoriteToggle,
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                Icon(Icons.Default.Favorite, contentDescription = "즐겨찾기 해제",
                    tint = Color.Red, modifier = Modifier.size(16.dp))
            }
            Text(menu.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
        }
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(Icons.Default.LocalFireDepartment, contentDescription = null,
                tint = Color(0xFFFF7043), modifier = Modifier.size(12.dp))
            Text(menu.calories, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 랜덤 추천 배너
// ─────────────────────────────────────────────────────────────
@Composable
fun RandomMenuBanner(menu: MenuItem, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)
        .clip(RoundedCornerShape(16.dp)).clickable { onClose() }) {
        SafeMenuImage(
            imageRes = menu.imageRes,
            menuName = menu.name,
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentScale = ContentScale.Crop
        )
        Box(modifier = Modifier.fillMaxWidth().height(180.dp)
            .background(Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(0.75f)))))
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text("🎲 오늘의 추천 메뉴", color = Color.White.copy(0.85f), fontSize = 12.sp)
            Text(menu.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(menu.description, color = Color.White.copy(0.8f), fontSize = 13.sp)
        }
        Surface(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            shape = CircleShape, color = Color.Black.copy(0.4f)) {
            Text("✕", color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 카테고리 필터 Row
// ─────────────────────────────────────────────────────────────
@Composable
fun CategorySection(categories: List<Category>, selectedId: Int,
    onCategorySelected: (Int) -> Unit) {
    LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories) { category ->
            val isSelected = category.id == selectedId
            FilterChip(selected = isSelected, onClick = { onCategorySelected(category.id) },
                label = {
                    Text("${category.emoji} ${category.name}",
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 피처드 카드 (가로 스크롤)
// ─────────────────────────────────────────────────────────────
@Composable
fun FeaturedSection(menus: List<MenuItem>, favorites: Set<Int>, onFavoriteToggle: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 8.dp)) {
        items(menus) { menu ->
            FeaturedMenuCard(menu = menu, isFavorite = menu.id in favorites,
                onFavoriteToggle = { onFavoriteToggle(menu.id) })
        }
    }
}

@Composable
fun FeaturedMenuCard(menu: MenuItem, isFavorite: Boolean, onFavoriteToggle: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f, animationSpec = tween(100), label = "scale")
    val heartColor by animateColorAsState(
        targetValue = if (isFavorite) Color.Red else Color.White, label = "heart")

    // ★ 건강식 배지 색상
    val healthBadgeColor: Color? = when (menu.category) {
        "한국건강식" -> Color(0xFF2E7D32)
        "글로벌건강" -> Color(0xFF1565C0)
        "건강식"     -> Color(0xFF00695C)
        else         -> null
    }
    val healthBadgeLabel: String? = when (menu.category) {
        "한국건강식" -> "🌿 한국건강"
        "글로벌건강" -> "🌍 글로벌건강"
        "건강식"     -> "🥗 건강식"
        else         -> null
    }

    Card(modifier = Modifier.width(220.dp).scale(scale).clickable { pressed = !pressed },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)) {
        Box {
            SafeMenuImage(
                imageRes = menu.imageRes,
                menuName = menu.name,
                modifier = Modifier.fillMaxWidth().height(140.dp),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxWidth().height(140.dp)
                .background(Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(0.6f)))))
            // ★ 건강식 배지
            if (healthBadgeLabel != null && healthBadgeColor != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = healthBadgeColor.copy(alpha = 0.9f)
                ) {
                    Text(healthBadgeLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            IconButton(onClick = onFavoriteToggle, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "즐겨찾기", tint = heartColor)
            }
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(menu.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null,
                        tint = Color(0xFFFF7043), modifier = Modifier.size(14.dp))
                    Text(menu.calories, color = Color.White.copy(0.9f), fontSize = 12.sp)
                }
            }
        }
        Column(modifier = Modifier.padding(12.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(menu.tags) { tag ->
                    Surface(shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(tag, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 메뉴 리스트 아이템
// ─────────────────────────────────────────────────────────────
@Composable
fun MenuListItem(menu: MenuItem, isFavorite: Boolean, onFavoriteToggle: () -> Unit) {
    val heartColor by animateColorAsState(
        targetValue = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "heart")

    // ★ 건강식 서브카테고리 배지 정보
    val healthBadge: Pair<String, Color>? = when (menu.category) {
        "한국건강식" -> Pair("🌿 한국 건강식", Color(0xFF2E7D32))
        "글로벌건강" -> Pair("🌍 글로벌 건강식", Color(0xFF1565C0))
        "건강식"     -> Pair("🥗 건강식", Color(0xFF00695C))
        else         -> null
    }

    // ★ 건강식 아이템은 배경색 구분
    val bgColor = when (menu.category) {
        "한국건강식" -> Color(0xFFF1F8E9)   // 연초록
        "글로벌건강" -> Color(0xFFE3F2FD)   // 연파랑
        "건강식"     -> Color(0xFFE8F5E9)   // 연초록
        else         -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(if (healthBadge != null) 2.dp else 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().clickable { }
            .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SafeMenuImage(
                imageRes = menu.imageRes,
                menuName = menu.name,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                // 건강식 서브카테고리 배지 (한국/글로벌 구분)
                if (healthBadge != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = healthBadge.second.copy(alpha = 0.13f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(healthBadge.first,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = healthBadge.second)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(menu.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    if (healthBadge == null) {
                        Surface(shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(menu.category,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(menu.description, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null,
                        tint = Color(0xFFFF7043), modifier = Modifier.size(14.dp))
                    Text(menu.calories, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onFavoriteToggle) {
                Icon(imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "즐겨찾기", tint = heartColor)
            }
        }
    }
}

