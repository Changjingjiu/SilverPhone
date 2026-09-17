package com.silverphone.app.platform.transfer

import kotlinx.serialization.json.Json

/**
 * Strict JSON for the exchange format.
 *
 * Every leniency is switched off on purpose: unknown keys, malformed values and
 * missing fields all fail rather than being absorbed. Note that a successful
 * parse still proves nothing about the *meaning* of the values - the business
 * checks in [BackupReader] run afterwards and are what actually decide whether an
 * archive is acceptable.
 */
object BackupJson {

    val format: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        encodeDefaults = true
        explicitNulls = true
    }

    fun encodeManifest(manifest: BackupManifest): String =
        format.encodeToString(BackupManifest.serializer(), manifest)

    fun decodeManifest(text: String): BackupManifest =
        format.decodeFromString(BackupManifest.serializer(), text)
}
