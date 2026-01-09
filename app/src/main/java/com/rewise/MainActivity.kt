package com.rewise

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rewise.data.Topic
import com.rewise.databinding.ActivityMainBinding
import com.rewise.domain.Scheduler
import com.rewise.ui.TopicAdapter
import com.rewise.ui.TopicListItem
import com.rewise.worker.BackupWorker
import com.rewise.worker.ReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: TopicAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    private val restorePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                performRestoreFromUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) 
        
        updateGreeting()
        setupRecyclerView()
        setupFab()
        observeTopics()
        scheduleDailyReminder()
        scheduleDailyBackup()
        
        checkPermissions()
    }

    private fun updateGreeting() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..11 -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            else -> "Good Evening,"
        }
        binding.tvAppGreeting.text = greeting
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_backup -> {
                manualBackup()
                true
            }
            R.id.action_restore -> {
                showRestoreDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun manualBackup() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val topics = (application as RewiseApp).database.topicDao().getAllTopics()
                if (topics.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No data to backup!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val jsonString = Gson().toJson(topics)
                val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                
                // Ensure directory exists (handles cases where Downloads might not be initialized)
                if (!downloadsFolder.exists()) {
                    downloadsFolder.mkdirs()
                }
                
                val file = File(downloadsFolder, "Rewise_Topic.json")
                FileOutputStream(file).use { output ->
                    output.write(jsonString.toByteArray())
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Backup saved to Downloads folder", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Backup failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showRestoreDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Restore Data")
            .setMessage("Select the 'Rewise_Topic.json' file from your storage to restore your topics. Note: If app data was cleared, you must pick the file manually.")
            .setPositiveButton("Pick Backup File") { _, _ ->
                launchFilePicker()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Some file managers don't use application/json, so we use */* and filter extension or allow text
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain"))
        }
        restorePickerLauncher.launch(intent)
    }

    private fun performRestoreFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val jsonString = reader.readText()
                    
                    val topicType = object : TypeToken<List<Topic>>() {}.type
                    val topics: List<Topic> = Gson().fromJson(jsonString, topicType)

                    if (topics != null && topics.isNotEmpty()) {
                        (application as RewiseApp).database.topicDao().insertAll(topics)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Successfully restored ${topics.size} topics!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        throw Exception("Backup file is empty or invalid")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = e.localizedMessage ?: "Unknown error"
                    Toast.makeText(this@MainActivity, "Restore failed: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = TopicAdapter(
            onRevisionClick = { topic ->
                onRevisionCompleted(topic)
            },
            onTopicClick = { topic ->
                showTopicDetailsDialog(topic)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            showAddTopicDialog()
        }
    }

    private fun showAddTopicDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_topic, null)
        val etTopicName = dialogView.findViewById<EditText>(R.id.etTopicName)
        val etDescription = dialogView.findViewById<EditText>(R.id.etDescription)
        val etResourceLink = dialogView.findViewById<EditText>(R.id.etResourceLink)

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etTopicName.text.toString()
                val description = etDescription.text.toString()
                val resourceLink = etResourceLink.text.toString()
                if (name.isNotEmpty()) {
                    addNewTopic(name, description, resourceLink)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addNewTopic(name: String, description: String, resourceLink: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, 1) 

            val newTopic = Topic(
                name = name,
                description = description,
                resourceLink = resourceLink,
                stage = 0,
                nextRevisionDate = calendar.timeInMillis
            )
            (application as RewiseApp).database.topicDao().insert(newTopic)
        }
    }

    private fun showTopicDetailsDialog(topic: Topic) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_topic_details, null)
        
        dialogView.findViewById<TextView>(R.id.tvDetailTitle).text = topic.name
        dialogView.findViewById<TextView>(R.id.tvDetailDescription).text = 
            if (topic.description.isNotEmpty()) topic.description else "No description provided."
        
        dialogView.findViewById<TextView>(R.id.tvDetailLink).text = 
            if (topic.resourceLink.isNotEmpty()) topic.resourceLink else "No resource link provided."
            
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val dateStr = dateFormat.format(Date(topic.nextRevisionDate))
        dialogView.findViewById<TextView>(R.id.tvDetailNextRevision).text = dateStr
        dialogView.findViewById<TextView>(R.id.tvDetailStage).text = "Stage ${topic.stage}"

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .setNeutralButton("Edit") { _, _ ->
                showEditTopicDialog(topic)
            }
            .show()
    }

    private fun showEditTopicDialog(topic: Topic) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_topic, null)
        val etTopicName = dialogView.findViewById<EditText>(R.id.etTopicName)
        val etDescription = dialogView.findViewById<EditText>(R.id.etDescription)
        val etResourceLink = dialogView.findViewById<EditText>(R.id.etResourceLink)

        etTopicName.setText(topic.name)
        etDescription.setText(topic.description)
        etResourceLink.setText(topic.resourceLink)

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Topic")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val name = etTopicName.text.toString()
                val description = etDescription.text.toString()
                val resourceLink = etResourceLink.text.toString()
                if (name.isNotEmpty()) {
                    updateTopic(topic.copy(name = name, description = description, resourceLink = resourceLink))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateTopic(topic: Topic) {
        lifecycleScope.launch(Dispatchers.IO) {
            (application as RewiseApp).database.topicDao().update(topic)
        }
    }

    private fun observeTopics() {
        lifecycleScope.launch {
            (application as RewiseApp).database.topicDao().getAllActiveTopics()
                .collect { topics: List<Topic> ->
                    val groupedTopics = groupTopics(topics)
                    adapter.submitList(groupedTopics)

                    binding.tvEmptyState.visibility = if (topics.isEmpty()) View.VISIBLE else View.GONE
                }
        }
    }

    private fun groupTopics(topics: List<Topic>): List<TopicListItem> {
        val today = mutableListOf<Topic>()
        val tomorrow = mutableListOf<Topic>()
        val upcoming = mutableListOf<Topic>()

        val now = Calendar.getInstance()

        for (topic in topics) {
            val revisionDate = Calendar.getInstance().apply { timeInMillis = topic.nextRevisionDate }

            when {
                revisionDate.before(now) || isSameDay(now, revisionDate) -> today.add(topic)
                isTomorrow(now, revisionDate) -> tomorrow.add(topic)
                else -> upcoming.add(topic)
            }
        }

        val list = mutableListOf<TopicListItem>()
        if (today.isNotEmpty()) {
            list.add(TopicListItem.HeaderItem("Today"))
            today.forEach { list.add(TopicListItem.TopicItem(it)) }
        }
        if (tomorrow.isNotEmpty()) {
            list.add(TopicListItem.HeaderItem("Tomorrow"))
            tomorrow.forEach { list.add(TopicListItem.TopicItem(it)) }
        }
        if (upcoming.isNotEmpty()) {
            list.add(TopicListItem.HeaderItem("Upcoming"))
            upcoming.forEach { list.add(TopicListItem.TopicItem(it)) }
        }
        return list
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isTomorrow(today: Calendar, tomorrow: Calendar): Boolean {
        val clone = today.clone() as Calendar
        clone.add(Calendar.DAY_OF_YEAR, 1)
        return isSameDay(clone, tomorrow)
    }

    private fun onRevisionCompleted(topic: Topic) {
        lifecycleScope.launch(Dispatchers.IO) {
            val (nextDate, nextStage) = Scheduler.scheduleNext(topic.stage)
            val updatedTopic = topic.copy(stage = nextStage, nextRevisionDate = nextDate)
            (application as RewiseApp).database.topicDao().update(updatedTopic)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Revision Scheduled!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scheduleDailyReminder() {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance()
        dueDate.set(Calendar.HOUR_OF_DAY, 9)
        dueDate.set(Calendar.MINUTE, 0)
        dueDate.set(Calendar.SECOND, 0)
        if (dueDate.before(currentDate)) dueDate.add(Calendar.HOUR_OF_DAY, 24)
        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis
        val reminderRequest = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("DailyReminder", ExistingPeriodicWorkPolicy.UPDATE, reminderRequest)
    }

    private fun scheduleDailyBackup() {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance()
        dueDate.set(Calendar.HOUR_OF_DAY, 23)
        dueDate.set(Calendar.MINUTE, 0)
        dueDate.set(Calendar.SECOND, 0)
        if (dueDate.before(currentDate)) dueDate.add(Calendar.HOUR_OF_DAY, 24)
        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis
        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("DailyBackup", ExistingPeriodicWorkPolicy.UPDATE, backupRequest)
    }
}
