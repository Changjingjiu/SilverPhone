package com.silverphone.app.domain

/**
 * A version number, compared the way releases are compared rather than the way text
 * is sorted.
 *
 * Text comparison is wrong in exactly the case that matters here: as text, "1.10.0"
 * sorts before "1.9.0", so a family member would be told that a release from months
 * ago is the newest one. Components are therefore compared as numbers, and a build
 * carrying a pre-release suffix - the debug build ships as "1.0.0-debug" - counts as
 * older than the release with the same number, so a test build is offered the real
 * release instead of being told it is already current.
 */
data class AppVersion(
    val components: List<Int>,
    val preRelease: String?,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        val length = maxOf(components.size, other.components.size)
        for (index in 0 until length) {
            // "1.2" and "1.2.0" are the same version, so a missing component is zero
            // rather than a comparison failure.
            val mine = components.getOrElse(index) { 0 }
            val theirs = other.components.getOrElse(index) { 0 }
            if (mine != theirs) return mine.compareTo(theirs)
        }
        return when {
            preRelease == null && other.preRelease == null -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            // Two pre-releases of the same number are only ever compared for equality;
            // this app has no ordering to give them and never needs one.
            else -> 0
        }
    }

    companion object {

        /**
         * Reads "1.2.3", "v1.2.3" and "1.2.3-debug"; returns null for anything else.
         *
         * A tag that is not a version must never be shown as an update, so an
         * unreadable string is reported as unreadable rather than guessed at.
         */
        fun parse(raw: String): AppVersion? {
            val trimmed = raw.trim()
            val withoutPrefix = trimmed.removePrefix("v").removePrefix("V")
            val dash = withoutPrefix.indexOf('-')
            val numbers = if (dash >= 0) withoutPrefix.substring(0, dash) else withoutPrefix
            val suffix = if (dash >= 0) withoutPrefix.substring(dash + 1) else ""
            if (numbers.isEmpty()) return null

            val parts = numbers.split('.')
            if (parts.size > 4) return null
            val components = parts.map { part -> part.toIntOrNull() ?: return null }
            if (components.any { it < 0 }) return null
            return AppVersion(components = components, preRelease = suffix.ifEmpty { null })
        }
    }
}
