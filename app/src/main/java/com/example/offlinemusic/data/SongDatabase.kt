package com.example.offlinemusic.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Song::class], version = 1, exportSchema = false)
abstract class SongDatabase : RoomDatabase() { abstract fun songDao(): SongDao }
