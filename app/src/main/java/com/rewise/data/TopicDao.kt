package com.rewise.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {
    @Insert
    suspend fun insert(topic: Topic)

    @Update
    suspend fun update(topic: Topic)

    @Query("SELECT * FROM topics WHERE isCompleted = 0 ORDER BY nextRevisionDate ASC")
    fun getAllActiveTopics(): Flow<List<Topic>>

    @Query("SELECT * FROM topics WHERE isCompleted = 0 AND nextRevisionDate <= :ms ORDER BY nextRevisionDate ASC")
    fun getTopicsDueBefore(ms: Long): Flow<List<Topic>>
    
    // For Worker (synchronous access might be needed, or blocking)
    @Query("SELECT * FROM topics WHERE isCompleted = 0 AND nextRevisionDate <= :ms")
    fun getTopicsDueBeforeSync(ms: Long): List<Topic>
}
