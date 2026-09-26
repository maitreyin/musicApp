package com.example.offlinemusic

import android.app.Application
import androidx.room.Room
import com.example.offlinemusic.auth.SupabaseAuthManager
import com.example.offlinemusic.data.SongDatabase
import com.example.offlinemusic.data.SongRepository
import com.example.offlinemusic.data.NewPipeHttpDownloader
import org.schabi.newpipe.extractor.NewPipe

class MusicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(NewPipeHttpDownloader())
    }

    val repository: SongRepository by lazy {
        val database = Room.databaseBuilder(this, SongDatabase::class.java, "music.db")
            .addMigrations(SongDatabase.MIGRATION_1_2)
            .build()
        SongRepository(this, database.songDao(), authManager.authState)
    }

    val authManager: SupabaseAuthManager by lazy {
        SupabaseAuthManager(this)
    }
}
