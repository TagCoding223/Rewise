package com.rewise.worker

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.rewise.RewiseApp
import com.rewise.data.Topic
import java.io.File
import java.io.FileOutputStream

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val database = (applicationContext as RewiseApp).database
            val topics = database.topicDao().getAllTopics()

            if (topics.isNotEmpty()) {
                val jsonString = Gson().toJson(topics)
                saveToDownloadFolder(jsonString)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("BackupWorker", "Error backing up data", e)
            Result.failure()
        }
    }

    private fun saveToDownloadFolder(jsonString: String) {
        try {
            val fileName = "Rewise_Topic.json"
            val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsFolder, fileName)

            FileOutputStream(file).use { output ->
                output.write(jsonString.toByteArray())
            }
            Log.d("BackupWorker", "Backup successful to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("BackupWorker", "File write failed", e)
        }
    }
}
