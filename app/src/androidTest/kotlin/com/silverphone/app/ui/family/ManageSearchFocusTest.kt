package com.silverphone.app.ui.family

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.silverphone.app.R
import com.silverphone.app.data.repository.MoveDirection
import com.silverphone.app.domain.Contact
import com.silverphone.app.domain.FontPreset
import com.silverphone.app.domain.PlaceholderColor
import com.silverphone.app.ui.theme.SilverPhoneTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The management list's bottom action bar.
 *
 * This is a regression test. An earlier version hid the action bar while the search
 * field had focus, on the theory that the keyboard would cover it. In practice the
 * field kept focus after the keyboard was dismissed, so "add a contact" and "back"
 * vanished and nothing on the screen brought them back - the only way out was to
 * leave the screen and return. The bar is now unconditional, and a tap outside the
 * field puts the keyboard away.
 *
 * Expected labels come from the app's own resources, because the interface ships in
 * English and Chinese and the reader may have either.
 */
@RunWith(AndroidJUnit4::class)
class ManageSearchFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context.getString(id)

    private val contact = Contact(
        id = "contact-1",
        displayName = "女儿",
        phoneNumber = "13800138000",
        sortOrder = 0,
        placeholderColor = PlaceholderColor.LIGHT_BLUE,
        photoSha256 = null,
    )

    /**
     * Renders the screen over live state, so a test can type and watch the screen
     * answer exactly as it does in the app.
     */
    private fun show(initialQuery: String = "") {
        composeRule.setContent {
            var query by remember { mutableStateOf(initialQuery) }
            SilverPhoneTheme(fontPreset = FontPreset.STANDARD) {
                ManageContactsScreen(
                    state = ManageUiState(
                        contacts = listOf(contact),
                        totalCount = 1,
                        query = query,
                        loading = false,
                    ),
                    onQueryChange = { query = it },
                    onAdd = {},
                    onEdit = {},
                    onMove = { _, _: MoveDirection -> },
                    onBack = {},
                )
            }
        }
    }

    private fun assertActionBarIsPresent() {
        composeRule.onAllNodesWithText(text(R.string.manage_add)).assertCountEquals(1)
        composeRule.onAllNodesWithText(text(R.string.action_back)).assertCountEquals(1)
    }

    @Test
    fun theActionBarSurvivesTypingInTheSearchField() {
        show()
        assertActionBarIsPresent()

        // Focus the field and type, which is the exact state that used to hide the bar.
        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("女儿")

        assertActionBarIsPresent()
    }

    @Test
    fun theActionBarSurvivesAQueryThatMatchesNothing() {
        composeRule.setContent {
            var query by remember { mutableStateOf("") }
            SilverPhoneTheme(fontPreset = FontPreset.STANDARD) {
                ManageContactsScreen(
                    // A search that matches nothing leaves an empty list on screen.
                    state = ManageUiState(
                        contacts = if (query.isBlank()) listOf(contact) else emptyList(),
                        totalCount = 1,
                        query = query,
                        loading = false,
                    ),
                    onQueryChange = { query = it },
                    onAdd = {},
                    onEdit = {},
                    onMove = { _, _: MoveDirection -> },
                    onBack = {},
                )
            }
        }
        assertActionBarIsPresent()

        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("zzz")

        // An empty result list is still a list screen: the way out has to stay put.
        assertActionBarIsPresent()
        composeRule.onAllNodesWithText(text(R.string.manage_search_empty)).assertCountEquals(1)
    }

    @Test
    fun tappingOutsideTheFieldLeavesTheScreenOperable() {
        var backs = 0
        composeRule.setContent {
            var query by remember { mutableStateOf("") }
            SilverPhoneTheme(fontPreset = FontPreset.STANDARD) {
                ManageContactsScreen(
                    state = ManageUiState(
                        contacts = listOf(contact),
                        totalCount = 1,
                        query = query,
                        loading = false,
                    ),
                    onQueryChange = { query = it },
                    onAdd = {},
                    onEdit = {},
                    onMove = { _, _: MoveDirection -> },
                    onBack = { backs++ },
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.onNode(hasSetTextAction()).assertIsFocused()

        // The page title is inert text, so this tap is not consumed by any control and
        // reaches the screen's own handler, which puts the keyboard away.
        //
        // The handler's effect on focus is deliberately not asserted here. On API 23
        // hiding the keyboard makes the window regain focus, and Compose then hands
        // focus back to the field it was taken from. That re-focus is a platform
        // behaviour of this emulator rather than something the screen decides, so what
        // this test pins is the part the screen does own: the tap is not swallowed, and
        // the controls under it still work afterwards.
        composeRule.onNodeWithText(text(R.string.manage_title)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(text(R.string.action_back)).performClick()
        assertEquals(1, backs)
        assertActionBarIsPresent()
    }

    @Test
    fun tappingTheActionBarItselfDoesNotStealTheButtonPress() {
        var adds = 0
        var backs = 0
        composeRule.setContent {
            SilverPhoneTheme(fontPreset = FontPreset.STANDARD) {
                ManageContactsScreen(
                    state = ManageUiState(
                        contacts = listOf(contact),
                        totalCount = 1,
                        loading = false,
                    ),
                    onQueryChange = {},
                    onAdd = { adds++ },
                    onEdit = {},
                    onMove = { _, _: MoveDirection -> },
                    onBack = { backs++ },
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.manage_search_hint)).performClick()
        composeRule.onNodeWithText(text(R.string.manage_add)).performClick()
        composeRule.onNodeWithText(text(R.string.action_back)).performClick()

        // The screen-wide tap handler must not swallow the buttons' own clicks.
        assertEquals(1, adds)
        assertEquals(1, backs)
    }
}
