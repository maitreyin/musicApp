package com.example.offlinemusic.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    /** Absolute path below the app's filesDir/audio directory. Never a public Downloads path. */
    val localPath: String,
    val artworkPath: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
