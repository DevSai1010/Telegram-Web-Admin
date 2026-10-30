@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.msma.tgpanel.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msma.tgpanel.R
import com.msma.tgpanel.data.ApiResult
import com.msma.tgpanel.data.ChatEntity
import kotlinx.coroutines.launch

/** Same split as the web panel: "bot" = everything except business-only chats; "Telegram" = business chats. */
private fun inBotView(c: ChatEntity) = c.bizId.isEmpty() || c.hasBotChat
private fun inTelegramView(c: ChatEntity) = c.bizId.isNotEmpty()

@Composable
fun ChatListScreen(onOpenChat: (String) -> Unit, onSettings: () -> Unit) {
    val app = appOf()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val chats by app.repo.chats.collectAsStateWithLifecycle(initialValue = emptyList())
    val conn by app.realtime.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var view by rememberSaveable { mutableIntStateOf(0) }       // 0 = bot, 1 = Telegram
    var filter by rememberSaveable { mutableIntStateOf(0) }     // 0 all · 1 unread · 2 pinned · 3 groups
    var newChat by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val botUnread = chats.count { inBotView(it) && it.unread > 0 }
    val tgUnread = chats.count { inTelegramView(it) && it.unread > 0 }
    val base = remember(chats, view) { chats.filter { if (view == 1) inTelegramView(it) else inBotView(it) } }
    val counts = remember(base) { listOf(base.size, base.count { it.unread > 0 }, base.count { it.pinned }, base.count { it.isGroup }) }
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxSize()) {
            // ---- .sidebar-header (64 dp) with the .search-bar (48 dp, pill) ----
            Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                Surface(Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Menu, stringResource(R.string.menu), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Box(Modifier.weight(1f).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) Text(stringResource(R.string.search), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                            BasicTextField(
                                value = query, onValueChange = { query = it }, singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, end = 12.dp).size(24.dp))
                    }
                }
            }

            ConnectionBanner(conn)

            // ---- .view-switcher: two-segment button, check replaces the icon when selected, badge sits on the top edge ----
            Box(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 10.dp)) {
                Segmented(
                    labels = listOf(stringResource(R.string.tab_bot), stringResource(R.string.tab_telegram)),
                    icons = listOf(Icons.Rounded.SmartToy, Icons.AutoMirrored.Rounded.Send),
                    badges = listOf(botUnread, tgUnread),
                    selected = view, onSelect = { view = it },
                )
            }

            // ---- .folder-tabs: filter chips (height 32, radius 8) with counts ----
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val names = listOf(R.string.filter_all, R.string.filter_unread, R.string.filter_pinned, R.string.filter_groups)
                names.forEachIndexed { i, res ->
                    FilterChip(
                        selected = filter == i,
                        onClick = { filter = i },
                        modifier = Modifier.height(32.dp),
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(res), maxLines = 1, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    counts[i].toString(), style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(start = 5.dp), color = LocalContentColorAlpha(if (filter == i) 1f else 0.75f),
                                )
                            }
                        },
                        leadingIcon = if (filter == i) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = filter == i,
                            borderColor = MaterialTheme.colorScheme.outline, selectedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    )
                }
            }

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.empty_chats), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // bottom padding keeps the last rows clear of the FAB (ul.chatlist padding-bottom 96)
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                    items(shown, key = { it.id }) { c -> ChatRow(c, onClick = { onOpenChat(c.id) }) }
                }
            }
        }

        // ---- .fab: compose a new chat ----
        FloatingActionButton(
            onClick = { newChat = true },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Rounded.Edit, stringResource(R.string.new_chat)) }
    }

    if (newChat) {
        NewChatDialog(
            onDismiss = { newChat = false },
            onStart = { id, text, done ->
                scope.launch {
                    when (val r = app.repo.startChat(id, text)) {
                        is ApiResult.Ok -> { newChat = false; onOpenChat(r.value.resolvedChatId.ifEmpty { id.trim().removePrefix("@") }) }
                        is ApiResult.Err -> { error = if (r.code == 0) ctx.getString(R.string.err_network) else r.message.ifEmpty { ctx.getString(R.string.err_generic) } }
                        else -> {}
                    }
                    done()
                }
            },
            error = error,
        )
    }
}

@Composable
private fun LocalContentColorAlpha(a: Float) = androidx.compose.material3.LocalContentColor.current.copy(alpha = a)

/** The web's .view-switcher: one 1 dp outline around two halves; the selected half is secondaryContainer and shows a check. */
@Composable
private fun Segmented(
    labels: List<String>, icons: List<androidx.compose.ui.graphics.vector.ImageVector>, badges: List<Int>, selected: Int, onSelect: (Int) -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline
    Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(50)).border(1.dp, outline, RoundedCornerShape(50))) {
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                val on = selected == i
                val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent, tween(200), label = "seg")
                val fg = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                Box(Modifier.weight(1f).fillMaxHeight().background(bg).clickable { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (on) Icons.Rounded.Check else icons[i], null, tint = fg, modifier = Modifier.size(18.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
                    }
                }
                if (i == 0) Box(Modifier.width(1.dp).fillMaxHeight().background(outline))
            }
        }
    }
}

@Composable
private fun ChatRow(c: ChatEntity, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.id, c.title, 52.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.title.ifEmpty { c.id }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (c.bizId.isNotEmpty()) Icon(Icons.Rounded.Work, null, Modifier.padding(start = 6.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.muted) Icon(Icons.Rounded.NotificationsOff, null, Modifier.padding(start = 6.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.aiPending) Box(Modifier.padding(start = 8.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                }
                Spacer2()
                val line = (if (c.lastSender.isNotEmpty()) c.lastSender + ": " else "") + c.lastText
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(c.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (c.pinned) Icon(Icons.Rounded.PushPin, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.unread > 0) {
                        Surface(shape = RoundedCornerShape(50), color = if (c.muted) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp).heightIn(min = 20.dp).widthIn(min = 20.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    if (c.unread > 99) "99+" else c.unread.toString(), modifier = Modifier.padding(horizontal = 6.dp),
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
}

@Composable
private fun Spacer2() = androidx.compose.foundation.layout.Spacer(Modifier.height(2.dp))

/** #newChatModal — start a conversation with someone by Telegram id / @username and a first message. */
@Composable
private fun NewChatDialog(onDismiss: () -> Unit, onStart: (String, String, () -> Unit) -> Unit, error: String?) {
    var id by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var local by remember { mutableStateOf<String?>(null) }
    val required = stringResource(R.string.nc_required)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Rounded.PersonAdd, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.new_chat)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.nc_id_label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextField(id, { id = it }, singleLine = true, placeholder = { Text(stringResource(R.string.nc_id_hint)) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.nc_msg_label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextField(msg, { msg = it }, placeholder = { Text(stringResource(R.string.nc_msg_hint)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                (local ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (id.isBlank() || msg.isBlank()) { local = required; return@Button }
                    local = null; busy = true
                    onStart(id, msg) { busy = false }
                },
                enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.nc_send)) }
        },
    )
}
