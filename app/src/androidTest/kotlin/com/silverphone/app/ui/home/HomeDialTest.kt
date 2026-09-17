package com.silverphone.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.silverphone.app.R
import com.silverphone.app.domain.Contact
import com.silverphone.app.domain.ContactLookup
import com.silverphone.app.domain.FontPreset
import com.silverphone.app.domain.PlaceholderColor
import com.silverphone.app.platform.phone.CallPermissionGate
import com.silverphone.app.platform.phone.DialCoordinator
import com.silverphone.app.platform.phone.DialProblem
import com.silverphone.app.platform.phone.DialState
import com.silverphone.app.platform.phone.LaunchOutcome
import com.silverphone.app.ui.theme.SilverPhoneTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The home screen's side of the dial contract, driven through the real Compose
 * tree: one card is one accessibility node, one tap produces exactly one request,
 * and while a request is in flight the cards accept nothing at all.
 *
 * The launcher is a recording double, so no test here can place a real call.
 *
 * Every expected label is read back from the app's own resources rather than typed
 * as a literal: the interface ships in English and Chinese and the reader may have
 * either, so a hard-coded string would make these tests assert the test device's
 * locale instead of the screen's behaviour.
 */
@RunWith(AndroidJUnit4::class)
class HomeDialTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    private val contact = Contact(
        id = "contact-1",
        displayName = "女儿",
        phoneNumber = "13800138000",
        sortOrder = 0,
        placeholderColor = PlaceholderColor.LIGHT_BLUE,
        photoSha256 = null,
    )

    /**
     * Records what the host would have dialled.
     *
     * The real [PhoneLauncher] needs an Activity context, so the host is what calls
     * it; this double stands in for the host and only records the number.
     */
    private class RecordingLauncher : CallPermissionGate {
        val dialed = mutableListOf<String>()

        override fun isGranted(): Boolean = true

        fun record(phoneNumber: String): LaunchOutcome {
            dialed.add(phoneNumber)
            return LaunchOutcome.HandedOff
        }
    }

    private fun stateWith(
        dialState: DialState,
        awaitingPermission: Boolean = false,
    ) = HomeUiState(
        load = ContactsLoad.Loaded(listOf(contact)),
        dialState = dialState,
        awaitingPermission = awaitingPermission,
    )

    @Test
    fun onlyTheCallButtonDials() {
        composeRule.setContent {
            SilverPhoneTheme(fontPreset = FontPreset.STANDARD) {
                HomeScreen(
                    state = stateWith(DialState.Ready),
                    onContactTap = {},
                    onFamilyEntry = {},
                    onRetry = {},
                    onProblemAcknowledged = {},
                    onOpenFamilySettings = {},
                )
            }
        }

        // The green block is the single dialling control, and it is labelled.
        composeRule.onAllNodesWithContentDescription(text(R.string.home_call_semantics, "女儿")).assertCountEquals(1)
        composeRule.onNodeWithContentDescription(text(R.string.home_call_semantics, "女儿")).assertIsEnabled()
        composeRule.onNodeWithContentDescription(text(R.string.home_call_semantics, "女儿")).assertHasClickAction()

        // The name is readable - a screen reader should say who this is before it
        // reaches the button - but it must not itself place a call. The photo is the
        // largest thing on the card, and a whole-card target meant a resting hand or
        // a scroll that ended on a card could dial.
        composeRule.onNodeWithText("女儿").assertExists()
        composeRule.onNodeWithText("女儿").assertHasNoClickAction()

        // Nothing else on the card is tappable either: one card, one call target, so
        // there is no large inert-looking area that a resting palm can dial from.
        composeRule.onNodeWithTag(cardTag(contact.id)).onChildren()
            .filter(hasClickAction())
            .assertCountEquals(1)
    }

    @Test
    fun theCallButtonIsBigEnoughToPress() {
        composeRule.setContent {
            SilverPhoneTheme(fontPreset = FontPreset.STANDARD) {
                HomeScreen(
                    state = stateWith(DialState.Ready),
                    onContactTap = {},
                    onFamilyEntry = {},
                    onRetry = {},
                    onProblemAcknowledged = {},
                    onOpenFamilySettings = {},
                )
            }
        }

        val bounds = composeRule.onNodeWithContentDescription(text(R.string.home_call_semantics, "女儿"))
            .fetchSemanticsNode()
            .boundsInRoot
        val density = composeRule.density
        // The product rule is at least 64 dp tall, and it takes the whole width of
        // its card. The width floor here is deliberately modest: the home screen is a
        // two-column grid, so on a small phone each card - and therefore each button -
        // is only about 140 dp wide. What matters is that it is a wide, tall block,
        // not a strip.
        val minHeightPx = with(density) { 64.dp.toPx() }
        val minWidthPx = with(density) { 120.dp.toPx() }
        assertTrue(
            "call button too short: ${bounds.height / density.density} dp",
            bounds.height >= minHeightPx,
        )
        assertTrue(
            "call button too narrow: ${bounds.width / density.density} dp",
            bounds.width >= minWidthPx,
        )
    }

    @Test
    fun oneTapOnTheCallButtonProducesExactlyOneCallRequest() {
        val launcher = RecordingLauncher()
        val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        val coordinator = DialCoordinator(
            contacts = ContactLookup { id -> contact.takeIf { it.id == id } },
            permission = launcher,
            scope = scope,
            // Monotonic, as production uses: the spacing check subtracts readings.
            clock = android.os.SystemClock::elapsedRealtime,
        )
        // Stands in for MainActivity's host loop.
        scope.launch {
            coordinator.requests.collect { request ->
                if (coordinator.claimForDispatch(request.eventId)) {
                    coordinator.onLaunchOutcome(
                        request.eventId,
                        launcher.record(request.phoneNumber),
                    )
                }
            }
        }

        composeRule.setContent {
            // Collected as state rather than read via .value, so the tree sees the
            // same transitions the real screen does.
            val dialState by coordinator.state.collectAsStateWithLifecycle()
            SilverPhoneTheme(fontPreset = FontPreset.DEFAULT) {
                HomeScreen(
                    state = stateWith(dialState),
                    onContactTap = { tapped -> coordinator.requestDial(tapped.id) },
                    onFamilyEntry = {},
                    onRetry = {},
                    onProblemAcknowledged = {},
                    onOpenFamilySettings = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(text(R.string.home_call_semantics, "女儿")).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { launcher.dialed.isNotEmpty() }

        assertEquals(listOf("13800138000"), launcher.dialed)
    }

    @Test
    fun whileARequestIsInFlightTheCardsAcceptNoFurtherTaps() {
        var taps = 0
        composeRule.setContent {
            SilverPhoneTheme(fontPreset = FontPreset.DEFAULT) {
                HomeScreen(
                    // Exactly the state the screen is in between an accepted tap
                    // and the system dialer taking over.
                    state = stateWith(DialState.Dispatching),
                    onContactTap = { taps++ },
                    onFamilyEntry = {},
                    onRetry = {},
                    onProblemAcknowledged = {},
                    onOpenFamilySettings = {},
                )
            }
        }

        // A disabled card has no click action, which is the second line of defence
        // behind the dial lock itself.
        composeRule.onNodeWithContentDescription(text(R.string.home_call_semantics, "女儿")).assertIsNotEnabled()
        assertEquals(0, taps)
    }

    @Test
    fun theHelpOverlayWaitsUntilThePermissionDialogHasBeenAnswered() {
        val problem = DialState.Problem(DialProblem.PermissionMissing)

        composeRule.setContent {
            SilverPhoneTheme(fontPreset = FontPreset.DEFAULT) {
                HomeScreen(
                    // The dialog is up: the help page behind it must not be drawn yet,
                    // because help under a permission dialog reads as though the tap was
                    // already refused.
                    state = stateWith(problem, awaitingPermission = true),
                    onContactTap = {},
                    onFamilyEntry = {},
                    onRetry = {},
                    onProblemAcknowledged = {},
                    onOpenFamilySettings = {},
                )
            }
        }

        composeRule.onAllNodesWithText(text(R.string.help_no_permission)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.help_usage_title)).assertCountEquals(0)
    }

    @Test
    fun theHelpOverlayAppearsOnceNothingIsWaitingOnTheDialog() {
        val problem = DialState.Problem(DialProblem.PermissionMissing)

        composeRule.setContent {
            SilverPhoneTheme(fontPreset = FontPreset.DEFAULT) {
                HomeScreen(
                    // The same problem with nothing waiting on the dialog, which is the
                    // state after the family member says no: now the help is the point.
                    state = stateWith(problem, awaitingPermission = false),
                    onContactTap = {},
                    onFamilyEntry = {},
                    onRetry = {},
                    onProblemAcknowledged = {},
                    onOpenFamilySettings = {},
                )
            }
        }

        composeRule.onAllNodesWithText(text(R.string.help_no_permission)).assertCountEquals(1)
    }

    @Test
    fun theEmptyStateOffersOnlyTheFamilyEntry() {
        composeRule.setContent {
            SilverPhoneTheme(fontPreset = FontPreset.DEFAULT) {
                HomeScreen(
                    state = HomeUiState(load = ContactsLoad.Loaded(emptyList())),
                    onContactTap = {},
                    onFamilyEntry = {},
                    onRetry = {},
                    onProblemAcknowledged = {},
                    onOpenFamilySettings = {},
                )
            }
        }

        // No invented relative, and a way for the family to configure the app.
        composeRule.onAllNodesWithContentDescription(text(R.string.home_call_semantics, "女儿")).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.home_empty_title)).assertCountEquals(1)
    }

    @Test
    fun aReadFailureIsShownAsAFailureNotAsAnEmptyList() {
        composeRule.setContent {
            SilverPhoneTheme(fontPreset = FontPreset.DEFAULT) {
                HomeScreen(
                    state = HomeUiState(load = ContactsLoad.Failed),
                    onContactTap = {},
                    onFamilyEntry = {},
                    onRetry = {},
                    onProblemAcknowledged = {},
                    onOpenFamilySettings = {},
                )
            }
        }

        composeRule.onAllNodesWithText(text(R.string.home_load_failed)).assertCountEquals(1)
        // Crucially, it must not suggest that the contacts are simply gone.
        composeRule.onAllNodesWithText(text(R.string.home_empty_title)).assertCountEquals(0)
    }
}
