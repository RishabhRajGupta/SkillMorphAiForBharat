package com.example.skillmorph.domain.repository

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.skillmorph.data.local.entities.UserProfileDao
import com.example.skillmorph.data.local.entities.UserProfileEntity
import com.example.skillmorph.data.remote.SkillMorphApi
import com.example.skillmorph.data.remote.dtos.UserProfileDto
import com.example.skillmorph.presentation.Profile.Badge
import com.example.skillmorph.presentation.Profile.DailyActivity
import com.example.skillmorph.presentation.Profile.ProfileState
import com.example.skillmorph.presentation.Profile.UserInfo
import com.example.skillmorph.presentation.Profile.UserStats
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val api: SkillMorphApi,
    private val dao: UserProfileDao
) {

    // 1. Expose the Database Data as a UI State Flow
    // Whenever the DB changes, this automatically converts the data and updates the UI
    val userProfile: Flow<ProfileState> = dao.getProfile().map { entity ->
        if (entity != null) {
            mapDtoToUiState(entity.data)
        } else {
            ProfileState.empty()
        }
    }

    // 2. Fetch Fresh Data from API
    suspend fun refreshProfile() {
        try {
            val remoteData = api.getUserProfile() // Calls GET /user/profile

            // Save to Local DB (Cache)
            // The ID is always 0 because we only store one profile (the current user)
            dao.insertProfile(UserProfileEntity(id = 0, data = remoteData))
        } catch (e: Exception) {
            e.printStackTrace()
            // If it fails, we do nothing. The UI continues showing the cached data.
        }
    }

    // --- HELPER: Maps Raw API Data to Beautiful UI State ---
    private fun mapDtoToUiState(dto: UserProfileDto): ProfileState {

        // A. Heatmap Logic: Backfill 365 days
        val today = LocalDate.now()
        val heatmapList = (0 until 365).map { offset ->
            val date = today.minusDays((364 - offset).toLong())
            val dateStr = date.toString() // YYYY-MM-DD

            val count = dto.heatmap[dateStr] ?: 0

            // Assign Intensity Color (0-4)
            val intensity = when {
                count == 0 -> 0
                count <= 2 -> 1
                count <= 4 -> 2
                count <= 7 -> 3
                else -> 4
            }
            DailyActivity(date, intensity, count)
        }

        // B. Radar Chart Logic: Normalize values (0.0 -> 1.0)
        // If highest skill is 20, and 'Coding' is 10, then Coding = 0.5
        val maxSkillValue = dto.skillMatrix.values.maxOrNull()?.toFloat() ?: 1f
        val radarMap = dto.skillMatrix.mapValues { (_, value) ->
            if (maxSkillValue == 0f) 0f else (value / maxSkillValue)
        }

        // C. Badge Logic: Map Server Strings to Local Icons
        // The API sends ["Streak Master"], we map it to the Fire Icon
        val allBadges = getBadgeCatalog()
        val mappedBadges = allBadges.map { (name, icon) ->
            Badge(
                name = name,
                icon = icon,
                isUnlocked = dto.badges.contains(name) // Check if user has it
            )
        }

        // D. Return Final State
        return ProfileState(
            user = UserInfo(
                // Use Firebase for immediate name display
                name = Firebase.auth.currentUser?.displayName ?: "User",
                handle = "",
                title = dto.stats.mainTag, // Calculated by Backend
                currentLevel = dto.stats.level,
                currentXp = dto.stats.currentXp,
                maxXp = dto.stats.nextLevelXp
            ),
            stats = UserStats(
                currentStreak = dto.stats.currentStreak,
                totalActiveDays = dto.stats.activeDays,
                maxStreak = dto.stats.maxStreak
            ),
            skillRadar = radarMap,
            heatmap = heatmapList,
            badges = mappedBadges
        )
    }

    // Hardcoded list of all possible badges and their icons
    private fun getBadgeCatalog(): Map<String, ImageVector> {
        return mapOf(
            "Early Riser" to Icons.Rounded.Bolt,
            "Code Ninja" to Icons.Default.Code,
            "Streak Master" to Icons.Default.LocalFireDepartment,
            "Bug Hunter" to Icons.Default.BugReport,
            "System Architect" to Icons.Default.Architecture,
            "Night Owl" to Icons.Default.DarkMode
        )
    }
}