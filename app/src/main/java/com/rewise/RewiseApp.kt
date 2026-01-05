package com.rewise

import android.app.Application
import androidx.room.Room
import com.rewise.AppDatabase

class RewiseApp : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "rewise-db"
        ).build()
    }
}
