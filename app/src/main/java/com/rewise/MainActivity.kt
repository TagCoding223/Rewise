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
import com.rewise.ui.TopicListItem
import com.rewise.worker.ReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import com.rewise.RewiseApp

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
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, 1) // First revision is tomorrow

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
            (application as RewiseApp).database.topicDao().getAllActiveTopics()
                .collect { topics: List<Topic> ->
                    val groupedTopics = groupTopics(topics)
                    adapter.submitList(groupedTopics)

                    binding.tvEmptyState.visibility = if (topics.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
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
                revisionDate.before(now) -> today.add(topic) // Overdue or today
                isSameDay(now, revisionDate) -> today.add(topic)
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

            val updatedTopic = topic.copy(
                stage = nextStage,
                nextRevisionDate = nextDate
            )

            (application as RewiseApp).database.topicDao().update(updatedTopic)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Revision Scheduled!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scheduleDailyReminder() {
        val reminderRequest = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyReminder",
            ExistingPeriodicWorkPolicy.KEEP,
            reminderRequest
        )
    }
}
