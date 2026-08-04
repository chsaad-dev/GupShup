package com.example.gupshup.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.gupshup.data.local.dao.FriendRequestDao
import com.example.gupshup.data.local.dao.MessageDao
import com.example.gupshup.data.local.dao.StatusDao
import com.example.gupshup.data.local.dao.UserDao
import com.example.gupshup.data.local.entity.FriendRequestEntity
import com.example.gupshup.data.local.entity.MessageEntity
import com.example.gupshup.data.local.entity.StatusEntity
import com.example.gupshup.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        FriendRequestEntity::class,
        MessageEntity::class,
        StatusEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun friendRequestDao(): FriendRequestDao
    abstract fun messageDao(): MessageDao
    abstract fun statusDao(): StatusDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gupshup_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
