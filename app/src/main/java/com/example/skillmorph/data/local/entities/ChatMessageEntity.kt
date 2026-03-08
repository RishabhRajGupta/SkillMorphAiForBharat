package com.example.skillmorph.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val isUser: Boolean,
    val sessionId: String, // Groups messages by Day
    val timestamp: Long = System.currentTimeMillis()
)