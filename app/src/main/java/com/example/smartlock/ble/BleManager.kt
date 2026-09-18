package com.example.smartlock.ble

import com.example.smartlock.domain.model.BleConnectionState
import com.example.smartlock.domain.model.CommandResult
import com.example.smartlock.domain.model.LockCommand
import com.example.smartlock.domain.model.ScannedDevice
import kotlinx.coroutines.flow.StateFlow

/**
 * 蓝牙门锁抽象层。
 * 真实项目请实现 RealBleManager（基于 BluetoothLeScanner / BluetoothGatt）。
 */
interface BleManager {

    val connectionState: StateFlow<BleConnectionState>
    val scannedDevices: StateFlow<List<ScannedDevice>>
    val isScanning: StateFlow<Boolean>

    fun startScan()
    fun stopScan()

    suspend fun connect(address: String): Boolean
    suspend fun disconnect()

    /** 下发开锁 / 上锁指令 */
    suspend fun sendCommand(password: String, command: LockCommand): CommandResult

    /** 把密码同步到门锁 */
    suspend fun syncPassword(password: String): CommandResult
}