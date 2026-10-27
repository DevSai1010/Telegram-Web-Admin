package com.msma.tgpanel

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.msma.tgpanel.data.Prefs
import com.msma.tgpanel.ui.AppRoot
import com.msma.tgpanel.ui.TgPanelTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    /** In-app language: when the user picked one in Settings, the whole UI resolves its strings (and RTL/LTR) from it. */
    override fun attachBaseContext(newBase: Context) {
        val lang = Prefs(newBase).language
        if (lang.isEmpty()) { super.attachBaseContext(newBase); return }
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val conf = Configuration(newBase.resources.configuration)
        conf.setLocale(locale)
        conf.setLayoutDirection(locale)
        super.attachBaseContext(newBase.createConfigurationContext(conf))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            TgPanelTheme { AppRoot() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val chat = i?.getStringExtra(Notifier.EXTRA_CHAT) ?: return
        (application as App).openChatRequest.value = chat
    }
}
