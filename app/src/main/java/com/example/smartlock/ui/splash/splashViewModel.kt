package com.example.smartlock.ui.splash

import androidx.lifecycle.ViewModel
import com.example.smartlock.domain.repository.LockRepository
import com.example.smartlock.ui.navigation.Routes
import kotlinx.coroutines.delay

class SplashViewModel(
    private val repository: LockRepository
) : ViewModel() {

    suspend fun resolveDestination(): String {
        delay(400)
        return when {
            !repository.hasPairedDevice() -> Routes.SCAN
            !repository.hasPassword() -> Routes.SET_PASSWORD
            else -> Routes.HOME
        }
    }
}