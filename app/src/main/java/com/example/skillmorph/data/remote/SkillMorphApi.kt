package com.example.skillmorph.data.remote

import com.example.skillmorph.data.remote.dtos.UserProfileDto
import com.google.gson.annotations.SerializedName
import retrofit2.http.Query
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

// 1. The Request Body (Matches Python 'ChatRequest')
data class ChatRequest(
    val message: String,

    @SerializedName("is_voice_mode")
    val isVoiceMode: Boolean = false,

    @SerializedName("session_id")
    val sessionId: String,

    // We keep this for compatibility, though the AuthInterceptor
    // handles the real ID via Headers.
    @SerializedName("user_id")
    val userId: String = "test_user_123"
)

// 2. The Response Body (Matches Python output)
data class ChatResponse(
    val response: String, // The text to show
    val mode: String      // "text" or "voice"
)

data class GoalDto(
    val id: String,
    val title: String,
    val category: String,
    @SerializedName("progress") val progress: Int, // Maps to g.progress_percentage
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("projected_end_date") val endDate: String?
)

// The Root Response
data class MetroMapDto(
    val title: String,
    val category: String,
    val days: List<DayNodeDto>
)

data class UpdateSubtasksRequest(
    @SerializedName("states")
    val states: List<Boolean>
)
// The Day Item
data class DayNodeDto(
    @SerializedName("day_number") val dayNumber: Int,
    val topic: String,
    @SerializedName("is_locked") val isLocked: Boolean,
    @SerializedName("is_completed") val isCompleted: Boolean,
    @SerializedName("sub_tasks") val subTasks: List<String>? = emptyList(),
    @SerializedName("sub_task_states") val subTaskStates: List<Boolean>? = emptyList(),
    val dateIso: String
)

data class ProgressResponse(
    val status: String,
    val new_progress: Int
)

// Chat session
data class SessionResponse(
    @SerializedName("session_id")
    val sessionId: String, // Matches "session_id" from JSON
    val title: String,
    val date: String
)

data class HistoryMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: String? = null
)

data class TaskDto(
    // This is now the UUID (e.g., "550e8400-e29b-41d4-a716-446655440000")
    val id: String? = "",

    // 🟢 NEW: The specific day index (e.g., 5) needed for the API call
    @SerializedName("day_number")
    val dayNumber: Int? = null,

    val title: String? = "Untitled",
    val type: String? = "SIDE_QUEST",

    @SerializedName("goal_title") val goalTitle: String? = null,
    @SerializedName("goal_id") val goalId: String? = null,
    @SerializedName("is_completed") val isCompleted: Boolean? = false
)

// Learning Resource DTOs
data class ResourceDto(
    val id: String? = null,
    val title: String,
    val type: String,      // "course", "article", "video", "exercise"
    val platform: String,
    val description: String,
    val url: String? = null
)

data class ResourcesResponse(
    val resources: List<ResourceDto>,
    val source: String  // "cached" or "generated"
)

// 3. The Interface
interface SkillMorphApi {
    @POST("/agent/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse

    @GET("/goals")
    suspend fun getGoals(@Query("user_id") userId: String = "test_user_123"): List<GoalDto>

    // We will use this in the next step for the Metro Map
    @GET("/goals/{goal_id}/roadmap")
    suspend fun getRoadmap(@Path("goal_id") goalId: String): MetroMapDto

    @POST("/goals/{goal_id}/days/{day_number}/complete")
    suspend fun completeGoalTask(
        @Path("goal_id") goalId: String,
        @Path("day_number") dayNumber: Int
    ): ProgressResponse

    @PUT("goals/{goalId}/days/{dayNumber}/subtasks")
    suspend fun updateSubtasks(
        @Path("goalId") goalId: String,
        @Path("dayNumber") dayNumber: Int,
        @Body request: UpdateSubtasksRequest // Sends {"states": [true, false]}
    )

    @GET("/tasks/today")
    suspend fun getTasks(@Query("date") date: String): List<TaskDto>

    @POST("/tasks")
    suspend fun createSideQuest(@Body task: Map<String, String>): Any

    @POST("/tasks/{id}/complete")
    suspend fun completeSideQuest(@Path("id") taskId: String): Any

    @POST("/chat/sessions/today") // You need to add this endpoint to main.py if not there
    suspend fun getOrCreateDailySession(): SessionResponse

    @GET("/chat/sessions/{session_id}/history")
    suspend fun getSessionHistory(@Path("session_id") sessionId: String): List<HistoryMessage>

    // 3. Get Sidebar List
    @GET("/chat/sessions")
    suspend fun getChatSessions(): List<SessionResponse>

    @GET("/user/profile")
    suspend fun getUserProfile(): UserProfileDto

    @GET("/goals/{goal_id}/resources")
    suspend fun getLearningResources(
        @Path("goal_id") goalId: String,
        @Query("refresh") refresh: Boolean = false
    ): ResourcesResponse
}