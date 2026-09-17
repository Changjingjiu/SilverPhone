package com.silverphone.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.silverphone.app.R
import com.silverphone.app.domain.Contact
import com.silverphone.app.platform.phone.DialState
import com.silverphone.app.ui.components.MessageState
import com.silverphone.app.ui.components.PRESSED_SCALE_GENTLE
import com.silverphone.app.ui.components.PressHaptics
import com.silverphone.app.ui.components.PrimaryActionButton
import com.silverphone.app.ui.components.pressScale
import com.silverphone.app.ui.components.rememberPressFeedback
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/** Below this window width the home screen always uses a single column. */
private val TWO_COLUMN_MIN_WINDOW = 360.dp

/** Four Chinese characters: the name length a two-column card must still hold. */
private const val COLUMN_SAMPLE_NAME = "爷爷奶奶"

/** How long a card takes to slide into a new position when the order changes. */
private const val PLACEMENT_MILLIS = 220

/**
 * S01: the screen the elderly user lives in.
 *
 * The only things on it are the title, a deliberately quieter entry to the family
 * settings, and the cards themselves. No contact details, no counters, no
 * management controls, and nothing that could dial by accident.
 *
 * Structurally this is the canonical single-pane screen: a [Scaffold] with a
 * [TopAppBar], and a [LazyVerticalGrid] whose column count follows the measured width
 * of the largest name the card has to hold - which is what decides whether a two-column
 * grid is legible, and which changes with the system font scale.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onContactTap: (Contact) -> Unit,
    onFamilyEntry: () -> Unit,
    onRetry: () -> Unit,
    onProblemAcknowledged: () -> Unit,
    onOpenFamilySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = LocalAppTextStyles.current.pageTitle,
                        color = AppColors.TextPrimary,
                    )
                },
                actions = {
                    FamilyEntry(
                        label = stringResource(R.string.home_family_entry),
                        onClick = onFamilyEntry,
                    )
                    Spacer(Modifier.width(dimens.spaceSnug))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Background,
                    titleContentColor = AppColors.TextPrimary,
                    actionIconContentColor = AppColors.Ink,
                ),
            )
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn600()
                    .fillMaxSize(),
            ) {
                // One crossfade between the four states of the screen, so a content
                // swap reads as the page changing rather than as a flicker.
                AnimatedContent(
                    targetState = state.load,
                    transitionSpec = {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(120))
                    },
                    label = "homeBody",
                ) { load ->
                    when (load) {
                        ContactsLoad.Loading -> LoadingBody()
                        ContactsLoad.Failed -> FailedBody(
                            onRetry = onRetry,
                            onFamilyEntry = onFamilyEntry,
                        )

                        is ContactsLoad.Loaded -> if (load.contacts.isEmpty()) {
                            EmptyBody(onFamilyEntry = onFamilyEntry)
                        } else {
                            ContactGrid(
                                contacts = load.contacts,
                                dialState = state.dialState,
                                onContactTap = onContactTap,
                            )
                        }
                    }
                }
            }

            val problem = (state.dialState as? DialState.Problem)?.problem
            if (problem != null && !state.awaitingPermission) {
                // Back closes the help. Without this the system back pops the only
                // destination in the graph and the elderly user is dropped onto the
                // phone's home screen, which looks like the app crashed.
                BackHandler(enabled = true) { onProblemAcknowledged() }
                DialHelpScreen(
                    problem = problem,
                    onBackHome = onProblemAcknowledged,
                    onOpenFamilySettings = onOpenFamilySettings,
                    onAcknowledgeAndReturn = onProblemAcknowledged,
                )
            }

            AnimatedVisibility(
                visible = state.dialState is DialState.Dispatching ||
                    state.dialState is DialState.HandedOff,
                enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.94f),
                exit = fadeOut(tween(120)),
            ) {
                HandingOffNotice()
            }
        }
    }
}

/** The 600 dp measure the rest of the app uses. */
private fun Modifier.widthIn600(): Modifier = widthIn(max = 600.dp)

/**
 * The family door.
 *
 * Graphically and verbally distinct from a contact card, and lower priority: a
 * sunken tile behind the gear, the same shape the family menu uses for every row, so
 * the entry reads as belonging to the settings side of the app rather than as a
 * decoration on the elderly user's page.
 */
@Composable
private fun FamilyEntry(label: String, onClick: () -> Unit) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    val shape = RoundedCornerShape(dimens.cardCorner)

    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource, pressedScale = PRESSED_SCALE_GENTLE)
    PressHaptics(interactionSource)
    val fill by animateColorAsState(
        targetValue = if (press.pressed) AppColors.SurfacePressed else AppColors.Surface,
        animationSpec = tween(90),
        label = "familyEntryFill",
    )

    Row(
        modifier = Modifier
            .pressScale(press.scale)
            .clip(shape)
            .background(fill)
            .border(1.dp, AppColors.Hairline, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = AppColors.Ink),
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 44.dp)
            .padding(start = dimens.spaceTight, end = dimens.touchGap, top = dimens.spaceTight, bottom = dimens.spaceTight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(dimens.chipCorner))
                .background(AppColors.SurfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = AppColors.Ink,
                modifier = Modifier
                    .size(18.dp)
                    .clearAndSetSemantics { },
            )
        }
        Text(
            text = label,
            style = styles.body,
            color = AppColors.Ink,
            modifier = Modifier.padding(start = dimens.spaceSnug),
        )
    }
}

