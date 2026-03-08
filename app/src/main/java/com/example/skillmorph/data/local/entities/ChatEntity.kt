
package com.example.skillmorph.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single chat message in the database.
 */
data class ChatEntity(
    val id: Long = 0,
    val message: String,
    val isUser: Boolean, // True if the message is from the user, false if from the AI
    val timestamp: Long
)
