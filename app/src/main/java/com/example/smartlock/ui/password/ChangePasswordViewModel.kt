package com.example.smartlock.ui.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlock.domain.repository.LockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChangePasswordUiState(
    val oldPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val error: String? = null,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false
)

class ChangePasswordViewModel(
    private val repository: LockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun onOldChange(value: String) {
        if (value.length > 6 || !value.all { it.isDigit() }) return
        _uiState.update { it.copy(oldPassword = value, error = null) }
    }

    fun onNewChange(value: String) {
        if (value.length > 6 || !value.all { it.isDigit() }) return
        _uiState.update { it.copy(newPassword = value, error = null) }
    }

    fun onConfirmChange(value: String) {
        if (value.length > 6 || !value.all { it.isDigit() }) return
        _uiState.update { it.copy(confirmPassword = value, error = null) }
    }

    fun submit() {
        val state = _uiState.value
        when {
            state.oldPassword.length != 6 -> {
                _uiState.update { it.copy(error = "请输入 6 位原密码") }
                return
            }
            state.newPassword.length != 6 -> {
                _uiState.update { it.copy(error = "请输入 6 位数字密码") }
                return
            }
            state.newPassword != state.confirmPassword -> {
                _uiState.update { it.copy(error = "两次密码不一致") }
                return
            }
            state.newPassword == state.oldPassword -> {
                _uiState.update { it.copy(error = "新密码不能与原密码相同") }
                return
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val verified = repository.verifyPassword(state.oldPassword)
            if (!verified) {
                _uiState.update { it.copy(isLoading = false, error = "原密码错误") }
                return@launch
            }
            val ok = repository.changePassword(state.oldPassword, state.newPassword)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isSuccess = ok,
                    error = if (ok) null else "密码同步失败，请重试"
                )
            }
        }
    }
}