package com.example.smartlock.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlock.domain.model.BleConnectionState
import com.example.smartlock.domain.model.CommandResult
import com.example.smartlock.domain.model.LockState
import com.example.smartlock.domain.repository.LockRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val connectionState: BleConnectionState = BleConnectionState.NotPaired,
    val lockState: LockState = LockState.UNKNOWN,
    val isLoading: Boolean = false
) {
    val isConnected: Boolean get() = connectionState is BleConnectionState.Connected
    val canOperate: Boolean get() = isConnected && !isLoading
    val buttonText: String get() = if (lockState == LockState.UNLOCKED) "上锁" else "解锁"
}

class HomeViewModel(
    private val repository: LockRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        repository.connectionState,
        repository.lockState,
        _isLoading
    ) { connection, lock, loading ->
        HomeUiState(
            connectionState = connection,
            lockState = lock,
            isLoading = loading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    init {
        reconnect()
    }

    fun onResume() {
        if (uiState.value.connectionState !is BleConnectionState.Connected) {
            reconnect()
        }
    }

    private fun reconnect() {
        viewModelScope.launch {
            repository.connectToPairedDevice()
        }
    }

    fun toggle() {
        if (_isLoading.value) return
        val current = uiState.value
        if (!current.isConnected) {
            viewModelScope.launch {
                _messages.send(
                    when (current.connectionState) {
                        BleConnectionState.BluetoothOff -> "请先开启手机蓝牙"
                        BleConnectionState.NotPaired -> "请先匹配门锁"
                        else -> "门锁未连接，请靠近门锁重试"
                    }
                )
            }
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val unlocking = current.lockState != LockState.UNLOCKED
            val result = if (unlocking) repository.unlock() else repository.lock()
            _isLoading.value = false

            when (result) {
                is CommandResult.Success ->
                    _messages.send(if (unlocking) "解锁成功" else "上锁成功")
                is CommandResult.Failure ->
                    _messages.send(result.message)
            }
        }
    }
}