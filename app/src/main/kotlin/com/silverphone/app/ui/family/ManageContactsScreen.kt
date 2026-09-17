package com.silverphone.app.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.silverphone.app.R
import com.silverphone.app.data.repository.MoveDirection
import com.silverphone.app.domain.Contact
import com.silverphone.app.ui.components.BackActionButton
import com.silverphone.app.ui.components.CompactActionButton
import com.silverphone.app.ui.components.ContactPhoto
import com.silverphone.app.ui.components.MessageState
import com.silverphone.app.ui.components.PrimaryActionButton
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

private val THUMB_SIZE = 72.dp

/**
 * S04: the management list.
 *
 * Rows show the photo, the name the elderly user sees, and the full number, so a
 * family member can check the number at a glance. Nothing on this screen dials:
 * the only actions are edit, reorder and add.
 */
@Composable
fun ManageContactsScreen(
    state: ManageUiState,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onMove: (String, MoveDirection) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    // Tapping anywhere that is not the search field puts the keyboard away. Without
    // this the field kept focus after the keyboard was dismissed, which left the
    // screen in a state the user could only escape by leaving and coming back.
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // Taps that no button or field consumes put the keyboard away. Children
            // consume their own events, so this only fires on empty space.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            },
    ) {
        Text(
            text = stringResource(R.string.manage_title),
            style = styles.pageTitle,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.pagePadding,
            ),
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = styles.body,
            label = { Text(stringResource(R.string.manage_search_hint), style = styles.caption) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = AppColors.Ink)
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(dimens.minTouchTarget),
                    ) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.manage_search_clear),
                            tint = AppColors.Ink,
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.inputHeight)
                .padding(horizontal = dimens.pagePadding, vertical = dimens.touchGap),
        )

        if (state.loading) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = AppColors.Ink,
                    modifier = Modifier.size(dimens.primaryGlyph),
                )
            }
        } else if (state.contacts.isEmpty()) {
            MessageState(
                icon = if (state.isSearching) Icons.Filled.Search else Icons.Filled.Person,
                title = stringResource(
                    if (state.isSearching) R.string.manage_search_empty else R.string.manage_empty,
                ),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = dimens.pagePadding,
                    end = dimens.pagePadding,
                    top = dimens.touchGap,
                    bottom = dimens.pagePadding,
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
            ) {
                items(
                    count = state.contacts.size,
                    key = { index -> state.contacts[index].id },
                ) { index ->
                    val contact = state.contacts[index]
                    ContactRow(
                        contact = contact,
                        // Reordering follows the stored order, so it is disabled
                        // while a search is narrowing the list.
                        canMoveUp = !state.isSearching && index > 0,
                        canMoveDown = !state.isSearching && index < state.contacts.lastIndex,
                        onEdit = { onEdit(contact.id) },
                        onMoveUp = { onMove(contact.id, MoveDirection.UP) },
                        onMoveDown = { onMove(contact.id, MoveDirection.DOWN) },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
        ) {
                if (state.atCapacity) {
                    Text(
                        text = stringResource(R.string.manage_capacity, state.totalCount),
                        style = styles.caption,
                        color = AppColors.DangerRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            PrimaryActionButton(
                text = stringResource(R.string.manage_add),
                icon = Icons.Filled.Add,
                onClick = onAdd,
                enabled = !state.atCapacity,
                modifier = Modifier.fillMaxWidth(),
            )
            BackActionButton(
                text = stringResource(R.string.action_back),
                icon = Icons.Filled.Info,
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * One row of the management list.
 *
 * The photo, name and full number get the whole width, and the labelled actions
 * sit on their own line underneath. Putting the actions beside the text squeezed
 * the number onto two lines, which is exactly the kind of thing a family member
 * needs to be able to read at a glance.
 */
@Composable
private fun ContactRow(
    contact: Contact,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(AppColors.Surface)
            .padding(dimens.cardInnerPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(THUMB_SIZE)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                ContactPhoto(
                    contact = contact,
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(1f),
                )
            }

            Spacer(Modifier.width(dimens.cardInnerPadding))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.displayName,
                    style = styles.body,
                    color = AppColors.TextPrimary,
                )
                Text(
                    text = contact.phoneNumber,
                    style = styles.body,
                    color = AppColors.TextSecondary,
                )
                if (!contact.hasPhoto) {
                    Text(
                        text = stringResource(R.string.manage_no_photo),
                        style = styles.caption,
                        color = AppColors.DangerRed,
                    )
                }
            }
        }

        // Nothing on this screen dials: only these clearly labelled controls act,
        // so browsing or reordering can never place a call or open an editor.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.touchGap),
        ) {
            CompactActionButton(
                text = stringResource(R.string.manage_move_up),
                icon = Icons.Filled.KeyboardArrowUp,
                enabled = canMoveUp,
                onClick = onMoveUp,
                modifier = Modifier.weight(1f),
            )
            CompactActionButton(
                text = stringResource(R.string.manage_move_down),
                icon = Icons.Filled.KeyboardArrowDown,
                enabled = canMoveDown,
                onClick = onMoveDown,
                modifier = Modifier.weight(1f),
            )
            CompactActionButton(
                text = stringResource(R.string.manage_edit),
                icon = Icons.Filled.Edit,
                onClick = onEdit,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
