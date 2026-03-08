package com.example.skillmorph.presentation.main.viewModel;

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillmorph.data.local.entities.ChatDao
import com.example.skillmorph.data.local.entities.ChatMessageEntity
import com.example.skillmorph.data.remote.ChatRequest
import com.example.skillmorph.data.remote.SessionResponse
import com.example.skillmorph.data.remote.SkillMorphApi
import com.example.skillmorph.domain.repository.AuthRepository
import com.example.skillmorph.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import kotlin.collections.toMutableList

// UI Model
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isThinking: Boolean = false
)

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val api: SkillMorphApi,
    private val sharedPrefs: SharedPreferences,
    private val chatDao: ChatDao,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    // --- STATE ---

    // 1. Current Session ID (The "Room" for today)
    private val _currentSessionId = MutableStateFlow<String?>(null)
    private val _pastSessions = MutableStateFlow<List<SessionResponse>>(emptyList())
    val pastSessions = _pastSessions.asStateFlow()


    // 2. Chat Messages
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isAgentThinking = MutableStateFlow(false)
    val isAgentThinking = _isAgentThinking.asStateFlow()

    private val _ttsText = MutableStateFlow<String?>(null)
    val ttsText = _ttsText.asStateFlow()

    // 🔴 FIX: Job variable declared correctly
    private var messageCollectionJob: Job? = null

    // For streak badge
    val currentStreak: StateFlow<Int> = profileRepository.userProfile
        .map { it.stats.currentStreak }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0 // Default value while loading
        )

    // --- INITIALIZATION ---
    init {
        initializeSession()
    }

    private fun initializeSession() {
        viewModelScope.launch {
            var sessionId: String? = null
            var isOffline = false

            // 1. Calculate Virtual Date
            val now = LocalDateTime.now()
            val cutoff = now.withHour(3).withMinute(30)
            val virtualDate = if (now.isBefore(cutoff)) {
                now.minusDays(1).toLocalDate().toString()
            } else {
                now.toLocalDate().toString()
            }

            Log.d("AgentVM", "Initializing for: $virtualDate")

            // 2. Try Network Handshake (ONLY GET ID)
            try {
                // We only fetch the ID here. Data fetching happens in Step 4.
                val sessionData = api.getOrCreateDailySession()
                sessionId = sessionData.sessionId

            } catch (e: Exception) {
                Log.e("AgentVM", "Backend Handshake Failed: ${e.message}")
                isOffline = true
                sessionId = chatDao.getLastSessionId() ?: UUID.randomUUID().toString()
            }

            // 3. Set Session ID & Start DB Observation
            // This ensures the UI shows cached data IMMEDIATELY
            _currentSessionId.value = sessionId
            startObservingMessages(sessionId!!)

            // 4. If Online, Sync History & Sidebar (DO IT ONCE HERE)
            if (!isOffline) {
                // Syncs history to DB. The UI updates automatically via startObservingMessages.
                syncSessionHistory(sessionId!!)

                // Fetch Sidebar
                try {
                    _pastSessions.value = api.getChatSessions()
                } catch (e: Exception) {
                    Log.e("AgentVM", "Failed to load sidebar: ${e.message}")
                }

                // 🟢 RESTORED: Trigger Briefing check
                // We check if the DB (which we just synced) is empty for this session
                // We use a small delay or check the DAO directly to be safe,
                // but since syncSessionHistory waits, we can check _messages.
                if (_messages.value.isEmpty()) {
                    triggerDailyBriefing(virtualDate)
                }

            } else {
                // Offline logic
                if (_messages.value.isEmpty()) {
                    // Optional: Local welcome message
                }
            }
        }
    }

    private fun startObservingMessages(sessionId: String) {
        // Stop observing previous session
        messageCollectionJob?.cancel()

        messageCollectionJob = viewModelScope.launch {
            // This Flow updates the UI automatically whenever the DB changes
            chatDao.getMessagesFlow(sessionId).collect { entities ->
                // Map Entity -> UI Model
                _messages.value = entities.map { entity ->
                    ChatMessage(
                        text = entity.text,
                        isUser = entity.isUser
                    )
                }
            }
        }
    }

    // Helper: Fetches from API and saves to DB (Room handles the UI update)
    private fun syncSessionHistory(sessionId: String) {
        viewModelScope.launch {
            try {
                // Fetch from Network
                val history = api.getSessionHistory(sessionId)

                // 🔴 FIX: Correctly map Network Model to Database Entity
                val entities = history.map {
                    ChatMessageEntity(
                        sessionId = sessionId,
                        text = it.text,
                        isUser = it.isUser,
                        timestamp = System.currentTimeMillis()
                    )
                }

                // Save to DB (UI updates automatically via startObservingMessages)
                chatDao.insertMessages(entities)

            } catch (e: Exception) {
                Log.e("AgentVM", "Sync failed: ${e.message}")
            }
        }
    }

    // --- CHAT LOGIC ---

    fun sendMessage(text: String, isVoice: Boolean) {
        // 1. Get the Active Session ID
        val sessionId = _currentSessionId.value ?: return

        // Optimistic Update (UI) - Keeping your manual update logic
        val currentList = _messages.value.toMutableList()
        currentList.add(ChatMessage(text, isUser = true))
        _messages.value = currentList

        _isAgentThinking.value = true

        viewModelScope.launch {
            // 1. Save User Message to DB
            val userMsg = ChatMessageEntity(
                sessionId = sessionId,
                text = text,
                isUser = true,
                timestamp = System.currentTimeMillis()
            )
            chatDao.insertMessage(userMsg)

            try {
                // 2. Network Call
                val apiResult = api.chat(
                    ChatRequest(
                        message = text,
                        isVoiceMode = isVoice,
                        sessionId = sessionId
                    )
                )

                // 3. Clean JSON (Safety Net)
                var displayMessage = apiResult.response
                var spokenMessage = apiResult.response

                try {
                    val cleanJson = apiResult.response
                        .replace("```json", "")
                        .replace("```", "")
                        .trim()

                    if (cleanJson.startsWith("{")) {
                        val jsonObject = JSONObject(cleanJson)
                        if (jsonObject.has("display_text")) displayMessage = jsonObject.getString("display_text")
                        if (jsonObject.has("spoken_text")) spokenMessage = jsonObject.getString("spoken_text")
                    }
                } catch (e: Exception) {
                    // Not JSON, ignore
                }

                // 4. Save AI Message to DB
                val aiMsg = ChatMessageEntity(
                    sessionId = sessionId,
                    text = displayMessage,
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                )
                chatDao.insertMessage(aiMsg)

                _isAgentThinking.value = false

                // Manual Update for UI (Keeping your original logic)
                val updatedList = _messages.value.toMutableList()
                updatedList.add(ChatMessage(displayMessage, isUser = false))
                _messages.value = updatedList

                // 5. Trigger TTS
                if (isVoice || apiResult.mode == "voice") {
                    _ttsText.value = spokenMessage
                }

            } catch (e: Exception) {
                _isAgentThinking.value = false
                Log.e("AgentVM", "Send Failed: ${e.message}")

                val errorList = _messages.value.toMutableList()
                errorList.add(ChatMessage("❌ Failed to send. Check Server.", isUser = false))
                _messages.value = errorList

                // Optional: Insert error message into DB so user sees it
                chatDao.insertMessage(ChatMessageEntity(
                    sessionId = sessionId,
                    text = "❌ Failed to send. Check internet.",
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                ))
            }
        }
    }

    // --- BRIEFING LOGIC (Integrated) ---

    private fun triggerDailyBriefing(todayDate: String) {
        viewModelScope.launch {
            // 1. Check Cache
            val cachedDate = sharedPrefs.getString("daily_briefing_date", "")
            val cachedText = sharedPrefs.getString("daily_briefing_text", "")

            if (cachedDate == todayDate && !cachedText.isNullOrEmpty()) {
                val currentList = _messages.value.toMutableList()
                currentList.add(ChatMessage(cachedText, isUser = false))
                _messages.value = currentList
                _ttsText.value = cachedText
                return@launch
            }

            // 2. Fetch Live
            try {
                val tasks = api.getTasks(todayDate)
                if (tasks.isEmpty()) {
                    val msg = "Good morning! No tasks for today."
                    _messages.value += ChatMessage(msg, false)
                    _ttsText.value = msg
                    return@launch
                }

                val taskListString = tasks.joinToString(", ") { "${it.title} (${it.goalTitle})" }
                val prompt = "SYSTEM: User tasks: $taskListString. Greet and summarize shortly."

                // Use the session ID we just initialized!
                val sessionId = _currentSessionId.value ?: UUID.randomUUID().toString()

                val response = api.chat(ChatRequest(prompt, false, sessionId))

                _messages.value += ChatMessage(response.response, false)
                _ttsText.value = response.response

            } catch (e: Exception) {
                Log.e("AgentVM", "Briefing failed: ${e.message}")
            }
        }
    }

    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            // 1. Set the active session ID
            _currentSessionId.value = sessionId

            // Start observing Local Data
            startObservingMessages(sessionId)

            // Sync with Network
            syncSessionHistory(sessionId)

            // 2. Clear current messages (Instant UI feedback)
            _messages.value = emptyList()

            try {
                Log.d("AgentVM", "Loading history for session: $sessionId")

                // 3. Fetch History from Backend
                val history = api.getSessionHistory(sessionId)

                // 4. Map to UI Model and Update
                if (history.isNotEmpty()) {
                    _messages.value = history.map {
                        ChatMessage(text = it.text, isUser = it.isUser)
                    }
                } else {
                    _messages.value = listOf(ChatMessage("This conversation is empty.", isUser = false))
                }

            } catch (e: Exception) {
                Log.e("AgentVM", "Failed to load session: ${e.message}")
                _messages.value = listOf(ChatMessage("⚠️ Failed to load history. Check connection.", isUser = false))
            }
        }
    }

    fun onTtsFinished() {
        _ttsText.value = null
    }

    suspend fun signout(){
        authRepository.signOut()
    }
}