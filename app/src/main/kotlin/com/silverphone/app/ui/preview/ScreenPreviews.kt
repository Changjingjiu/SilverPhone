package com.silverphone.app.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.silverphone.app.domain.Contact
import com.silverphone.app.domain.FontPreset
import com.silverphone.app.domain.PlaceholderColor
import com.silverphone.app.platform.phone.DialState
import com.silverphone.app.ui.family.FamilyMenuPane
import com.silverphone.app.ui.family.FamilySection
import com.silverphone.app.ui.home.ContactsLoad
import com.silverphone.app.ui.home.HomeScreen
import com.silverphone.app.ui.home.HomeUiState
import com.silverphone.app.ui.theme.SilverPhoneTheme

/**
 * The two layouts that matter, at the two window sizes they have to survive.
 *
 * These are the layouts the app is judged on: the elderly user's home screen, which
 * must stay legible and large-targeted at any width, and the family menu, which becomes
 * the list pane of a two-pane settings screen as soon as there is room for it.
 */

private val previewContacts = listOf(
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
    Contact(
        id = "c3",
        displayName = "老伴",
        phoneNumber = "13700137000",
        sortOrder = 2,
        placeholderColor = PlaceholderColor.LIGHT_PURPLE,
        photoSha256 = null,
    ),
    Contact(
        id = "c4",
        displayName = "小孙女",
        phoneNumber = "13600136000",
        sortOrder = 3,
        placeholderColor = PlaceholderColor.LIGHT_ROSE,
        photoSha256 = null,
    ),
)

@Preview(name = "Home · phone", device = "spec:width=411dp,height=891dp,dpi=420")
@Preview(name = "Home · tablet", device = "spec:width=1000dp,height=700dp,dpi=240")
@Composable
private fun HomePreview() {
    SilverPhoneTheme(fontPreset = FontPreset.STANDARD) {
        HomeScreen(
            state = HomeUiState(
                load = ContactsLoad.Loaded(previewContacts),
                dialState = DialState.Ready,
                awaitingPermission = false,
            ),
            onContactTap = {},
            onFamilyEntry = {},
            onRetry = {},
            onProblemAcknowledged = {},
            onOpenFamilySettings = {},
        )
    }
}

@Preview(name = "Family menu · phone", device = "spec:width=411dp,height=891dp,dpi=420")
@Preview(name = "Family list pane · tablet", device = "spec:width=1000dp,height=700dp,dpi=240")
@Composable
private fun FamilyMenuPreview() {
    SilverPhoneTheme(fontPreset = FontPreset.STANDARD) {
        FamilyMenuPane(
            contactCount = previewContacts.size,
            selected = FamilySection.MANAGE,
            onSelect = {},
            onExit = {},
        )
    }
}
