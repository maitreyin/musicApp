package com.example.offlinemusic

import android.app.Application
import androidx.room.Room
import com.example.offlinemusic.data.SongDatabase
import com.example.offlinemusic.data.SongRepository

class MusicApplication : Application() {
    val repository: SongRepository by lazy {
        val database = Room.databaseBuilder(this, SongDatabase::class.java, "music.db").build()
        SongRepository(this, database.songDao())
    }
}
