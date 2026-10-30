package com.msma.tgpanel.data

import android.content.Context
import com.msma.tgpanel.BuildConfig

/** Small key-value store for the session and sync bookkeeping (private to the app). */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("panel", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = sp.getString("base", null) ?: BuildConfig.DEFAULT_SERVER
        set(v) = sp.edit().putString("base", v.trim().trimEnd('/')).apply()

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit().putString("token", v).apply()

    var username: String
        get() = sp.getString("username", "") ?: ""
        set(v) = sp.edit().putString("username", v).apply()

    var isOwner: Boolean
        get() = sp.getBoolean("owner", false)
        set(v) = sp.edit().putBoolean("owner", v).apply()

    /** Last /api/sync cursor we fully processed. */
    var cursor: String
        get() = sp.getString("cursor", "") ?: ""
        set(v) = sp.edit().putString("cursor", v).apply()

    /** ETag of the last /api/chats answer. */
    var chatsEtag: String
        get() = sp.getString("chats_etag", "") ?: ""
        set(v) = sp.edit().putString("chats_etag", v).apply()

    /** "Always-on connection": a foreground service keeps the live connection while the app is in the background. */
    var keepConnected: Boolean
        get() = sp.getBoolean("keep_connected", false)
        set(v) = sp.edit().putBoolean("keep_connected", v).apply()

    /** "" = follow the system, otherwise "fa" / "en" / "ru". */
    var language: String
        get() = sp.getString("lang", "") ?: ""
        set(v) = sp.edit().putString("lang", v).apply()

    /** "emoji:count,emoji:count" — how often each reaction / emoji was used, so the most-used come first (same idea as the web panel). */
    var emojiUsage: String
        get() = sp.getString("emoji_usage", "") ?: ""
        set(v) = sp.edit().putString("emoji_usage", v).apply()

    fun usageCounts(): Map<String, Int> =
        emojiUsage.split(',').mapNotNull { p -> p.split(':').takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: 0) } }.toMap()

    fun bumpEmoji(e: String) {
        val m = usageCounts().toMutableMap()
        m[e] = (m[e] ?: 0) + 1
        emojiUsage = m.entries.sortedByDescending { it.value }.take(60).joinToString(",") { it.key + ":" + it.value }
    }

    val loggedIn: Boolean get() = token.isNotEmpty()

    /** Sign out locally: forget the session and every sync marker (the DB is cleared by App.logout). */
    fun clearSession() {
        sp.edit().remove("token").remove("username").remove("owner").remove("cursor").remove("chats_etag").remove("keep_connected").apply()
    }
}
