package com.biddrive.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

class BidDriveApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences("biddrive_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        lateinit var instance: BidDriveApp
            private set

        lateinit var prefs: SharedPreferences
            private set
    }
}
