@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.msma.tgpanel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.msma.tgpanel.R
import com.msma.tgpanel.core.Side
import com.msma.tgpanel.core.groupsWithPrevious
import com.msma.tgpanel.core.sideOf
import com.msma.tgpanel.data.ApiResult
import com.msma.tgpanel.data.ChatEntity
import com.msma.tgpanel.data.MessageEntity
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the list shows: a day separator or a message (and whether it visually joins the one before it). */
sealed interface ChatRowItem {
    data class Day(val ts: Long, val key: String) : ChatRowItem
    data class Msg(val m: MessageEntity, val contPrev: Boolean) : ChatRowItem
}

private fun dayKey(ts: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    return c.get(Calendar.YEAR) * 1000L + c.get(Calendar.DAY_OF_YEAR)
}

/** Chronological rows with day separators; grouping never crosses a day. */
fun buildRows(msgs: List<MessageEntity>): List<ChatRowItem> {
    val out = ArrayList<ChatRowItem>(msgs.size + 8)
    var prev: MessageEntity? = null
    var prevDay = Long.MIN_VALUE
    for (m in msgs) {
        val d = dayKey(m.ts)
        if (d != prevDay) { out.add(ChatRowItem.Day(m.ts, "d$d")); prevDay = d; prev = null }
        val p = prev
        val cont = p != null && groupsWithPrevious(
            p.role, p.sender, m.role, m.sender,
            hasReply = m.replyToMsgId.isNotEmpty() || m.replyPreview.isNotEmpty(),
            hasForward = m.forwardedFrom.isNotEmpty(),
        )
        out.add(ChatRowItem.Msg(m, cont))
        prev = m
    }
    return out
}

