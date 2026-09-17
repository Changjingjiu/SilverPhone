package com.silverphone.app.platform.phone

import com.silverphone.app.domain.Contact
import com.silverphone.app.domain.ContactLookup
import com.silverphone.app.domain.PlaceholderColor
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dial lock.
 *
 * These are the highest-risk behaviours in the product: a duplicated request calls
 * someone twice, and a replayed request calls them again when the elderly user
 * only wanted to look. The coordinator is tested with a recording launcher, so no
 * automated test can ever place a real call.
 */
class DialCoordinatorTest {

    private val contact = Contact(
        id = "contact-1",
        displayName = "女儿",
        phoneNumber = "13800138000",
        sortOrder = 0,
        placeholderColor = PlaceholderColor.DEFAULT,
        photoSha256 = null,
    )

    private val lookup = ContactLookup { id -> contact.takeIf { it.id == id } }

    /**
     * Stands in for the foreground host.
     *
     * In the real app the host is what calls [PhoneLauncher], because starting an
     * Activity needs an Activity context. Here it records the number instead, and
     * also answers the permission probe the dial lock consults.
     */
    private class RecordingHost(
        var permissionGranted: Boolean = true,
        var outcome: LaunchOutcome = LaunchOutcome.HandedOff,
    ) : CallPermissionGate {
        val dialed = mutableListOf<String>()

        override fun isGranted(): Boolean = permissionGranted

        fun record(phoneNumber: String): LaunchOutcome {
            dialed.add(phoneNumber)
            return outcome
        }
    }

    /** Consumes each request at most once, as MainActivity's host loop does. */
    private fun TestScope.attachHost(coordinator: DialCoordinator, host: RecordingHost) {
        backgroundScope.launch {
            coordinator.requests.collect { request ->
                if (coordinator.claimForDispatch(request.eventId)) {
                    coordinator.onLaunchOutcome(
                        request.eventId,
                        host.record(request.phoneNumber),
                    )
                }
            }
        }
    }

    private fun TestScope.coordinator(
        host: RecordingHost,
        clock: () -> Long,
    ): DialCoordinator = DialCoordinator(
        contacts = lookup,
        permission = host,
        scope = backgroundScope,
        clock = clock,
    )

    @Test
    fun oneTapProducesExactlyOneRequest() = runTest {
        val launcher = RecordingHost()
        val coordinator = coordinator(launcher) { 0L }
        attachHost(coordinator, launcher)

        assertTrue(coordinator.requestDial(contact.id))
        runCurrent()

        assertEquals(listOf("13800138000"), launcher.dialed)
    }

    @Test
    fun tenRapidTapsStillProduceOneRequest() = runTest {
        val launcher = RecordingHost()
        var now = 0L
        val coordinator = coordinator(launcher) { now }
        attachHost(coordinator, launcher)

        val accepted = (1..10).count { coordinator.requestDial(contact.id) }
        runCurrent()

        assertEquals(1, accepted)
        assertEquals(1, launcher.dialed.size)
    }

    @Test
    fun tappingADifferentContactWhileInFlightIsIgnored() = runTest {
        val launcher = RecordingHost()
        val coordinator = coordinator(launcher) { 0L }
        attachHost(coordinator, launcher)

        assertTrue(coordinator.requestDial(contact.id))
        runCurrent()
        // The second tap names an unknown contact, but it must be refused by the
        // lock before the lookup even matters.
        assertFalse(coordinator.requestDial("another-contact"))
        runCurrent()

        assertEquals(1, launcher.dialed.size)
    }

    @Test
    fun consentIsReleasedAfterTheTimeoutAndOnlyOneFurtherCallHappens() = runTest {
        val launcher = RecordingHost()
        var now = 0L
        val coordinator = coordinator(launcher) { now }
        attachHost(coordinator, launcher)

        coordinator.requestDial(contact.id)
        runCurrent()
        assertEquals(1, launcher.dialed.size)

        // Still locked inside the minimum spacing window.
        now = 500L
        assertFalse(coordinator.requestDial(contact.id))

        // After the release timeout the cards accept taps again.
        now = 4_000L
        advanceTimeBy(DialCoordinator.LATCH_RELEASE_TIMEOUT_MILLIS + 100)
        runCurrent()
        assertEquals(DialState.Ready, coordinator.state.value)

        assertTrue(coordinator.requestDial(contact.id))
        runCurrent()
        assertEquals(2, launcher.dialed.size)
    }

