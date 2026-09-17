package com.silverphone.app.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverphone.app.data.repository.ContactRepository
import com.silverphone.app.data.repository.MoveDirection
import com.silverphone.app.domain.Contact
import com.silverphone.app.domain.ContactLimits
import com.silverphone.app.domain.ContactSearch
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
) {
    val isSearching: Boolean get() = query.isNotBlank()
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

    val uiState: StateFlow<ManageUiState> = combine(
        repository.observeContacts(),
        query,
    ) { contacts, currentQuery ->
        ManageUiState(
            contacts = ContactSearch.filter(contacts, currentQuery),
            totalCount = contacts.size,
            query = currentQuery,
            atCapacity = contacts.size >= ContactLimits.MAX_CONTACTS,
            loading = false,
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
}
