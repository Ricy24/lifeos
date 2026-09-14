package com.example.andresfinanzas

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LifeOSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialization code (e.g. WorkManager, Analytics) will go here
    }
}
