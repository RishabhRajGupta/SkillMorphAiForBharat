
package com.example.skillmorph.presentation.goaldetail.models

import com.google.gson.annotations.SerializedName

/**
 * Represents the status of a timeline node.
 */
enum class TimelineStatus {
    COMPLETED, CURRENT, LOCKED
}

/**
 * Represents a single day's plan in the Metro Map timeline.
 */
data class DayPlan(
    @SerializedName("day_number")
    val dayNumber: Int,

    @SerializedName("day_label")
    val dayLabel: String,

    @SerializedName("topic")
    val topic: String,

    @SerializedName("is_buffer_day")
    val isBufferDay: Boolean,

    @SerializedName("status")
    val status: TimelineStatus,

    @SerializedName("sub_tasks")
    val subTasks: List<String>,

    @SerializedName("date_iso")
    val dateIso: String,

    val subTaskStates: List<Boolean>
)
