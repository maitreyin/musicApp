package com.example.offlinemusic.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "favorites",
    primaryKeys = ["user_id", "song_id"],
    indices = [Index(value = ["song_id"])]
)
data class Favorite(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlists",
    primaryKeys = ["user_id", "id"],
    indices = [Index(value = ["id"])]
)
data class Playlist(
    @ColumnInfo(name = "user_id") val userId: String,
    val id: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["user_id", "playlist_id", "song_id"],
    indices = [Index(value = ["playlist_id"]), Index(value = ["song_id"])]
)
data class PlaylistSong(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recent_plays",
    primaryKeys = ["user_id", "song_id"],
    indices = [Index(value = ["user_id", "played_at"]), Index(value = ["song_id"])]
)
data class RecentPlay(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "played_at") val playedAt: Long = System.currentTimeMillis()
)