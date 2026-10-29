package com.example

import android.app.Application
import com.example.orb.ForegroundOwner
import com.example.orb.OrbSetupClient

class SIMApplication : Application() {
    val orbClient by lazy { OrbSetupClient(this) }
    private lateinit var foregroundOwner: ForegroundOwner
    override fun onCreate() {
        super.onCreate()
        foregroundOwner = ForegroundOwner(this) { active ->
            if (active) orbClient.onHostStarted() else orbClient.onHostStopped()
        }
    }
    override fun onTerminate() {
        foregroundOwner.close()
        orbClient.close()
        super.onTerminate()
    }
}
