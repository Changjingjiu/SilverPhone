package com.silverphone.app.ui.family

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
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
 * Multi-select on the management list.
 *
 * A long press has to start a selection, a tap has to add to it, and the delete has to
 * ask before it runs. This is the one place in the app where a gesture removes data, so
 * the behaviour is pinned here rather than left to the screen's own judgement.
 */
@RunWith(AndroidJUnit4::class)
class ManageSelectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    private val contacts = listOf(
        Contact(
            id = "c1",
            displayName = "女儿",
            phoneNumber = "13800138000",
            sortOrder = 0,
            placeholderColor = PlaceholderColor.LIGHT_BLUE,
            photoSha256 = null,
        ),
        Contact(
            id = "c2",
            displayName = "儿子",
            phoneNumber = "13900139000",
            sortOrder = 1,
            placeholderColor = PlaceholderColor.LIGHT_AMBER,
            photoSha256 = null,
        ),
    )

    private var deleted = 0

    private fun show() {
        composeRule.setContent {
            var selected by remember { mutableStateOf(emptySet<String>()) }
            SilverPhoneTheme(fontPreset = FontPreset.STANDARD) {
                ManageContactsScreen(
                    state = ManageUiState(
                        contacts = contacts,
                        totalCount = contacts.size,
                        loading = false,
                        selected = selected,
                    ),
                    onQueryChange = {},
                    onAdd = {},
                    onEdit = {},
                    onMove = { _, _: MoveDirection -> },
                    onLongPress = { id -> selected = selected + id },
                    onToggleSelected = { id ->
                        selected = if (id in selected) selected - id else selected + id
                    },
                    onClearSelection = { selected = emptySet() },
                    onDeleteSelected = { deleted++ },
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun aLongPressStartsASelectionAndShowsTheDeleteAction() {
        show()

        // Nothing is selected to begin with, and the hint says how to start.
        composeRule.onAllNodesWithText(text(R.string.manage_long_press_hint)).assertCountEquals(1)
        composeRule.onAllNodesWithText(text(R.string.manage_delete_selected)).assertCountEquals(0)

        composeRule.onNodeWithText("女儿").performTouchInput { longClick() }

        composeRule.onNodeWithText(text(R.string.manage_selected_count, 1)).assertExists()
        composeRule.onNodeWithText(text(R.string.manage_delete_selected)).assertExists()
    }

    @Test
    fun tappingAnotherCardAddsItToTheSelection() {
        show()

        composeRule.onNodeWithText("女儿").performTouchInput { longClick() }
        composeRule.onNodeWithText("儿子").performClick()

        composeRule.onNodeWithText(text(R.string.manage_selected_count, 2)).assertExists()
    }

    @Test
    fun deletingAsksFirstAndNothingIsRemovedUntilTheConfirmation() {
        show()

        composeRule.onNodeWithText("女儿").performTouchInput { longClick() }
        composeRule.onNodeWithText("儿子").performClick()
        composeRule.onNodeWithText(text(R.string.manage_delete_selected)).performClick()

        // The dialog names the count, and the tap that opened it deleted nothing.
        composeRule.onNodeWithText(text(R.string.manage_delete_confirm_title, 2)).assertExists()
        assertEquals(0, deleted)

        // The confirmation is the last "delete" on screen: the bar's, then the dialog's.
        composeRule.onAllNodesWithText(text(R.string.manage_delete_selected))[1].performClick()
        assertEquals(1, deleted)
    }

    @Test
    fun cancellingTheSelectionKeepsEveryone() {
        show()

        composeRule.onNodeWithText("女儿").performTouchInput { longClick() }
        composeRule.onNodeWithText(text(R.string.action_cancel)).performClick()

        composeRule.onAllNodesWithText(text(R.string.manage_selected_count, 1)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.manage_long_press_hint)).assertCountEquals(1)
        assertEquals(0, deleted)
    }
}
