package com.example.skillmorph.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.skillmorph.data.remote.dtos.UserProfileDto

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 0, // Singleton row
    val data: UserProfileDto, // We save the whole DTO using a TypeConverter
    val lastUpdated: Long = System.currentTimeMillis()
)