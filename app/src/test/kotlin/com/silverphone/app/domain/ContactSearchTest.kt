package com.silverphone.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Management-list search.
 *
 * The case that matters is a query with no digits in it: an implementation that
 * feeds the digit-stripped query to `contains` gets `contains("")`, which is true
 * for every number, so a name search would return the whole list and the "no
 * results" state would be unreachable.
 */
class ContactSearchTest {

    private fun contact(name: String, number: String) = Contact(
        id = "id-$name",
        displayName = name,
        phoneNumber = number,
        sortOrder = 0,
        placeholderColor = PlaceholderColor.DEFAULT,
        photoSha256 = null,
    )

    private val contacts = listOf(
        contact("女儿", "13800138000"),
        contact("儿子", "13900139000"),
        contact("老伴", "01012345678"),
    )

    @Test
    fun anEmptyQueryKeepsEverything() {
        assertEquals(contacts, ContactSearch.filter(contacts, ""))
        assertEquals(contacts, ContactSearch.filter(contacts, "   "))
    }

    @Test
    fun aNameThatDoesNotExistReturnsNothing() {
        // Without the empty-digit guard this returns all three.
        assertTrue(ContactSearch.filter(contacts, "孙子").isEmpty())
    }

    @Test
    fun matchesOnPartOfTheName() {
        assertEquals(listOf("女儿"), ContactSearch.filter(contacts, "女").map { it.displayName })
    }

    @Test
    fun matchesOnDigitsWithSeparatorsTypedIn() {
        assertEquals(
            listOf("女儿"),
            ContactSearch.filter(contacts, "138 0013").map { it.displayName },
        )
        assertEquals(listOf("女儿"), ContactSearch.filter(contacts, "13800138000").map { it.displayName })
    }

    @Test
    fun matchesOnCrossFormattingOfALandline() {
        assertEquals(listOf("老伴"), ContactSearch.filter(contacts, "010-1234").map { it.displayName })
    }

    @Test
    fun aQueryWithNoDigitsNeverMatchesTheNumberColumn() {
        // "+" alone is digits-and-plus filtered to "+", which no number contains
        // unless it is an international one, and none here are.
        assertTrue(ContactSearch.filter(contacts, "++").isEmpty())
    }

    @Test
    fun aMixedQueryDoesNotFallThroughToTheNumberColumn() {
        // "老伴2" contains a digit, but it is a name query. Stripping it to "2" and
        // asking whether any number contains a 2 would return 女儿 (…1380 0 0 2)
        // as well, i.e. a contact the user never asked for.
        val result = ContactSearch.filter(contacts, "老伴2")
        assertTrue("expected no match, got ${result.map { it.displayName }}", result.isEmpty())
    }

    @Test
    fun aMixedQueryStillMatchesOnTheNamePart() {
        val result = ContactSearch.filter(contacts, "女儿2")
        assertTrue("a name query with a stray digit should not match", result.isEmpty())

        // But the plain name still works.
        assertEquals(listOf("女儿"), ContactSearch.filter(contacts, "女儿").map { it.displayName })
    }

    @Test
    fun aQueryThatIsEntirelyNumberCharactersStillSearchesNumbers() {
        assertEquals(listOf("老伴"), ContactSearch.filter(contacts, "010-1234").map { it.displayName })
        assertEquals(listOf("女儿"), ContactSearch.filter(contacts, "(138) 0013").map { it.displayName })
        assertEquals(listOf("女儿"), ContactSearch.filter(contacts, "+13800138000").map { it.displayName })
    }

    @Test
    fun matchingIsIndependentOfLetterCase() {
        val latin = listOf(contact("Daughter", "10000000001"))
        assertEquals(1, ContactSearch.filter(latin, "daughter").size)
        assertEquals(1, ContactSearch.filter(latin, "DAUGHTER").size)
    }

    @Test
    fun aNumberThatIsNotPresentReturnsNothing() {
        assertFalse(ContactSearch.matches(contacts[0], "99999999999"))
    }

    @Test
    fun fullWidthDigitsFindTheNumberTheyWereTypedFrom() {
        // A full-width keyboard produces these characters, and the save path folds them
        // to ASCII before storing the number. The search has to fold them the same way,
        // or it answers "no results" for a contact that is on the phone.
        assertEquals(listOf("女儿"), ContactSearch.filter(contacts, "１３８").map { it.displayName })
        assertEquals(
            listOf("女儿"),
            ContactSearch.filter(contacts, "１３８００１３８０００").map { it.displayName },
        )
        assertTrue(ContactSearch.filter(contacts, "９９９").isEmpty())
    }

    @Test
    fun fullWidthLettersFindTheNameTheyWereTypedFrom() {
        val latin = listOf(contact("Daughter", "10000000001"))
        assertEquals(1, ContactSearch.filter(latin, "ＤＡＵＧＨＴＥＲ").size)
    }
}
