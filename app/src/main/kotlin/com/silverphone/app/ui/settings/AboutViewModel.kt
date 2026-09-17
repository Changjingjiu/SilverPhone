package com.silverphone.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverphone.app.BuildConfig
import com.silverphone.app.domain.AppVersion
import com.silverphone.app.platform.update.ReleaseLookup
import com.silverphone.app.platform.update.ReleaseSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The result of the last update check, and nothing else. */
sealed interface UpdateState {

    /** No check has been run since the screen was opened. */
    data object NotChecked : UpdateState

    /** A check is in flight; the button refuses a second tap meanwhile. */
    data object Checking : UpdateState

    /** The published release is not newer than the installed build. */
    data object UpToDate : UpdateState

    /** A newer release exists. Nothing is downloaded: the family opens the page. */
    data class Available(val version: String, val releaseUrl: String) : UpdateState

    /** The project has no published release yet. Not a failure. */
    data object NotPublishedYet : UpdateState

    /** Offline, timed out, or an answer this app cannot read. */
    data object Failed : UpdateState
}

/**
 * The installed version, plus the update check the family member starts by hand.
 *
 * The check never runs on its own: opening the About screen makes no network request,
 * and every state the screen can show is reachable while the phone is offline. The
 * installed build's own name and code come from BuildConfig, which is the same source
 * the installer used, so what is displayed cannot drift from what was installed.
 */
class AboutViewModel(
    private val releases: ReleaseSource,
    val installedVersionName: String = BuildConfig.VERSION_NAME,
    val installedVersionCode: Int = BuildConfig.VERSION_CODE,
) : ViewModel() {

    private val installed: AppVersion? = AppVersion.parse(installedVersionName)

    private val _update = MutableStateFlow<UpdateState>(UpdateState.NotChecked)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    fun checkForUpdates() {
        // A second tap while the first is still running is not a second request.
        if (_update.value == UpdateState.Checking) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = when (val lookup = releases.latestRelease()) {
                is ReleaseLookup.Found -> judge(lookup)
                ReleaseLookup.NotPublishedYet -> UpdateState.NotPublishedYet
                ReleaseLookup.Unreachable -> UpdateState.Failed
            }
        }
    }

    private fun judge(lookup: ReleaseLookup.Found): UpdateState {
        // A tag that is not a version, or an installed name that cannot be read, is an
        // answer this app cannot judge. Saying "up to date" would be a guess.
        val published = AppVersion.parse(lookup.tag) ?: return UpdateState.Failed
        val current = installed ?: return UpdateState.Failed
        return if (published > current) {
            UpdateState.Available(version = lookup.tag, releaseUrl = lookup.pageUrl)
        } else {
            UpdateState.UpToDate
        }
    }
}
