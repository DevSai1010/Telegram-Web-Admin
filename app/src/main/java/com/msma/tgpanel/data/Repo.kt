package com.msma.tgpanel.data

import com.msma.tgpanel.core.nextUnread
import com.msma.tgpanel.core.sortKeyOf
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val PAGE = 100
private const val MAX_UPLOAD = 50L * 1024 * 1024        // Telegram bots can upload up to 50 MB

/** Local-first data access: the UI only ever reads Room; this class keeps Room in step with the server. */
class Repo(private val prefs: Prefs, private val api: Api, private val db: PanelDb) {
    private val dao get() = db.dao()
    private val syncMutex = Mutex()
    private val pageEtags = HashMap<String, String>()          // newest-page ETag per chat (memory only)
    private val exhausted = HashSet<String>()                  // chats whose older history is fully loaded

    /** The chat currently on screen (its unread counter stays 0 and its history is always refreshed). */
    val openChatId = MutableStateFlow<String?>(null)

    /** Fires for every chat that just got a new incoming message while it was not on screen (muted chats excluded). */
    val incoming = MutableSharedFlow<ChatEntity>(extraBufferCapacity = 16)

    val chats: Flow<List<ChatEntity>> get() = dao.observeChats()
    fun chat(id: String): Flow<ChatEntity?> = dao.observeChat(id)
    fun messages(chatId: String): Flow<List<MessageEntity>> = dao.observeMessages(chatId)

    // ---- chat list ----
    suspend fun refreshChats(force: Boolean = false): Boolean {
        val (res, etag) = api.chats(if (force) null else prefs.chatsEtag.ifEmpty { null })
        return when (res) {
            is ApiResult.Ok -> { applyChats(res.value); prefs.chatsEtag = etag ?: ""; true }
            is ApiResult.NotModified -> true
            is ApiResult.Err -> false
        }
    }

    private suspend fun applyChats(list: List<ChatDto>) {
        val old = dao.allChats().associateBy { it.id }
        val open = openChatId.value
        val rows = list.mapIndexed { i, c ->
            val prev = old[c.id]
            ChatEntity(
                id = c.id, title = c.title, lastText = c.last_text, lastSender = c.last_sender, time = c.time,
                isGroup = c.is_group, bizId = c.business_connection_id, hasBotChat = c.has_bot_chat,
                muted = c.muted, pinned = c.pinned, aiPending = c.ai_pending,
                unread = nextUnread(prev?.lastText, c.last_text, c.last_sender, prev?.unread ?: 0, chatOpen = c.id == open),
                orderIdx = i,
            )
        }
        dao.upsertChats(rows)
        for (r in rows) {
            val before = old[r.id]?.unread ?: 0
            if (r.unread > before && !r.muted && r.id != open) incoming.tryEmit(r)
        }
        val keep = rows.map { it.id }
        dao.deleteChatsNotIn(keep)
        dao.deleteMessagesOfChatsNotIn(keep)
    }

    suspend fun markRead(chatId: String) = dao.setUnread(chatId, 0)

    // ---- messages ----
    suspend fun loadNewest(chatId: String, force: Boolean = false): Boolean {
        val (res, etag) = api.chatPage(chatId, PAGE, null, if (force) null else pageEtags[chatId])
        return when (res) {
            is ApiResult.Ok -> {
                dao.mergePage(chatId, res.value.map { it.toEntity(chatId) }, newest = true)
                if (etag != null) pageEtags[chatId] = etag
                true
            }
            is ApiResult.NotModified -> true
            is ApiResult.Err -> false
        }
    }

    /** Loads the page before the oldest cached message. Returns false when there is nothing older (or on failure). */
    suspend fun loadOlder(chatId: String): Boolean {
        if (chatId in exhausted) return false
        val oldest = dao.oldestId(chatId) ?: return false
        val (res, _) = api.chatPage(chatId, PAGE, oldest, null)
        return when (res) {
            is ApiResult.Ok -> {
                if (res.value.isEmpty()) { exhausted.add(chatId); false }
                else { dao.mergePage(chatId, res.value.map { it.toEntity(chatId) }, newest = false); true }
            }
            else -> false
        }
    }

    fun hasMoreOlder(chatId: String) = chatId !in exhausted

    suspend fun send(chat: ChatEntity, text: String, senderMode: String, replyToMsgId: String?): ApiResult<ApiAck> {
        val res = api.send(chat.id, text, senderMode, chat.bizId, replyToMsgId)
        if (res is ApiResult.Ok) loadNewest(chat.id, force = true)
        return res
    }

