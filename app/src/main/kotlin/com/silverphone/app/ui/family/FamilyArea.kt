package com.silverphone.app.ui.family

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.silverphone.app.app.AppLanguage
import com.silverphone.app.platform.hasReadContactsPermission
import com.silverphone.app.platform.openAppSettingsPage
import com.silverphone.app.ui.appViewModel
import com.silverphone.app.ui.contacts.ContactsImportScreen
import com.silverphone.app.ui.contacts.ContactsImportViewModel
import com.silverphone.app.ui.settings.AboutScreen
import com.silverphone.app.ui.settings.AboutViewModel
import com.silverphone.app.ui.settings.CallPermissionScreen
import com.silverphone.app.ui.settings.CallPermissionViewModel
import com.silverphone.app.ui.settings.FontSettingsScreen
import com.silverphone.app.ui.settings.FontSettingsViewModel
import com.silverphone.app.ui.settings.PreferencesScreen
import com.silverphone.app.ui.settings.PreferencesViewModel
import com.silverphone.app.ui.theme.SilverPhoneTheme
import com.silverphone.app.ui.transfer.ExportScreen
import com.silverphone.app.ui.transfer.ExportViewModel
import com.silverphone.app.ui.transfer.FileImportScreen
import com.silverphone.app.ui.transfer.FileImportViewModel

/**
 * Everything the family touches, as one list-detail destination.
 *
 * This is the canonical [ListDetailPaneScaffold]: on a phone the menu and the screen
 * behind it are one at a time and moving between them is a pane transition; from a
 * medium window upwards the menu stays beside the screen it opened, which is what the
 * extra width is for. The pane scaffold owns the back stack, so system back and the
 * arrow in each pane's bar do the same thing.
 *
 * The elderly user never sees any of it. The home screen is the only screen built
 * around them, and it is a separate destination on purpose.
 */
enum class FamilySection {
    MANAGE,
    CONTACTS_IMPORT,
    TRANSFER,
    FILE_IMPORT,
    EXPORT,
    FONT,
    PREFERENCES,
    PERMISSION,
    ABOUT,
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun FamilyArea(
    onExit: () -> Unit,
    onOpenEditor: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<FamilySection>()
    val scope = rememberCoroutineScope()
    val goBack: () -> Unit = { scope.launch { navigator.navigateBack() } }
    val goTo: (FamilySection) -> Unit = { section ->
        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, section) }
    }

    // The pane scaffold keeps its own back stack: without this, system back would leave
    // the whole family area instead of closing the screen that is open.
    BackHandler(enabled = navigator.canNavigateBack()) { goBack() }

    val current = navigator.currentDestination?.contentKey
    val listVisible =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded

    ListDetailPaneScaffold(
        modifier = modifier.fillMaxSize(),
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            // The count lives in the ViewModel; the pane itself is a pure function of
            // the numbers it is given, which is also what makes it previewable.
            val menuViewModel = appViewModel { container ->
                ManageContactsViewModel(container.contactRepository)
            }
            val menuState by menuViewModel.uiState.collectAsStateWithLifecycle()
            FamilyMenuPane(
                contactCount = menuState.totalCount,
                selected = current,
                onSelect = goTo,
                onExit = onExit,
            )
        },
        detailPane = {
            val section = current
            if (section == null) {
                EmptyDetailPane()
            } else {
                // Beside the menu there is nothing to go back to, so the arrow is only
                // drawn when the pane is alone on the screen.
                SectionDetail(
                    section = section,
                    onBack = if (listVisible) null else goBack,
                    onOpenEditor = onOpenEditor,
                    onGoTo = goTo,
                )
            }
        },
    )
}

