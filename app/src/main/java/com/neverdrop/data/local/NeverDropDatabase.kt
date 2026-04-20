package com.neverdrop.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.neverdrop.data.local.dao.TaskDao
import com.neverdrop.data.local.entity.TaskEntity

@Database(entities = [TaskEntity::class], version = 1, exportSchema = false)
abstract class NeverDropDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: NeverDropDatabase? = null

        fun getInstance(context: Context): NeverDropDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NeverDropDatabase::class.java,
                    "neverdrop_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