    /** Sends a picked file / camera photo / recorded voice note. [kind] is "voice" for recordings, otherwise derived from the MIME type. */
    suspend fun sendMedia(
        ctx: Context, chat: ChatEntity, uri: Uri, kind: String?, caption: String, senderMode: String, replyToMsgId: String?,
    ): ApiResult<ApiAck> {
        val (name, size) = describe(ctx.contentResolver, uri)
        if (size > MAX_UPLOAD) return ApiResult.Err(413, "file_too_big")
        val mime = ctx.contentResolver.getType(uri)
            ?: if (kind == "voice") (if (name.endsWith(".ogg")) "audio/ogg" else "audio/mp4") else "application/octet-stream"   // the server converts non-OGG voice notes
        val type = when {
            kind == "photo" && size > 10L * 1024 * 1024 -> "document"        // Telegram photos cap at 10 MB — send the original as a file
            kind != null -> kind
            mime.startsWith("image/") && size <= 10L * 1024 * 1024 && mime != "image/gif" -> "photo"
            mime.startsWith("video/") -> "video"
            else -> "document"
        }
        val res = api.sendMedia(ctx, uri, name, mime, size, type, chat.id, caption, if (chat.bizId.isNotEmpty()) senderMode else "bot", chat.bizId, replyToMsgId)
        if (res is ApiResult.Ok) loadNewest(chat.id, force = true)
        return res
    }

    private fun describe(resolver: ContentResolver, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "file"
        var size = -1L
        if (uri.scheme == "content") {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                }
            }
        } else if (uri.scheme == "file") {
            uri.path?.let { java.io.File(it) }?.let { name = it.name; size = it.length() }
        }
        return name to size
    }

    // ---- actions: each one asks the server, then refreshes what it touched so the UI updates at once ----
    private suspend fun <T> afterMessageChange(chatId: String, res: ApiResult<T>): ApiResult<T> {
        if (res is ApiResult.Ok) loadNewest(chatId, force = true)
        return res
    }

    private suspend fun <T> afterChatChange(res: ApiResult<T>): ApiResult<T> {
        if (res is ApiResult.Ok) refreshChats(force = true)
        return res
    }

    suspend fun startChat(id: String, text: String): ApiResult<ApiAck> =
        afterChatChange(api.send(id.trim().removePrefix("@"), text, "bot", "", null, "مخاطب " + id.trim()))

    suspend fun react(chatId: String, msgId: String, emoji: String?) = afterMessageChange(chatId, api.react(chatId, msgId, emoji))
    suspend fun edit(m: MessageEntity, text: String) = afterMessageChange(m.chatId, api.editMessage(m.chatId, m.msgId, m.id, text))
    suspend fun deleteMessage(m: MessageEntity) = afterMessageChange(m.chatId, api.deleteMessage(m.chatId, m.msgId))
    suspend fun pinMessage(chat: ChatEntity, m: MessageEntity) = api.pinMessage(chat.id, m.msgId, chat.bizId)
    suspend fun forward(chat: ChatEntity, m: MessageEntity, toChatId: String) = api.forward(chat.id, toChatId.trim(), m.msgId, chat.bizId)
    suspend fun pinChat(chatId: String, pinned: Boolean) = afterChatChange(api.pinChat(chatId, pinned))
    suspend fun muteChat(chatId: String, muted: Boolean) = afterChatChange(api.muteChat(chatId, muted))
    suspend fun deleteChat(chatId: String) = afterChatChange(api.deleteChat(chatId))
    suspend fun sendSticker(chat: ChatEntity, fileId: String, kind: String, emoji: String, senderMode: String, replyToMsgId: String?) =
        afterMessageChange(chat.id, api.sendSticker(chat.id, fileId, kind, emoji, if (chat.bizId.isNotEmpty()) senderMode else "bot", chat.bizId, replyToMsgId))

    // ---- staying in step (docs/API.md §3) ----
    /** Asks the server what changed since our cursor and refreshes only that. Safe to call any time, any number of times. */
    suspend fun syncNow(): Boolean = syncMutex.withLock {
        val res = api.sync(prefs.cursor)
        if (res !is ApiResult.Ok) return@withLock false
        val s = res.value
        var ok = true
        if (s.reset) {
            ok = refreshChats(force = true)
            openChatId.value?.let { ok = loadNewest(it, force = true) && ok }
        } else {
            if (s.index || s.chats.isNotEmpty()) ok = refreshChats() && ok
            for (id in s.chats) {
                if (id == openChatId.value || dao.messageCount(id) > 0) ok = loadNewest(id) && ok
            }
        }
        if (ok) prefs.cursor = s.cursor          // only advance once everything it announced has been applied
        ok
    }

    suspend fun clearAll() {
        dao.clearMessages(); dao.clearChats(); pageEtags.clear(); exhausted.clear()
    }
}

fun MessageDto.toEntity(chatId: String) = MessageEntity(
    chatId = chatId, id = id, sortKey = sortKeyOf(id), msgId = msg_id, role = role, text = text, sender = sender,
    time = time, ts = ts, type = type, fileId = file_id, replyToMsgId = reply_to_msg_id, replyPreview = reply_to_preview,
    forwardedFrom = forwarded_from, myReaction = my_reaction, theirReaction = their_reaction,
)
