package com.silverphone.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Name rules at the boundaries the spec calls out: the 12-code-point limit, the
 * difference between code points and UTF-16 units, and rejected characters.
 */
class DisplayNameRulesTest {

    private fun reason(raw: String): DisplayNameRules.Reason? =
        (DisplayNameRules.validate(raw) as? DisplayNameRules.Result.Invalid)?.reason

    private fun value(raw: String): String? =
        (DisplayNameRules.validate(raw) as? DisplayNameRules.Result.Valid)?.value

    @Test
    fun acceptsOrdinaryChineseName() {
        assertEquals("女儿", value("女儿"))
    }

    @Test
    fun acceptsTwelveCodePointsAndRejectsThirteen() {
        val twelve = "一二三四五六七八九十十一"
        assertEquals(12, twelve.codePointCount(0, twelve.length))
        assertEquals(twelve, value(twelve))

        val thirteen = twelve + "二"
        assertEquals(13, thirteen.codePointCount(0, thirteen.length))
        assertEquals(DisplayNameRules.Reason.TOO_LONG, reason(thirteen))
    }

    @Test
    fun rejectsEmptyAndWhitespaceOnly() {
        assertEquals(DisplayNameRules.Reason.EMPTY, reason(""))
        assertEquals(DisplayNameRules.Reason.EMPTY, reason("   "))
        assertEquals(DisplayNameRules.Reason.EMPTY, reason("\u3000"))
    }

    @Test
    fun trimsSurroundingWhitespaceButKeepsInnerSpaces() {
        assertEquals("王 小明", value("  王 小明  "))
    }

    @Test
    fun trimsASurroundingLineBreakRatherThanRejectingTheName() {
        // Ends are trimmed before anything is checked, so a stray newline pasted
        // alongside a name does not invalidate it.
        assertEquals("女儿", value("女儿\n"))
        assertEquals("女儿", value("\n女儿"))
    }

    @Test
    fun rejectsControlCharactersInsideTheName() {
        assertEquals(DisplayNameRules.Reason.CONTROL_CHARACTER, reason("女\u0000儿"))
        assertEquals(DisplayNameRules.Reason.CONTROL_CHARACTER, reason("女\t儿"))
        assertEquals(DisplayNameRules.Reason.CONTROL_CHARACTER, reason("女\n儿"))
    }

    @Test
    fun countsSupplementaryPlaneCharactersAsOneCodePoint() {
        // An emoji is one code point but two UTF-16 units, so a naive length check
        // would reject a name that is actually within the limit.
        val emoji = "\uD83D\uDE00"
        assertEquals(2, emoji.length)
        assertEquals(1, emoji.codePointCount(0, emoji.length))

        val name = emoji.repeat(12)
        assertTrue(value(name) != null)
        assertEquals(DisplayNameRules.Reason.TOO_LONG, reason(name + emoji))
    }

    @Test
    fun rejectsUnpairedSurrogate() {
        assertEquals(DisplayNameRules.Reason.INVALID_UNICODE, reason("女\uD83D儿"))
        assertEquals(DisplayNameRules.Reason.INVALID_UNICODE, reason("女儿\uDE00"))
    }
}
