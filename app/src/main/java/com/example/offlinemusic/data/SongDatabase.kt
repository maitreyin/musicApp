package com.example.offlinemusic.data

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Song::class], version = 2, exportSchema = false)
abstract class SongDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        /**
         * Existing rows are retained but deliberately left unassigned. There is no trustworthy
         * historical Supabase user ID to attach to a pre-account local import.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE songs ADD COLUMN user_id TEXT NOT NULL DEFAULT '__unassigned_legacy_song__'"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_songs_user_id ON songs(user_id)")
            }
        }
    }
}
