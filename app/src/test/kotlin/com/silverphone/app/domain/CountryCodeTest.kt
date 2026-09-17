package com.silverphone.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dialling prefix.
 *
 * The rule that matters is what happens to a number that is already international,
 * and to short service codes: adding a country code to either would place a
 * different call than the family intended.
 */
class CountryCodeTest {

    @Test
    fun aPlainSubscriberNumberGetsThePrefix() {
        assertEquals("+8613800138000", CountryCode.apply("13800138000", "+86"))
        assertEquals("+15550134", CountryCode.apply("5550134", "+1"))
    }

    @Test
    fun aNumberThatAlreadyHasACountryCodeIsLeftAlone() {
        assertEquals("+8613800138000", CountryCode.apply("+8613800138000", "+1"))
        assertEquals("+15550134", CountryCode.apply("+15550134", "+86"))
    }

    @Test
    fun shortServiceNumbersAreDialledAsTheyAre() {
        // Prepending +86 to 110 or 10086 would dial something else entirely, and these
        // are exactly the numbers people store for banks and hotlines.
        assertEquals("10086", CountryCode.apply("10086", "+86"))
        assertEquals("110", CountryCode.apply("110", "+86"))
        assertEquals("95588", CountryCode.apply("95588", "+86"))
    }

    @Test
    fun sixDigitsIsThePointWhereThePrefixStartsApplying() {
        assertEquals("+86123456", CountryCode.apply("123456", "+86"))
        assertEquals("12345", CountryCode.apply("12345", "+86"))
    }

    @Test
    fun anEmptySettingAddsNothing() {
        // This is how a family who stores full international numbers, or who wants the
        // phone's own behaviour, opts out.
        assertEquals("13800138000", CountryCode.apply("13800138000", ""))
        assertEquals("10086", CountryCode.apply("10086", ""))
    }

    @Test
    fun changingThePrefixChangesWhatIsDialledWithoutTouchingTheStoredNumber() {
        val stored = "5550134"
        assertEquals("+15550134", CountryCode.apply(stored, "+1"))
        assertEquals("+445550134", CountryCode.apply(stored, "+44"))
        // The stored value is the same string in all three cases.
        assertEquals("5550134", stored)
    }

    @Test
    fun validateAcceptsTheShapesPeopleActuallyType() {
        assertTrue(CountryCode.validate(""))
        assertTrue(CountryCode.validate("+86"))
        assertTrue(CountryCode.validate("+1"))
        assertTrue(CountryCode.validate("+852"))
        assertTrue(CountryCode.validate("  +86  "))
    }

    @Test
    fun validateRejectsEverythingElse() {
        assertFalse(CountryCode.validate("86"))
        assertFalse(CountryCode.validate("+"))
        assertFalse(CountryCode.validate("+12345"))
        assertFalse(CountryCode.validate("+86a"))
        assertFalse(CountryCode.validate("++86"))
        assertFalse(CountryCode.validate("+86 10"))
    }

    @Test
    fun thePresetListIsAllValid() {
        CountryCode.PRESETS.forEach { code ->
            assertTrue("preset $code should validate", CountryCode.validate(code))
        }
    }

    @Test
    fun aNationalTrunkZeroIsReplacedByTheCountryCode() {
        // A Beijing landline as the address-book import stores it.
        assertEquals("+861012345678", CountryCode.apply("01012345678", "+86"))
        // And a Shanghai one, and a UK one.
        assertEquals("+862112345678", CountryCode.apply("02112345678", "+86"))
        assertEquals("+442079460018", CountryCode.apply("02079460018", "+44"))
    }

    @Test
    fun italyKeepsItsLeadingZero() {
        // Italian national numbers carry the zero abroad too: +39 06 ... is correct.
        assertEquals("+3906123456", CountryCode.apply("06123456", "+39"))
    }

    @Test
    fun anInternationalNumberIsStillLeftAlone() {
        assertEquals("+8613800138000", CountryCode.apply("+8613800138000", "+1"))
    }

}