    @Test
    fun returningToTheForegroundReleasesTheLatchWithoutRedialling() = runTest {
        val launcher = RecordingHost()
        var now = 0L
        val coordinator = coordinator(launcher) { now }
        attachHost(coordinator, launcher)

        coordinator.requestDial(contact.id)
        runCurrent()

        now = 2_000L
        coordinator.onHostResumed()
        runCurrent()

        assertEquals(DialState.Ready, coordinator.state.value)
        assertEquals(1, launcher.dialed.size)
    }

    @Test
    fun aRequestIsNeverLaunchedTwiceEvenIfTheHostReclaimsIt() = runTest {
        val launcher = RecordingHost()
        var now = 0L
        val coordinator = coordinator(launcher) { now }

        coordinator.requestDial(contact.id)
        runCurrent()

        // Simulates the host collecting the same request again, as it would after
        // an Activity recreation with the request still in the buffer.
        val request = DialRequest(
            eventId = 1L,
            contactId = contact.id,
            displayName = contact.displayName,
            phoneNumber = contact.phoneNumber,
        )
        assertTrue(coordinator.claimForDispatch(request.eventId))
        assertFalse(coordinator.claimForDispatch(request.eventId))
    }

    @Test
    fun missingPermissionStopsTheRequestAndOffersHelp() = runTest {
        val launcher = RecordingHost(permissionGranted = false)
        val coordinator = coordinator(launcher) { 0L }
        attachHost(coordinator, launcher)

        assertTrue(coordinator.requestDial(contact.id))
        runCurrent()

        assertEquals(emptyList<String>(), launcher.dialed)
        assertEquals(DialState.Problem(DialProblem.PermissionMissing), coordinator.state.value)
    }

    @Test
    fun grantingPermissionDoesNotDialOnItsOwn() = runTest {
        val launcher = RecordingHost(permissionGranted = false)
        var now = 0L
        val coordinator = coordinator(launcher) { now }
        attachHost(coordinator, launcher)

        coordinator.requestDial(contact.id)
        runCurrent()

        // The family turns the permission on elsewhere; nothing may be replayed.
        launcher.permissionGranted = true
        now = 10_000L
        coordinator.acknowledgeProblem()
        runCurrent()

        assertEquals(emptyList<String>(), launcher.dialed)
        assertEquals(DialState.Ready, coordinator.state.value)
    }

    @Test
    fun aFailedDispatchReleasesTheLockSoTheHomeScreenIsNotStuck() = runTest {
        val launcher = RecordingHost(outcome = LaunchOutcome.NoPhoneApp)
        var now = 0L
        val coordinator = coordinator(launcher) { now }
        attachHost(coordinator, launcher)

        coordinator.requestDial(contact.id)
        runCurrent()

        assertEquals(DialState.Problem(DialProblem.NoPhoneApp), coordinator.state.value)

        now = 10_000L
        coordinator.acknowledgeProblem()
        assertTrue(coordinator.requestDial(contact.id))
        runCurrent()
    }

    @Test
    fun aDeletedContactDoesNotDial() = runTest {
        val launcher = RecordingHost()
        val missing = DialCoordinator(
            contacts = ContactLookup { null },
            permission = launcher,
            scope = backgroundScope,
            clock = { 0L },
        )
        attachHost(missing, launcher)

        missing.requestDial(contact.id)
        runCurrent()

        assertEquals(emptyList<String>(), launcher.dialed)
        assertEquals(DialState.Problem(DialProblem.ContactUnavailable), missing.state.value)
    }

    @Test
    fun aLateOutcomeForAnEarlierTapIsIgnored() = runTest {
        val launcher = RecordingHost()
        var now = 0L
        val coordinator = coordinator(launcher) { now }
        attachHost(coordinator, launcher)

        coordinator.requestDial(contact.id)
        runCurrent()
        assertEquals(DialState.HandedOff, coordinator.state.value)

        // An outcome that belongs to no current request must not change the state.
        coordinator.acknowledgeProblem()
        now = 10_000L
        coordinator.onLaunchOutcome(eventId = 999L, outcome = LaunchOutcome.NoPhoneApp)
        assertEquals(DialState.HandedOff, coordinator.state.value)
    }
}
