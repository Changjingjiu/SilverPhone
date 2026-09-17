package com.silverphone.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import com.silverphone.app.ui.components.PrimaryActionButton
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/** Below this window width the home screen always uses a single column. */
private val TWO_COLUMN_MIN_WINDOW = 360.dp

/** Four Chinese characters: the name length a two-column card must still hold. */
private const val COLUMN_SAMPLE_NAME = "爷爷奶奶"

/**
 * A face smaller than this stops being recognisable, which is the whole point of the
 * card. Below it the card scrolls instead of shrinking any further.
 */
private val MIN_PHOTO_SIDE = 96.dp

/**
 * S01: the screen the elderly user lives in.
 *
 * The only things on it are the title, a deliberately quieter entry to the family
 * settings, and the cards themselves. No contact details, no counters, no
 * management controls, and nothing that could dial by accident.
 */
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeHeader(onFamilyEntry = onFamilyEntry)
            when (val load = state.load) {
                ContactsLoad.Loading -> LoadingBody()
                ContactsLoad.Failed -> FailedBody(onRetry = onRetry, onFamilyEntry = onFamilyEntry)
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
        } else if (state.dialState is DialState.Dispatching ||
            state.dialState is DialState.HandedOff
        ) {
            HandingOffNotice()
        }
    }
}

/**
 * Title and family entry, on one line.
 *
 * The entry always sits on the trailing edge, which is where a settings action lives
 * in every other app, and the title takes whatever is left and wraps if it has to.
 * An earlier version switched between a row and a stacked column depending on a
 * measured width, and the stacked form put a lone pill under a large title with dead
 * space beside it - which read as a broken layout rather than a decision. Keeping the
 * entry pinned to the trailing edge makes the header look the same at every width,
 * font preset and system font scale.
 */
@Composable
private fun HomeHeader(onFamilyEntry: () -> Unit) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.pagePadding, vertical = dimens.touchGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = styles.pageTitle,
            color = AppColors.TextPrimary,
            // Takes the space the entry does not need, so the entry lands flush
            // against the trailing edge. The entry is measured first, so it is never
            // squeezed; the title wraps instead of shrinking.
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(dimens.touchGap))
        FamilyEntry(label = stringResource(R.string.home_family_entry), onClick = onFamilyEntry)
    }
}

/** Graphically and verbally distinct from a contact card, and lower priority. */
@Composable
private fun FamilyEntry(label: String, onClick: () -> Unit) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .heightIn(min = dimens.minTouchTarget)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = AppColors.Ink,
            modifier = Modifier
                .size(dimens.secondaryGlyph)
                .clearAndSetSemantics { },
        )
        Text(
            text = label,
            style = styles.body,
            color = AppColors.Ink,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

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
    val listState = rememberLazyListState()
    val enabled = !dialState.blocksFurtherTaps

    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val available = maxWidth - dimens.pagePadding * 2

        // How wide one name has to be to hold four characters at the *current*
        // font size, measured rather than assumed, so the two-column choice
        // follows the system font scale and the app preset.
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

        // Line count per contact, so every card in a row can reserve the same
        // height. A long name raises its whole row instead of being truncated.
        val lineCounts = remember(contacts, styles.contactName, nameAreaPx) {
            contacts.associate { contact ->
                contact.id to measurer.measure(
                    text = AnnotatedString(contact.displayName),
                    style = styles.contactName,
                    constraints = Constraints(maxWidth = nameAreaPx),
                ).lineCount
            }
        }

        // How tall the photo may be.
        //
        // The photo is a square as wide as its card, which is right in a tall window
        // and wrong in a short one: in landscape the square is taller than the space
        // left for it, so the name and the Call button - the only control the app has -
        // ended up below the fold even at the standard text size. The card needs its
        // chrome before it needs a big face, so the face is what gives way.
        val tallestNameLines = lineCounts.values.maxOrNull() ?: 1
        val nameLineHeight = with(density) { styles.contactName.lineHeight.toDp() }
        val cardChrome = dimens.cardInnerPadding * 2 +
            dimens.touchGap * 2 +
            dimens.primaryButtonHeight +
            nameLineHeight * tallestNameLines
        val photoMax = (
            maxHeight - dimens.touchGap - dimens.pagePadding * 2 - cardChrome
            ).coerceAtLeast(MIN_PHOTO_SIDE)
        val photoSide = minOf(columnWidth - dimens.cardInnerPadding * 2, photoMax)

        val rows = remember(contacts, columns) { contacts.chunked(columns) }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.touchGap,
                bottom = dimens.pagePadding * 2,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            items(
                count = rows.size,
                key = { index -> rows[index].firstOrNull()?.id ?: index },
            ) { index ->
                val row = rows[index]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.cardGap),
                ) {
                    row.forEach { contact ->
                        ContactCard(
                            contact = contact,
                            enabled = enabled,
                            onClick = { onContactTap(contact) },
                            reservedNameLines = lineCounts[contact.id] ?: 1,
                            photoSide = photoSide,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keeps a lone card in the last row at column width instead of
                    // stretching across both columns.
                    if (row.size < columns) {
                        repeat(columns - row.size) {
                            androidx.compose.foundation.layout.Spacer(
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(dimens.cardCorner))
                .background(AppColors.Surface)
                .semantics(mergeDescendants = true) { contentDescription = label }
                .padding(horizontal = 24.dp, vertical = 20.dp),
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
