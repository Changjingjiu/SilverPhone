package com.silverphone.app.platform.update

/** What one look at the project's published releases produced. */
sealed interface ReleaseLookup {

    /** A release is published. [tag] is the raw tag, for example "v1.2.0". */
    data class Found(val tag: String, val pageUrl: String) : ReleaseLookup

    /**
     * The repository answers, but no release has been published yet.
     *
     * Kept apart from [Unreachable] because it is not a failure: it is the true state
     * of a project whose first release has not been cut.
     */
    data object NotPublishedYet : ReleaseLookup

    /** No usable answer: offline, timed out, an HTTP error, or a body we cannot read. */
    data object Unreachable : ReleaseLookup
}

/**
 * The newest published release of this project.
 *
 * An interface rather than a concrete class for two reasons: the About screen can be
 * driven through every state in a test without a socket, and the type is a visible
 * reminder that this is the only network call the app has.
 */
fun interface ReleaseSource {
    suspend fun latestRelease(): ReleaseLookup
}
