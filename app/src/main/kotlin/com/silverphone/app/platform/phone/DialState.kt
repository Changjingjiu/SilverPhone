package com.silverphone.app.platform.phone

/** Where the app is in handling one tap. Never describes the state of a call. */
sealed interface DialState {

    /** Nothing in flight; cards accept taps. */
    data object Ready : DialState

    /** Re-reading the contact and checking the permission. Cards refuse further taps. */
    data object Checking : DialState

    /** A request is on its way to the foreground host. */
    data object Dispatching : DialState

    /** The system dialer accepted it. The app no longer controls anything. */
    data object HandedOff : DialState

    /** The request stopped inside this app. No call was placed. */
    data class Problem(val problem: DialProblem) : DialState

    /** True while taps must be ignored to prevent a second request. */
    val blocksFurtherTaps: Boolean
        get() = this !is Ready
}

/** Reasons a tap can fail before the system takes over. */
enum class DialProblem {
    /** CALL_PHONE is not granted. */
    PermissionMissing,

    /** No activity on the device can place a call. */
    NoPhoneApp,

    /** The system refused to start the call. */
    DispatchFailed,

    /** The contact disappeared or is no longer usable. */
    ContactUnavailable,

    /**
     * The app could not read its own database while handling the tap.
     *
     * Distinct from [ContactUnavailable]: the contact is probably fine and the phone
     * simply failed to read it, so the message tells the family to try again rather
     * than to go and fix the contact.
     */
    StorageUnavailable,
}

/**
 * One accepted tap, carrying the number to dial.
 *
 * [eventId] is unique within the process, which is what lets the host ignore a
 * late result belonging to an earlier tap and lets the app prove that one tap
 * produces exactly one request.
 */
data class DialRequest(
    val eventId: Long,
    val contactId: String,
    val displayName: String,
    val phoneNumber: String,
)
