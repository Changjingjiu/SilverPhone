package com.silverphone.app.ui.navigation

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.silverphone.app.app.AppLanguage
import com.silverphone.app.platform.phone.DialProblem
import com.silverphone.app.platform.phone.DialState
import com.silverphone.app.platform.hasReadContactsPermission
import com.silverphone.app.platform.openAppSettingsPage
import com.silverphone.app.ui.appViewModel
import com.silverphone.app.ui.contacts.ContactsImportScreen
import com.silverphone.app.ui.contacts.ContactsImportViewModel
import com.silverphone.app.ui.editor.ContactEditorScreen
import com.silverphone.app.ui.editor.EditorViewModel
import com.silverphone.app.ui.family.FamilySettingsScreen
import com.silverphone.app.ui.family.ManageContactsScreen
import com.silverphone.app.ui.family.ManageContactsViewModel
import com.silverphone.app.ui.family.TransferScreen
import com.silverphone.app.ui.home.HomeScreen
import com.silverphone.app.ui.home.HomeViewModel
import com.silverphone.app.ui.settings.AboutScreen
import com.silverphone.app.ui.settings.AboutViewModel
import com.silverphone.app.ui.settings.CallPermissionScreen
import com.silverphone.app.ui.settings.CallPermissionViewModel
import com.silverphone.app.ui.settings.FontSettingsScreen
import com.silverphone.app.ui.settings.FontSettingsViewModel
import com.silverphone.app.ui.settings.PreferencesViewModel
import com.silverphone.app.ui.settings.PreferencesScreen
import com.silverphone.app.ui.theme.SilverPhoneTheme
import com.silverphone.app.ui.transfer.ExportScreen
import com.silverphone.app.ui.transfer.ExportViewModel
import com.silverphone.app.ui.transfer.FileImportScreen
import com.silverphone.app.ui.transfer.FileImportViewModel

/**
 * Every in-app destination.
 *
 * Only identifiers travel through the back stack: a contact id, or the literal
 * that means "new". No contact object, no bitmap and no byte array is ever put
 * into a navigation argument or saved state.
 */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val MANAGE = "manage"
    const val FONT = "font"
    const val PREFERENCES = "preferences"
    const val PERMISSION = "permission"
    const val ABOUT = "about"
    const val CONTACTS_IMPORT = "contacts-import"
    const val TRANSFER = "transfer"
    const val FILE_IMPORT = "file-import"
    const val EXPORT = "export"

    const val ARG_CONTACT_ID = "contactId"
    const val NEW_CONTACT = "new"

    const val EDITOR_PATTERN = "editor/{$ARG_CONTACT_ID}"

    fun editor(contactId: String?): String = "editor/${contactId ?: NEW_CONTACT}"
}

/** Short enough that an elderly user does not wait for the animation to finish. */
private const val NAV_ANIMATION_MILLIS = 220

