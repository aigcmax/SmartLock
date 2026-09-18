package com.example.smartlock.data.repository

import com.example.smartlock.ble.BleManager
import com.example.smartlock.data.local.LockDeviceDao
import com.example.smartlock.data.local.LockDeviceEntity
import com.example.smartlock.data.security.PasswordCipher
import com.example.smartlock.domain.model.BleConnectionState
import com.example.smartlock.domain.model.CommandResult
import com.example.smartlock.domain.model.LockCommand
import com.example.smartlock.domain.model.LockState
import com.example.smartlock.domain.model.ScannedDevice
import com.example.smartlock.domain.repository.LockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class LockRepositoryImpl(
    private val dao: LockDeviceDao,
    private val ble: BleManager
) : LockRepository {

    override val connectionState: StateFlow<BleConnectionState> = ble.connectionState
    override val scannedDevices: StateFlow<List<ScannedDevice>> = ble.scannedDevices
    override val isScanning: StateFlow<Boolean> = ble.isScanning

    override val lockState: Flow<LockState> = dao.observeDevice()
        .map { LockState.fromName(it?.lockState) }
        .distinctUntilChanged()

    override val deviceName: Flow<String?> = dao.observeDevice()
        .map { it?.name }
        .distinctUntilChanged()

    override val deviceAddress: Flow<String?> = dao.observeDevice()
        .map { it?.address }
        .distinctUntilChanged()

    override fun startScan() = ble.startScan()

    override fun stopScan() = ble.stopScan()

    override suspend fun hasPairedDevice(): Boolean = dao.getDevice() != null

    override suspend fun hasPassword(): Boolean {
        val device = dao.getDevice() ?: return false
        return device.encryptedPassword.isNotBlank()
    }

    override suspend fun pairDevice(device: ScannedDevice): Boolean {
        val connected = ble.connect(device.address)
        if (!connected) return false
        dao.upsert(
            LockDeviceEntity(
                id = 1,
                name = device.name,
                address = device.address,
                encryptedPassword = "",
                passwordIv = "",
                lockState = LockState.UNKNOWN.name,
                pairedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    override suspend fun connectToPairedDevice(): Boolean {
        val device = dao.getDevice() ?: return false
        return ble.connect(device.address)
    }

    override suspend fun unpair() {
        ble.disconnect()
        dao.clear()
    }

    override suspend fun setPassword(password: String): Boolean {
        if (dao.getDevice() == null) return false
        val sync = ble.syncPassword(password)
        if (sync !is CommandResult.Success) return false
        val encrypted = PasswordCipher.encrypt(password)
        dao.updatePassword(encrypted.cipherText, encrypted.iv)
        return true
    }

    override suspend fun verifyPassword(password: String): Boolean {
        val device = dao.getDevice() ?: return false
        if (device.encryptedPassword.isBlank()) return false
        val stored = PasswordCipher.decrypt(
            PasswordCipher.EncryptedData(device.encryptedPassword, device.passwordIv)
        )
        return stored == password
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String): Boolean {
        if (!verifyPassword(oldPassword)) return false
        val sync = ble.syncPassword(newPassword)
        if (sync !is CommandResult.Success) return false
        val encrypted = PasswordCipher.encrypt(newPassword)
        dao.updatePassword(encrypted.cipherText, encrypted.iv)
        return true
    }

    override suspend fun unlock(): CommandResult {
        val device = dao.getDevice() ?: return CommandResult.Failure("未匹配门锁")
        val password = PasswordCipher.decrypt(
            PasswordCipher.EncryptedData(device.encryptedPassword, device.passwordIv)
        )
        if (password.isBlank()) return CommandResult.Failure("请先设置密码")
        val result = ble.sendCommand(password, LockCommand.UNLOCK)
        if (result is CommandResult.Success) {
            dao.updateLockState(LockState.UNLOCKED.name)
        }
        return result
    }

    override suspend fun lock(): CommandResult {
        val device = dao.getDevice() ?: return CommandResult.Failure("未匹配门锁")
        val password = PasswordCipher.decrypt(
            PasswordCipher.EncryptedData(device.encryptedPassword, device.passwordIv)
        )
        if (password.isBlank()) return CommandResult.Failure("请先设置密码")
        val result = ble.sendCommand(password, LockCommand.LOCK)
        if (result is CommandResult.Success) {
            dao.updateLockState(LockState.LOCKED.name)
        }
        return result
    }
}