package com.example.offlinemusic.data

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Song::class, Favorite::class, Playlist::class, PlaylistSong::class, RecentPlay::class],
    version = 4,
    exportSchema = false
)
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
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE songs ADD COLUMN genre TEXT NOT NULL DEFAULT 'Unknown genre'"
                )
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS favorites (user_id TEXT NOT NULL, song_id TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(user_id, song_id))")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorites_song_id ON favorites(song_id)")
                database.execSQL("CREATE TABLE IF NOT EXISTS playlists (user_id TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(user_id, id))")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_playlists_id ON playlists(id)")
                database.execSQL("CREATE TABLE IF NOT EXISTS playlist_songs (user_id TEXT NOT NULL, playlist_id TEXT NOT NULL, song_id TEXT NOT NULL, position INTEGER NOT NULL, added_at INTEGER NOT NULL, PRIMARY KEY(user_id, playlist_id, song_id))")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_playlist_id ON playlist_songs(playlist_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_song_id ON playlist_songs(song_id)")
                database.execSQL("CREATE TABLE IF NOT EXISTS recent_plays (user_id TEXT NOT NULL, song_id TEXT NOT NULL, played_at INTEGER NOT NULL, PRIMARY KEY(user_id, song_id))")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_recent_plays_user_id_played_at ON recent_plays(user_id, played_at)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_recent_plays_song_id ON recent_plays(song_id)")
            }
        }
    }
}
