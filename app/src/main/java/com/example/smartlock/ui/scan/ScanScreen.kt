package com.example.smartlock.ui.scan

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartlock.domain.model.ScannedDevice
import com.example.smartlock.ui.common.requiredBlePermissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onPaired: () -> Unit
) {
    val context = LocalContext.current
    val permissions = remember { requiredBlePermissions() }

    fun checkGranted(): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var permissionGranted by remember { mutableStateOf(checkGranted()) }
    // 是否已在本页面请求过一次
    var hasRequested by remember { mutableStateOf(false) }
    // 请求失败后再次点击"重新授权"也允许重新弹（系统会自动决定是否弹）
    var requestAttempted by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasRequested = true
        permissionGranted = result.values.all { it }
        if (permissionGranted) {
            viewModel.startScan()
        }
    }

    // 首次进入：未授权则请求，已授权则扫描
    LaunchedEffect(Unit) {
        if (permissionGranted) {
            viewModel.startScan()
        } else {
            requestAttempted = true
            launcher.launch(permissions)
        }
    }

    // 从系统设置返回时重新检查
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !permissionGranted) {
                if (checkGranted()) {
                    permissionGranted = true
                    viewModel.startScan()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ScanEvent.Paired -> onPaired()
                is ScanEvent.Error -> { /* 可接 Snackbar */ }
            }
        }
    }

    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val connectingAddress by viewModel.connectingAddress.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("匹配门锁") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (permissionGranted) {
                                viewModel.startScan()
                            } else {
                                requestAttempted = true
                                launcher.launch(permissions)
                            }
                        },
                        enabled = !isScanning
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "重新扫描")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !permissionGranted -> PermissionBlock(
                    // 只有真正请求过一次之后，才展示"去系统设置"的强引导
                    showSettingsButton = hasRequested && requestAttempted,
                    onRetry = {
                        requestAttempted = true
                        launcher.launch(permissions)
                    },
                    onOpenSettings = { context.openAppSettings() }
                )
                devices.isEmpty() && isScanning -> ScanningHint()
                devices.isEmpty() -> EmptyHint()
                else -> DeviceList(
                    devices = devices,
                    connectingAddress = connectingAddress,
                    onPair = viewModel::pair
                )
            }
        }
    }
}

@Composable
private fun PermissionBlock(
    showSettingsButton: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "需要蓝牙权限才能连接门锁",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "用于连接门锁并完成解锁/上锁",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onRetry) {
            Text("重新授权")
        }
        if (showSettingsButton) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenSettings) {
                Text("去系统设置")
            }
        }
    }
}

@Composable
private fun ScanningHint() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("正在扫描附近门锁…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyHint() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "未发现门锁，请靠近门锁后重试",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeviceList(
    devices: List<ScannedDevice>,
    connectingAddress: String?,
    onPair: (ScannedDevice) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(devices, key = { it.address }) { device ->
            DeviceRow(
                device = device,
                connecting = connectingAddress == device.address,
                enabled = connectingAddress == null,
                onClick = { onPair(device) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun DeviceRow(
    device: ScannedDevice,
    connecting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Phone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${device.address}  ·  信号 ${device.rssi} dBm",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (connecting) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}