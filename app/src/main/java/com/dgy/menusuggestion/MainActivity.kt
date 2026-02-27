package com.dgy.menusuggestion

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.dgy.menusuggestion.ui.MenuSuggestionScreen
import com.dgy.menusuggestion.ui.theme.MenusuggestionTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LlamaInference"
    }

    // ViewModel은 Activity 스코프에서 생성 (LlamaInference 라이프사이클 관리)
    private val llamaViewModel: LlamaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "====== MainActivity onCreate ======")
        enableEdgeToEdge()

        setContent {
            MenusuggestionTheme {
                MenuSuggestionScreen(llamaViewModel = llamaViewModel)
            }
        }
    }
}
