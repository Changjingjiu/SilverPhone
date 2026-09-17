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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.silverphone.app.platform.phone.DialProblem
import com.silverphone.app.platform.phone.DialState
import com.silverphone.app.ui.appViewModel
import com.silverphone.app.ui.editor.ContactEditorScreen
import com.silverphone.app.ui.editor.EditorViewModel
import com.silverphone.app.ui.family.FamilyArea
import com.silverphone.app.ui.home.HomeScreen
import com.silverphone.app.ui.home.HomeViewModel

/**
 * The whole in-app navigation graph.
 *
 * There are three destinations, and that is deliberate:
 *
 *  * `home` is the screen the elderly user lives in. One pane, oversized targets,
 *    nothing else on it.
 *  * `family` is everything the family touches. It is one destination, because the
 *    list of sections and the section behind it are the two panes of one
 *    [androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold]; the pane
 *    scaffold owns the movement between them, which is what makes the extra width of a
 *    tablet worth having.
 *  * `editor` is the one full-screen task that both of the other two can open. It is a
 *    modal job - a photo, a name, a number - and it is finished before anything else
 *    happens.
 *
 * Only identifiers travel through the back stack: a contact id, or the literal that
 * means "new". No contact object, no bitmap and no byte array is ever put into a
 * navigation argument or saved state.
 */
object Routes {
    const val HOME = "home"
    const val FAMILY = "family"

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
                onFamilyEntry = { navController.navigate(Routes.FAMILY) },
                onRetry = viewModel::onRetry,
                onProblemAcknowledged = viewModel::onProblemAcknowledged,
                onOpenFamilySettings = {
                    viewModel.onProblemAcknowledged()
                    navController.navigate(Routes.FAMILY)
                },
            )
        }

        composable(Routes.FAMILY) {
            FamilyArea(
                onExit = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onOpenEditor = { contactId -> navController.navigate(Routes.editor(contactId)) },
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
    }
}
