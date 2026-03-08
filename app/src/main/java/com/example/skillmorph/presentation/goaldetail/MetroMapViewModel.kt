
package com.example.skillmorph.presentation.goaldetail

import com.example.skillmorph.presentation.goaldetail.models.DayPlan
import com.example.skillmorph.presentation.goaldetail.models.TimelineStatus
import kotlin.collections.map
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillmorph.data.remote.SkillMorphApi
import com.example.skillmorph.data.remote.UpdateSubtasksRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MetroMapViewModel @Inject constructor(
    private val api: SkillMorphApi,
    savedStateHandle: SavedStateHandle // Auto-grabs arguments from Navigation
) : ViewModel() {

    private val _dayPlans = MutableStateFlow<List<DayPlan>>(emptyList())
    val dayPlans = _dayPlans.asStateFlow()

    // 1. Get goalId from the navigation route "metro_map_screen/{goalId}"
    private val goalId: String? = savedStateHandle["goalId"]

    init {
        fetchRoadmap()
    }

    private fun fetchRoadmap() {
        if (goalId == null) return

        viewModelScope.launch {
            try {
                // 2. Fetch from Backend
                val response = api.getRoadmap(goalId)

                // 3. Map Backend Data -> UI Data
                val uiDays = response.days.map { dto ->
                    val status = when {
                        dto.isCompleted -> TimelineStatus.COMPLETED
                        dto.isLocked -> TimelineStatus.LOCKED
                        else -> TimelineStatus.CURRENT
                    }

                    // 🔴 LOGIC UPDATE: Use real subtasks or fallback defaults
                    val realSubTasks = if (!dto.subTasks.isNullOrEmpty()) {
                        dto.subTasks
                    } else {
                        // Fallback for old goals created before this update
                        listOf("Review Topic Materials", "Practice Core Concepts", "Summary Notes")
                    }

                    val realStates = realSubTasks.mapIndexed { index, _ ->
                        // If the backend sent a state for this index, use it. Otherwise false.
                        if (dto.subTaskStates != null && index < dto.subTaskStates.size) {
                            dto.subTaskStates[index]
                        } else {
                            false
                        }
                    }

                    DayPlan(
                        dayNumber = dto.dayNumber,
                        dayLabel = "Day ${dto.dayNumber}",
                        topic = dto.topic,
                        isBufferDay = false,
                        status = status,
                        subTasks = realSubTasks, // <--- Pass the real list here
                        subTaskStates = realStates,
                        dateIso = ""
                    )
                }
                _dayPlans.value = uiDays

            } catch (e: Exception) {
                Log.e("MetroMapVM", "Error: ${e.message}")
            }
        }
    }

    fun toggleSubtask(dayNumber: Int, subTaskIndex: Int) {
        val currentList = _dayPlans.value.toMutableList()
        val dayIndex = currentList.indexOfFirst { it.dayNumber == dayNumber }
        if (dayIndex == -1) return

        val day = currentList[dayIndex]
        val newStates = day.subTaskStates.toMutableList()
        if (subTaskIndex < newStates.size) {
            newStates[subTaskIndex] = !newStates[subTaskIndex]
        }
        currentList[dayIndex] = day.copy(subTaskStates = newStates)
        _dayPlans.value = currentList

        // 2. 🔴 NEW: Network Call (Save to DB)
        viewModelScope.launch {
            try {
                // Send the entire list of booleans for this day
                val request = UpdateSubtasksRequest(states = newStates)
                api.updateSubtasks(
                    goalId = goalId!!,
                    dayNumber = dayNumber,
                    request = request
                )
            } catch (e: Exception) {
                Log.e("MetroVM", "Failed to save checkbox: ${e.message}")
            }
        }
    }

    fun completeDay(dayNumber: Int, context: android.content.Context) {
        if (goalId == null) return

        val day = _dayPlans.value.find { it.dayNumber == dayNumber } ?: return

        // 1. Check if all tasks are done
        // We check if "subTaskStates" contains any 'false'
        if (day.subTaskStates.contains(false)) {
            android.widget.Toast.makeText(context, "Complete all sub-quest first! 🛑", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // 2. If Pass, Optimistic Update
        val currentList = _dayPlans.value.toMutableList()
        val updatedList = currentList.map { d ->
            when (d.dayNumber) {
                dayNumber -> d.copy(status = TimelineStatus.COMPLETED)
                dayNumber + 1 -> d.copy(status = TimelineStatus.CURRENT)
                else -> d
            }
        }
        _dayPlans.value = updatedList

        // 3. Network Call
        viewModelScope.launch {
            try {
                api.completeGoalTask(goalId, dayNumber)
            } catch (e: Exception) {
                Log.e("MetroVM", "Sync failed: ${e.message}")
            }
        }
    }
}
