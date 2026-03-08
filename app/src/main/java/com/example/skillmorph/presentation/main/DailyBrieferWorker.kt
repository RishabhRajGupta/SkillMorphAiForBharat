package com.example.skillmorph.presentation.main

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.skillmorph.data.remote.ChatRequest
import com.example.skillmorph.data.remote.SkillMorphApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.UUID

@HiltWorker
class DailyBriefingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val api: SkillMorphApi,
    private val sharedPrefs: SharedPreferences
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val todayDate = LocalDate.now().toString()

            // 1. Fetch Tasks
            val tasks = api.getTasks(todayDate)
            if (tasks.isEmpty()) return Result.success()

            // 2. Generate Briefing
            val taskList = tasks.joinToString(", ") { "${it.title} (${it.goalTitle})" }
            val prompt = """
                SYSTEM_INSTRUCTION: 
                The user has these tasks today: $taskList.
                Act as a motivational JARVIS-like assistant.
                Greet the user and summarize the plan. Keep it short.
            """.trimIndent()

            val sessionData = api.getOrCreateDailySession()
            val realSessionId = sessionData.sessionId

            // 🟢 UNCOMMENTED THESE LINES:
            // In DailyBriefingWorker.kt

            val response = api.chat(
                ChatRequest(
                    message = prompt,
                    isVoiceMode = false,
                    sessionId = realSessionId // Use the ID you fetched from getOrCreateDailySession
                )
            )

            // Note: If your chat returns a JSON string, handle parsing here or just save raw response
            val briefingText = response.response

            // 3. Save to Local Storage (Cache)
            sharedPrefs.edit()
                .putString("daily_briefing_text", briefingText) // 🟢 UNCOMMENTED
                .putString("daily_briefing_date", todayDate)
                .apply()

            Log.d("DailyBriefing", "Briefing cached successfully: $briefingText")
            Result.success()
        } catch (e: Exception) {
            Log.e("DailyBriefing", "Failed: ${e.message}")
            Result.retry()
        }
    }
}