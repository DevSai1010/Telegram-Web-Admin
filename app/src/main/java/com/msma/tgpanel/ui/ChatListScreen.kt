@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.msma.tgpanel.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msma.tgpanel.R
import com.msma.tgpanel.data.ChatEntity

/** Same split as the web panel: "bot" = everything except business-only chats; "Telegram" = business chats. */
private fun inBotView(c: ChatEntity) = c.bizId.isEmpty() || c.hasBotChat
private fun inTelegramView(c: ChatEntity) = c.bizId.isNotEmpty()

@Composable
fun ChatListScreen(onOpenChat: (String) -> Unit, onSettings: () -> Unit) {
    val app = appOf()
    val chats by app.repo.chats.collectAsStateWithLifecycle(initialValue = emptyList())
    val conn by app.realtime.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var view by rememberSaveable { mutableIntStateOf(0) }       // 0 = bot, 1 = Telegram
    var filter by rememberSaveable { mutableIntStateOf(0) }     // 0 all · 1 unread · 2 pinned · 3 groups

    val botUnread = chats.count { inBotView(it) && it.unread > 0 }
    val tgUnread = chats.count { inTelegramView(it) && it.unread > 0 }
    val base = remember(chats, view) { chats.filter { if (view == 1) inTelegramView(it) else inBotView(it) } }
    val shown = remember(base, query, filter) {
        base.filter { c ->
            (query.isBlank() || c.title.contains(query, ignoreCase = true) || c.lastText.contains(query, ignoreCase = true)) &&
                when (filter) {
                    1 -> c.unread > 0
                    2 -> c.pinned
                    3 -> c.isGroup
                    else -> true
                }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ---- search bar (M3 style: leading menu, trailing search) ----
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(52.dp),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Menu, stringResource(R.string.menu)) }
                Box(Modifier.weight(1f).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.search), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 14.dp))
            }
        }

        ConnectionBanner(conn)

        // ---- bot / Telegram switch (M3 segmented button) ----
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            val labels = listOf(R.string.tab_bot to botUnread, R.string.tab_telegram to tgUnread)
            labels.forEachIndexed { i, (res, n) ->
                SegmentedButton(
                    selected = view == i,
                    onClick = { view = i },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                ) {
                    Text(stringResource(res) + if (n > 0) "  ($n)" else "")
                }
            }
        }

        // ---- filter chips ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val names = listOf(R.string.filter_all, R.string.filter_unread, R.string.filter_pinned, R.string.filter_groups)
            names.forEachIndexed { i, res ->
                FilterChip(
                    selected = filter == i,
                    onClick = { filter = i },
                    label = { Text(stringResource(res), maxLines = 1) },
                    leadingIcon = if (filter == i) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                )
            }
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_chats), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                items(shown, key = { it.id }) { c -> ChatRow(c, onClick = { onOpenChat(c.id) }) }
            }
        }
    }
}

@Composable
private fun ChatRow(c: ChatEntity, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.id, c.title, 52.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.title.ifEmpty { c.id }, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (c.bizId.isNotEmpty()) Icon(Icons.Rounded.Work, null, Modifier.padding(start = 6.dp).size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.muted) Icon(Icons.Rounded.NotificationsOff, null, Modifier.padding(start = 6.dp).size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.aiPending) Surface(
                        shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 8.dp).size(8.dp),
                    ) {}
                }
                val line = (if (c.lastSender.isNotEmpty()) c.lastSender + ": " else "") + c.lastText
                Text(
                    line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(c.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (c.pinned) Icon(Icons.Rounded.PushPin, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.unread > 0) {
                        val badgeColor by animateColorAsState(
                            if (c.muted) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary, label = "badge",
                        )
                        Surface(
                            shape = RoundedCornerShape(50), color = badgeColor,
                            modifier = Modifier.padding(start = 6.dp).widthIn(min = 22.dp),
                        ) {
                            Text(
                                if (c.unread > 99) "99+" else c.unread.toString(),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (c.muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}
