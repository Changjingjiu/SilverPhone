package com.silverphone.app.ui.settings

import androidx.lifecycle.ViewModel
import com.silverphone.app.platform.phone.CallPermissionGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live CALL_PHONE state for the permission page.
 *
 * The value always comes from the platform, never from storage, and is re-read
 * whenever the screen returns to the foreground - including after the user
 * changes the permission on the system settings page.
 */
class CallPermissionViewModel(
    private val permission: CallPermissionGate,
) : ViewModel() {

    private val _granted = MutableStateFlow(permission.isGranted())
    val granted: StateFlow<Boolean> = _granted.asStateFlow()

    fun refresh() {
        _granted.value = permission.isGranted()
    }

    fun onRequestResult(granted: Boolean) {
        _granted.value = granted
    }
}
