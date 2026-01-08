package com.rewise.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rewise.R
import com.rewise.data.Topic
import com.rewise.databinding.ItemHeaderBinding
import com.rewise.databinding.ItemTopicBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Sealed class to represent items in the list (either a topic or a header)
sealed class TopicListItem {
    data class TopicItem(val topic: Topic) : TopicListItem()
    data class HeaderItem(val title: String) : TopicListItem()
}

class TopicAdapter(
    private val onRevisionClick: (Topic) -> Unit
) : ListAdapter<TopicListItem, RecyclerView.ViewHolder>(TopicDiffCallback()) {

    companion object {
        private const val ITEM_VIEW_TYPE_HEADER = 0
        private const val ITEM_VIEW_TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TopicListItem.HeaderItem -> ITEM_VIEW_TYPE_HEADER
            is TopicListItem.TopicItem -> ITEM_VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_HEADER -> {
                val binding = ItemHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                HeaderViewHolder(binding)
            }
            ITEM_VIEW_TYPE_ITEM -> {
                val binding = ItemTopicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                TopicViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TopicListItem.HeaderItem -> (holder as HeaderViewHolder).bind(item.title)
            is TopicListItem.TopicItem -> (holder as TopicViewHolder).bind(item.topic)
        }
    }

    inner class TopicViewHolder(private val binding: ItemTopicBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(topic: Topic) {
            binding.tvTopicName.text = topic.name

            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val dateStr = dateFormat.format(Date(topic.nextRevisionDate))

            val isDueToday = isTopicDueToday(topic)

            binding.tvNextRevision.text = if (isDueToday) {
                "Due Today (Stage ${topic.stage})"
            } else {
                "Next Revision: $dateStr (Stage ${topic.stage})"
            }

            // Enable button only if due today
            binding.btnDone.isEnabled = isDueToday

            // Change opacity for non-due items
            binding.root.alpha = if (isDueToday) 1.0f else 0.6f

            binding.btnDone.setOnClickListener {
                if (binding.btnDone.isEnabled) {
                    onRevisionClick(topic)
                }
            }
        }

        private fun isTopicDueToday(topic: Topic): Boolean {
            val today = Calendar.getInstance()
            val revisionDate = Calendar.getInstance().apply { timeInMillis = topic.nextRevisionDate }
            return revisionDate.before(today)
        }
    }

    inner class HeaderViewHolder(private val binding: ItemHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.tvHeader.text = title
        }
    }

    class TopicDiffCallback : DiffUtil.ItemCallback<TopicListItem>() {
        override fun areItemsTheSame(oldItem: TopicListItem, newItem: TopicListItem): Boolean {
            return if (oldItem is TopicListItem.TopicItem && newItem is TopicListItem.TopicItem) {
                oldItem.topic.id == newItem.topic.id
            } else if (oldItem is TopicListItem.HeaderItem && newItem is TopicListItem.HeaderItem) {
                oldItem.title == newItem.title
            } else {
                false
            }
        }

        override fun areContentsTheSame(oldItem: TopicListItem, newItem: TopicListItem): Boolean {
            return if (oldItem is TopicListItem.TopicItem && newItem is TopicListItem.TopicItem) {
                oldItem.topic == newItem.topic
            } else {
                oldItem == newItem
            }
        }
    }
}
