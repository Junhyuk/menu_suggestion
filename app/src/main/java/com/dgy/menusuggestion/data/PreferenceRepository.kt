package com.dgy.menusuggestion.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "menu_preferences")

// 평점 이력 항목
data class RatingEntry(
    val menuName: String,
    val rating: Int,       // 1~5점
    val timestamp: Long
)

class PreferenceRepository(private val context: Context) {

    companion object {
        private val FAVORITES_KEY      = stringPreferencesKey("favorites")
        private val SELECTED_TAB_KEY   = stringPreferencesKey("selected_tab")
        // 좋아요한 메뉴명 이력 (LLM 추천 결과에서 좋아요 누른 것)
        private val LIKED_MENUS_KEY    = stringPreferencesKey("liked_menus")
        // 평점 이력 "메뉴명:점수:타임스탬프|..." 형태
        private val RATINGS_KEY        = stringPreferencesKey("ratings")

        @Volatile private var INSTANCE: PreferenceRepository? = null
        fun getInstance(context: Context): PreferenceRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferenceRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    // ── 즐겨찾기 ─────────────────────────────────────────────────
    val favoritesFlow: Flow<Set<Int>> = context.dataStore.data.map { prefs ->
        val raw = prefs[FAVORITES_KEY] ?: ""
        if (raw.isBlank()) emptySet()
        else raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    suspend fun toggleFavorite(menuId: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[FAVORITES_KEY]
                ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toMutableSet() ?: mutableSetOf()
            if (current.contains(menuId)) current.remove(menuId) else current.add(menuId)
            prefs[FAVORITES_KEY] = current.joinToString(",")
        }
    }

    // ── 탭 저장 ──────────────────────────────────────────────────
    val selectedTabFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SELECTED_TAB_KEY]?.toIntOrNull() ?: 0
    }

    suspend fun saveSelectedTab(tabIndex: Int) {
        context.dataStore.edit { prefs -> prefs[SELECTED_TAB_KEY] = tabIndex.toString() }
    }

    // ── LLM 좋아요 메뉴 이력 ─────────────────────────────────────
    // "메뉴명1,메뉴명2,..." 최대 20개 유지
    val likedMenusFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[LIKED_MENUS_KEY] ?: ""
        if (raw.isBlank()) emptyList()
        else raw.split("|").filter { it.isNotBlank() }
    }

    suspend fun addLikedMenu(menuName: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[LIKED_MENUS_KEY] ?: "")
                .split("|").filter { it.isNotBlank() }.toMutableList()
            current.remove(menuName)           // 중복 제거
            current.add(0, menuName)           // 최신 순 앞에 추가
            prefs[LIKED_MENUS_KEY] = current.take(20).joinToString("|")
        }
    }

    suspend fun removeLikedMenu(menuName: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[LIKED_MENUS_KEY] ?: "")
                .split("|").filter { it.isNotBlank() && it != menuName }.toMutableList()
            prefs[LIKED_MENUS_KEY] = current.joinToString("|")
        }
    }

    // ── 평점 이력 ─────────────────────────────────────────────────
    // "메뉴명:점수:타임스탬프|..." 형태, 최대 50개
    val ratingsFlow: Flow<List<RatingEntry>> = context.dataStore.data.map { prefs ->
        val raw = prefs[RATINGS_KEY] ?: ""
        if (raw.isBlank()) emptyList()
        else raw.split("|").filter { it.isNotBlank() }.mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size >= 3) {
                val rating = parts[parts.size - 2].toIntOrNull() ?: return@mapNotNull null
                val ts     = parts[parts.size - 1].toLongOrNull() ?: return@mapNotNull null
                val name   = parts.dropLast(2).joinToString(":")
                RatingEntry(name, rating, ts)
            } else null
        }
    }

    suspend fun saveRating(menuName: String, rating: Int) {
        context.dataStore.edit { prefs ->
            val current = (prefs[RATINGS_KEY] ?: "")
                .split("|").filter { it.isNotBlank() }.toMutableList()
            // 같은 메뉴 기존 평점 제거 후 새로 추가
            current.removeAll { it.startsWith("$menuName:") }
            current.add(0, "$menuName:$rating:${System.currentTimeMillis()}")
            prefs[RATINGS_KEY] = current.take(50).joinToString("|")
        }
    }
}

