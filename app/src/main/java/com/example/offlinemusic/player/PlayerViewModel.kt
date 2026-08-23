package com.example.offlinemusic.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.offlinemusic.MusicApplication
import com.example.offlinemusic.data.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(val current: Song? = null, val isPlaying: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MusicApplication).repository
    val songs: StateFlow<List<Song>> = repository.songs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val player = ExoPlayer.Builder(application).build()
    private val _playback = MutableStateFlow(PlaybackState())
    val playback = _playback.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = publish()
            override fun onPlaybackStateChanged(playbackState: Int) = publish()
        })
        viewModelScope.launch { while (isActive) { publish(); delay(500) } }
    }

    fun play(song: Song) {
        val list = songs.value
        player.setMediaItems(list.map { MediaItem.fromUri(it.localPath) }, list.indexOfFirst { it.id == song.id }.coerceAtLeast(0), 0)
        player.prepare(); player.play(); publish()
    }
    fun toggle() { if (player.isPlaying) player.pause() else player.play(); publish() }
    fun seekTo(positionMs: Long) { player.seekTo(positionMs); publish() }
    fun previous() { player.seekToPreviousMediaItem(); publish() }
    fun next() { player.seekToNextMediaItem(); publish() }
    fun delete(song: Song) { if (currentSong()?.id == song.id) player.stop(); viewModelScope.launch { repository.delete(song) } }
    private fun currentSong() = songs.value.getOrNull(player.currentMediaItemIndex)
    private fun publish() { _playback.value = PlaybackState(currentSong(), player.isPlaying, player.currentPosition.coerceAtLeast(0), player.duration.coerceAtLeast(0)) }
    override fun onCleared() { player.release() }
}
