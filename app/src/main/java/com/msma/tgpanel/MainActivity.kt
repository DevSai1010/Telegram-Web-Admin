package com.msma.tgpanel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.msma.tgpanel.ui.AppRoot
import com.msma.tgpanel.ui.TgPanelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TgPanelTheme { AppRoot() }
        }
    }
}
