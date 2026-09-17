package com.silverphone.app.platform.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The GitHub body, read for the two fields that matter and nothing else.
 *
 * GitHub adds fields to this payload regularly, so an unknown field must not read as
 * a failed check; a missing field must, because the alternative is showing a version
 * number that came from nowhere.
 */
class GitHubReleaseJsonTest {

    @Test
    fun `reads the tag and the page from a full payload`() {
        val body = """
            {
              "url": "https://api.github.com/repos/Changjingjiu/SilverPhone/releases/1",
              "assets_url": "https://api.github.com/repos/Changjingjiu/SilverPhone/releases/1/assets",
              "tag_name": "v1.2.0",
              "name": "SilverPhone 1.2.0",
              "draft": false,
              "prerelease": false,
              "assets": [{"name": "SilverPhone-v1.2.0.apk", "size": 1234}],
              "body": "Release notes",
              "html_url": "https://github.com/Changjingjiu/SilverPhone/releases/tag/v1.2.0"
            }
        """.trimIndent()

        assertEquals(
            ReleaseLookup.Found(
                tag = "v1.2.0",
                pageUrl = "https://github.com/Changjingjiu/SilverPhone/releases/tag/v1.2.0",
            ),
            GitHubReleaseJson.parseLatest(body),
        )
    }

    @Test
    fun `a payload without a tag is unreadable rather than an update`() {
        val body = """{"html_url": "https://github.com/Changjingjiu/SilverPhone/releases/tag/v1.0.0"}"""
        assertEquals(ReleaseLookup.Unreachable, GitHubReleaseJson.parseLatest(body))
    }

    @Test
    fun `a payload without a page is unreadable`() {
        val body = """{"tag_name": "v1.0.0"}"""
        assertEquals(ReleaseLookup.Unreachable, GitHubReleaseJson.parseLatest(body))
    }

    @Test
    fun `a tag that is blank is unreadable`() {
        val body = """{"tag_name": "   ", "html_url": "https://example.invalid"}"""
        assertEquals(ReleaseLookup.Unreachable, GitHubReleaseJson.parseLatest(body))
    }

    @Test
    fun `malformed input never throws`() {
        assertTrue(GitHubReleaseJson.parseLatest("") is ReleaseLookup.Unreachable)
        assertTrue(GitHubReleaseJson.parseLatest("<html>Not Found</html>") is ReleaseLookup.Unreachable)
        assertTrue(GitHubReleaseJson.parseLatest("""{"tag_name": }""") is ReleaseLookup.Unreachable)
    }
}
