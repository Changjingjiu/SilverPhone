package com.silverphone.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Number normalisation. The rules must accept landlines, short numbers and
 * international prefixes, and must not quietly rewrite any of them.
 */
class PhoneNumberRulesTest {

    private fun normalized(raw: String): String? =
        (PhoneNumberRules.normalize(raw) as? PhoneNumberRules.Result.Valid)?.normalized

    private fun reason(raw: String): PhoneNumberRules.Reason? =
        (PhoneNumberRules.normalize(raw) as? PhoneNumberRules.Result.Invalid)?.reason

    @Test
    fun keepsPlainMobileNumberUnchanged() {
        assertEquals("13800138000", normalized("13800138000"))
    }

    @Test
    fun stripsTheSeparatorsPeopleActuallyType() {
        assertEquals("01012345678", normalized("010-1234 5678"))
        assertEquals("13800138000", normalized(" 138 0013 8000 "))
        assertEquals("01012345678", normalized("(010) 1234-5678"))
    }

    @Test
    fun foldsFullWidthDigitsAndPlus() {
        assertEquals("13800138000", normalized("１３８００１３８０００"))
        assertEquals("+8613800138000", normalized("＋８６１３８００１３８０００"))
    }

    @Test
    fun keepsAnExplicitCountryCode() {
        assertEquals("+8613800138000", normalized("+86 138 0013 8000"))
    }

    @Test
    fun doesNotAddACountryCode() {
        // Nothing is invented for the user; the digits stay as typed.
        assertEquals("13800138000", normalized("13800138000"))
    }

    @Test
    fun acceptsShortServiceNumbers() {
        assertEquals("110", normalized("110"))
        assertEquals("95588", normalized("95588"))
    }

    @Test
    fun rejectsLettersAndPauseCharacters() {
        assertEquals(PhoneNumberRules.Reason.ILLEGAL_CHARACTERS, reason("1380013800a"))
        assertEquals(PhoneNumberRules.Reason.ILLEGAL_CHARACTERS, reason("138,0013"))
        assertEquals(PhoneNumberRules.Reason.ILLEGAL_CHARACTERS, reason("*123#"))
        assertEquals(PhoneNumberRules.Reason.ILLEGAL_CHARACTERS, reason("tel:13800138000"))
        assertEquals(PhoneNumberRules.Reason.ILLEGAL_CHARACTERS, reason("13800138000;"))
    }

    @Test
    fun rejectsAPlusThatIsNotAtTheStart() {
        assertEquals(PhoneNumberRules.Reason.MISPLACED_PLUS, reason("86+13800138000"))
        assertEquals(PhoneNumberRules.Reason.MISPLACED_PLUS, reason("+86+13800138000"))
    }

    @Test
    fun rejectsTooShortAndTooLong() {
        assertEquals(PhoneNumberRules.Reason.TOO_SHORT, reason("12"))
        assertEquals(PhoneNumberRules.Reason.TOO_LONG, reason("1".repeat(21)))
        assertEquals("1".repeat(20), normalized("1".repeat(20)))
    }

    @Test
    fun rejectsInternalLineBreak() {
        assertEquals(PhoneNumberRules.Reason.ILLEGAL_CHARACTERS, reason("138\n00138000"))
    }

    @Test
    fun acceptsPlusOnlyNumberShape() {
        assertEquals("+8613800138000", normalized("+8613800138000"))
    }

    @Test
    fun rejectsEmptyAfterSeparatorRemoval() {
        assertNull(normalized("   "))
        assertEquals(PhoneNumberRules.Reason.EMPTY, reason("()"))
    }
}
