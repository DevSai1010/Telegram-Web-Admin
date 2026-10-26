package com.msma.tgpanel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle

sealed interface Screen {
    data object Chats : Screen
    data class Chat(val id: String) : Screen
    data object Settings : Screen
    data object Devices : Screen
}

private fun Screen.depth(): Int = when (this) {
    is Screen.Chats -> 0
    is Screen.Chat -> 1
    is Screen.Settings -> 1
    is Screen.Devices -> 2
}

@Composable
fun AppRoot() {
    val app = appOf()
    val loggedIn by app.loggedIn.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (loggedIn) MainFlow() else LoginScreen()
    }
}

@Composable
private fun MainFlow() {
    val stack = remember { mutableStateListOf<Screen>(Screen.Chats) }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }     // (removeLast clashes with the JDK 21 method on old Android)

    // slide direction follows the reading direction, so "forward" feels the same in Persian and in English
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1

    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = {
            val forward = targetState.depth() >= initialState.depth()
            if (forward) {
                (slideInHorizontally(tween(300)) { sign * it / 4 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(200)) { -sign * it / 8 } + fadeOut(tween(150)))
            } else {
                (slideInHorizontally(tween(300)) { -sign * it / 4 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(200)) { sign * it / 8 } + fadeOut(tween(150)))
            }
        },
        label = "nav",
    ) { screen ->
        when (screen) {
            is Screen.Chats -> ChatListScreen(
                onOpenChat = { stack.add(Screen.Chat(it)) },
                onSettings = { stack.add(Screen.Settings) },
            )
            is Screen.Chat -> ChatScreen(screen.id, onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) })
            is Screen.Settings -> SettingsScreen(
                onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                onDevices = { stack.add(Screen.Devices) },
            )
            is Screen.Devices -> DevicesScreen(onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) })
        }
    }
}
