package com.example.smartlock.ble

import android.bluetooth.BluetoothManager
import android.content.Context
import com.example.smartlock.domain.model.BleConnectionState
import com.example.smartlock.domain.model.CommandResult
import com.example.smartlock.domain.model.LockCommand
import com.example.smartlock.domain.model.ScannedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 模拟 BLE 实现，用于在没有硬件的情况下完整跑通业务流程。
 * 替换为真实实现时，只需保证接口行为一致即可。
 */
class FakeBleManager(
    private val context: Context
) : BleManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _connectionState =
        MutableStateFlow<BleConnectionState>(BleConnectionState.NotPaired)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    override val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null
    private var connectedAddress: String? = null

    private val mockDevices = listOf(
        ScannedDevice("AA:BB:CC:11:22:33", "SmartLock-A1B2", -42),
        ScannedDevice("AA:BB:CC:44:55:66", "SmartLock-C3D4", -61),
        ScannedDevice("AA:BB:CC:77:88:99", "SmartLock-E5F6", -78)
    )

    override fun startScan() {
        if (scanJob?.isActive == true) return
        if (!isBluetoothEnabled()) {
            _connectionState.value = BleConnectionState.BluetoothOff
            return
        }
        _scannedDevices.value = emptyList()
        _isScanning.value = true
        scanJob = scope.launch {
            delay(700)
            _scannedDevices.value = mockDevices.take(1)
            delay(600)
            _scannedDevices.value = mockDevices.take(2)
            delay(600)
            _scannedDevices.value = mockDevices
            delay(1800)
            _isScanning.value = false
        }
    }

    override fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    override suspend fun connect(address: String): Boolean {
        stopScan()
        if (!isBluetoothEnabled()) {
            _connectionState.value = BleConnectionState.BluetoothOff
            return false
        }
        _connectionState.value = BleConnectionState.Connecting
        delay(900)
        val device = mockDevices.firstOrNull { it.address == address }
        return if (device != null) {
            connectedAddress = address
            _connectionState.value = BleConnectionState.Connected(device.name)
            true
        } else {
            _connectionState.value = BleConnectionState.Disconnected
            false
        }
    }

    override suspend fun disconnect() {
        stopScan()
        connectedAddress = null
        _connectionState.value = BleConnectionState.NotPaired
    }

    override suspend fun sendCommand(
        password: String,
        command: LockCommand
    ): CommandResult {
        if (password.isBlank()) return CommandResult.Failure("密码为空")
        if (_connectionState.value !is BleConnectionState.Connected) {
            return CommandResult.Failure("门锁未连接，请靠近门锁重试")
        }
        delay(600)
        return CommandResult.Success
    }

    override suspend fun syncPassword(password: String): CommandResult {
        if (password.isBlank()) return CommandResult.Failure("密码为空")
        if (_connectionState.value !is BleConnectionState.Connected) {
            return CommandResult.Failure("门锁未连接")
        }
        delay(700)
        return CommandResult.Success
    }

    private fun isBluetoothEnabled(): Boolean = runCatching {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter?.isEnabled == true
    }.getOrDefault(true)
}