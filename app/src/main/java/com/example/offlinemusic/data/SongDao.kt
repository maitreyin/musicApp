package com.example.offlinemusic.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE user_id = :userId ORDER BY title COLLATE NOCASE")
    fun observeAll(userId: String): Flow<List<Song>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(song: Song)
    @Query("DELETE FROM songs WHERE id = :id AND user_id = :userId")
    suspend fun delete(id: String, userId: String)
}
