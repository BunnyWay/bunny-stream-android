package net.bunnystream.android.demo

import android.annotation.SuppressLint
import android.app.Application
import net.bunnystream.android.demo.di.Di

class App : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var di: Di
    }
    override fun onCreate() {
        super.onCreate()
        di = Di(this)
    }
}