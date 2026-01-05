package com.rewise

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rewise.data.Topic
import com.rewise.databinding.ActivityMainBinding
import com.rewise.domain.Scheduler
import com.rewise.ui.TopicAdapter
import com.rewise.worker.ReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: TopicAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupFab()
        observeTopics()
        scheduleDailyReminder()
    }

    private fun setupRecyclerView() {
        adapter = TopicAdapter { topic ->
            onRevisionCompleted(topic)
        }
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

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_topic))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = etTopicName.text.toString()
                if (name.isNotEmpty()) {
                    addNewTopic(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addNewTopic(name: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            // First revision is usually tomorrow (Day 1)
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, 1)

            val newTopic = Topic(
                name = name,
                stage = 0,
                nextRevisionDate = calendar.timeInMillis
            )
            (application as RewiseApp).database.topicDao().insert(newTopic)
        }
    }

    private fun observeTopics() {
        lifecycleScope.launch {
            // Observe all active topics
            // In a real app we might want to filter by "Today" or split the list.
            // For now, show all sorted by date.
            (application as RewiseApp).database.topicDao().getAllActiveTopics()
                .collect { topics ->
                    adapter.submitList(topics)

                    binding.tvEmptyState.visibility = if (topics.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                }
        }
    }

    private fun onRevisionCompleted(topic: Topic) {
        lifecycleScope.launch(Dispatchers.IO) {
            val (nextDate, nextStage) = Scheduler.scheduleNext(topic.stage)

            val updatedTopic = topic.copy(
                stage = nextStage,
                nextRevisionDate = nextDate
            )

            (application as RewiseApp).database.topicDao().update(updatedTopic)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Revision Scheduled for next date!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scheduleDailyReminder() {
        val reminderRequest = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyReminder",
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing if already scheduled
            reminderRequest
        )
    }
}
