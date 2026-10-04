package com.example.offlinemusic

import android.app.Application
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import com.example.offlinemusic.auth.SupabaseAuthManager
import com.example.offlinemusic.data.SongDatabase
import com.example.offlinemusic.data.SongRepository
import com.example.offlinemusic.data.NewPipeHttpDownloader
import org.schabi.newpipe.extractor.NewPipe

class MusicApplication : Application() {
    @Volatile
    var playbackOwnerUserId: String? = null
        internal set

    @Volatile
    var playerViewModelAttached: Boolean = false
        internal set

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(NewPipeHttpDownloader())
    }

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            setHandleAudioBecomingNoisy(true)
        }
    }

    val repository: SongRepository by lazy {
        val database = Room.databaseBuilder(this, SongDatabase::class.java, "music.db")
            .addMigrations(
                SongDatabase.MIGRATION_1_2,
                SongDatabase.MIGRATION_2_3,
                SongDatabase.MIGRATION_3_4,
                SongDatabase.MIGRATION_4_5
            )
            .build()
        SongRepository(this, database.songDao(), authManager.authState)
    }

    val authManager: SupabaseAuthManager by lazy {
        SupabaseAuthManager(this)
    }
}
