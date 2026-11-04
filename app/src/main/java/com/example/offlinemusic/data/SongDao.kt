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

    @Query("SELECT songs.* FROM favorites INNER JOIN songs ON songs.id = favorites.song_id AND songs.user_id = favorites.user_id WHERE favorites.user_id = :userId ORDER BY favorites.created_at DESC")
    fun observeFavorites(userId: String): Flow<List<Song>>

    @Query("SELECT song_id FROM favorites WHERE user_id = :userId")
    fun observeFavoriteIds(userId: String): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE user_id = :userId AND song_id = :songId)")
    suspend fun isFavorite(userId: String, songId: String): Boolean

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE user_id = :userId AND song_id = :songId")
    suspend fun deleteFavorite(userId: String, songId: String)

    @Query("SELECT * FROM playlists WHERE user_id = :userId ORDER BY created_at DESC")
    fun observePlaylists(userId: String): Flow<List<Playlist>>

    @Query("SELECT EXISTS(SELECT 1 FROM playlists WHERE user_id = :userId AND id = :playlistId)")
    suspend fun ownsPlaylist(userId: String, playlistId: String): Boolean

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insertPlaylist(playlist: Playlist)

    @Query("UPDATE playlists SET name = :name WHERE user_id = :userId AND id = :playlistId")
    suspend fun renamePlaylist(userId: String, playlistId: String, name: String)

    @Query("DELETE FROM playlists WHERE user_id = :userId AND id = :playlistId")
    suspend fun deletePlaylist(userId: String, playlistId: String)

    @Query("DELETE FROM playlist_songs WHERE user_id = :userId AND playlist_id = :playlistId")
    suspend fun deletePlaylistSongs(userId: String, playlistId: String)

    @Query("SELECT songs.* FROM playlist_songs INNER JOIN songs ON songs.id = playlist_songs.song_id AND songs.user_id = playlist_songs.user_id WHERE playlist_songs.user_id = :userId AND playlist_songs.playlist_id = :playlistId ORDER BY playlist_songs.position, playlist_songs.added_at")
    fun observePlaylistSongs(userId: String, playlistId: String): Flow<List<Song>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE user_id = :userId AND playlist_id = :playlistId")
    suspend fun nextPlaylistPosition(userId: String, playlistId: String): Int

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSong(playlistSong: PlaylistSong)

    @Query("DELETE FROM playlist_songs WHERE user_id = :userId AND playlist_id = :playlistId AND song_id = :songId")
    suspend fun deletePlaylistSong(userId: String, playlistId: String, songId: String)

    @Query("SELECT songs.* FROM recent_plays INNER JOIN songs ON songs.id = recent_plays.song_id AND songs.user_id = recent_plays.user_id WHERE recent_plays.user_id = :userId ORDER BY recent_plays.played_at DESC LIMIT 30")
    fun observeRecentlyPlayed(userId: String): Flow<List<Song>>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertRecentPlay(recentPlay: RecentPlay)

    @Query("DELETE FROM recent_plays WHERE user_id = :userId AND song_id = :songId")
    suspend fun deleteRecentPlay(userId: String, songId: String)

    @Query("DELETE FROM playlist_songs WHERE user_id = :userId AND song_id = :songId")
    suspend fun deletePlaylistSongReferences(userId: String, songId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(song: Song)
    @Query("UPDATE songs SET title = :title, artist = :artist, album = :album, genre = :genre WHERE id = :songId AND user_id = :userId")
    suspend fun updateMetadata(userId: String, songId: String, title: String, artist: String, album: String, genre: String): Int
    @Query("UPDATE songs SET lyrics = :lyrics WHERE id = :songId AND user_id = :userId")
    suspend fun updateLyrics(userId: String, songId: String, lyrics: String): Int
    @Query("DELETE FROM songs WHERE id = :id AND user_id = :userId")
    suspend fun delete(id: String, userId: String)
}
