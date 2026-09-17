package com.silverphone.app.platform.phone

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.silverphone.app.platform.hasCallPermission

/**
 * Places calls through the platform dialer using ACTION_CALL.
 *
 * ACTION_CALL is what makes a single tap actually start the call. ACTION_DIAL
 * would only open the dialer with the number typed in, which does not meet the
 * product requirement and is never used as a substitute.
 *
 * This app is not a dialer: it does not take the phone role, does not implement
 * InCallService, and does not draw any in-call controls.
 */
class AndroidPhoneLauncher(context: Context) : PhoneLauncher, CallPermissionGate {

    /** Only used to ask the platform about the permission; never to start the call. */
    private val appContext: Context = context.applicationContext

    override fun isGranted(): Boolean = appContext.hasCallPermission()

    override fun launch(host: Context, phoneNumber: String): LaunchOutcome {
        // The number was normalised by PhoneNumberRules before it reached this
        // point, so it contains only digits and at most one leading '+'.
        val uri: Uri = Uri.fromParts("tel", phoneNumber, null)
        val intent = Intent(Intent.ACTION_CALL, uri)

        // Only an Activity may start this without a new task. The normal path
        // passes the foreground Activity; the flag covers any other caller, and
        // without it Android throws and the failure would look like a broken
        // phone app rather than a broken call site.
        if (host !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            host.startActivity(intent)
            LaunchOutcome.HandedOff
        } catch (notFound: ActivityNotFoundException) {
            LaunchOutcome.NoPhoneApp
        } catch (denied: SecurityException) {
            LaunchOutcome.PermissionDenied
        } catch (failure: Exception) {
            LaunchOutcome.Failed(failure)
        }
    }
}
