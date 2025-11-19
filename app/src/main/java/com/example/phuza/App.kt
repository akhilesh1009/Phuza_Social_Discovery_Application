package com.example.phuza

import android.app.Application
import com.example.phuza.offline.MessagesSyncScheduler

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // Periodic background sync every 15 minutes when network is available
        MessagesSyncScheduler.schedule(this)

        // Optional: also trigger a one-shot sync on startup
        MessagesSyncScheduler.oneShot(this)
    }
}
