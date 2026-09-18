package com.example.smartlock.domain.repository

import com.example.smartlock.domain.model.BleConnectionState
import com.example.smartlock.domain.model.CommandResult
import com.example.smartlock.domain.model.LockState
import com.example.smartlock.domain.model.ScannedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LockRepository {

    val connectionState: StateFlow<BleConnectionState>
    val scannedDevices: StateFlow<List<ScannedDevice>>
    val isScanning: StateFlow<Boolean>
    val lockState: Flow<LockState>
    val deviceName: Flow<String?>
    val deviceAddress: Flow<String?>

    fun startScan()
    fun stopScan()

    suspend fun hasPairedDevice(): Boolean
    suspend fun hasPassword(): Boolean

    suspend fun pairDevice(device: ScannedDevice): Boolean
    suspend fun connectToPairedDevice(): Boolean
    suspend fun unpair()

    suspend fun setPassword(password: String): Boolean
    suspend fun verifyPassword(password: String): Boolean
    suspend fun changePassword(oldPassword: String, newPassword: String): Boolean

    suspend fun unlock(): CommandResult
    suspend fun lock(): CommandResult
}