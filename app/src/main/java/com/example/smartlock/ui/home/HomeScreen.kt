package com.example.smartlock.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartlock.domain.model.BleConnectionState
import com.example.smartlock.ui.theme.StatusError
import com.example.smartlock.ui.theme.StatusNeutral
import com.example.smartlock.ui.theme.StatusSuccess
import com.example.smartlock.ui.theme.StatusWarning

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToManage: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 回到前台时自动重连
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 成功 / 失败提示
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LockButton(
                text = uiState.buttonText,
                enabled = uiState.canOperate,
                isLoading = uiState.isLoading,
                onClick = viewModel::toggle,
                onLongClick = onNavigateToManage
            )

            Spacer(Modifier.height(28.dp))

            ConnectionStatus(state = uiState.connectionState)

            Spacer(Modifier.height(8.dp))

            Text(
                text = "长按按钮可进入设备管理",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LockButton(
    text: String,
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(200.dp)
            .clip(CircleShape)
            .background(containerColor)
            .combinedClickable(
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = contentColor,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun ConnectionStatus(state: BleConnectionState) {
    val (dotColor, label) = when (state) {
        is BleConnectionState.Connected ->
            StatusSuccess to "已连接 · ${state.deviceName}"
        BleConnectionState.Connecting ->
            StatusWarning to "连接中…"
        BleConnectionState.Disconnected ->
            StatusNeutral to "未连接"
        BleConnectionState.NotPaired ->
            StatusNeutral to "未匹配门锁"
        BleConnectionState.BluetoothOff ->
            StatusError to "蓝牙未开启"
        BleConnectionState.Unauthorized ->
            StatusError to "蓝牙未授权"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}