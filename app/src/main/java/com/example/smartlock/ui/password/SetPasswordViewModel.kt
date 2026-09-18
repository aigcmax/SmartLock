package com.example.smartlock.ui.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlock.domain.repository.LockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetPasswordUiState(
    val password: String = "",
    val confirmPassword: String = "",
    val error: String? = null,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false
)

class SetPasswordViewModel(
    private val repository: LockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetPasswordUiState())
    val uiState: StateFlow<SetPasswordUiState> = _uiState.asStateFlow()

    fun onPasswordChange(value: String) {
        if (value.length > 6 || !value.all { it.isDigit() }) return
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun onConfirmChange(value: String) {
        if (value.length > 6 || !value.all { it.isDigit() }) return
        _uiState.update { it.copy(confirmPassword = value, error = null) }
    }

    fun submit() {
        val state = _uiState.value
        when {
            state.password.length != 6 -> {
                _uiState.update { it.copy(error = "请输入 6 位数字密码") }
                return
            }
            state.password != state.confirmPassword -> {
                _uiState.update { it.copy(error = "两次密码不一致") }
                return
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val ok = repository.setPassword(state.password)
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