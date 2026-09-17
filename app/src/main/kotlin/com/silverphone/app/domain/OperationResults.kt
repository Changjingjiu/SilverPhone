package com.silverphone.app.domain

/**
 * Business-meaningful outcomes for contact writes.
 *
 * Errors are returned as values instead of thrown so that every caller has to say
 * what it does about them, and so that "cancelled" never has to be smuggled into
 * a null return alongside "empty" and "corrupt".
 */
sealed interface ContactWriteResult {

    data object Success : ContactWriteResult

    /** The database still holds exactly what it held before. */
    data class InvalidInput(
        val nameReason: DisplayNameRules.Reason? = null,
        val phoneReason: PhoneNumberRules.Reason? = null,
    ) : ContactWriteResult

    data class CapacityExceeded(val limit: Int) : ContactWriteResult

    data object NotFound : ContactWriteResult

    /** A storage-level failure. Nothing was committed. */
    data class StorageFailed(val cause: Throwable) : ContactWriteResult
}

/** Outcomes of a bulk import commit. */
sealed interface ImportCommitResult {

    data class Appended(
        val added: Int,
        val skipped: Int,
    ) : ImportCommitResult

    data class Replaced(val total: Int) : ImportCommitResult

    /** Nothing to add: the target already held every source contact. */
    data object NoChanges : ImportCommitResult

    data class CapacityExceeded(val limit: Int, val current: Int, val incoming: Int) :
        ImportCommitResult

    /** The preview no longer matches the database; the family member must redo it. */
    data object StalePreview : ImportCommitResult

    data class StorageFailed(val cause: Throwable) : ImportCommitResult
}
