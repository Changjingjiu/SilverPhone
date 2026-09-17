package com.silverphone.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverphone.app.app.AppLanguage
import com.silverphone.app.data.repository.ContactRepository
import com.silverphone.app.domain.CountryCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PreferencesUiState(
    /** The draft, which equals the stored value until the family changes it. */
    val languageTag: String = AppLanguage.SYSTEM,
    val countryCode: String = CountryCode.DEFAULT,
    val saving: Boolean = false,
    /** How the last save ended, so the page can confirm it or say it failed. */
    val outcome: SaveOutcome = SaveOutcome.NONE,
) {
    /** A number the family can recognise their own dialling in. */
    val exampleDial: String get() = CountryCode.apply(EXAMPLE_NUMBER, countryCode)

    val countryCodeInvalid: Boolean get() = !CountryCode.validate(countryCode)

    private companion object {
        const val EXAMPLE_NUMBER = "5550134"
    }
}

/** What the last save did. */
enum class SaveOutcome { NONE, SAVED, FAILED }

/**
 * S12 state.
 *
 * Both values are drafts until Save, for the same reason the text size is: the point
 * of a settings page is that leaving without saving changes nothing. Applying the
 * language is left to the screen, because switching it is a platform call that
 * recreates the Activity.
 */
class PreferencesViewModel(
    private val repository: ContactRepository,
) : ViewModel() {

    private val draftLanguage = MutableStateFlow<String?>(null)
    private val draftCountry = MutableStateFlow<String?>(null)
    private val saving = MutableStateFlow(false)
    private val outcome = MutableStateFlow(SaveOutcome.NONE)

    val uiState: StateFlow<PreferencesUiState> = combine(
        repository.observeSettings(),
        draftLanguage,
        draftCountry,
        saving,
        outcome,
    ) { settings, language, country, isSaving, lastOutcome ->
        PreferencesUiState(
            languageTag = language ?: settings.languageTag,
            countryCode = country ?: settings.countryCode,
            saving = isSaving,
            outcome = lastOutcome,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PreferencesUiState(),
    )

    fun onLanguageSelected(tag: String) {
        draftLanguage.value = tag
        outcome.value = SaveOutcome.NONE
    }

    fun onCountryCodeChanged(value: String) {
        draftCountry.value = value
        outcome.value = SaveOutcome.NONE
    }

    /**
     * Writes both drafts and asks the screen to put the chosen language in force.
     *
     * The language is applied on every save, not only when the tag differs from the
     * stored one. Those two can legitimately disagree - a cold start that has not
     * applied the stored choice yet is exactly that case - and pressing Save is the
     * family saying "make it so". Applying a tag that is already in force is a
     * no-op.
     */
    fun save(onLanguageChanged: (String) -> Unit) {
        val state = uiState.value
        if (state.saving || state.countryCodeInvalid) return

        saving.value = true
        viewModelScope.launch {
            val countryWritten = repository.setCountryCode(state.countryCode)
            val languageWritten = repository.setLanguageTag(state.languageTag)
            saving.value = false
            if (!countryWritten || !languageWritten) {
                outcome.value = SaveOutcome.FAILED
                return@launch
            }
            draftCountry.value = null
            draftLanguage.value = null
            outcome.value = SaveOutcome.SAVED
            onLanguageChanged(state.languageTag)
        }
    }

    /** Drops the drafts, leaving the stored values as they were. */
    fun cancel() {
        draftLanguage.value = null
        draftCountry.value = null
        outcome.value = SaveOutcome.NONE
    }
}
