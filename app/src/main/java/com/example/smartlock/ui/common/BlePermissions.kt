package com.example.smartlock.ui.common

import android.Manifest
import android.os.Build

/**
 * 按系统版本返回所需的蓝牙权限：
 * - Android 12 (S) 及以上：BLUETOOTH_SCAN + BLUETOOTH_CONNECT
 * - Android 11 及以下：ACCESS_FINE_LOCATION
 */
fun requiredBlePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }