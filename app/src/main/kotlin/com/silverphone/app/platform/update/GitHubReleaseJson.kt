package com.silverphone.app.platform.update

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The two fields of a GitHub release this app reads.
 *
 * Lenient, unlike the exchange-archive format: GitHub adds fields to its payload
 * whenever it likes, and an unknown field is not a reason to tell a family member
 * that the check failed. Only what is missing matters, and that is reported as
 * unreadable rather than as an update.
 */
object GitHubReleaseJson {

    private val format = Json { ignoreUnknownKeys = true }

    fun parseLatest(body: String): ReleaseLookup {
        val release = try {
            format.decodeFromString(LatestRelease.serializer(), body)
        } catch (failure: SerializationException) {
            return ReleaseLookup.Unreachable
        }
        val tag = release.tagName?.trim().orEmpty()
        val pageUrl = release.pageUrl?.trim().orEmpty()
        if (tag.isEmpty() || pageUrl.isEmpty()) return ReleaseLookup.Unreachable
        return ReleaseLookup.Found(tag = tag, pageUrl = pageUrl)
    }

    @Serializable
    private data class LatestRelease(
        @kotlinx.serialization.SerialName("tag_name") val tagName: String? = null,
        @kotlinx.serialization.SerialName("html_url") val pageUrl: String? = null,
    )
}
