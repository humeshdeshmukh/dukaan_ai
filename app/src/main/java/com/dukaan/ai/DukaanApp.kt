package com.dukaan.ai

import android.app.Application
import com.dukaan.ai.ads.AdManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DukaanApp : Application() {

    @Inject
    lateinit var adManager: AdManager

    override fun onCreate() {
        super.onCreate()

        // Initialize AdMob SDK
        adManager.initialize()
    }
}
