package com.silverphone.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverphone.app.data.repository.ContactRepository
import com.silverphone.app.domain.FontPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FontUiState(
    val selected: FontPreset = FontPreset.DEFAULT,
    val saved: FontPreset = FontPreset.DEFAULT,
    val dismissed: Boolean = false,
    /** The write failed, so the page stays put and says so. */
    val saveFailed: Boolean = false,
)

/**
 * S10 state.
 *
 * The choice is a draft until it is saved, so cancelling really does restore the
 * previous size instead of leaving a half-applied preference behind.
 */
class FontSettingsViewModel(
    private val repository: ContactRepository,
) : ViewModel() {

    private val draft = MutableStateFlow<FontPreset?>(null)
    private val dismissed = MutableStateFlow(false)
    private val saveFailed = MutableStateFlow(false)

    val uiState: StateFlow<FontUiState> = combine(
        repository.observeSettings(),
        draft,
        dismissed,
        saveFailed,
    ) { settings, pending, wasDismissed, didFail ->
        FontUiState(
            selected = pending ?: settings.fontPreset,
            saved = settings.fontPreset,
            dismissed = wasDismissed,
            saveFailed = didFail,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FontUiState(),
    )

    fun onSelect(preset: FontPreset) {
        draft.value = preset
        saveFailed.value = false
    }

    fun onSave() {
        val preset = uiState.value.selected
        viewModelScope.launch {
            if (!repository.setFontPreset(preset)) {
                saveFailed.value = true
                return@launch
            }
            draft.value = null
            dismissed.value = true
        }
    }

    fun onCancel() {
        draft.value = null
        dismissed.value = true
    }
}