/**
 * The relatives, in the fixed stored order.
 *
 * The grid is the canonical [LazyVerticalGrid]; the only decision this code makes is
 * how many columns the current width and the current text size can carry. Cards keep
 * their order and animate into place when it changes, so reordering a contact does not
 * read as the list flickering.
 */
@Composable
private fun ContactGrid(
    contacts: List<Contact>,
    dialState: DialState,
    onContactTap: (Contact) -> Unit,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val enabled = !dialState.blocksFurtherTaps

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val available = maxWidth - dimens.pagePadding * 2

        // How wide one name has to be to hold four characters at the *current* font
        // size, measured rather than assumed, so the grid follows the system font
        // scale and the app preset. This is the one measurement in the app that cannot
        // be replaced by a size class, because it depends on the text inside the
        // window rather than on the window itself.
        val sampleWidthPx = remember(styles.contactName) {
            measurer.measure(AnnotatedString(COLUMN_SAMPLE_NAME), styles.contactName).size.width
        }
        val sampleWidth = with(density) { sampleWidthPx.toDp() }
        val neededColumn = sampleWidth + dimens.cardInnerPadding * 2

        val columns = if (maxWidth >= TWO_COLUMN_MIN_WINDOW &&
            available >= neededColumn * 2 + dimens.cardGap
        ) {
            2
        } else {
            1
        }

        val columnWidth = if (columns == 2) (available - dimens.cardGap) / 2 else available
        val nameAreaPx = with(density) {
            (columnWidth - dimens.cardInnerPadding * 2).coerceAtLeast(1.dp).roundToPx()
        }

        // Line count per contact, so every card in a row can reserve the same height.
        // A long name raises its whole row instead of being truncated.
        val lineCounts = remember(contacts, styles.contactName, nameAreaPx) {
            contacts.associate { contact ->
                contact.id to measurer.measure(
                    text = AnnotatedString(contact.displayName),
                    style = styles.contactName,
                    constraints = Constraints(maxWidth = nameAreaPx),
                ).lineCount
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.touchGap,
                bottom = dimens.pagePadding * 2,
            ),
            horizontalArrangement = Arrangement.spacedBy(dimens.cardGap),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            items(
                items = contacts,
                key = { contact -> contact.id },
                contentType = { "contact" },
            ) { contact ->
                ContactCard(
                    contact = contact,
                    enabled = enabled,
                    onClick = { onContactTap(contact) },
                    reservedNameLines = lineCounts[contact.id] ?: 1,
                    modifier = Modifier.animateItem(
                        placementSpec = tween(PLACEMENT_MILLIS),
                        fadeInSpec = tween(PLACEMENT_MILLIS),
                        fadeOutSpec = tween(PLACEMENT_MILLIS),
                    ),
                )
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    val dimens = LocalAppDimens.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            color = AppColors.Ink,
            modifier = Modifier.size(dimens.primaryGlyph),
        )
        Text(
            text = stringResource(R.string.home_loading),
            style = LocalAppTextStyles.current.body,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(top = dimens.touchGap),
        )
    }
}

@Composable
private fun EmptyBody(onFamilyEntry: () -> Unit) {
    MessageState(
        icon = Icons.Filled.Person,
        title = stringResource(R.string.home_empty_title),
        hint = stringResource(R.string.home_empty_hint),
        actions = {
            PrimaryActionButton(
                text = stringResource(R.string.home_empty_action),
                icon = Icons.Filled.Settings,
                onClick = onFamilyEntry,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun FailedBody(onRetry: () -> Unit, onFamilyEntry: () -> Unit) {
    MessageState(
        icon = Icons.Filled.Person,
        title = stringResource(R.string.home_load_failed),
        hint = stringResource(R.string.home_load_failed_hint),
        actions = {
            PrimaryActionButton(
                text = stringResource(R.string.home_retry),
                icon = Icons.Filled.Person,
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryActionButton(
                text = stringResource(R.string.home_family_entry),
                icon = Icons.Filled.Settings,
                onClick = onFamilyEntry,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/** Short-lived, and never claims the call connected. */
@Composable
private fun HandingOffNotice() {
    val dimens = LocalAppDimens.current
    val label = stringResource(R.string.home_handing_off)
    val shape = RoundedCornerShape(dimens.cardCorner)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .shadow(6.dp, shape)
                .clip(shape)
                .background(AppColors.Surface)
                .border(1.dp, AppColors.Hairline, shape)
                .semantics(mergeDescendants = true) { contentDescription = label }
                .padding(horizontal = dimens.spaceLoose, vertical = dimens.spaceRoomy),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                color = AppColors.CallGreen,
                modifier = Modifier
                    .size(dimens.secondaryGlyph)
                    .clearAndSetSemantics { },
            )
            Text(
                text = label,
                style = LocalAppTextStyles.current.button,
                color = AppColors.TextPrimary,
                modifier = Modifier
                    .padding(start = dimens.touchGap)
                    .clearAndSetSemantics { },
            )
        }
    }
}
