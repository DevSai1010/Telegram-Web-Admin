@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.msma.tgpanel.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msma.tgpanel.R
import com.msma.tgpanel.VoiceRecorder
import com.msma.tgpanel.core.groupsWithPrevious
import com.msma.tgpanel.data.ApiResult
import com.msma.tgpanel.data.MessageEntity
import java.io.File
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
    var profileOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MessageEntity?>(null) }
    var deleting by remember { mutableStateOf<MessageEntity?>(null) }
    var forwarding by remember { mutableStateOf<MessageEntity?>(null) }

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
        if (m.replyToMsgId.isNotEmpty()) return list.indexOfFirst { it is ChatRowItem.Msg && it.m.msgId == m.replyToMsgId }
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

    fun errText(r: ApiResult.Err): String = when {
        r.message == "file_too_big" -> ctx.getString(R.string.file_too_big)
        r.code == 0 -> ctx.getString(R.string.err_network)
        r.message.isNotEmpty() -> r.message
        else -> ctx.getString(R.string.err_generic)
    }

    /** Runs a server action; a failure becomes a snackbar, success needs no message (the list refreshes itself). */
    fun act(okText: Int? = null, block: suspend () -> ApiResult<*>) {
        scope.launch {
            when (val r = block()) {
                is ApiResult.Err -> snack.showSnackbar(errText(r))
                is ApiResult.Ok -> if (okText != null) snack.showSnackbar(ctx.getString(okText))
                else -> {}
            }
        }
    }

    fun send() {
        val text = draft.trim()
        val c = chat
        if (text.isEmpty() || c == null || sending) return
        sending = true
        val reply = replyTo
        scope.launch {
            when (val res = app.repo.send(c, text, if (c.bizId.isNotEmpty()) senderMode else "bot", reply?.msgId)) {
                is ApiResult.Ok -> { draft = ""; replyTo = null; listState.animateScrollToItem(0) }
                is ApiResult.Err -> snack.showSnackbar(errText(res))
                else -> {}
            }
            sending = false
        }
    }

    // ---- attachments: gallery / video / GIF / file / camera, and voice notes ----
    val recorder = remember { VoiceRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    var recordMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(recording) { while (recording) { recordMs = recorder.elapsedMs(); delay(200) } }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    fun sendUri(uri: Uri, kind: String) {
        val c = chat ?: return
        if (sending) return
        sending = true
        val reply = replyTo
        val caption = if (kind == "voice") "" else draft.trim()        // a caption goes with files, not with voice notes
        scope.launch {
            when (val res = app.repo.sendMedia(ctx, c, uri, kind, caption, senderMode, reply?.msgId)) {
                is ApiResult.Ok -> { if (kind != "voice") draft = ""; replyTo = null; listState.animateScrollToItem(0) }
                is ApiResult.Err -> snack.showSnackbar(errText(res))
                else -> {}
            }
            sending = false
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) sendUri(uri, "photo") }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) sendUri(uri, "video") }
    val pickGif = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) sendUri(uri, "animation") }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) sendUri(uri, "document") }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) sendUri(u, "photo")
    }

    fun attach(kind: AttachKind) {
        when (kind) {
            AttachKind.Photo -> pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            AttachKind.Video -> pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            AttachKind.Gif -> pickGif.launch(arrayOf("image/gif", "video/mp4"))
            AttachKind.File -> pickFile.launch(arrayOf("*/*"))
            AttachKind.Camera -> {
                val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
                val photo = File(dir, "IMG_" + System.currentTimeMillis() + ".jpg")
                val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", photo)
                cameraUri = u
                takePicture.launch(u)
            }
        }
    }

    fun startRecording() {
        if (recorder.start()) recording = true else scope.launch { snack.showSnackbar(ctx.getString(R.string.record_failed)) }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else scope.launch { snack.showSnackbar(ctx.getString(R.string.mic_denied)) }
    }
    fun onMicTap() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun finishRecording(send: Boolean) {
        recording = false
        if (send) recorder.stop()?.let { sendUri(Uri.fromFile(it), "voice") } else recorder.cancel()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = { ChatTopBar(chatId, chat?.title.orEmpty(), chat?.aiPending == true, onBack = onBack, onProfile = { profileOpen = true }) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ConnectionBanner(conn)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(rows, key = { r -> if (r is ChatRowItem.Msg) "m" + r.m.id else (r as ChatRowItem.Day).key }) { r ->
                        when (r) {
                            is ChatRowItem.Day -> DaySeparator(r.ts)
                            is ChatRowItem.Msg -> {
                                val m = r.m
                                MessageBubble(
                                    m = m, contPrev = r.contPrev, showSender = chat?.isGroup == true, isGroup = chat?.isGroup == true, flash = flashId == m.id,
                                    actions = BubbleActions(
                                        onReplyQuote = { jumpToOriginal(m) },
                                        onReply = { if (m.msgId.isNotEmpty()) replyTo = m },
                                        onReact = { e ->
                                            app.prefs.bumpEmoji(e)
                                            act { app.repo.react(chatId, m.msgId, if (m.myReaction == e) null else e) }
                                        },
                                        onEdit = { editing = m },
                                        onForward = { forwarding = m },
                                        onPin = { chat?.let { c -> act(R.string.saved) { app.repo.pinMessage(c, m) } } },
                                        onDelete = { deleting = m },
                                        onAddToPanel = { act(R.string.saved) { app.api.addRecent(m.fileId, m.type, m.text) } },
                                    ),
                                )
                            }
                        }
                    }
                }
                // .scroll-btn: small FAB, count badge when messages arrived while scrolled away
                androidx.compose.animation.AnimatedVisibility(
                    visible = !atBottom,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    Box {
                        SmallFloatingActionButton(
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.jump_latest)) }
                        if (newAway > 0) {
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).widthIn(min = 20.dp)) {
                                Text(
                                    if (newAway > 99) "99+" else newAway.toString(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            }
            if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
            Composer(
                chat = chat, draft = draft, onDraft = { draft = it },
                replyTo = replyTo, onCancelReply = { replyTo = null },
                senderMode = senderMode, onSenderMode = { senderMode = it },
                sending = sending, onSend = { send() },
                recording = recording, recordMs = recordMs, onMic = { onMicTap() },
                onCancelRecording = { finishRecording(false) }, onSendRecording = { finishRecording(true) },
                onAttach = { attach(it) },
                onSticker = { fileId, kind, emoji ->
                    val c = chat
                    if (c != null) {
                        val reply = replyTo
                        replyTo = null
                        act { app.repo.sendSticker(c, fileId, kind, emoji, senderMode, reply?.msgId) }
                    }
                },
            )
        }
    }

    // ---- dialogs ----
    editing?.let { m ->
        var text by remember(m.id) { mutableStateOf(m.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.edit_title)) },
            text = { TextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 2) },
            confirmButton = {
                TextButton(onClick = {
                    val t = text.trim()
                    editing = null
                    if (t.isNotEmpty() && t != m.text) act(R.string.saved) { app.repo.edit(m, t) }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { deleting = null; act { app.repo.deleteMessage(m) } }) {
                    Text(stringResource(R.string.ctx_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    forwarding?.let { m ->
        var target by remember(m.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { forwarding = null },
            title = { Text(stringResource(R.string.ctx_forward)) },
            text = {
                Column {
                    Text(stringResource(R.string.forward_prompt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    TextField(target, { target = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val to = target.trim()
                    forwarding = null
                    val c = chat
                    if (to.isNotEmpty() && c != null) act(R.string.forwarded) { app.repo.forward(c, m, to) }
                }) { Text(stringResource(R.string.ctx_forward)) }
            },
            dismissButton = { TextButton(onClick = { forwarding = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (profileOpen) ProfileSheet(chatId = chatId, onDismiss = { profileOpen = false }, onChatDeleted = { profileOpen = false; onBack() })
}

/** .chat-topbar — 64 dp, surfaceContainer; the back button, avatar and title are one tap target that opens the profile. */
@Composable
private fun ChatTopBar(chatId: String, title: String, aiPending: Boolean, onBack: () -> Unit, onProfile: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.statusBarsPadding().height(64.dp).padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(
                Modifier.weight(1f).padding(start = 4.dp).clip16().clickable(onClick = onProfile).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(chatId, title, 40.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(title.ifEmpty { chatId }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (aiPending) {
                        val a by rememberInfiniteTransition(label = "pend").animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pendAlpha")
                        Text(stringResource(R.string.ai_pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.alpha(a), maxLines = 1)
                    } else {
                        Text(stringResource(R.string.chat_id_fmt, chatId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun Modifier.clip16() = this.then(Modifier.clip(RoundedCornerShape(16.dp)))

@Composable
private fun DaySeparator(ts: Long) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 1.dp) {
            Text(
                dayLabel(ts), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
