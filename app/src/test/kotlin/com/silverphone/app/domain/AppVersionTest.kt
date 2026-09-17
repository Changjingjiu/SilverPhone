package com.silverphone.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The comparison that decides whether a family member is told about a new version.
 *
 * Getting it wrong is not cosmetic: text comparison says "1.10.0" is older than
 * "1.9.0", which would offer a stale download, and treating a debug build's suffix as
 * a higher version would tell every test build that it is already current.
 */
class AppVersionTest {

    private fun version(raw: String): AppVersion =
        requireNotNull(AppVersion.parse(raw)) { "expected $raw to parse" }

    @Test
    fun `reads a plain version`() {
        assertEquals(AppVersion(listOf(1, 2, 3), preRelease = null), version("1.2.3"))
    }

    @Test
    fun `reads a tag with a leading v`() {
        assertEquals(AppVersion(listOf(1, 2, 3), preRelease = null), version("v1.2.3"))
        assertEquals(AppVersion(listOf(1, 2, 3), preRelease = null), version("V1.2.3"))
    }

    @Test
    fun `reads the suffix a debug build carries`() {
        assertEquals(AppVersion(listOf(1, 0, 0), preRelease = "debug"), version("1.0.0-debug"))
        assertEquals(AppVersion(listOf(1, 0, 0), preRelease = "rc1"), version("v1.0.0-rc1"))
    }

    @Test
    fun `rejects anything that is not a version`() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("v"))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("1.x.0"))
        assertNull(AppVersion.parse("1.2.3.4.5"))
        assertNull(AppVersion.parse("-debug"))
    }

    @Test
    fun `equal versions compare equal however they are written`() {
        assertEquals(0, version("1.0.0").compareTo(version("v1.0.0")))
        assertEquals(0, version("1.2").compareTo(version("1.2.0")))
    }

    @Test
    fun `ten is newer than nine, which text comparison gets backwards`() {
        assertTrue(version("1.10.0") > version("1.9.0"))
        assertTrue(version("v2.0.0") > version("v1.99.99"))
    }

    @Test
    fun `a pre-release of a number is older than the release itself`() {
        assertTrue(version("1.0.0") > version("1.0.0-debug"))
        assertTrue(version("v2.1.0") > version("2.1.0-rc1"))
    }

    @Test
    fun `an older published tag is not an update`() {
        assertTrue(version("1.0.0") >= version("v1.0.0"))
        assertTrue(version("1.0.0") > version("v0.9.0"))
    }
}
