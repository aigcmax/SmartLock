package com.example.smartlock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LockDeviceDao {

    @Query("SELECT * FROM lock_device WHERE id = 1")
    fun observeDevice(): Flow<LockDeviceEntity?>

    @Query("SELECT * FROM lock_device WHERE id = 1")
    suspend fun getDevice(): LockDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: LockDeviceEntity)

    @Query("UPDATE lock_device SET lockState = :state WHERE id = 1")
    suspend fun updateLockState(state: String)

    @Query("UPDATE lock_device SET encryptedPassword = :cipher, passwordIv = :iv WHERE id = 1")
    suspend fun updatePassword(cipher: String, iv: String)

    @Query("DELETE FROM lock_device")
    suspend fun clear()
}