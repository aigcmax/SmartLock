package com.example.smartlock.ble.uart

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.smartlock.ble.BleManager
import com.example.smartlock.domain.model.BleConnectionState
import com.example.smartlock.domain.model.CommandResult
import com.example.smartlock.domain.model.LockCommand
import com.example.smartlock.domain.model.ScannedDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("MissingPermission")
class RealBleManager(
    private val context: Context
) : BleManager {

    private val tag = "RealBleManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _connectionState = MutableStateFlow<BleConnectionState>(
        BleConnectionState.NotPaired
    )
    override val connectionState: StateFlow<BleConnectionState> =
        _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    override val scannedDevices: StateFlow<List<ScannedDevice>> =
        _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private var txChar: BluetoothGattCharacteristic? = null

    private var connectDeferred: CompletableDeferred<Boolean>? = null
    private var discoverDeferred: CompletableDeferred<Boolean>? = null
    private var writeDeferred: CompletableDeferred<Boolean>? = null
    private var notifyDeferred: CompletableDeferred<Boolean>? = null
    private var mtuDeferred: CompletableDeferred<Int>? = null

    private val receiveBuffer = ByteArrayOutputStream()
    private var commandDeferred: CompletableDeferred<BleUartProtocol.Parsed>? = null

    private val gattMutex = Mutex()
    private var scanJob: Job? = null
    private var keepAliveJob: Job? = null
    private var closingGatt = false

    private val deviceNamePrefixes = listOf(
        "ESP32-C3-Lock",
        "SmartLock",
        "ESP32",
        "NUS"
    )
    private val SHOW_ALL_DEVICES = false

    // ============================================================
    // 权限 / 蓝牙状态
    // ============================================================
    private fun hasBlePermission(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return perms.all {
            ContextCompat.checkSelfPermission(context, it) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun adapter(): BluetoothAdapter? {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter
    }

    private fun isBluetoothOn(): Boolean = adapter()?.isEnabled == true

    private fun isDeviceNameMatched(name: String): Boolean {
        if (SHOW_ALL_DEVICES) return true
        val lower = name.lowercase()
        return deviceNamePrefixes.any { lower.startsWith(it.lowercase()) }
    }

    // ============================================================
    // 扫描
    // ============================================================
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = runCatching { device.name }.getOrNull()
                ?: result.scanRecord?.deviceName
                ?: return
            if (!isDeviceNameMatched(name)) return

            val item = ScannedDevice(
                address = device.address,
                name = name,
                rssi = result.rssi
            )
            val list = _scannedDevices.value.toMutableList()
            val idx = list.indexOfFirst { it.address == item.address }
            if (idx >= 0) list[idx] = item else list.add(item)
            _scannedDevices.value = list
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(tag, "scan failed: $errorCode")
            _isScanning.value = false
        }
    }

    override fun startScan() {
        if (!hasBlePermission()) {
            _connectionState.value = BleConnectionState.Unauthorized
            return
        }
        if (!isBluetoothOn()) {
            _connectionState.value = BleConnectionState.BluetoothOff
            return
        }
        val scanner = adapter()?.bluetoothLeScanner ?: return
        if (scanJob?.isActive == true) return

        _scannedDevices.value = emptyList()
        _isScanning.value = true

        val filters: List<ScanFilter> = emptyList()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        runCatching {
            scanner.startScan(filters, settings, scanCallback)
        }.onFailure { e ->
            Log.e(tag, "startScan failed", e)
            _isScanning.value = false
            return
        }

        scanJob = scope.launch {
            delay(BleUartConfig.SCAN_TIMEOUT_MS)
            stopScan()
        }
    }

    override fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        val scanner = adapter()?.bluetoothLeScanner ?: return
        runCatching { scanner.stopScan(scanCallback) }
        _isScanning.value = false
    }

    // ============================================================
    // 连接
    // ============================================================
    override suspend fun connect(address: String): Boolean {
        if (!hasBlePermission()) {
            _connectionState.value = BleConnectionState.Unauthorized
            return false
        }
        if (!isBluetoothOn()) {
            _connectionState.value = BleConnectionState.BluetoothOff
            return false
        }

        // ⚠️ 关键：先停扫描、再等待上一次 close 完成
        stopScan()
        stopKeepAlive()

        // 如果上一个 gatt 还没关闭完成，等待
        var waited = 0L
        while (closingGatt && waited < 2000) {
            delay(50)
            waited += 50
        }

        closeGatt()
        delay(BleUartConfig.RECONNECT_DELAY_MS)  // ⚠️ 增加延迟，让栈清理

        val device: BluetoothDevice = runCatching {
            adapter()?.getRemoteDevice(address)
        }.getOrNull() ?: return false

        _connectionState.value = BleConnectionState.Connecting
        connectDeferred = CompletableDeferred()

        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }

        val connected = withTimeoutOrNull(BleUartConfig.CONNECT_TIMEOUT_MS.milliseconds) {
            connectDeferred?.await() ?: false
        } ?: false

        if (!connected) {
            _connectionState.value = BleConnectionState.Disconnected
            closeGatt()
            return false
        }

        // ⚠️ 连接成功后立即请求高优先级
        runCatching {
            gatt?.requestConnectionPriority(
                BluetoothGatt.CONNECTION_PRIORITY_HIGH
            )
            Log.d(tag, "requestConnectionPriority HIGH")
        }

        // ⚠️ 请求 MTU
        runCatching {
            mtuDeferred = CompletableDeferred()
            gatt?.requestMtu(BleUartConfig.TARGET_MTU)
            val mtu = withTimeoutOrNull(2000) { mtuDeferred?.await() } ?: 23
            Log.d(tag, "negotiated MTU = $mtu")
        }
        mtuDeferred = null

        // 服务发现
        val discovered = discoverServices()
        if (!discovered) {
            _connectionState.value = BleConnectionState.Disconnected
            closeGatt()
            return false
        }

        val service = gatt?.getService(BleUartConfig.SERVICE_UUID)
        rxChar = service?.getCharacteristic(BleUartConfig.RX_CHARACTERISTIC_UUID)
        txChar = service?.getCharacteristic(BleUartConfig.TX_CHARACTERISTIC_UUID)

        if (rxChar == null || txChar == null) {
            Log.e(tag, "NUS Service/Char not found on $address")
            _connectionState.value = BleConnectionState.Disconnected
            closeGatt()
            return false
        }

        val notifyOk = enableNotification(txChar!!)
        if (!notifyOk) {
            Log.e(tag, "enable TX notification failed")
            _connectionState.value = BleConnectionState.Disconnected
            closeGatt()
            return false
        }

        _connectionState.value = BleConnectionState.Connected(
            runCatching { device.name }.getOrNull() ?: address
        )

        // ⚠️ 启动心跳保活
        startKeepAlive()

        return true
    }

    override suspend fun disconnect() {
        stopKeepAlive()
        _connectionState.value = BleConnectionState.NotPaired
        closeGatt()
    }

    private fun closeGatt() {
        keepAliveJob?.cancel()
        keepAliveJob = null

        val g = gatt
        gatt = null
        rxChar = null
        txChar = null
        receiveBuffer.reset()
        connectDeferred = null
        discoverDeferred = null
        writeDeferred = null
        notifyDeferred = null
        mtuDeferred = null
        commandDeferred = null

        if (g == null) return

        closingGatt = true
        runCatching { g.disconnect() }

        // ⚠️ 延迟 close，给栈清理时间
        mainHandler.postDelayed({
            runCatching { g.close() }
            closingGatt = false
        }, BleUartConfig.CLOSE_DELAY_MS)
    }

    private suspend fun discoverServices(): Boolean {
        val g = gatt ?: return false
        discoverDeferred = CompletableDeferred()
        if (!g.discoverServices()) return false
        return withTimeoutOrNull(BleUartConfig.DISCOVER_TIMEOUT_MS.milliseconds) {
            discoverDeferred?.await() ?: false
        } ?: false
    }

    private suspend fun enableNotification(char: BluetoothGattCharacteristic): Boolean {
        val g = gatt ?: return false
        if (!g.setCharacteristicNotification(char, true)) return false

        val cccd = char.getDescriptor(BleUartConfig.CCCD_UUID) ?: return false
        notifyDeferred = CompletableDeferred()

        val ok = writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        if (!ok) return false

        return withTimeoutOrNull(BleUartConfig.NOTIFY_TIMEOUT_MS.milliseconds) {
            notifyDeferred?.await() ?: false
        } ?: false
    }

    private fun writeDescriptor(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean {
        val g = gatt ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(descriptor)
        }
    }

    // ============================================================
    // 心跳保活
    // ============================================================
    private fun startKeepAlive() {
        stopKeepAlive()
        keepAliveJob = scope.launch {
            while (true) {
                delay(BleUartConfig.KEEP_ALIVE_INTERVAL_MS)
                if (_connectionState.value !is BleConnectionState.Connected) break
                val g = gatt ?: break
                runCatching {
                    g.readRemoteRssi()
                }.onFailure {
                    Log.w(tag, "keepAlive readRemoteRssi failed: ${it.message}")
                }
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    // ============================================================
    // GATT 回调
    // ============================================================
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            Log.d(tag, "onConnectionStateChange status=$status newState=$newState")

            if (newState == BluetoothProfile.STATE_CONNECTED &&
                status == BluetoothGatt.GATT_SUCCESS
            ) {
                connectDeferred?.complete(true)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectDeferred?.complete(false)

                if (_connectionState.value is BleConnectionState.Connected) {
                    _connectionState.value = BleConnectionState.Disconnected
                }

                stopKeepAlive()

                // ⚠️ 延迟 close，让栈有时间清理
                closingGatt = true
                mainHandler.postDelayed({
                    runCatching { gatt.close() }
                    if (this@RealBleManager.gatt === gatt) {
                        this@RealBleManager.gatt = null
                        rxChar = null
                        txChar = null
                    }
                    closingGatt = false
                }, BleUartConfig.CLOSE_DELAY_MS)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(tag, "onServicesDiscovered status=$status")
            discoverDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(tag, "onMtuChanged mtu=$mtu status=$status")
            mtuDeferred?.complete(mtu)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            Log.d(tag, "onReadRemoteRssi rssi=$rssi status=$status")
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == BleUartConfig.CCCD_UUID) {
                notifyDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == BleUartConfig.RX_CHARACTERISTIC_UUID) {
                writeDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == BleUartConfig.TX_CHARACTERISTIC_UUID) {
                onIncoming(value)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            if (characteristic.uuid == BleUartConfig.TX_CHARACTERISTIC_UUID) {
                onIncoming(value)
            }
        }
    }

    // ============================================================
    // 发送 / 接收
    // ============================================================
    override suspend fun sendCommand(
        password: String,
        command: LockCommand
    ): CommandResult = gattMutex.withLock {
        val cmd = when (command) {
            LockCommand.UNLOCK -> BleUartProtocol.Cmd.UNLOCK
            LockCommand.LOCK -> BleUartProtocol.Cmd.LOCK
        }
        val frame = BleUartProtocol.buildPasswordCommand(cmd, password)
        val response = sendFrameAndAwaitResponse(frame)

        if (response == null) {
            CommandResult.Failure("操作超时，请重试")
        } else {
            when (response.cmd) {
                BleUartProtocol.Cmd.ACK -> CommandResult.Success
                BleUartProtocol.Cmd.NACK -> CommandResult.Failure("门锁校验失败")
                else -> CommandResult.Failure("门锁返回未知响应")
            }
        }
    }

    override suspend fun syncPassword(password: String): CommandResult =
        gattMutex.withLock {
            val frame = BleUartProtocol.buildPasswordCommand(
                BleUartProtocol.Cmd.SET_PASSWORD,
                password
            )
            val response = sendFrameAndAwaitResponse(frame)

            if (response == null) {
                CommandResult.Failure("密码同步超时")
            } else {
                when (response.cmd) {
                    BleUartProtocol.Cmd.ACK -> CommandResult.Success
                    BleUartProtocol.Cmd.NACK -> CommandResult.Failure("门锁拒绝设置密码")
                    else -> CommandResult.Failure("门锁返回未知响应")
                }
            }
        }

    private suspend fun sendFrameAndAwaitResponse(
        frame: ByteArray
    ): BleUartProtocol.Parsed? {
        if (_connectionState.value !is BleConnectionState.Connected) return null

        receiveBuffer.reset()
        commandDeferred = CompletableDeferred()

        val wrote = writeFrame(frame)
        if (!wrote) {
            commandDeferred = null
            return null
        }

        val result = withTimeoutOrNull(BleUartConfig.COMMAND_TIMEOUT_MS.milliseconds) {
            commandDeferred?.await()
        }
        commandDeferred = null
        return result
    }

    private suspend fun writeFrame(frame: ByteArray): Boolean {
        val rx = rxChar ?: return false
        var offset = 0
        while (offset < frame.size) {
            val end = minOf(offset + BleUartConfig.DEFAULT_CHUNK_SIZE, frame.size)
            val chunk = frame.copyOfRange(offset, end)
            if (!writeChunk(rx, chunk)) return false
            offset = end
        }
        return true
    }

    private suspend fun writeChunk(
        char: BluetoothGattCharacteristic,
        data: ByteArray
    ): Boolean {
        val g = gatt ?: return false
        writeDeferred = CompletableDeferred()

        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(
                char,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                char.value = data
                g.writeCharacteristic(char)
            }
        }

        if (!started) {
            writeDeferred = null
            return false
        }

        val ok = withTimeoutOrNull(BleUartConfig.WRITE_TIMEOUT_MS.milliseconds) {
            writeDeferred?.await() ?: false
        } ?: false
        writeDeferred = null
        return ok
    }

    private fun onIncoming(data: ByteArray) {
        receiveBuffer.write(data)
        while (true) {
            val buffer = receiveBuffer.toByteArray()
            val result = BleUartProtocol.tryParse(buffer)
            if (result.consumed > 0) {
                val remain = buffer.copyOfRange(result.consumed, buffer.size)
                receiveBuffer.reset()
                receiveBuffer.write(remain)
            }
            val frame = result.frame ?: break
            commandDeferred?.complete(frame)
            break
        }
    }
}