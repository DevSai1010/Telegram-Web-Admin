package com.msma.tgpanel

import com.msma.tgpanel.core.Backoff
import com.msma.tgpanel.core.Side
import com.msma.tgpanel.core.avatarHue
import com.msma.tgpanel.core.compareIds
import com.msma.tgpanel.core.groupsWithPrevious
import com.msma.tgpanel.core.hslToArgb
import com.msma.tgpanel.core.idsToDelete
import com.msma.tgpanel.core.isSelfSender
import com.msma.tgpanel.core.nextUnread
import com.msma.tgpanel.core.sideOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test fun idsCompareNumerically() {
        assertTrue(compareIds("999", "1000") < 0)
        assertTrue(compareIds("1700000000001", "1700000000000") > 0)
        assertEquals(0, compareIds("42", "42"))
    }

    @Test fun newestPageDeletesMissingRowsInsideAndAboveItsRange() {
        val local = listOf("100", "200", "300", "400", "500")
        // server page has 200, 400 and 500 only → 300 was deleted; 100 is older than the page → untouched
        assertEquals(listOf("300"), idsToDelete(local, listOf("200", "400", "500"), newest = true))
        // a local row NEWER than everything on the server is gone too (open-ended upward)
        assertEquals(listOf("500"), idsToDelete(local, listOf("200", "300", "400"), newest = true))
    }

    @Test fun olderPageNeverTouchesRowsNewerThanItsRange() {
        val local = listOf("100", "200", "300", "400", "500")
        assertEquals(listOf("200"), idsToDelete(local, listOf("100", "300"), newest = false))
    }

    @Test fun emptyPageProvesNothing() {
        assertEquals(emptyList<String>(), idsToDelete(listOf("1", "2"), emptyList(), newest = true))
    }

    @Test fun rowsOlderThanThePageAreNeverDeleted() {
        // server only keeps recent history: old local rows stay even though the page doesn't list them
        assertEquals(emptyList<String>(), idsToDelete(listOf("10", "20", "300", "400"), listOf("300", "400"), newest = true))
    }

    @Test fun backoffGrowsAndCaps() {
        assertEquals(800L, Backoff.delayMs(0, 0.0))
        assertEquals(1600L, Backoff.delayMs(1, 0.0))
        assertEquals(15000L, Backoff.delayMs(9, 0.0))
        assertEquals(15400L, Backoff.delayMs(50, 1.0))
    }

    @Test fun unreadFollowsTheWebPanelHeuristic() {
        assertEquals(0, nextUnread(null, "hi", "سارا", 0, chatOpen = false))        // first sight = baseline
        assertEquals(1, nextUnread("hi", "hello", "سارا", 0, chatOpen = false))     // customer wrote something new
        assertEquals(1, nextUnread("hi", "reply", "پنل", 1, chatOpen = false))      // our own line doesn't count
        assertEquals(1, nextUnread("hi", "hi", "سارا", 1, chatOpen = false))        // unchanged
        assertEquals(0, nextUnread("hi", "hello", "سارا", 3, chatOpen = true))      // open chat is always read
        assertTrue(isSelfSender("دستیار (AI)"))
    }

    @Test fun sidesAndGrouping() {
        assertEquals(Side.OUT, sideOf("owner"))
        assertEquals(Side.OUT, sideOf("model"))
        assertEquals(Side.IN, sideOf("user"))
        assertTrue(groupsWithPrevious("user", "A", "user", "A", hasReply = false, hasForward = false))
        assertTrue(!groupsWithPrevious("user", "A", "user", "A", hasReply = true, hasForward = false))
        assertTrue(!groupsWithPrevious("user", "A", "owner", "A", hasReply = false, hasForward = false))
    }

    @Test fun avatarHueIsStableAndOnTheTwelveHueWheel() {
        assertEquals(avatarHue("123456"), avatarHue("123456"), 0f)
        for (id in listOf("1", "99", "5551234567", "-100123")) {
            val h = avatarHue(id)
            assertTrue((h - 8f) % 30f == 0f && h in 8f..338f)
        }
    }

    @Test fun hslConversionMatchesKnownColours() {
        assertEquals(0xFFFF0000.toInt(), hslToArgb(0f, 1f, 0.5f))      // red
        assertEquals(0xFF00FF00.toInt(), hslToArgb(120f, 1f, 0.5f))    // green
        assertEquals(0xFF0000FF.toInt(), hslToArgb(240f, 1f, 0.5f))    // blue
        assertEquals(0xFFFFFFFF.toInt(), hslToArgb(77f, 0.3f, 1f))     // any hue at full lightness is white
        assertEquals(0xFF000000.toInt(), hslToArgb(77f, 0.3f, 0f))
    }
}
