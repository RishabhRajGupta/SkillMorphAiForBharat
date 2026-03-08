package com.example.skillmorph.data.local.entities

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.skillmorph.data.local.entities.ChatDao
import com.example.skillmorph.data.local.entities.ChatMessageEntity
import com.example.skillmorph.data.local.entities.UserProfileEntity
import com.example.skillmorph.data.remote.dtos.UserProfileDto
import com.google.gson.Gson

// 1. The Converter (Handles JSON)
class ProfileConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromDto(value: UserProfileDto): String = gson.toJson(value)

    @TypeConverter
    fun toDto(value: String): UserProfileDto = gson.fromJson(value, UserProfileDto::class.java)
}

// 2. The Database Class
@Database(
    entities = [
        ChatMessageEntity::class,
        UserProfileEntity::class  // <--- Added Profile Table
    ],
    version = 2, // <--- Incremented Version
    exportSchema = false
)
@TypeConverters(ProfileConverter::class) // <--- Added Converter
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun userProfileDao(): UserProfileDao // <--- Expose the DAO
}