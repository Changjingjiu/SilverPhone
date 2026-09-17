package com.silverphone.app.platform.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/**
 * Reads the newest published release from GitHub over HTTPS.
 *
 * The request is anonymous, sends no identifier, and is only ever started by the
 * family member tapping "Check for updates". Anything that goes wrong - no network,
 * a blocked request, a timeout, a body GitHub changed - ends as
 * [ReleaseLookup.Unreachable], because a missing answer must never be presented as
 * "you are up to date" and must never stop the app from working offline.
 */
class GitHubReleaseSource(
    private val appVersion: String,
    private val apiUrl: String = ProjectLinks.LATEST_RELEASE_API,
) : ReleaseSource {

    override suspend fun latestRelease(): ReleaseLookup = withContext(Dispatchers.IO) {
        val connection = try {
            URL(apiUrl).openConnection() as HttpURLConnection
        } catch (failure: IOException) {
            return@withContext ReleaseLookup.Unreachable
        }

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            // A User-Agent that names the app: GitHub rejects requests without one,
            // and this keeps it honest about who is asking.
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "SilverPhone/$appVersion")

            when (connection.responseCode) {
                HTTP_OK -> GitHubReleaseJson.parseLatest(
                    connection.inputStream.use { it.readBytes() }.decodeToString(),
                )

                // GitHub answers 404 both for "no such repository" and for a
                // repository with no published release, and the second is the
                // expected state until the first release is cut.
                HTTP_NOT_FOUND -> ReleaseLookup.NotPublishedYet

                else -> ReleaseLookup.Unreachable
            }
        } catch (failure: IOException) {
            ReleaseLookup.Unreachable
        } catch (failure: SerializationException) {
            ReleaseLookup.Unreachable
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404
    }
}
