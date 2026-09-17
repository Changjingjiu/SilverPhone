package com.silverphone.app.platform.transfer

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The archive manifest is parsed strictly. Every test here is a shape that must be
 * refused rather than absorbed, because guessing at a malformed file could import
 * the wrong people.
 */
class BackupJsonTest {

    private val validManifest = """
        {
          "format": "silverphone-backup",
          "schemaVersion": 1,
          "exportedAt": "2026-09-17T08:00:00Z",
          "appVersion": "1.0.0",
          "contacts": [
            {
              "id": "11111111-1111-4111-8111-111111111111",
              "displayName": "女儿",
              "phoneNumber": "00000000000",
              "sortOrder": 0,
              "placeholderColor": "LIGHT_BLUE",
              "photo": null
            }
          ]
        }
    """.trimIndent()

    @Test
    fun decodesTheDocumentedExample() {
        val manifest = BackupJson.decodeManifest(validManifest)
        assertEquals(BackupProtocol.FORMAT_MARKER, manifest.format)
        assertEquals(1, manifest.schemaVersion)
        assertEquals(1, manifest.contacts.size)
        assertNull(manifest.contacts[0].photo)
    }

    @Test
    fun rejectsAnUnknownField() {
        val text = validManifest.replace(
            "\"appVersion\": \"1.0.0\",",
            "\"appVersion\": \"1.0.0\", \"surprise\": 1,",
        )
        assertThrows(SerializationException::class.java) { BackupJson.decodeManifest(text) }
    }

    @Test
    fun rejectsAMissingPhotoField() {
        // The protocol requires photo to be present and null, so an omitted field
        // is an error rather than an implied "no photo".
        val text = validManifest.replace("\"photo\": null", "\"note\": null")
        assertThrows(SerializationException::class.java) { BackupJson.decodeManifest(text) }
    }

    @Test
    fun rejectsAWrongType() {
        val text = validManifest.replace("\"sortOrder\": 0", "\"sortOrder\": \"first\"")
        assertThrows(SerializationException::class.java) { BackupJson.decodeManifest(text) }
    }

    @Test
    fun rejectsAMissingTopLevelField() {
        val text = validManifest.replace("\"exportedAt\": \"2026-09-17T08:00:00Z\",", "")
        assertThrows(SerializationException::class.java) { BackupJson.decodeManifest(text) }
    }

    @Test
    fun rejectsMalformedJson() {
        assertThrows(SerializationException::class.java) {
            BackupJson.decodeManifest("{ not json at all")
        }
    }

    @Test
    fun encodesPhotoAsExplicitNull() {
        val manifest = BackupManifest(
            format = BackupProtocol.FORMAT_MARKER,
            schemaVersion = BackupProtocol.SCHEMA_VERSION,
            exportedAt = "2026-09-17T08:00:00Z",
            appVersion = "1.0.0",
            contacts = listOf(
                BackupContact(
                    id = "11111111-1111-4111-8111-111111111111",
                    displayName = "女儿",
                    phoneNumber = "00000000000",
                    sortOrder = 0,
                    placeholderColor = "LIGHT_BLUE",
                    photo = null,
                ),
            ),
        )
        val text = BackupJson.encodeManifest(manifest)
        // An omitted field would make the file unreadable by a strict reader.
        assertEquals(true, text.contains("\"photo\":null"))
    }

    @Test
    fun photoPathIsDerivedFromTheId() {
        val id = "11111111-1111-4111-8111-111111111111"
        assertEquals("photos/$id.jpg", BackupProtocol.photoPath(id))
    }

    @Test
    fun onlyTheTwoDocumentedEntryShapesAreAllowed() {
        assertEquals(true, BackupProtocol.isAllowedEntry("manifest.json"))
        assertEquals(true, BackupProtocol.isAllowedEntry("photos/anything.jpg"))
        assertEquals(false, BackupProtocol.isAllowedEntry("photos/"))
        assertEquals(false, BackupProtocol.isAllowedEntry("extra/notes.txt"))
        assertEquals(false, BackupProtocol.isAllowedEntry("photos/nested/deep.jpg"))
    }
}
