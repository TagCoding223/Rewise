package com.rewise.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "topics")
data class Topic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val resourceLink: String = "",
    val stage: Int = 0, // 0-indexed stage in the sequence
    val nextRevisionDate: Long, // Timestamp
    val isCompleted: Boolean = false
)
