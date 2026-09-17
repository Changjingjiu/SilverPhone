package com.silverphone.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The duplicate rules for the default append mode.
 *
 * These are the cases the spec lists explicitly, including the one that is easy to
 * get wrong: an empty target keeps everyone from the archive even when two of
 * those people share one number.
 */
class ImportPlannerTest {

    private fun target(vararg contacts: Pair<String, String>): List<Contact> =
        contacts.mapIndexed { index, (id, number) ->
            Contact(
                id = id,
                displayName = "现有$index",
                phoneNumber = number,
                sortOrder = index,
                placeholderColor = PlaceholderColor.DEFAULT,
                photoSha256 = null,
            )
        }

    private fun source(vararg contacts: Triple<String, String, String>): List<ImportedContact> =
        contacts.map { (id, name, number) ->
            ImportedContact(
                id = id,
                displayName = name,
                phoneNumber = number,
                placeholderColor = PlaceholderColor.DEFAULT,
                photo = null,
            )
        }

    @Test
    fun emptyTargetKeepsEveryoneEvenWithASharedNumber() {
        val plan = ImportPlanner.planAppend(
            source = source(
                Triple(ID_A, "女儿", "11111111111"),
                Triple(ID_B, "大女儿", "11111111111"),
            ),
            target = emptyList(),
        )
        assertEquals(2, plan.additions.size)
        assertEquals(0, plan.skipped)
    }

    @Test
    fun sameIdIsSkippedSoTheTargetsOwnNameSurvives() {
        val plan = ImportPlanner.planAppend(
            source = source(Triple(ID_A, "姐姐", "11111111111")),
            target = target(ID_A to "11111111111"),
        )
        assertTrue(plan.additions.isEmpty())
        assertEquals(1, plan.skippedByIdentity)
        assertEquals(0, plan.skippedByNumber)
    }

    @Test
    fun sameNumberUnderADifferentIdIsSkippedAndKeepsEveryExistingCard() {
        val plan = ImportPlanner.planAppend(
            source = source(Triple(ID_C, "小女儿", "22222222222")),
            target = target(ID_A to "22222222222"),
        )
        assertTrue(plan.additions.isEmpty())
        assertEquals(1, plan.skippedByNumber)
    }

    @Test
    fun sameIdWithAChangedNumberIsSkippedAndReportedAsAnIdentityConflict() {
        val plan = ImportPlanner.planAppend(
            source = source(Triple(ID_A, "女儿", "33333333333")),
            target = target(ID_A to "11111111111"),
        )
        assertTrue(plan.additions.isEmpty())
        assertEquals(1, plan.skippedByIdentity)
    }

    @Test
    fun reimportingTheSameArchiveAddsNothing() {
        val source = source(
            Triple(ID_A, "女儿", "11111111111"),
            Triple(ID_B, "儿子", "22222222222"),
        )
        val first = ImportPlanner.planAppend(source, emptyList())
        assertEquals(2, first.additions.size)

        // The target now holds exactly what the first import added.
        val after = target(ID_A to "11111111111", ID_B to "22222222222")
        val second = ImportPlanner.planAppend(source, after)
        assertTrue(second.additions.isEmpty())
        assertEquals(2, second.skipped)
    }

    @Test
    fun comparisonUsesTheOriginalTargetNotAProgressiveSet() {
        // Two people in the archive share one number. The target holds neither, so
        // both must be added - a set that grew as entries matched would wrongly
        // drop the second one.
        val plan = ImportPlanner.planAppend(
            source = source(
                Triple(ID_A, "爷爷", "44444444444"),
                Triple(ID_B, "奶奶", "44444444444"),
            ),
            target = emptyList(),
        )
        assertEquals(2, plan.additions.size)
    }

    @Test
    fun capacityIsCheckedAgainstTheWholeArchiveNotOneAtATime() {
        val existing = (0 until 499).map { index ->
            Contact(
                id = "existing-$index",
                displayName = "亲人",
                phoneNumber = "1${index.toString().padStart(10, '0')}",
                sortOrder = index,
                placeholderColor = PlaceholderColor.DEFAULT,
                photoSha256 = null,
            )
        }
        val plan = ImportPlanner.planAppend(
            source = source(
                Triple(ID_A, "女儿", "55555555555"),
                Triple(ID_B, "儿子", "66666666666"),
            ),
            target = existing,
        )
        // 499 + 2 exceeds the limit; the whole operation must be refused rather
        // than importing only the first one.
        assertTrue(plan.exceedsCapacity)
    }

    @Test
    fun additionsKeepTheSourceOrderIncludingTheirIdAndColour() {
        val plan = ImportPlanner.planAppend(
            source = listOf(
                ImportedContact(
                    id = ID_B,
                    displayName = "老伴",
                    phoneNumber = "22222222222",
                    placeholderColor = PlaceholderColor.LIGHT_TEAL,
                    photo = null,
                ),
                ImportedContact(
                    id = ID_A,
                    displayName = "女儿",
                    phoneNumber = "11111111111",
                    placeholderColor = PlaceholderColor.LIGHT_ROSE,
                    photo = null,
                ),
            ),
            target = emptyList(),
        )
        assertEquals(listOf(ID_B, ID_A), plan.additions.map { it.id })
        assertEquals(PlaceholderColor.LIGHT_TEAL, plan.additions[0].placeholderColor)
    }

    private companion object {
        const val ID_A = "11111111-1111-4111-8111-111111111111"
        const val ID_B = "22222222-2222-4222-8222-222222222222"
        const val ID_C = "33333333-3333-4333-8333-333333333333"
    }
}
