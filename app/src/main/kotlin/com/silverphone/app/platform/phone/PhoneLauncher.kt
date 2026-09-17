package com.silverphone.app.platform.phone

import android.content.Context

/**
 * Answers whether the app is currently allowed to place a call.
 *
 * Narrow on purpose: the dial lock only needs this yes/no, so it never has to hold
 * an Android Context and stays testable as plain JVM code.
 */
fun interface CallPermissionGate {
    fun isGranted(): Boolean
}

/**
 * The only place in the app that is allowed to start a phone call.
 *
 * Kept behind an interface so tests can record calls instead of placing them: no
 * automated test in this project may dial a real number.
 */
interface PhoneLauncher {

    /**
     * Hands a validated number to the system dialer via ACTION_CALL.
     *
     * [host] must be the foreground Activity, not an application context: starting
     * an Activity from a non-Activity context requires FLAG_ACTIVITY_NEW_TASK and
     * otherwise throws, which would surface as "the phone did not open" for a reason
     * that has nothing to do with the call.
     *
     * Never throws: every failure mode is mapped onto [LaunchOutcome] so the caller
     * can show a readable message.
     */
    fun launch(host: Context, phoneNumber: String): LaunchOutcome
}

/** What happened when we tried to hand the call to the system. */
sealed interface LaunchOutcome {

    /** The system dialer accepted the request. Says nothing about the call itself. */
    data object HandedOff : LaunchOutcome

    /** No activity on this device can place a call. */
    data object NoPhoneApp : LaunchOutcome

    /** CALL_PHONE was revoked between the check and the launch. */
    data object PermissionDenied : LaunchOutcome

    /** The system refused to start the call for some other reason. */
    data class Failed(val cause: Throwable) : LaunchOutcome
}
