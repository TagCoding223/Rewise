package com.rewise.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(topic: Topic)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(topics: List<Topic>)

    @Update
    suspend fun update(topic: Topic)

    @Query("SELECT * FROM topics WHERE isCompleted = 0 ORDER BY nextRevisionDate ASC")
    fun getAllActiveTopics(): Flow<List<Topic>>

    @Query("SELECT * FROM topics")
    suspend fun getAllTopics(): List<Topic>

    @Query("SELECT * FROM topics WHERE isCompleted = 0 AND nextRevisionDate <= :ms ORDER BY nextRevisionDate ASC")
    fun getTopicsDueBefore(ms: Long): Flow<List<Topic>>
    
    @Query("SELECT * FROM topics WHERE isCompleted = 0 AND nextRevisionDate <= :ms")
    fun getTopicsDueBeforeSync(ms: Long): List<Topic>
}
