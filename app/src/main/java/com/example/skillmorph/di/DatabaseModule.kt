package com.example.skillmorph.di

import com.example.skillmorph.data.local.entities.UserProfileDao
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.skillmorph.data.local.entities.ChatDao
import com.example.skillmorph.data.local.entities.ChatMessageEntity
import com.example.skillmorph.data.local.entities.UserProfileEntity
import com.example.skillmorph.data.remote.dtos.UserProfileDto
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// --- 1. THE CONVERTER (Handles JSON for Profile) ---
class ProfileConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromDto(value: UserProfileDto): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toDto(value: String): UserProfileDto {
        return gson.fromJson(value, UserProfileDto::class.java)
    }
}

// --- 2. THE DATABASE CLASS ---
@Database(
    entities = [
        ChatMessageEntity::class,
        UserProfileEntity::class // Added the new Profile table
    ],
    version = 2, // Incremented version
    exportSchema = false
)
@TypeConverters(ProfileConverter::class) // Registered the converter
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun userProfileDao(): UserProfileDao // Expose the new DAO
}

// --- 3. THE HILT MODULE ---
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "skillmorph_db"
        )
            .fallbackToDestructiveMigration() // Wipes DB when version changes (prevents crashes)
            .build()
    }

    @Provides
    fun provideChatDao(db: AppDatabase): ChatDao {
        return db.chatDao()
    }

    @Provides
    fun provideUserProfileDao(db: AppDatabase): UserProfileDao {
        return db.userProfileDao()
    }
}