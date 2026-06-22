package com.neverdrop.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.neverdrop.data.local.dao.TaskDao
import com.neverdrop.data.local.entity.TaskEntity

@Database(entities = [TaskEntity::class], version = 2, exportSchema = false)
abstract class NeverDropDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: NeverDropDatabase? = null

        /** v1 -> v2: add cloud-sync metadata and backfill stable ids for existing rows. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE tasks ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                // Give existing rows a stable id and a sane updatedAt.
                db.execSQL("UPDATE tasks SET uuid = lower(hex(randomblob(16))) WHERE uuid = ''")
                db.execSQL("UPDATE tasks SET updatedAtEpochMillis = createdAtEpochMillis WHERE updatedAtEpochMillis = 0")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tasks_uuid ON tasks(uuid)")
            }
        }

        fun getInstance(context: Context): NeverDropDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NeverDropDatabase::class.java,
                    "neverdrop_database"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
        }
    }
}
