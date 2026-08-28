package com.sonuchaudhary.notificationforward.ui.pairing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sonuchaudhary.notificationforward.data.FamilyRepository
import com.sonuchaudhary.notificationforward.data.PairingRepository
import com.sonuchaudhary.notificationforward.firebase.FirebaseModule
import com.sonuchaudhary.notificationforward.settings.DeviceRole
import com.sonuchaudhary.notificationforward.settings.RoleStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class PairingUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val code: String? = null,
    val paired: Boolean = false
)

/**
 * Backs both pairing screens (child code generation, parent code entry). Async by nature —
 * Cloud Function calls and a Firestore listener — which is exactly the state this app had no
 * ViewModel layer for before this feature; the existing 5 tabs' simpler synchronous state stays
 * as plain Compose state and is left alone.
 */
class PairingViewModel(
    private val roleStore: RoleStore,
    private val pairingRepository: PairingRepository,
    private val familyRepository: FamilyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState())
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    fun generateChildCode() {
        if (_state.value.loading || _state.value.code != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                // Reopening this screen before the local pairingStatus caught up with an ACTIVE
                // family (e.g. app was killed right after a parent redeemed the code) must not
                // re-request a fresh code — that would ask the backend to reset an already-paired
                // child back to PENDING. Short-circuit into the paired state instead.
                val existingFamilyId = roleStore.familyId
                if (existingFamilyId != null && fetchDeviceStatus(existingFamilyId) == "ACTIVE") {
                    applyStatus("ACTIVE")
                    _state.value = _state.value.copy(loading = false)
                    return@launch
                }

                val result = pairingRepository.createPairingCode(roleStore.deviceId, roleStore.displayName)
                roleStore.familyId = result.familyId
                if (result.alreadyPaired) {
                    applyStatus("ACTIVE")
                    _state.value = _state.value.copy(loading = false)
                    return@launch
                }
                familyRepository.recordConsent(result.familyId, roleStore.deviceId)
                _state.value = _state.value.copy(loading = false, code = result.code)
                observeStatus(result.familyId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Failed to create pairing code")
            }
        }
    }

    private suspend fun fetchDeviceStatus(familyId: String): String? {
        return runCatching {
            FirebaseModule.firestore.collection("families").document(familyId)
                .collection("devices").document(roleStore.deviceId).get().await()
                .getString("status")
        }.getOrNull()
    }

    private fun observeStatus(familyId: String) {
        viewModelScope.launch {
            pairingRepository.observeDeviceStatus(familyId, roleStore.deviceId).collect { status ->
                applyStatus(status)
            }
        }
    }

    /** Foreground-only fallback for the listener above, triggered from OnResumeEffect. */
    fun refreshStatusOnce() {
        val familyId = roleStore.familyId ?: return
        if (_state.value.paired) return
        viewModelScope.launch {
            try {
                val doc = FirebaseModule.firestore.collection("families").document(familyId)
                    .collection("devices").document(roleStore.deviceId).get().await()
                doc.getString("status")?.let { applyStatus(it) }
            } catch (_: Exception) {
                // Best-effort; the live listener is the primary path.
            }
        }
    }

    private fun applyStatus(status: String) {
        roleStore.pairingStatus = status
        if (status == "ACTIVE") {
            roleStore.role = DeviceRole.CHILD
            _state.value = _state.value.copy(paired = true)
        }
    }

    fun submitParentCode(code: String) {
        if (_state.value.loading) return
        if (code.isBlank()) {
            _state.value = _state.value.copy(error = "Enter the code shown on the child's device")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val familyId = pairingRepository.consumePairingCode(code, roleStore.deviceId, roleStore.displayName)
                roleStore.role = DeviceRole.PARENT
                roleStore.familyId = familyId
                roleStore.pairingStatus = "ACTIVE"
                _state.value = _state.value.copy(loading = false, paired = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Invalid or expired code")
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                PairingViewModel(
                    roleStore = RoleStore(appContext),
                    pairingRepository = PairingRepository(),
                    familyRepository = FamilyRepository(appContext)
                )
            }
        }
    }
}
