package com.silverphone.app.platform

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hands an https address to whatever app can open it.
 *
 * Returns false instead of throwing when nothing on the device can: a phone without a
 * browser must still show the address as text, not crash on a tap.
 */
fun Context.openWebPage(url: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(intent)
        true
    } catch (failure: Exception) {
        false
    }
}
