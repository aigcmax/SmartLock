package com.example.smartlock.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.smartlock.ui.home.HomeViewModel
import com.example.smartlock.ui.manage.DeviceManageViewModel
import com.example.smartlock.ui.password.ChangePasswordViewModel
import com.example.smartlock.ui.password.SetPasswordViewModel
import com.example.smartlock.ui.scan.ScanViewModel
import com.example.smartlock.ui.splash.SplashViewModel

class AppViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(SplashViewModel::class.java) ->
            SplashViewModel(container.lockRepository) as T

        modelClass.isAssignableFrom(ScanViewModel::class.java) ->
            ScanViewModel(container.lockRepository) as T

        modelClass.isAssignableFrom(SetPasswordViewModel::class.java) ->
            SetPasswordViewModel(container.lockRepository) as T

        modelClass.isAssignableFrom(ChangePasswordViewModel::class.java) ->
            ChangePasswordViewModel(container.lockRepository) as T

        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(container.lockRepository) as T

        modelClass.isAssignableFrom(DeviceManageViewModel::class.java) ->
            DeviceManageViewModel(container.lockRepository) as T

        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}