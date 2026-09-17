package com.silverphone.app.platform.phone

import com.silverphone.app.domain.ContactLookup
import com.silverphone.app.domain.CountryCode
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Turns taps on contact cards into at most one call request each.
 *
 * The lock lives here, in an application-scoped object, rather than in a screen:
 * that is what makes a request survive recomposition, rotation and Activity
 * recreation without being issued twice, while still being discarded on process
 * death (a new process starts with no pending request, as required).
 *
 * The intent itself is not started here. A request is published on [requests] and
 * only the foreground host collects it, so a request cannot be fired from a
 * Composable body or from a state collector that may re-run.
 *
 * All methods are main-thread confined.
 */
class DialCoordinator(
    private val contacts: ContactLookup,
    private val permission: CallPermissionGate,
    private val scope: CoroutineScope,
    /**
     * Required, and must be monotonic.
     *
     * The spacing check subtracts two readings. A wall clock can jump backwards
     * (time sync, timezone, the user changing the date), which would make the
     * difference negative and therefore permanently "too soon" - every tap on every
     * card would be silently ignored. Production passes elapsed real time.
     */
    private val clock: () -> Long,
    /**
     * The dialling prefix to use for numbers stored without one, read at dispatch
     * time so changing it never rewrites a stored contact.
     */
    private val countryCode: suspend () -> String = { "" },
) {

    companion object {
        /**
         * Minimum spacing between two accepted taps. Guards against one gesture
         * arriving twice; it is not shown to the user as a countdown.
         */
        const val MIN_REQUEST_SPACING_MILLIS: Long = 1000L

        /**
         * If no window switch is ever observed, the latch is released after this
         * long anyway. Releasing means "the screen accepts taps again"; it makes
         * no claim about whether the call connected or failed.
         */
        const val LATCH_RELEASE_TIMEOUT_MILLIS: Long = 3000L
    }

    private val _state = MutableStateFlow<DialState>(DialState.Ready)
    val state: StateFlow<DialState> = _state.asStateFlow()

    private val requestChannel = Channel<DialRequest>(Channel.BUFFERED)

    /** Consumed exactly once by the foreground host, while it is started. */
    val requests: Flow<DialRequest> = requestChannel.receiveAsFlow()

    private var nextEventId: Long = 0L

    /**
     * When the last tap was accepted, or null if none has been.
     *
     * A "never" sentinel of Long.MIN_VALUE would overflow the subtraction below and
     * make the very first tap look too soon.
     */
    private var lastAcceptedAt: Long? = null
    private var inFlightEventId: Long? = null
    private var dispatchedEventId: Long? = null
    private var releaseJob: Job? = null

    /**
     * Attempts to start a call. Returns true when this tap was accepted, which
     * happens at most once per gesture.
     *
     * [resumed] marks the second half of a tap that was already accepted: the user
     * pressed a card, the app asked for the call permission, and the answer has just
     * arrived. It skips the spacing check because this is not a second gesture - and
     * without that, answering the system dialog quickly threw the call away, since
     * the resumed request arrived inside the window opened by the original tap.
     */
    fun requestDial(contactId: String, resumed: Boolean = false): Boolean {
        if (inFlightEventId != null) return false
        if (_state.value is DialState.Problem) return false

        val now = clock()
        if (!resumed) {
            val previous = lastAcceptedAt
            if (previous != null && now - previous < MIN_REQUEST_SPACING_MILLIS) return false
        }

        val eventId = ++nextEventId
        inFlightEventId = eventId
        dispatchedEventId = null
        lastAcceptedAt = now
        _state.value = DialState.Checking

        scope.launch {
            // Nothing may escape this block. An uncaught failure here would both kill
            // the process (this scope has no other handler) and leave the state at
            // Checking, which disables every card with no way back: the one screen the
            // app exists for would be dead until the app was restarted.
            try {
                val contact = contacts.findContact(contactId)
                if (inFlightEventId != eventId) return@launch

                if (contact == null) {
                    enterProblem(eventId, DialProblem.ContactUnavailable)
                    return@launch
                }
                if (!permission.isGranted()) {
                    enterProblem(eventId, DialProblem.PermissionMissing)
                    return@launch
                }

                _state.value = DialState.Dispatching
                requestChannel.send(
                    DialRequest(
                        eventId = eventId,
                        contactId = contact.id,
                        displayName = contact.displayName,
                        // The prefix is applied here, not on save: the stored number
                        // stays exactly as the family typed it. If even the prefix
                        // cannot be read, dial exactly what is stored rather than
                        // guessing a country.
                        phoneNumber = CountryCode.apply(contact.phoneNumber, readCountryCode()),
                    ),
                )
                scheduleLatchRelease(eventId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                enterProblem(eventId, DialProblem.StorageUnavailable)
            }
        }
        return true
    }

    /**
     * Called by the foreground host just before it starts the call.
     *
     * Returns true only for the first claim of the current request. A request that
     * was already handed to the launcher can never be launched again, which is what
     * makes it safe for the host to restart its collection after an Activity
     * recreation or after returning from the system dialer.
     */
    fun claimForDispatch(eventId: Long): Boolean {
        if (inFlightEventId != eventId) return false
        if (dispatchedEventId == eventId) return false
        dispatchedEventId = eventId
        return true
    }

    /** Reports what the foreground host got back from the system. */
    fun onLaunchOutcome(eventId: Long, outcome: LaunchOutcome) {
        if (inFlightEventId != eventId) return // a late result from an earlier tap
        when (outcome) {
            LaunchOutcome.HandedOff -> {
                _state.value = DialState.HandedOff
                scheduleLatchRelease(eventId)
            }

            LaunchOutcome.NoPhoneApp -> enterProblem(eventId, DialProblem.NoPhoneApp)
            LaunchOutcome.PermissionDenied -> enterProblem(eventId, DialProblem.PermissionMissing)
            is LaunchOutcome.Failed -> enterProblem(eventId, DialProblem.DispatchFailed)
        }
    }

    /**
     * Called when the host returns to the foreground. Returning from the system
     * dialer re-enables the cards; it never re-issues the previous request.
     */
    fun onHostResumed() {
        val eventId = inFlightEventId ?: return
        val state = _state.value
        if (state !is DialState.HandedOff && state !is DialState.Dispatching) return
        // Honour the minimum spacing even if the user bounced back immediately.
        val previous = lastAcceptedAt ?: return
        if (clock() - previous < MIN_REQUEST_SPACING_MILLIS) return
        releaseLatch(eventId)
    }

    /** Dismisses the help screen and re-enables the cards. */
    fun acknowledgeProblem() {
        if (_state.value is DialState.Problem) {
            _state.value = DialState.Ready
        }
    }

    /** The prefix if it can be read; "add nothing" if the database cannot be read. */
    private suspend fun readCountryCode(): String = try {
        countryCode()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        ""
    }

    private fun scheduleLatchRelease(eventId: Long) {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(LATCH_RELEASE_TIMEOUT_MILLIS)
            releaseLatch(eventId)
        }
    }

    private fun releaseLatch(eventId: Long) {
        if (inFlightEventId != eventId) return
        releaseJob?.cancel()
        releaseJob = null
        inFlightEventId = null
        _state.value = DialState.Ready
    }

    private fun enterProblem(eventId: Long, problem: DialProblem) {
        if (inFlightEventId != eventId) return
        releaseJob?.cancel()
        releaseJob = null
        // The latch is opened here: the request is over, and a failed attempt must
        // not leave the home screen permanently unable to accept a tap.
        inFlightEventId = null
        _state.value = DialState.Problem(problem)
    }
}
