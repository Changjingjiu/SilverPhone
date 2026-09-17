package com.silverphone.app.domain

import java.security.MessageDigest

/**
 * SHA-256, rendered as 64 lowercase hex characters.
 *
 * Written by hand rather than with java.util.Base64 or HexFormat, both of which
 * are unavailable at API 23. Used for the photo cache key and for the archive
 * integrity check; it detects corruption, and is not a signature.
 */
object Sha256 {

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    fun hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val out = CharArray(digest.size * 2)
        for (index in digest.indices) {
            val value = digest[index].toInt() and 0xFF
            out[index * 2] = HEX_DIGITS[value ushr 4]
            out[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
        }
        return String(out)
    }
}
