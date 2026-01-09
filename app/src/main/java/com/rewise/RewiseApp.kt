package com.rewise

import android.app.Application
import com.rewise.data.AppDatabase

class RewiseApp : Application() {
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }
}
