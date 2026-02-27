package com.dgy.menusuggestion.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PreferenceViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PreferenceRepository.getInstance(application)

    // 즐겨찾기 ID Set (영구 저장 → Flow)
    val favorites: StateFlow<Set<Int>> = repo.favoritesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // 마지막 선택 탭
    val selectedTab: StateFlow<Int> = repo.selectedTabFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // 즐겨찾기 토글
    fun toggleFavorite(menuId: Int) {
        viewModelScope.launch { repo.toggleFavorite(menuId) }
    }

    // 탭 저장
    fun saveTab(tabIndex: Int) {
        viewModelScope.launch { repo.saveSelectedTab(tabIndex) }
    }

    // 즐겨찾기 메뉴만 필터링
    fun getFavoriteMenus(): List<MenuItem> {
        val favIds = favorites.value
        return sampleMenuItems.filter { it.id in favIds }
    }

    // 탭 + 즐겨찾기 기반 재추천 (즐겨찾기 우선)
    fun getRecommendedMenus(tab: MealTimeTab): List<MenuItem> {
        val tabMenuIds = mealTimeMenuMap[tab] ?: sampleMenuItems.map { it.id }
        val favIds = favorites.value

        val tabMenus = sampleMenuItems.filter { it.id in tabMenuIds }
        // 즐겨찾기한 메뉴를 탭 필터 안에서 우선 배치
        val favFirst = tabMenus.sortedByDescending { if (it.id in favIds) 1 else 0 }
        return favFirst
    }

    companion object {
        val Factory: ViewModelProvider.Factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            android.app.Application()
        )
    }
}

