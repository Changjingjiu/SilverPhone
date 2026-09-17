package com.silverphone.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silverphone.app.app.AppContainer

/**
 * The container, made available to screens.
 *
 * Screens receive their dependencies through a ViewModel rather than reaching for
 * the container themselves, so a screen never touches the database or the
 * platform directly.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer was read outside the application root")
}

/** Builds a screen ViewModel with container-provided dependencies. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel { create(container) }
}
