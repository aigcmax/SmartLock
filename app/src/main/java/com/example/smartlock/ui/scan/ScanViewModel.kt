package com.example.smartlock.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlock.domain.model.BleConnectionState
import com.example.smartlock.domain.model.ScannedDevice
import com.example.smartlock.domain.repository.LockRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface ScanEvent {
    data object Paired : ScanEvent
    data class Error(val message: String) : ScanEvent
}

class ScanViewModel(
    private val repository: LockRepository
) : ViewModel() {

    val devices: StateFlow<List<ScannedDevice>> = repository.scannedDevices
    val isScanning: StateFlow<Boolean> = repository.isScanning
    val connectionState: StateFlow<BleConnectionState> = repository.connectionState

    private val _connectingAddress = MutableStateFlow<String?>(null)
    val connectingAddress: StateFlow<String?> = _connectingAddress.asStateFlow()

    private val _events = Channel<ScanEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun startScan() {
        repository.startScan()
    }

    fun stopScan() {
        repository.stopScan()
    }

    fun pair(device: ScannedDevice) {
        if (_connectingAddress.value != null) return
        viewModelScope.launch {
            _connectingAddress.value = device.address
            val ok = repository.pairDevice(device)
            _connectingAddress.value = null
            if (ok) {
                _events.send(ScanEvent.Paired)
            } else {
                _events.send(ScanEvent.Error("匹配失败，请靠近门锁重试"))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopScan()
    }
}