package com.example.skillmorph.presentation.Profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillmorph.domain.repository.ProfileRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// --- Data Models ---
data class ProfileState(
    val user: UserInfo,
    val stats: UserStats,
    val skillRadar: Map<String, Float>,
    val heatmap: List<DailyActivity>,
    val badges: List<Badge>
) {
    // 🔴 ADD THIS COMPANION OBJECT
    companion object {
        fun empty() = ProfileState(
            user = UserInfo(
                name = Firebase.auth.currentUser?.displayName ?: "Loading...",
                handle = "",
                title = "Novice",
                currentLevel = 1,
                currentXp = 0,
                maxXp = 500
            ),
            stats = UserStats(0, 0, 0),
            skillRadar = emptyMap(),
            heatmap = emptyList(),
            badges = emptyList()
        )
    }
}

data class UserInfo(
    val name: String,
    val handle: String,
    val title: String,
    val currentLevel: Int,
    val currentXp: Int,
    val maxXp: Int
)

data class UserStats(
    val currentStreak: Int,
    val totalActiveDays: Int,
    val maxStreak: Int
)

data class DailyActivity(
    val date: LocalDate,
    val intensity: Int,
    val count: Int
)

data class Badge(
    val name: String,
    val icon: ImageVector,
    val isUnlocked: Boolean
)

// --- ViewModel ---
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository
) : ViewModel() {

    // 1. Connects to the Room Database (Cache)
    // Whenever the database updates, this state updates automatically.
    val state: StateFlow<ProfileState> = repository.userProfile
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProfileState.empty() // Uses the helper we just added above
        )


    init {
        // 2. Fetch fresh data from API immediately
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // This pulls from Python -> Updates Room -> Updates 'state' Flow above
            repository.refreshProfile()
        }
    }
}