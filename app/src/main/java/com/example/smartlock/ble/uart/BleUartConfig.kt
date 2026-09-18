package com.example.smartlock.ble.uart

import java.util.UUID

/**
 * BLE UART 协议配置。
 * 默认使用 Nordic UART Service (NUS)。
 * 若你的硬件使用其他 UUID（如 HM-10 的 FFE0/FFE1），改这里即可。
 */
object BleUartConfig {

    // ============ Nordic UART Service ============
    val SERVICE_UUID: UUID =
        UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val RX_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    val TX_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

    // ============ 备用：HM-10 / CC2541 ============
    // val SERVICE_UUID: UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
    // val RX_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
    // val TX_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")

    // ============ 通用 CCCD ============
    val CCCD_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    // ============ 超时（毫秒）============
    const val SCAN_TIMEOUT_MS = 15_000L
    const val CONNECT_TIMEOUT_MS = 10_000L
    const val DISCOVER_TIMEOUT_MS = 10_000L
    const val NOTIFY_TIMEOUT_MS = 3_000L
    const val WRITE_TIMEOUT_MS = 3_000L
    const val COMMAND_TIMEOUT_MS = 5_000L

    // ============ 分包 ============
    /** 默认 BLE MTU 23 时，可写 payload = 20 字节。此处保守使用 20。 */
    const val DEFAULT_CHUNK_SIZE = 20
}