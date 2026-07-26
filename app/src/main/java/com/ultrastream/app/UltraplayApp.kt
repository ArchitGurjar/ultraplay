package com.ultrastream.app

import android.app.Application

class UltraplayApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLogger.init(this)
    }
}
