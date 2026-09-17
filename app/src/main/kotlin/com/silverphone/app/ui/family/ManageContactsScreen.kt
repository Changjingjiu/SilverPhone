package com.silverphone.app.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.silverphone.app.R
import com.silverphone.app.data.repository.MoveDirection
import com.silverphone.app.domain.Contact
import com.silverphone.app.ui.components.AppTextField
import com.silverphone.app.ui.components.CompactActionButton
import com.silverphone.app.ui.components.ContactPhoto
import com.silverphone.app.ui.components.FamilyScreen
import com.silverphone.app.ui.components.FilledActionButton
import com.silverphone.app.ui.components.MessageState
import com.silverphone.app.ui.components.QuietActionButton
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

private val THUMB_SIZE = 64.dp

/**
 * S04: the management list.
 *
 * Rows show the photo, the name the elderly user sees, and the full number, so a
 * family member can check the number at a glance. Nothing on this screen dials.
 *
 * The row follows the platform's own list grammar, which is also what the import
 * picker uses: **tap a row to open it, press and hold to start selecting**. The first
 * build had a tap that did nothing and a separate 修改 button, which meant two screens
 * in the same app answered the same gesture differently.
 */
@Composable
fun ManageContactsScreen(
    state: ManageUiState,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onMove: (String, MoveDirection) -> Unit,
    onLongPress: (String) -> Unit,
    onToggleSelected: (String) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    // Tapping anywhere that is not the search field puts the keyboard away. Without
    // this the field kept focus after the keyboard was dismissed, which left the
    // screen in a state the user could only escape by leaving and coming back.
    val focusManager = LocalFocusManager.current

    FamilyScreen(
        title = stringResource(R.string.manage_title),
        subtitle = stringResource(R.string.settings_manage_desc),
        onBack = onBack,
        backLabel = stringResource(R.string.action_back),
        modifier = modifier
            .fillMaxSize()
            // Taps that no button or field consumes put the keyboard away. Children
            // consume their own events, so this only fires on empty space.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            },
    ) {

        AppTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.manage_search_hint),
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = AppColors.Ink,
                )
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
            minHeight = 56.dp,
            modifier = Modifier.padding(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.touchGap,
            ),
        )

        if (state.isSelecting) {
            // The bar replaces the hint while selecting: one row of the screen, and the
            // only place the destructive action can be reached from.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.pagePadding, vertical = dimens.spaceSnug),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.touchGap),
            ) {
                Text(
                    text = stringResource(R.string.manage_selected_count, state.selected.size),
                    style = styles.button,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                QuietActionButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onClearSelection,
                )
                FilledActionButton(
                    text = stringResource(R.string.manage_delete_selected),
                    icon = Icons.Filled.Delete,
                    danger = true,
                    enabled = !state.deleting,
                    onClick = { confirmDelete = true },
                )
            }
        } else {
            Text(
                text = stringResource(R.string.manage_long_press_hint),
                style = styles.caption,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(
                    start = dimens.pagePadding,
                    end = dimens.pagePadding,
                    bottom = dimens.spaceSnug,
                ),
            )
        }

        if (state.deleteFailed) {
            Text(
                text = stringResource(R.string.manage_delete_failed),
                style = styles.caption,
                color = AppColors.DangerRed,
                modifier = Modifier.padding(horizontal = dimens.pagePadding),
            )
        }

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
                    top = dimens.spaceRoomy,
                    bottom = dimens.touchGap,
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
                        modifier = Modifier.animateItem(),
                        selecting = state.isSelecting,
                        selected = contact.id in state.selected,
                        onLongPress = { onLongPress(contact.id) },
                        onToggleSelected = { onToggleSelected(contact.id) },
                        onOpen = { onEdit(contact.id) },
                        // Reordering follows the stored order, so it is disabled
                        // while a search is narrowing the list.
                        canMoveUp = !state.isSearching && index > 0,
                        canMoveDown = !state.isSearching && index < state.contacts.lastIndex,
                        onMoveUp = { onMove(contact.id, MoveDirection.UP) },
                        onMoveDown = { onMove(contact.id, MoveDirection.DOWN) },
                    )
                }
            }
        }

        if (state.atCapacity) {
            Text(
                text = stringResource(R.string.manage_capacity, state.totalCount),
                style = styles.caption,
                color = AppColors.DangerRed,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.pagePadding),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.pagePadding, vertical = dimens.touchGap),
            horizontalArrangement = Arrangement.spacedBy(dimens.touchGap),
        ) {
            if (!state.isSelecting) {
            FilledActionButton(
                text = stringResource(R.string.manage_add),
                icon = Icons.Filled.Add,
                onClick = onAdd,
                enabled = !state.atCapacity,
                modifier = Modifier.weight(1f),
            )
            }
            FilledActionButton(
                text = stringResource(R.string.action_back),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                emphasis = false,
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(
                    text = stringResource(
                        R.string.manage_delete_confirm_title,
                        state.selected.size,
                    ),
                    style = styles.body,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.manage_delete_confirm_message),
                    style = styles.caption,
                )
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = {
                        confirmDelete = false
                        onDeleteSelected()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.manage_delete_selected),
                        style = styles.body,
                        color = AppColors.DangerRed,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = { confirmDelete = false },
                ) {
                    Text(stringResource(R.string.action_cancel), style = styles.body)
                }
            },
        )
    }
}

/**
 * One row of the management list.
 *
 * The photo, name and full number get the whole width, and the two reorder controls
 * sit on their own line underneath, divided off by a hairline. Editing is the row's own
 * tap; keeping a third button for it made the row look like a toolbar and squeezed the
 * number onto two lines.
 */
@Composable
private fun ContactRow(
    contact: Contact,
    modifier: Modifier = Modifier,
    selecting: Boolean,
    selected: Boolean,
    onLongPress: () -> Unit,
    onToggleSelected: () -> Unit,
    onOpen: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    val shape = RoundedCornerShape(dimens.cardCorner)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) AppColors.InkSoft else AppColors.Surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AppColors.Ink else AppColors.Hairline,
                shape = shape,
            )
            .combinedClickable(
                onClick = { if (selecting) onToggleSelected() else onOpen() },
                onLongClick = onLongPress,
                role = Role.Button,
            )
            .padding(dimens.cardInnerPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelected() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AppColors.Ink,
                        uncheckedColor = AppColors.Outline,
                        checkmarkColor = AppColors.Surface,
                    ),
                )
                Spacer(Modifier.width(dimens.spaceSnug))
            }
            Box(
                modifier = Modifier
                    .size(THUMB_SIZE)
                    .clip(RoundedCornerShape(dimens.photoCorner)),
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
                    style = styles.button,
                    color = AppColors.TextPrimary,
                )
                Text(
                    text = contact.phoneNumber,
                    style = styles.caption,
                    color = AppColors.TextSecondary,
                )
                if (!contact.hasPhoto) {
                    Text(
                        text = stringResource(R.string.manage_no_photo),
                        style = styles.caption,
                        color = AppColors.DangerRed,
                        modifier = Modifier
                            .padding(top = dimens.spaceTight)
                            .clip(RoundedCornerShape(dimens.badgeCorner))
                            .background(AppColors.DangerSoft)
                            .padding(horizontal = dimens.spaceSnug, vertical = dimens.spaceTight),
                    )
                }
            }
        }

        if (selecting) {
            return@Column
        }

        HorizontalDivider(color = AppColors.Hairline)

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
        }
    }
}
