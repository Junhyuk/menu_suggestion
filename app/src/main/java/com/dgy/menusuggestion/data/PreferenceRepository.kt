package com.dgy.menusuggestion.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore 싱글턴
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "menu_preferences")

class PreferenceRepository(private val context: Context) {

    companion object {
        private val FAVORITES_KEY = stringPreferencesKey("favorites")
        private val SELECTED_TAB_KEY = stringPreferencesKey("selected_tab")

        @Volatile
        private var INSTANCE: PreferenceRepository? = null

        fun getInstance(context: Context): PreferenceRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferenceRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // 즐겨찾기 ID 목록 Flow
    val favoritesFlow: Flow<Set<Int>> = context.dataStore.data.map { prefs ->
        val raw = prefs[FAVORITES_KEY] ?: ""
        if (raw.isBlank()) emptySet()
        else raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    // 마지막 선택 탭 Flow
    val selectedTabFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SELECTED_TAB_KEY]?.toIntOrNull() ?: 0
    }

    // 즐겨찾기 토글 (추가/제거)
    suspend fun toggleFavorite(menuId: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[FAVORITES_KEY]
                ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toMutableSet()
                ?: mutableSetOf()
            if (current.contains(menuId)) current.remove(menuId) else current.add(menuId)
            prefs[FAVORITES_KEY] = current.joinToString(",")
        }
    }

    // 즐겨찾기 추가
    suspend fun addFavorite(menuId: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[FAVORITES_KEY]
                ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toMutableSet()
                ?: mutableSetOf()
            current.add(menuId)
            prefs[FAVORITES_KEY] = current.joinToString(",")
        }
    }

    // 즐겨찾기 제거
    suspend fun removeFavorite(menuId: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[FAVORITES_KEY]
                ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toMutableSet()
                ?: mutableSetOf()
            current.remove(menuId)
            prefs[FAVORITES_KEY] = current.joinToString(",")
        }
    }

    // 선택 탭 저장
    suspend fun saveSelectedTab(tabIndex: Int) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_TAB_KEY] = tabIndex.toString()
        }
    }
}

