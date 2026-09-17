package com.silverphone.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rules for bringing relatives in from the phone's address book.
 *
 * "Skipped" must mean one thing only - this phone already has that number. It
 * must not silently absorb a person who cannot be imported yet, because the
 * preview tells the family those two things separately.
 */
class ContactsImportPlannerTest {

    private fun candidate(
        name: String = "女儿",
        number: String? = "13800138000",
        selected: Boolean = true,
        hasPhoto: Boolean = true,
    ) = ContactsImportPlanner.Candidate(
        selected = selected,
        name = name,
        number = number,
        hasPhoto = hasPhoto,
    )

    @Test
    fun aChosenNumberBecomesAnAddition() {
        val counts = ContactsImportPlanner.count(listOf(candidate()), emptySet())
        assertEquals(1, counts.toAdd)
        assertEquals(0, counts.skippedExisting)
        assertEquals(0, counts.needsFix)
    }

    @Test
    fun anUnselectedPersonIsIgnoredEntirely() {
        val counts = ContactsImportPlanner.count(
            listOf(candidate(selected = false), candidate(name = "儿子", selected = false)),
            emptySet(),
        )
        assertEquals(0, counts.toAdd + counts.skippedExisting + counts.needsFix)
    }

    @Test
    fun anAlreadyPresentNumberIsSkippedAndNotCountedAsNeedingAFix() {
        val counts = ContactsImportPlanner.count(
            listOf(candidate()),
            targetNumbers = setOf("13800138000"),
        )
        assertEquals(0, counts.toAdd)
        assertEquals(1, counts.skippedExisting)
        assertEquals(0, counts.needsFix)
    }

    @Test
    fun aNumberSeparatedBySpacesStillCountsAsAlreadyPresent() {
        // The comparison is made on the normalised form, so how the number was
        // typed in the address book does not matter.
        val counts = ContactsImportPlanner.count(
            listOf(candidate(number = "138 0013 8000")),
            targetNumbers = setOf("13800138000"),
        )
        assertEquals(1, counts.skippedExisting)
    }

    @Test
    fun aPersonWithNoChosenNumberMustBeFixedNotSkipped() {
        val counts = ContactsImportPlanner.count(listOf(candidate(number = null)), emptySet())
        assertEquals(0, counts.toAdd)
        assertEquals(0, counts.skippedExisting)
        assertEquals(1, counts.needsFix)
    }

    @Test
    fun aNameThatIsTooLongMustBeFixed() {
        val thirteen = "一二三四五六七八九十十一二"
        assertEquals(13, thirteen.codePointCount(0, thirteen.length))
        val counts = ContactsImportPlanner.count(listOf(candidate(name = thirteen)), emptySet())
        assertEquals(1, counts.needsFix)
    }

    @Test
    fun aMalformedNumberMustBeFixed() {
        val counts = ContactsImportPlanner.count(
            listOf(candidate(number = "1380013800a")),
            emptySet(),
        )
        assertEquals(1, counts.needsFix)
        assertEquals(0, counts.toAdd)
    }

    @Test
    fun aMissingPhotoIsReportedButStillImportable() {
        val counts = ContactsImportPlanner.count(listOf(candidate(hasPhoto = false)), emptySet())
        assertEquals(1, counts.toAdd)
        assertEquals(1, counts.missingPhoto)
    }

    @Test
    fun countAndAdditionsAgreeOnTheSameInput() {
        val candidates = listOf(
            candidate(name = "女儿", number = "13800138000"),
            candidate(name = "儿子", number = "13900139000"),
            // Shares a number with 女儿 but is a different person, so both come in:
            // the comparison is against what this phone already has, never against
            // the entries already taken from the same source.
            candidate(name = "老伴", number = "13800138000"),
            candidate(name = "孙子", number = null), // needs a fix
            candidate(name = "孙女", number = "01012345678", selected = false),
        )
        val counts = ContactsImportPlanner.count(candidates, emptySet())
        val additions = ContactsImportPlanner.additions(candidates, emptySet())

        assertEquals(3, counts.toAdd)
        assertEquals(1, counts.needsFix)
        assertEquals(additions.size, counts.toAdd)
        assertEquals(listOf("女儿", "儿子", "老伴"), additions.map { it.name })
    }

    @Test
    fun twoSourcePeopleMayShareOneNumberWhenTheTargetIsEmpty() {
        // A shared landline is normal. Neither is dropped just because the other
        // was seen first; only the target's own contents cause a skip.
        val candidates = listOf(
            candidate(name = "爷爷", number = "01012345678"),
            candidate(name = "奶奶", number = "01012345678"),
        )
        assertEquals(2, ContactsImportPlanner.count(candidates, emptySet()).toAdd)
    }

    @Test
    fun bothAreSkippedWhenTheTargetAlreadyHasThatSharedNumber() {
        val candidates = listOf(
            candidate(name = "爷爷", number = "01012345678"),
            candidate(name = "奶奶", number = "01012345678"),
        )
        val counts = ContactsImportPlanner.count(candidates, setOf("01012345678"))
        assertEquals(0, counts.toAdd)
        assertEquals(2, counts.skippedExisting)
    }
}
