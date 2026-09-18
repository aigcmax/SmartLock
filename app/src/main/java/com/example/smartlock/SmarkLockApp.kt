package com.example.smartlock

import android.app.Application
import com.example.smartlock.di.AppContainer

class SmartLockApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}