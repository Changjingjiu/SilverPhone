package com.silverphone.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.silverphone.app.app.AppContainer
import com.silverphone.app.ui.AppViewModel
import com.silverphone.app.ui.LocalAppContainer
import com.silverphone.app.ui.appViewModel
import com.silverphone.app.ui.components.LocalImageLoader
import com.silverphone.app.ui.navigation.AppNav
import com.silverphone.app.ui.theme.SilverPhoneTheme

/**
 * The single Activity.
 *
 * It has two jobs: provide the theme, container and image loader to the Compose
 * tree, and act as the one foreground host allowed to consume a dial request.
 * Starting the call from here - rather than from a Composable body or a state
 * collector that may re-run - is what keeps one tap to exactly one request.
 *
 * It extends [AppCompatActivity] for one reason: `AppCompatDelegate.setApplicationLocales`
 * is the supported way to switch the whole interface language at runtime on API 23,
 * and it persists the choice and recreates the Activity itself. Writing that by hand
 * would mean wrapping every Context by hand and getting `attachBaseContext` right.
 */
class MainActivity : AppCompatActivity() {

    private val container: AppContainer
        get() = (application as App).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            CompositionLocalProvider(
                LocalAppContainer provides container,
                LocalImageLoader provides container.imageLoader,
            ) {
                // The preset comes from a ViewModel rather than a Flow built inline,
                // so composition never constructs a new pipeline.
                val appViewModel = appViewModel { scope -> AppViewModel(scope.contactRepository) }
                val fontPreset by appViewModel.fontPreset.collectAsStateWithLifecycle()

                SilverPhoneTheme(fontPreset = fontPreset) {
                    AppNav()
                }

                DialRequestHost(container = container)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Returning from the system dialer re-enables the cards; it never re-issues
        // the previous request.
        container.dialCoordinator.onHostResumed()
    }
}

/**
 * Collects dial requests while the Activity is started and hands each one to the
 * platform at most once.
 *
 * Requests live in an application-scoped channel, so an Activity recreation does
 * not lose one; [DialCoordinator.claimForDispatch] makes sure a request that was
 * already launched is never launched again.
 */
@Composable
private fun DialRequestHost(container: AppContainer) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // The Activity itself, so the call is started from a foreground Activity
    // context rather than an application context.
    val host = LocalContext.current

    LaunchedEffect(container, host) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            container.dialCoordinator.requests.collect { request ->
                if (container.dialCoordinator.claimForDispatch(request.eventId)) {
                    val outcome = container.phoneLauncher.launch(host, request.phoneNumber)
                    container.dialCoordinator.onLaunchOutcome(request.eventId, outcome)
                }
            }
        }
    }
}
