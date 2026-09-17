package com.silverphone.app.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverphone.app.data.repository.ContactRepository
import com.silverphone.app.data.repository.MoveDirection
import com.silverphone.app.domain.Contact
import com.silverphone.app.domain.ContactLimits
import com.silverphone.app.domain.ContactSearch
import com.silverphone.app.domain.ContactWriteResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
data class ManageUiState(
    val contacts: List<Contact> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val atCapacity: Boolean = false,
    /**
     * True until the first database emission arrives. Without it the screen showed
     * "还没有亲人" for the first frames of a list that is in fact populated, which
     * reads as data loss.
     */
    val loading: Boolean = true,
    /** Ids the family member has ticked for deletion. Empty means not selecting. */
    val selected: Set<String> = emptySet(),
    /**
     * A bulk delete is in flight. The screen refuses a second confirmation while it is
     * true, so a fast double tap cannot run the transaction twice.
     */
    val deleting: Boolean = false,
    /** The last bulk delete failed; the selection is kept so it can be tried again. */
    val deleteFailed: Boolean = false,
) {
    val isSearching: Boolean get() = query.isNotBlank()

    /** True while the screen is in multi-select mode. */
    val isSelecting: Boolean get() = selected.isNotEmpty()
}

/**
 * S04 state.
 *
 * Searching only filters this list; it never reorders the home screen. Reordering
 * is explicit, one neighbour at a time, and always writes through the repository.
 */
class ManageContactsViewModel(
    private val repository: ContactRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selection = MutableStateFlow<Set<String>>(emptySet())
    private val deleting = MutableStateFlow(false)
    private val deleteFailed = MutableStateFlow(false)

    val uiState: StateFlow<ManageUiState> = combine(
        repository.observeContacts(),
        query,
        selection,
        combine(deleting, deleteFailed) { busy, failed -> busy to failed },
    ) { contacts, currentQuery, selected, deleteState ->
        ManageUiState(
            contacts = ContactSearch.filter(contacts, currentQuery),
            totalCount = contacts.size,
            query = currentQuery,
            atCapacity = contacts.size >= ContactLimits.MAX_CONTACTS,
            loading = false,
            // Ids that no longer exist cannot stay selected: the list is the truth, and
            // a deletion elsewhere must not leave a ghost in the count.
            selected = selected.intersect(contacts.map { contact -> contact.id }.toSet()),
            deleting = deleteState.first,
            deleteFailed = deleteState.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ManageUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onMove(id: String, direction: MoveDirection) {
        viewModelScope.launch { repository.moveContact(id, direction) }
    }

    /**
     * A long press starts multi-select with this contact ticked. A second long press on
     * a different contact adds it, so the gesture can build a selection without leaving
     * the list.
     */
    fun onLongPress(id: String) {
        deleteFailed.value = false
        selection.value = selection.value + id
    }

    /** A plain tap while selecting ticks or unticks one contact. */
    fun onToggleSelected(id: String) {
        deleteFailed.value = false
        selection.value = if (id in selection.value) {
            selection.value - id
        } else {
            selection.value + id
        }
    }

    fun onClearSelection() {
        deleteFailed.value = false
        selection.value = emptySet()
    }

    /** Deletes exactly what is ticked, in one transaction. */
    fun onDeleteSelected() {
        if (deleting.value) return
        val ids = selection.value.toList()
        if (ids.isEmpty()) return
        deleting.value = true
        viewModelScope.launch {
            val result = repository.deleteContacts(ids)
            deleting.value = false
            if (result is ContactWriteResult.Success) {
                selection.value = emptySet()
                deleteFailed.value = false
            } else {
                deleteFailed.value = true
            }
        }
    }
}
