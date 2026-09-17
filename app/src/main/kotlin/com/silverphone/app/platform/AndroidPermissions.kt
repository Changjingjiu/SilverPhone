package com.silverphone.app.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * The two runtime permissions this app uses, always read from the platform.
 *
 * Nothing here is cached in storage: the user or the system can change either one
 * at any moment, and a remembered flag would then be a lie.
 */
fun Context.hasCallPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) ==
        PackageManager.PERMISSION_GRANTED

fun Context.hasReadContactsPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Opens this app's own system settings page, where a denied permission can be
 * changed. Used only after a denial, never in a loop.
 */
fun Context.openAppSettingsPage() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (failure: Exception) {
        // The family can still reach the same page from system settings manually.
    }
}
