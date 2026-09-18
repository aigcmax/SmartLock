package com.example.smartlock.di

import android.content.Context
import com.example.smartlock.ble.BleManager
import com.example.smartlock.ble.FakeBleManager
import com.example.smartlock.ble.uart.RealBleManager
import com.example.smartlock.data.local.AppDatabase
import com.example.smartlock.data.repository.LockRepositoryImpl
import com.example.smartlock.domain.repository.LockRepository

/**
 * 手动依赖注入容器。
 * 接入真实硬件时，仅需把 FakeBleManager 替换为 RealBleManager。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    private val database: AppDatabase = AppDatabase.get(appContext)

    //val bleManager: BleManager = FakeBleManager(appContext)
    val bleManager: BleManager = RealBleManager(appContext)

    val lockRepository: LockRepository =
        LockRepositoryImpl(database.lockDeviceDao(), bleManager)
}