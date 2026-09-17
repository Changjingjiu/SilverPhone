package com.silverphone.app.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.silverphone.app.R
import com.silverphone.app.domain.FontPreset
import com.silverphone.app.platform.update.ProjectLinks
import com.silverphone.app.ui.theme.SilverPhoneTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the About screen says, in each state the update check can leave it in.
 *
 * The screen is rendered from a given state rather than from a live request: what is
 * asserted here is the copy a family member reads, and no test may reach GitHub.
 * Expected labels come from the app's own resources, because the interface ships in
 * English and Chinese and the reader may have either.
 */
@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun text(id: Int, vararg arguments: Any): String =
        context.getString(id, *arguments)

    private fun show(updateState: UpdateState) {
        composeRule.setContent {
            SilverPhoneTheme(fontPreset = FontPreset.STANDARD) {
                AboutScreen(
                    installedVersionName = "1.0.0",
                    installedVersionCode = 1,
                    updateState = updateState,
                    onCheckForUpdates = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun showsTheInstalledVersionAndTheProjectAddressBeforeAnyCheck() {
        show(UpdateState.NotChecked)

        composeRule.onNodeWithText(text(R.string.about_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_version, "1.0.0", 1))
            .assertIsDisplayed()
        // The address is readable as text, not only as a tappable glyph.
        composeRule.onNodeWithText(ProjectLinks.REPOSITORY_DISPLAY).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.about_check_updates)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.about_privacy_title)).assertIsDisplayed()
    }

    @Test
    fun offersTheDownloadPageOnlyWhenANewerVersionExists() {
        show(UpdateState.Available(version = "v1.2.0", releaseUrl = ProjectLinks.RELEASES))

        composeRule.onNodeWithText(text(R.string.about_update_available, "v1.2.0"))
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.about_open_release)).assertIsDisplayed()
    }

    @Test
    fun anUnansweredCheckIsNeverPresentedAsBeingUpToDate() {
        show(UpdateState.Failed)

        composeRule.onNodeWithText(text(R.string.about_check_failed)).assertIsDisplayed()
    }
}
