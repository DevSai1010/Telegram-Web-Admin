package com.msma.tgpanel.core

import kotlin.math.min

/*
 * Pure logic — no Android, no I/O — so it can be unit-tested on a plain JVM
 * (see app/src/test). Everything here mirrors rules the web panel and the API
 * contract (docs/API.md) already define.
 */

/** Panel message ids are millisecond timestamps as strings: longer = newer, equal length compares lexically. */
fun compareIds(a: String, b: String): Int =
    if (a.length != b.length) a.length.compareTo(b.length) else a.compareTo(b)
÷
/** Sort key for SQLite (ids are digit strings; anything else sorts first). */
fun sortKeyOf(id: String): Long = id.toLongOrNull() ?: 0L

/**
 * Which locally cached message ids a freshly fetched page proves were deleted (API.md "merging rule").
 *  - The page covers [first..last]; local rows inside that range that are missing from the page were deleted.
 *  - [newest] = this is the newest page, so its range is open upward: local rows newer than `last`
 *    that the server no longer has are gone too.
 *  - Rows OLDER than the page are never touched: the server keeps history only for a while, so "not on
 *    the server any more" is not "deleted" there — the local copy may be the only one.
 *  - An empty page proves nothing.
 */
fun idsToDelete(localIds: List<String>, pageIds: List<String>, newest: Boolean): List<String> {
    if (pageIds.isEmpty()) return emptyList()
    var first = pageIds[0]
    var last = pageIds[0]
    for (id in pageIds) {
        if (compareIds(id, first) < 0) first = id
        if (compareIds(id, last) > 0) last = id
    }
    val present = pageIds.toHashSet()
    return localIds.filter { it !in present && compareIds(it, first) >= 0 && (newest || compareIds(it, last) <= 0) }
}

/** Reconnect delay: 0.8 s, 1.6 s, 3.2 s … capped at 15 s, plus up to 0.4 s of jitter (jitter in 0..1). */
object Backoff {
    fun delayMs(attempt: Int, jitter: Double): Long {
        val base = min(15000.0, 800.0 * Math.pow(2.0, attempt.coerceIn(0, 10).toDouble()))
        return (base + jitter.coerceIn(0.0, 1.0) * 400.0).toLong()
    }
}

private val SELF_LABELS = setOf("شما", "پنل", "شما (من)", "شما (ربات)", "ربات", "دستیار", "دستیار (AI)")

/** True when the last line of a chat was written by us / the bot / the AI rather than the customer. */
fun isSelfSender(label: String): Boolean = label in SELF_LABELS

/**
 * Unread counter, same heuristic as the web panel (the chat list carries no per-message ids):
 * a change of the chat's last line counts as one new incoming message unless we wrote it or the chat is open.
 * The very first time a chat is seen it only sets the baseline.
 */
fun nextUnread(prevText: String?, newText: String, newSender: String, current: Int, chatOpen: Boolean): Int = when {
    chatOpen -> 0
    prevText == null -> current
    prevText == newText -> current
    isSelfSender(newSender) -> current
    else -> current + 1
}

/** Same bubble colouring/side rules as the web panel. */
enum class Side { IN, OUT }

fun sideOf(role: String): Side = when (role) {
    "admin", "model", "owner", "bot_auto" -> Side.OUT
    else -> Side.IN
}

/** Two consecutive messages visually group when same speaker and the later one is a plain message. */
fun groupsWithPrevious(prevRole: String?, prevSender: String?, role: String, sender: String, hasReply: Boolean, hasForward: Boolean): Boolean =
    prevRole != null && prevRole == role && prevSender == sender && !hasReply && !hasForward

/** The web panel's avatar hue: a stable hash of the chat id, 12 hues 30° apart. */
fun avatarHue(id: String): Float {
    var h = 0L
    for (ch in id) h = (h * 31 + ch.code) and 0xFFFFFFFFL
    return ((h % 12) * 30 + 8).toFloat()
}

/** HSL (h in degrees, s/l in 0..1) → opaque ARGB int. Kept here (pure) so avatar colours are testable. */
fun hslToArgb(h: Float, s: Float, l: Float): Int {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val hp = (((h % 360f) + 360f) % 360f) / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val r1: Float; val g1: Float; val b1: Float
    when {
        hp < 1f -> { r1 = c; g1 = x; b1 = 0f }
        hp < 2f -> { r1 = x; g1 = c; b1 = 0f }
        hp < 3f -> { r1 = 0f; g1 = c; b1 = x }
        hp < 4f -> { r1 = 0f; g1 = x; b1 = c }
        hp < 5f -> { r1 = x; g1 = 0f; b1 = c }
        else -> { r1 = c; g1 = 0f; b1 = x }
    }
    val m = l - c / 2f
    fun ch(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
}
