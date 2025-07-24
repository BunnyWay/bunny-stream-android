package net.bunnystream.android.demo

import android.annotation.SuppressLint
import android.app.Application
import net.bunnystream.android.demo.di.Di
import net.bunnystream.android.demo.worker.PositionCleanupWorker

class App : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var di: Di
    }

    override fun onCreate() {
        super.onCreate()
        di = Di(this)

        // Schedule periodic cleanup of expired resume positions
        // Use default retention of 7 days, or get from preferences if you have them
        PositionCleanupWorker.schedulePeriodicCleanup(
            context = this,
            retentionDays = 7 // You can make this configurable later
        )
    }
}