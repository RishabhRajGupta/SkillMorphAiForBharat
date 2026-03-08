package com.example.skillmorph.data.remote.dtos

import com.google.gson.annotations.SerializedName

data class UserProfileDto(
    @SerializedName("stats") val stats: UserStatsDto,
    @SerializedName("heatmap") val heatmap: Map<String, Int>, // "2026-01-28" -> 4
    @SerializedName("skill_matrix") val skillMatrix: Map<String, Int>, // "Coding" -> 15
    @SerializedName("badges") val badges: List<String> // ["Streak Master", "Code Ninja"]
)

data class UserStatsDto(
    val level: Int,
    @SerializedName("current_xp") val currentXp: Int,
    @SerializedName("next_level_xp") val nextLevelXp: Int,
    @SerializedName("prev_level_xp") val prevLevelXp: Int,
    @SerializedName("current_streak") val currentStreak: Int,
    @SerializedName("max_streak") val maxStreak: Int,
    @SerializedName("active_days") val activeDays: Int,
    @SerializedName("main_tag") val mainTag: String
)