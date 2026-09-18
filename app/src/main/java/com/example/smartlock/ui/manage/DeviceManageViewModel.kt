package com.example.smartlock.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlock.domain.model.BleConnectionState
import com.example.smartlock.domain.repository.LockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeviceManageUiState(
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val connectionState: BleConnectionState = BleConnectionState.NotPaired,
    val isUnpairing: Boolean = false
)

class DeviceManageViewModel(
    private val repository: LockRepository
) : ViewModel() {

    private val _isUnpairing = MutableStateFlow(false)

    val uiState: StateFlow<DeviceManageUiState> = combine(
        repository.deviceName,
        repository.deviceAddress,
        repository.connectionState,
        _isUnpairing
    ) { name, address, connection, unpairing ->
        DeviceManageUiState(
            deviceName = name,
            deviceAddress = address,
            connectionState = connection,
            isUnpairing = unpairing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DeviceManageUiState()
    )

    fun unpair(onDone: () -> Unit) {
        if (_isUnpairing.value) return
        viewModelScope.launch {
            _isUnpairing.value = true
            repository.unpair()
            _isUnpairing.value = false
            onDone()
        }
    }
}