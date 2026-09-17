package com.silverphone.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverphone.app.data.repository.ContactRepository
import com.silverphone.app.domain.FontPreset
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The font preset, as the Activity needs it.
 *
 * This exists so the Activity never has to build a Flow inside composition: doing
 * that would construct a new pipeline on every recomposition. The preset is
 * exposed as state instead, and the whole tree re-renders when it changes, so
 * switching size never needs an Activity restart.
 */
class AppViewModel(repository: ContactRepository) : ViewModel() {

    val fontPreset: StateFlow<FontPreset> = repository.observeSettings()
        .map { settings -> settings.fontPreset }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = FontPreset.DEFAULT,
        )
}
