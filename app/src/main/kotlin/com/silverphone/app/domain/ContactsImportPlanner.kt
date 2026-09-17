package com.silverphone.app.domain

/**
 * The decision rules for bringing contacts in from the phone's address book.
 *
 * Kept out of the ViewModel and free of Android types so the awkward cases - a
 * person with several numbers, a name that is too long, a number this phone
 * already has - are covered by ordinary unit tests instead of only by hand.
 */
object ContactsImportPlanner {

    /**
     * One selectable person, reduced to the facts the rules need.
     *
     * [number] is the number the family picked, or null when none has been chosen
     * yet; [name] is the wording they want the elderly user to see.
     */
    class Candidate(
        val selected: Boolean,
        val name: String,
        val number: String?,
        val hasPhoto: Boolean,
    ) {
        /** Normalised number, or null when it is missing or malformed. */
        val normalizedNumber: String? =
            number?.let { PhoneNumberRules.normalize(it) as? PhoneNumberRules.Result.Valid }
                ?.normalized

        val nameIsValid: Boolean = DisplayNameRules.validate(name) is DisplayNameRules.Result.Valid

        /** Selected, but not importable until the family fixes something. */
        val needsFix: Boolean get() = selected && (normalizedNumber == null || !nameIsValid)

        /** Selected, importable, and this phone does not already have the number. */
        fun isAddition(targetNumbers: Set<String>): Boolean =
            selected && !needsFix && normalizedNumber !in targetNumbers
    }

    class Counts(
        val toAdd: Int,
        val skippedExisting: Int,
        val needsFix: Int,
        val missingPhoto: Int,
    )

    fun count(candidates: List<Candidate>, targetNumbers: Set<String>): Counts {
        var toAdd = 0
        var skipped = 0
        var needsFix = 0
        var missingPhoto = 0

        for (candidate in candidates) {
            if (!candidate.selected) continue
            if (candidate.needsFix) {
                needsFix++
                continue
            }
            if (candidate.normalizedNumber in targetNumbers) {
                // Already on this phone: keep the existing card, its wording and
                // its photo, and say so rather than importing it again.
                skipped++
                continue
            }
            toAdd++
            if (!candidate.hasPhoto) missingPhoto++
        }

        return Counts(
            toAdd = toAdd,
            skippedExisting = skipped,
            needsFix = needsFix,
            missingPhoto = missingPhoto,
        )
    }

    /** The candidates that should actually be written, in the order shown. */
    fun additions(candidates: List<Candidate>, targetNumbers: Set<String>): List<Candidate> =
        candidates.filter { it.isAddition(targetNumbers) }
}
