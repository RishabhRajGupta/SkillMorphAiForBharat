
package com.example.skillmorph.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Represents a single task associated with a Goal.
 */
data class TaskEntity(
    val id: String, // Changed to String to match backend in future
    val goalId: String, // Changed to String to link to GoalEntity
    val title: String,
    val scheduledDate: Long,
    val isCompleted: Boolean = false,
    val isBufferTask: Boolean = false
)
