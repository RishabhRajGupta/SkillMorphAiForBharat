
package com.example.skillmorph.domain.repository

import com.example.skillmorph.data.local.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

interface TasksRepository {

    /**
     * Fetches all tasks for a given date.
     * @param date The date in milliseconds (timestamp).
     * @return A flow emitting the list of tasks for that day.
     */
//    fun getTasksForDate(date: Long): Flow<List<TaskEntity>>

    /**
     * Updates a task in the database.
     * @param task The task entity to update.
     */
//    suspend fun updateTask(task: TaskEntity)
}
