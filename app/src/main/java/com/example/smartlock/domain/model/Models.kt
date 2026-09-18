package com.example.smartlock.domain.model

/** 蓝牙连接状态 */
sealed interface BleConnectionState {
    data object Unauthorized : BleConnectionState
    data object BluetoothOff : BleConnectionState
    data object NotPaired : BleConnectionState
    data object Connecting : BleConnectionState
    data class Connected(val deviceName: String) : BleConnectionState
    data object Disconnected : BleConnectionState
}

/** 门锁状态 */
enum class LockState {
    LOCKED, UNLOCKED, UNKNOWN;

    companion object {
        fun fromName(value: String?): LockState =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

/** 扫描到的设备 */
data class ScannedDevice(
    val address: String,
    val name: String,
    val rssi: Int
)

/** 蓝牙指令 */
enum class LockCommand { UNLOCK, LOCK }

/** 指令执行结果 */
sealed interface CommandResult {
    data object Success : CommandResult
    data class Failure(val message: String) : CommandResult
}