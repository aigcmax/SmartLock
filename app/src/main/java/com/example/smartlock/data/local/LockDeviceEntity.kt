package com.example.smartlock.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 门锁设备表。id 固定为 1，只保存单个已配对设备。
 * 密码使用 Android Keystore 加密后以密文 + IV 存储。
 */
@Entity(tableName = "lock_device")
data class LockDeviceEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val address: String,
    val encryptedPassword: String = "",
    val passwordIv: String = "",
    val lockState: String = "UNKNOWN",
    val pairedAt: Long = System.currentTimeMillis()
)