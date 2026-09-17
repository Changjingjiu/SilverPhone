package com.silverphone.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverphone.app.data.repository.ContactRepository
import com.silverphone.app.domain.Contact
import com.silverphone.app.platform.phone.DialCoordinator
import com.silverphone.app.platform.phone.DialProblem
import com.silverphone.app.platform.phone.DialState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What the home screen knows about the stored contacts. */
sealed interface ContactsLoad {
    /** Structure is on screen, but no cards are drawn yet. */
    data object Loading : ContactsLoad

    data class Loaded(val contacts: List<Contact>) : ContactsLoad

    /** Reading failed. Distinct from "no contacts", and never shown as empty. */
    data object Failed : ContactsLoad
}

data class HomeUiState(
    val load: ContactsLoad = ContactsLoad.Loading,
    val dialState: DialState = DialState.Ready,
    /**
     * A tap is waiting on the system permission dialog.
     *
     * The screen uses this to hold the help overlay back until the dialog has been
     * answered: showing help behind the dialog made it look as though the app had
     * already given up on the tap that asked for permission.
     */
    val awaitingPermission: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: ContactRepository,
    private val coordinator: DialCoordinator,
) : ViewModel() {

    /** Bumped by [onRetry]; each value re-subscribes to the database. */
    private val reload = MutableStateFlow(0)

    private val contacts: Flow<ContactsLoad> = reload.flatMapLatest {
        // `catch` inside the flatMapLatest is what makes retry meaningful: after a
        // read failure the inner flow completes, so only a new subscription can
        // produce data again. Catching outside would leave the failure permanent.
        repository.observeContacts()
            .map<_, ContactsLoad> { ContactsLoad.Loaded(it) }
            .catch { emit(ContactsLoad.Failed) }
    }

    /**
     * The contact whose tap is waiting on the call permission.
     *
     * Held only in memory, and only until the system dialog answers. It is a
     * continuation of a tap the user already made, not a stored command: after
     * process death there is nothing left to resume.
     */
    private val pendingContactId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        contacts,
        coordinator.state,
        pendingContactId,
    ) { load, dialState, pending ->
        HomeUiState(
            load = load,
            dialState = dialState,
            awaitingPermission = pending != null &&
                dialState == DialState.Problem(DialProblem.PermissionMissing),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    /**
     * A tap on a card. The coordinator decides whether it becomes a call; the screen
     * only reports the tap, so a rapid double tap can never dispatch twice.
     */
    fun onContactTapped(contact: Contact) {
        pendingContactId.value = contact.id
        coordinator.requestDial(contact.id)
    }

    /**
     * The answer to the inline permission dialog.
     *
     * Granted: the call the user asked for goes through, because the tap already
     * expressed the intent and making them tap again was the complaint. Denied: the
     * pending tap is dropped and the help screen explains what to do.
     */
    fun onCallPermissionResult(granted: Boolean) {
        if (!granted) {
            pendingContactId.value = null
            return
        }
        val contactId = pendingContactId.value ?: return
        pendingContactId.value = null
        coordinator.acknowledgeProblem()
        coordinator.requestDial(contactId, resumed = true)
    }

    fun onProblemAcknowledged() {
        pendingContactId.value = null
        coordinator.acknowledgeProblem()
    }

    /** Re-reads the contacts after a read failure. */
    fun onRetry() {
        reload.update { it + 1 }
    }
}