/**
 * The body of one section.
 *
 * Each section is the same screen the app had before, unchanged, except that its way
 * back is a callback instead of a navigation route.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun SectionDetail(
    section: FamilySection,
    onBack: (() -> Unit)?,
    onOpenEditor: (String?) -> Unit,
    onGoTo: (FamilySection) -> Unit,
) {
    when (section) {
        FamilySection.MANAGE -> {
            val viewModel = appViewModel { container ->
                ManageContactsViewModel(container.contactRepository)
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            ManageContactsScreen(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onAdd = { onOpenEditor(null) },
                onEdit = { contactId -> onOpenEditor(contactId) },
                onMove = viewModel::onMove,
                onLongPress = viewModel::onLongPress,
                onToggleSelected = viewModel::onToggleSelected,
                onClearSelection = viewModel::onClearSelection,
                onDeleteSelected = viewModel::onDeleteSelected,
                onBack = onBack ?: {},
            )
        }

        FamilySection.CONTACTS_IMPORT -> {
            val viewModel = appViewModel { container ->
                ContactsImportViewModel(
                    repository = container.contactRepository,
                    source = container.systemContactsSource,
                    photoNormalizer = container.photoNormalizer,
                )
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val context = androidx.compose.ui.platform.LocalContext.current

            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            ) { granted -> viewModel.onPermissionResult(granted) }

            androidx.lifecycle.compose.LifecycleEventEffect(
                androidx.lifecycle.Lifecycle.Event.ON_RESUME,
            ) {
                viewModel.onObservedPermission(context.hasReadContactsPermission())
            }

            ContactsImportScreen(
                state = state,
                onRequestPermission = {
                    permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                },
                onOpenAppSettings = { context.openAppSettingsPage() },
                onManualAdd = { onOpenEditor(null) },
                onQueryChange = viewModel::onQueryChange,
                onToggleSelected = viewModel::onToggleSelected,
                onSelectAllVisible = viewModel::onSelectAllVisible,
                onClearSelection = viewModel::onClearSelection,
                onOpenNumberPicker = viewModel::onOpenNumberPicker,
                onDismissNumberPicker = viewModel::onDismissNumberPicker,
                onChooseNumber = viewModel::onChooseNumber,
                onOpenNameEditor = viewModel::onOpenNameEditor,
                onDismissNameEditor = viewModel::onDismissNameEditor,
                onNameChanged = viewModel::onNameChanged,
                onGoToPreview = viewModel::onGoToPreview,
                onBackToPick = viewModel::onBackToPick,
                onCommit = viewModel::onCommit,
                onFinished = {
                    viewModel.onCancel()
                    onGoTo(FamilySection.MANAGE)
                },
                onBack = onBack ?: {},
            )
        }

        FamilySection.TRANSFER -> TransferScreen(
            onImport = { onGoTo(FamilySection.FILE_IMPORT) },
            onExport = { onGoTo(FamilySection.EXPORT) },
            onBack = onBack ?: {},
        )

        FamilySection.FILE_IMPORT -> {
            val viewModel = appViewModel { container ->
                FileImportViewModel(container.contactRepository, container.backupReader)
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            FileImportScreen(
                state = state,
                onFileChosen = viewModel::onFileChosen,
                onModeChange = viewModel::onModeChange,
                onRequestReplace = viewModel::onRequestReplace,
                onDismissReplaceConfirm = viewModel::onDismissReplaceConfirm,
                onCancelPreview = viewModel::onCancelPreview,
                onCommitAppend = viewModel::commitAppend,
                onCommitReplace = viewModel::commitReplace,
                onExportFirst = { onGoTo(FamilySection.EXPORT) },
                onFinished = {
                    viewModel.onFinished()
                    onGoTo(FamilySection.MANAGE)
                },
                onBack = onBack ?: {},
            )
        }

        FamilySection.EXPORT -> {
            val viewModel = appViewModel { container ->
                ExportViewModel(
                    repository = container.contactRepository,
                    writer = container.backupWriter,
                    gateway = container.exportGateway,
                )
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            ExportScreen(
                state = state,
                onGenerate = viewModel::generate,
                onSaveTargetChosen = viewModel::onSaveTargetChosen,
                onBuildShareIntent = viewModel::shareIntent,
                onChooserFor = viewModel::chooserFor,
                onBack = onBack ?: {},
            )
        }

        FamilySection.FONT -> {
            val viewModel = appViewModel { container ->
                FontSettingsViewModel(container.contactRepository)
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            androidx.compose.runtime.LaunchedEffect(state.dismissed) {
                if (state.dismissed) onGoTo(FamilySection.MANAGE)
            }

            // The page renders at the size being previewed, which is why it is
            // scrollable at the largest presets.
            SilverPhoneTheme(fontPreset = state.selected) {
                FontSettingsScreen(
                    state = state,
                    onSelect = viewModel::onSelect,
                    onSave = viewModel::onSave,
                    onCancel = onBack ?: {},
                )
            }
        }

        FamilySection.PREFERENCES -> {
            val viewModel = appViewModel { container ->
                PreferencesViewModel(container.contactRepository)
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            PreferencesScreen(
                state = state,
                onLanguageSelected = viewModel::onLanguageSelected,
                onCountryCodeChanged = viewModel::onCountryCodeChanged,
                onSave = { viewModel.save(AppLanguage::apply) },
                onCancel = onBack ?: {},
            )
        }

        FamilySection.PERMISSION -> {
            val viewModel = appViewModel { container ->
                CallPermissionViewModel(container.callPermission)
            }
            val granted by viewModel.granted.collectAsStateWithLifecycle()
            val context = androidx.compose.ui.platform.LocalContext.current

            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            ) { result -> viewModel.onRequestResult(result) }

            // Re-read the real platform state whenever this screen comes back, so
            // returning from the system settings page shows the truth.
            androidx.lifecycle.compose.LifecycleEventEffect(
                androidx.lifecycle.Lifecycle.Event.ON_RESUME,
            ) { viewModel.refresh() }

            CallPermissionScreen(
                granted = granted,
                onRequestPermission = {
                    permissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
                },
                onBack = onBack ?: {},
            )
        }

        FamilySection.ABOUT -> {
            val viewModel = appViewModel { container ->
                AboutViewModel(container.releaseSource)
            }
            val updateState by viewModel.update.collectAsStateWithLifecycle()

            AboutScreen(
                installedVersionName = viewModel.installedVersionName,
                updateState = updateState,
                onCheckForUpdates = viewModel::checkForUpdates,
                onBack = onBack ?: {},
            )
        }
    }
}
