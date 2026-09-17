package com.silverphone.app.domain

/**
 * One contact read out of an exchange archive, fully validated, with its photo
 * already normalised and staged in private storage.
 *
 * The source UUID is preserved, because identity across devices is what makes a
 * repeated import of the same archive a no-op instead of a duplicate.
 */
class ImportedContact(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val placeholderColor: PlaceholderColor,
    val photo: NormalizedPhoto?,
)

/**
 * The result of comparing an archive against the target device, for the default
 * append mode.
 *
 * The comparison is always made against the target snapshot taken when the
 * preview was built, never against a set that grows as entries are matched. That
 * is what allows an empty target to keep every contact from an archive even when
 * two of them share one phone number.
 */
data class AppendPlan(
    val additions: List<ImportedContact>,
    val skippedByIdentity: Int,
    val skippedByNumber: Int,
    val existingCount: Int,
    val expectedRevision: Long,
) {
    val skipped: Int get() = skippedByIdentity + skippedByNumber

    val exceedsCapacity: Boolean
        get() = existingCount + additions.size > ContactLimits.MAX_CONTACTS
}

/** The result of comparing an archive against the target for replace mode. */
data class ReplacePlan(
    val incoming: List<ImportedContact>,
    val existingCount: Int,
    val expectedRevision: Long,
)

/**
 * Decides, purely from data, what an import would change.
 *
 * Deliberately free of Android types: the whole rule set is exercised by JVM unit
 * tests, including the awkward cases listed in the logic specification.
 */
object ImportPlanner {

    fun planAppend(source: List<ImportedContact>, target: List<Contact>): AppendPlan {
        // Both sets are taken once, from the target as it was when the preview
        // started. Later additions never enter the comparison.
        val targetIds = HashSet<String>(target.size)
        val targetNumbers = HashSet<String>(target.size)
        for (contact in target) {
            targetIds.add(contact.id)
            targetNumbers.add(contact.phoneNumber)
        }

        val additions = ArrayList<ImportedContact>(source.size)
        var skippedByIdentity = 0
        var skippedByNumber = 0

        for (candidate in source) {
            if (targetIds.contains(candidate.id)) {
                // Same identity: keep the target's own name, photo and position.
                skippedByIdentity++
                continue
            }
            if (targetNumbers.contains(candidate.phoneNumber)) {
                // A different identity already uses this number; keep every
                // existing card that shares it.
                skippedByNumber++
                continue
            }
            additions.add(candidate)
        }

        return AppendPlan(
            additions = additions,
            skippedByIdentity = skippedByIdentity,
            skippedByNumber = skippedByNumber,
            existingCount = target.size,
            expectedRevision = 0L,
        )
    }

    fun planReplace(source: List<ImportedContact>, target: List<Contact>): ReplacePlan =
        ReplacePlan(
            incoming = source,
            existingCount = target.size,
            expectedRevision = 0L,
        )
}
