package com.example.skillmorph.data.local.entities

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // 1. Observe messages (Updates UI automatically)
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesFlow(sessionId: String): Flow<List<ChatMessageEntity>>

    // 2. Insert one (for sending)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: ChatMessageEntity)

    // 3. Bulk Insert (for syncing history)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    // 4. Get latest session ID (Useful for offline startup)
    @Query("SELECT sessionId FROM chat_messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastSessionId(): String?
}