@Composable
fun AppNav(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
        // A consistent forward/back slide makes it obvious which way the app just
        // moved, which matters more for a family member working through settings
        // than for someone who reads the title bar.
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(NAV_ANIMATION_MILLIS),
            ) + fadeIn(tween(NAV_ANIMATION_MILLIS))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(NAV_ANIMATION_MILLIS),
            ) + fadeOut(tween(NAV_ANIMATION_MILLIS))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(NAV_ANIMATION_MILLIS),
            ) + fadeIn(tween(NAV_ANIMATION_MILLIS))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(NAV_ANIMATION_MILLIS),
            ) + fadeOut(tween(NAV_ANIMATION_MILLIS))
        },
    ) {
        composable(Routes.HOME) {
            val viewModel = appViewModel { container ->
                HomeViewModel(container.contactRepository, container.dialCoordinator)
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            // The permission is asked for right here, on the tap that needed it.
            // Sending the user to a help screen to read where to go and tap again was
            // the single worst detour in the app.
            val callPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted -> viewModel.onCallPermissionResult(granted) }

            LaunchedEffect(state.dialState) {
                if (state.dialState == DialState.Problem(DialProblem.PermissionMissing)) {
                    callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                }
            }

            HomeScreen(
                state = state,
                onContactTap = viewModel::onContactTapped,
                onFamilyEntry = { navController.navigate(Routes.SETTINGS) },
                onRetry = viewModel::onRetry,
                onProblemAcknowledged = viewModel::onProblemAcknowledged,
                onOpenFamilySettings = {
                    viewModel.onProblemAcknowledged()
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel = appViewModel { container ->
                ManageContactsViewModel(container.contactRepository)
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            FamilySettingsScreen(
                contactCount = state.totalCount,
                onManage = { navController.navigate(Routes.MANAGE) },
                onContactsImport = { navController.navigate(Routes.CONTACTS_IMPORT) },
                onTransfer = { navController.navigate(Routes.TRANSFER) },
                onFont = { navController.navigate(Routes.FONT) },
                onPreferences = { navController.navigate(Routes.PREFERENCES) },
                onPermission = { navController.navigate(Routes.PERMISSION) },
                onAbout = { navController.navigate(Routes.ABOUT) },
                onBackHome = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }

        composable(Routes.MANAGE) {
            val viewModel = appViewModel { container ->
                ManageContactsViewModel(container.contactRepository)
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            ManageContactsScreen(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onAdd = { navController.navigate(Routes.editor(null)) },
                onEdit = { contactId -> navController.navigate(Routes.editor(contactId)) },
                onMove = viewModel::onMove,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.EDITOR_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_CONTACT_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val rawId = entry.arguments?.getString(Routes.ARG_CONTACT_ID)
            val contactId = rawId?.takeIf { it != Routes.NEW_CONTACT }

            val viewModel = appViewModel { container ->
                EditorViewModel(
                    repository = container.contactRepository,
                    photoNormalizer = container.photoNormalizer,
                    contactId = contactId,
                )
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            // The picked file is a step inside this destination, so no image
            // reference is ever passed through navigation.
            var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

            LaunchedEffect(state.saved, state.deleted) {
                if (state.saved || state.deleted) {
                    navController.popBackStack()
                }
            }

            ContactEditorScreen(
                state = state,
                onNameChange = viewModel::onNameChange,
                onPhoneChange = viewModel::onPhoneChange,
                onPlaceholderColorChange = viewModel::onPlaceholderColorChange,
                onPhotoPicked = { uri -> pendingCropUri = uri },
                onCropped = { bitmap ->
                    pendingCropUri = null
                    viewModel.onCroppedBitmap(bitmap)
                },
                onCropCancelled = { pendingCropUri = null },
                onRemovePhoto = viewModel::onRemovePhoto,
                onSave = viewModel::save,
                onDelete = viewModel::delete,
                onFinished = { navController.popBackStack() },
                pendingCropUri = pendingCropUri,
            )
        }

        composable(Routes.FONT) {
            val viewModel = appViewModel { container ->
                FontSettingsViewModel(container.contactRepository)
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(state.dismissed) {
                if (state.dismissed) navController.popBackStack()
            }

            // The page renders at the size being previewed, which is why it is
            // scrollable at the largest presets.
            SilverPhoneTheme(fontPreset = state.selected) {
                FontSettingsScreen(
                    state = state,
                    onSelect = viewModel::onSelect,
                    onSave = viewModel::onSave,
                    onCancel = viewModel::onCancel,
                )
            }
        }

        composable(Routes.CONTACTS_IMPORT) {
            val viewModel = appViewModel { container ->
                ContactsImportViewModel(
                    repository = container.contactRepository,
                    source = container.systemContactsSource,
                    photoNormalizer = container.photoNormalizer,
                )
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted -> viewModel.onPermissionResult(granted) }

            // The permission is read from the platform every time this screen
            // comes back, so granting it in system settings takes effect at once.
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                viewModel.onObservedPermission(context.hasReadContactsPermission())
            }

            ContactsImportScreen(
                state = state,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                onOpenAppSettings = { context.openAppSettingsPage() },
                onManualAdd = { navController.navigate(Routes.editor(null)) },
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
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        // Reading a file and writing one are two ends of the same job, so they sit
        // behind one menu entry and are chosen here rather than in the menu.
        composable(Routes.TRANSFER) {
            TransferScreen(
                onImport = { navController.navigate(Routes.FILE_IMPORT) },
                onExport = { navController.navigate(Routes.EXPORT) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.FILE_IMPORT) {
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
                onExportFirst = { navController.navigate(Routes.EXPORT) },
                onFinished = {
                    viewModel.onFinished()
                    navController.popBackStack(Routes.SETTINGS, inclusive = false)
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.EXPORT) {
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
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PREFERENCES) {
            val viewModel = appViewModel { container ->
                PreferencesViewModel(container.contactRepository)
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            PreferencesScreen(
                state = state,
                onLanguageSelected = viewModel::onLanguageSelected,
                onCountryCodeChanged = viewModel::onCountryCodeChanged,
                onSave = {
                    // Applying the choice is AppCompat's job; see AppLanguage.
                    viewModel.save(AppLanguage::apply)
                },
                onCancel = {
                    viewModel.cancel()
                    navController.popBackStack()
                },
            )
        }

        composable(Routes.PERMISSION) {
            val viewModel = appViewModel { container ->
                CallPermissionViewModel(container.callPermission)
            }
            val granted by viewModel.granted.collectAsStateWithLifecycle()

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { result -> viewModel.onRequestResult(result) }

            // Re-read the real platform state whenever this screen comes back,
            // so returning from the system settings page shows the truth.
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

            CallPermissionScreen(
                granted = granted,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CALL_PHONE) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ABOUT) {
            val viewModel = appViewModel { container ->
                AboutViewModel(container.releaseSource)
            }
            val updateState by viewModel.update.collectAsStateWithLifecycle()

            AboutScreen(
                installedVersionName = viewModel.installedVersionName,
                installedVersionCode = viewModel.installedVersionCode,
                updateState = updateState,
                onCheckForUpdates = viewModel::checkForUpdates,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
