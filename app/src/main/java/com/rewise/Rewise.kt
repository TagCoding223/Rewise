package com.rewise

import android.app.Application

class rewiseApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    
    override fun onCreate() {
        super.onCreate()
    }
}
