package com.example.offlinemusic.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE") fun observeAll(): Flow<List<Song>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(song: Song)
    @Query("DELETE FROM songs WHERE id = :id") suspend fun delete(id: String)
}
