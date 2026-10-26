package com.msma.tgpanel.data

import android.os.Build
import com.msma.tgpanel.BuildConfig
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed class ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>()
    /** 304 — the cached copy is still current. */
    object NotModified : ApiResult<Nothing>()
    /** [code] 0 = no connection; 401 = session gone; 429 = rate limited ([retryAfter] s). */
    data class Err(val code: Int, val message: String = "", val retryAfter: Int = 0) : ApiResult<Nothing>()
}

class RawResponse(val code: Int, val body: String, val headers: Headers)

/** Thin client for docs/API.md. All calls are main-safe suspend functions. */
class Api(private val prefs: Prefs) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)          // keeps the WebSocket honest (a dead peer fails fast)
        .build()

    /** Emits when the server says our token is no longer valid (401). */
    val authLost = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val jsonType = "application/json".toMediaType()

    private suspend fun exec(
        path: String,
        method: String = "GET",
        json: String? = null,
        etag: String? = null,
        auth: Boolean = true,
    ): RawResponse? = withContext(Dispatchers.IO) {
        try {
            val rb = Request.Builder().url(prefs.baseUrl + path)
            if (auth && prefs.token.isNotEmpty()) rb.header("Authorization", "Bearer " + prefs.token)
            if (etag != null) rb.header("If-None-Match", etag)
            val body = if (method == "GET") null else (json ?: "").toRequestBody(jsonType)
            rb.method(method, body)
            http.newCall(rb.build()).execute().use { r -> RawResponse(r.code, r.body?.string() ?: "", r.headers) }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {      // malformed server address typed by the user
            null
        }
    }

    private inline fun <reified T> parse(s: String): T? = try {
        AppJson.decodeFromString<T>(s)
    } catch (e: Exception) {
        null
    }

    private fun failure(r: RawResponse?): ApiResult.Err {
        if (r == null) return ApiResult.Err(0)
        if (r.code == 401) authLost.tryEmit(Unit)
        val err = parse<ApiAck>(r.body)?.error.orEmpty()
        return ApiResult.Err(r.code, err, r.headers["Retry-After"]?.toIntOrNull() ?: 0)
    }

    private inline fun <reified T> typed(r: RawResponse?): ApiResult<T> {
        if (r == null || r.code !in 200..299) return failure(r)
        val v = parse<T>(r.body) ?: return ApiResult.Err(r.code, "bad response")
        return ApiResult.Ok(v)
    }

    // ---- auth ----
    suspend fun version(): ApiResult<VersionDto> = typed(exec("/api/version", auth = false))

    suspend fun login(username: String, password: String): ApiResult<LoginResp> {
        val body = JsonObject(
            mapOf(
                "username" to JsonPrimitive(username),
                "password" to JsonPrimitive(password),
                "deviceName" to JsonPrimitive(Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL),
                "platform" to JsonPrimitive("android"),
                "appVersion" to JsonPrimitive(BuildConfig.VERSION_NAME),
            )
        )
        val r = exec("/api/login", "POST", AppJson.encodeToString(JsonObject.serializer(), body), auth = false)
        if (r == null) return ApiResult.Err(0)
        // login errors carry a message / retry_after / suspended flag in the body — keep them
        val parsed = parse<LoginResp>(r.body)
        if (r.code in 200..299 && parsed != null) return ApiResult.Ok(parsed)
        return ApiResult.Err(r.code, parsed?.error.orEmpty(), parsed?.retry_after ?: r.headers["Retry-After"]?.toIntOrNull() ?: 0)
            .let { if (parsed?.suspended == true) it.copy(message = "suspended") else it }
    }

    suspend fun logout() { exec("/api/logout", "POST") }

    // ---- reading ----
    suspend fun chats(etag: String?): Pair<ApiResult<List<ChatDto>>, String?> {
        val r = exec("/api/chats", etag = etag)
        if (r != null && r.code == 304) return ApiResult.NotModified to null
        return typed<List<ChatDto>>(r) to r?.headers?.get("ETag")
    }

    suspend fun chatPage(chatId: String, limit: Int, beforeId: String?, etag: String?): Pair<ApiResult<List<MessageDto>>, String?> {
        var path = "/api/chat?id=${enc(chatId)}&limit=$limit"
        if (beforeId != null) path += "&before_id=${enc(beforeId)}"
        val r = exec(path, etag = etag)
        if (r != null && r.code == 304) return ApiResult.NotModified to null
        return typed<List<MessageDto>>(r) to r?.headers?.get("ETag")
    }

    suspend fun sync(cursor: String): ApiResult<SyncDto> = typed(exec("/api/sync?cursor=${enc(cursor)}"))

    // ---- writing ----
    suspend fun send(chatId: String, text: String, senderMode: String, businessConnectionId: String, replyToMsgId: String?): ApiResult<ApiAck> {
        val m = mutableMapOf<String, JsonElement>(
            "chatId" to JsonPrimitive(chatId),
            "text" to JsonPrimitive(text),
            "senderMode" to JsonPrimitive(senderMode),
            "businessConnectionId" to JsonPrimitive(businessConnectionId.ifEmpty { null }),
        )
        val rid = replyToMsgId?.takeIf { it.isNotEmpty() }
        if (rid != null) m["replyToMsgId"] = rid.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(rid)
        val r = exec("/api/send", "POST", AppJson.encodeToString(JsonObject.serializer(), JsonObject(m)))
        if (r == null || r.code !in 200..299) return failure(r)
        val ack = parse<ApiAck>(r.body) ?: return ApiResult.Err(r.code, "bad response")
        return if (ack.error.isNotEmpty()) ApiResult.Err(r.code, ack.error) else ApiResult.Ok(ack)
    }

    // ---- devices ----
    suspend fun sessions(): ApiResult<List<SessionDto>> = typed(exec("/api/sessions"))

    suspend fun renameSession(id: String, name: String): ApiResult<ApiAck> {
        val body = AppJson.encodeToString(JsonObject.serializer(), JsonObject(mapOf("name" to JsonPrimitive(name))))
        val r = exec("/api/sessions/${enc(id)}", "PATCH", body)
        if (r == null || r.code !in 200..299) return failure(r)
        return ApiResult.Ok(parse<ApiAck>(r.body) ?: ApiAck(success = true))
    }

    suspend fun revokeSession(id: String): ApiResult<ApiAck> {
        val r = exec("/api/sessions/${enc(id)}", "DELETE")
        if (r == null || r.code !in 200..299) return failure(r)
        return ApiResult.Ok(parse<ApiAck>(r.body) ?: ApiAck(success = true))
    }

    // ---- media URLs (the token is sent as a header by the image loader, never put in the URL) ----
    fun mediaUrl(fileId: String) = prefs.baseUrl + "/api/media?file_id=" + enc(fileId)
    fun photoUrl(chatId: String) = prefs.baseUrl + "/api/user-photo?id=" + enc(chatId)
    fun wsUrl() = prefs.baseUrl.replaceFirst("http", "ws") + "/ws"          // https→wss, http→ws

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