@Composable
fun ChatScreen(chatId: String, onBack: () -> Unit) {
    val app = appOf()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val chat by app.repo.chat(chatId).collectAsStateWithLifecycle(initialValue = null)
    val msgs by app.repo.messages(chatId).collectAsStateWithLifecycle(initialValue = emptyList())
    val conn by app.realtime.state.collectAsStateWithLifecycle()

    // newest at the bottom: the list is laid out reversed, so index 0 is the latest row
    val rows = remember(msgs) { buildRows(msgs).asReversed() }
    val rowsNow by rememberUpdatedState(rows)
    val listState = rememberLazyListState()
    val snack = remember { SnackbarHostState() }
    var replyTo by remember { mutableStateOf<MessageEntity?>(null) }
    var flashId by remember { mutableStateOf<String?>(null) }
    var newAway by remember { mutableIntStateOf(0) }
    var loadingOlder by remember { mutableStateOf(false) }
    var senderMode by rememberSaveable { mutableStateOf("bot") }
    var draft by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    // ---- bookkeeping: this chat is "open" (so it never counts unread) and gets a fresh newest page ----
    DisposableEffect(chatId) {
        app.repo.openChatId.value = chatId
        onDispose { if (app.repo.openChatId.value == chatId) app.repo.openChatId.value = null }
    }
    LaunchedEffect(chatId) {
        app.repo.markRead(chatId)
        app.repo.loadNewest(chatId, force = true)
    }

    // ---- stick to the bottom when a message arrives and we're there; otherwise count it on the jump button ----
    val newestId = msgs.lastOrNull()?.id
    var lastNewest by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(newestId) {
        if (newestId == null) return@LaunchedEffect
        if (lastNewest != null && lastNewest != newestId) {
            if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0) else newAway++
        }
        lastNewest = newestId
    }
    val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 2 } }
    LaunchedEffect(atBottom) { if (atBottom) newAway = 0 }

    // ---- endless history: when the oldest rows come into view, fetch the page before them ----
    LaunchedEffect(rows.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { lastIdx ->
            if (rows.isNotEmpty() && lastIdx >= rows.size - 8 && !loadingOlder && app.repo.hasMoreOlder(chatId)) {
                loadingOlder = true
                app.repo.loadOlder(chatId)
                loadingOlder = false
            }
        }
    }

    // ---- tap on a reply preview → scroll to the original and flash it (pages back through history if needed) ----
    fun targetIndex(m: MessageEntity): Int {
        val list = rowsNow
        if (m.replyToMsgId.isNotEmpty()) {
            return list.indexOfFirst { it is ChatRowItem.Msg && it.m.msgId == m.replyToMsgId }
        }
        // old rows saved without an id: preview is "<name>: <text>" — match the text, only among EARLIER messages
        val ptxt = m.replyPreview.substringAfter(':', "").trimStart()
        if (ptxt.isEmpty()) return -1
        val here = list.indexOfFirst { it is ChatRowItem.Msg && it.m.id == m.id }
        if (here < 0) return -1
        for (i in here + 1 until list.size) {                // reversed list: larger index = older
            val r = list[i]
            if (r is ChatRowItem.Msg && r.m.text.ifEmpty { "[رسانه]" }.startsWith(ptxt)) return i
        }
        return -1
    }

    fun jumpToOriginal(m: MessageEntity) {
        scope.launch {
            var idx = targetIndex(m)
            var guard = 0
            while (idx < 0 && app.repo.hasMoreOlder(chatId) && guard++ < 60) {
                if (!app.repo.loadOlder(chatId)) break
                repeat(8) { if (idx < 0) { delay(80); idx = targetIndex(m) } }     // give the DB flow time to re-emit
            }
            if (idx < 0) { snack.showSnackbar(ctx.getString(R.string.original_not_found)); return@launch }
            listState.animateScrollToItem(idx)
            (rowsNow.getOrNull(idx) as? ChatRowItem.Msg)?.let { flashId = it.m.id }
            delay(1700)
            flashId = null
        }
    }

    fun send() {
        val text = draft.trim()
        val c = chat
        if (text.isEmpty() || c == null || sending) return
        sending = true
        val reply = replyTo
        scope.launch {
            val res = app.repo.send(c, text, if (c.bizId.isNotEmpty()) senderMode else "bot", reply?.msgId)
            when (res) {
                is ApiResult.Ok -> { draft = ""; replyTo = null; listState.animateScrollToItem(0) }
                is ApiResult.Err -> snack.showSnackbar(res.message.ifEmpty { ctx.getString(R.string.err_generic) })
                else -> {}
            }
            sending = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(chatId, chat?.title.orEmpty(), 40.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(chat?.title.orEmpty().ifEmpty { chatId }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (chat?.aiPending == true) stringResource(R.string.ai_pending) else stringResource(R.string.chat_id_fmt, chatId),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (chat?.aiPending == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ConnectionBanner(conn)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(rows, key = { r -> if (r is ChatRowItem.Msg) "m" + r.m.id else (r as ChatRowItem.Day).key }) { r ->
                        when (r) {
                            is ChatRowItem.Day -> DaySeparator(r.ts)
                            is ChatRowItem.Msg -> {
                                // the row *above* this one in time is the next index in the reversed list
                                MessageBubble(
                                    m = r.m,
                                    contPrev = r.contPrev,
                                    showSender = chat?.isGroup == true,
                                    flash = flashId == r.m.id,
                                    onReplyQuoteClick = { jumpToOriginal(r.m) },
                                    onReply = { if (r.m.msgId.isNotEmpty()) replyTo = r.m },
                                )
                            }
                        }
                    }
                }
                // jump to latest (small FAB) + count of messages that arrived while scrolled away
                if (!atBottom) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    ) {
                        SmallFloatingActionButton(
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.jump_latest)) }
                        if (newAway > 0) {
                            Surface(
                                shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.TopEnd).widthIn(min = 20.dp),
                            ) {
                                Text(
                                    if (newAway > 99) "99+" else newAway.toString(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            }
            Composer(
                chat = chat, draft = draft, onDraft = { draft = it },
                replyTo = replyTo, onCancelReply = { replyTo = null },
                senderMode = senderMode, onSenderMode = { senderMode = it },
                sending = sending, onSend = { send() },
            )
        }
    }
}

@Composable
private fun DaySeparator(ts: Long) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                dayLabel(ts), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    m: MessageEntity,
    contPrev: Boolean,
    showSender: Boolean,
    flash: Boolean,
    onReplyQuoteClick: () -> Unit,
    onReply: () -> Unit,
) {
    val ext = LocalExtColors.current
    val cs = MaterialTheme.colorScheme
    val side = sideOf(m.role)
    val (bg, fg) = when (m.role) {
        "owner" -> cs.primaryContainer to cs.onPrimaryContainer
        "admin", "model", "bot_auto" -> ext.greenContainer to ext.onGreenContainer
        else -> cs.surfaceContainerHigh to cs.onSurface
    }
    val accent = when (m.role) { "owner" -> cs.onPrimaryContainer; "admin", "model", "bot_auto" -> ext.green; else -> cs.primary }

    // flat corner on the side that faces the screen edge (start edge for incoming, end edge for outgoing);
    // the top one only when this bubble continues a group, the bottom one always — same rule as the web panel
    val big = 20.dp
    val small = 6.dp
    val shape = if (side == Side.IN) {
        RoundedCornerShape(topStart = if (contPrev) small else big, topEnd = big, bottomEnd = big, bottomStart = small)
    } else {
        RoundedCornerShape(topStart = big, topEnd = if (contPrev) small else big, bottomEnd = small, bottomStart = big)
    }

    val flashAlpha by animateFloatAsState(if (flash) 0.35f else 0f, tween(if (flash) 160 else 500), label = "flash")
    var menu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val copiedText = stringResource(R.string.copied)

    Row(
        Modifier.fillMaxWidth().padding(top = if (contPrev) 0.dp else 6.dp),
        horizontalArrangement = if (side == Side.OUT) Arrangement.End else Arrangement.Start,
    ) {
        Box {
            Surface(
                color = bg, contentColor = fg, shape = shape,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(shape)
                    .drawWithContent {
                        drawContent()
                        if (flashAlpha > 0.001f) {
                            drawOutline(shape.createOutline(size, layoutDirection, this), color = cs.primary, alpha = flashAlpha)
                        }
                    }
                    .combinedClickable(onClick = {}, onLongClick = { menu = true }),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    val label = when {
                        m.role == "owner" -> stringResource(R.string.role_owner)
                        m.role == "admin" -> stringResource(R.string.role_admin)
                        m.role == "model" -> stringResource(R.string.role_model)
                        m.role == "bot_auto" -> stringResource(R.string.role_auto)
                        showSender && m.sender.isNotEmpty() -> m.sender
                        else -> ""
                    }
                    if (label.isNotEmpty() && !contPrev) Text(label, style = MaterialTheme.typography.labelMedium, color = accent)
                    if (m.forwardedFrom.isNotEmpty()) {
                        Text(stringResource(R.string.forwarded_from_fmt, m.forwardedFrom), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f))
                    }
                    if (m.replyPreview.isNotEmpty()) ReplyQuote(m.replyPreview, accent, onReplyQuoteClick)
                    MediaBlock(m)
                    if (m.text.isNotEmpty() && m.type != "sticker") {
                        Text(m.text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp))
                    }
                    Row(Modifier.align(Alignment.End).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        val react = (m.theirReaction + m.myReaction)
                        if (react.isNotEmpty()) Text(react, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
                        Text(m.time, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.65f))
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (m.msgId.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reply)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Reply, null) },
                        onClick = { menu = false; onReply() },
                    )
                }
                if (m.text.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy)) },
                        onClick = { menu = false; clipboard.setText(AnnotatedString(m.text)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplyQuote(preview: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.padding(top = 2.dp, bottom = 6.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .background(accent.copy(alpha = 0.12f)).height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.Reply, null, Modifier.size(15.dp), tint = accent)
            Text(
                preview, modifier = Modifier.padding(start = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Photos and stickers render inline; video / voice / files show a labelled chip until playback lands in a later version. */
@Composable
private fun MediaBlock(m: MessageEntity) {
    if (m.type.isEmpty() || m.fileId.isEmpty()) return
    val app = appOf()
    val ctx = LocalContext.current
    fun request() = ImageRequest.Builder(ctx).data(app.api.mediaUrl(m.fileId))
        .addHeader("Authorization", "Bearer " + app.prefs.token).crossfade(true).build()

    when (m.type) {
        "photo" -> AsyncImage(
            model = request(), contentDescription = stringResource(R.string.media_photo), contentScale = ContentScale.Fit,
            modifier = Modifier.padding(bottom = 4.dp).widthIn(max = 280.dp).heightIn(max = 340.dp).clip(RoundedCornerShape(12.dp)),
        )
        "sticker" -> Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
            Text(m.text, fontSize = 52.sp)                       // the sticker's emoji shows if the image format can't be decoded
            AsyncImage(model = request(), contentDescription = stringResource(R.string.media_sticker), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        else -> {
            val (icon, label) = when (m.type) {
                "video" -> Icons.Rounded.PlayCircle to stringResource(R.string.media_video)
                "animation" -> Icons.Rounded.PlayCircle to stringResource(R.string.media_gif)
                "voice" -> Icons.Rounded.GraphicEq to stringResource(R.string.media_voice)
                "document" -> Icons.Rounded.InsertDriveFile to stringResource(R.string.media_doc)
                else -> Icons.Rounded.AttachFile to stringResource(R.string.media_doc)
            }
            Row(
                Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, Modifier.size(28.dp))
                Column(Modifier.padding(start = 10.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    if (m.type == "video" || m.type == "voice" || m.type == "animation") {
                        Text(stringResource(R.string.media_soon), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 1.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    chat: ChatEntity?,
    draft: String,
    onDraft: (String) -> Unit,
    replyTo: MessageEntity?,
    onCancelReply: () -> Unit,
    senderMode: String,
    onSenderMode: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // business chats can be answered "as me" or as the bot (same switch the web panel has)
        if (chat != null && chat.bizId.isNotEmpty()) {
            Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = senderMode == "bot", onClick = { onSenderMode("bot") }, label = { Text(stringResource(R.string.sender_bot)) })
                FilterChip(selected = senderMode == "me", onClick = { onSenderMode("me") }, label = { Text(stringResource(R.string.sender_me)) })
            }
        }
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column {
                AnimatedVisibility(visible = replyTo != null, enter = fadeIn(), exit = fadeOut()) {
                    if (replyTo != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Reply, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(
                                stringResource(R.string.replying_to_fmt, replyTo.replyPreviewOrText()),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(onClick = onCancelReply) { Icon(Icons.Rounded.Close, stringResource(R.string.close), Modifier.size(18.dp)) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    TextField(
                        value = draft, onValueChange = onDraft,
                        placeholder = { Text(stringResource(R.string.type_message)) },
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    AnimatedVisibility(
                        visible = draft.isNotBlank(),
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                        modifier = Modifier.padding(4.dp),
                    ) {
                        FilledIconButton(onClick = onSend, enabled = !sending, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.send))
                        }
                    }
                }
            }
        }
    }
}

private fun MessageEntity.replyPreviewOrText(): String = text.ifEmpty { sender }.take(60)
