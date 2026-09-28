package com.example.andresfinanzas

import android.app.Application
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LifeOSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.MAPS_API_KEY.isNotBlank() && !Places.isInitialized()) {
            try {
                Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

