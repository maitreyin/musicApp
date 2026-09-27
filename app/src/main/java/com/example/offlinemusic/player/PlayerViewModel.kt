package com.example.offlinemusic.player

import android.app.Application
import android.media.audiofx.Equalizer
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

enum class RepeatMode {
    OFF,
    ONE,
    ALL
}

data class PlaybackState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false
)

data class EqualizerState(
    val enabled: Boolean = true,
    val presetName: String = "Flat",
    val bandLevels: List<Int> = listOf(50, 50, 50, 50, 50)
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MusicApplication).repository
    val songs: StateFlow<List<Song>> = repository.songs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val player = ExoPlayer.Builder(application).build()
    private var equalizer: Equalizer? = null

    private val _playback = MutableStateFlow(PlaybackState())
    val playback = _playback.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled = _shuffleEnabled.asStateFlow()

    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState = _equalizerState.asStateFlow()

    private val presets = mapOf(
        "Flat" to listOf(50, 50, 50, 50, 50),
        "Bass" to listOf(72, 68, 64, 56, 48),
        "Treble" to listOf(38, 46, 58, 70, 80),
        "Vocal" to listOf(56, 62, 60, 52, 44),
        "Club" to listOf(68, 66, 58, 62, 72)
    )

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = publish()
            override fun onPlaybackStateChanged(playbackState: Int) = publish()
        })
        // A playing queue is also account-owned. Stop it before a different account's library
        // becomes observable so a switch cannot continue exposing the previous user's music.
        viewModelScope.launch {
            var previousUserId: String? = null
            repository.userId.collect { userId ->
                if (previousUserId != userId) {
                    player.stop()
                    player.clearMediaItems()
                    publish()
                    previousUserId = userId
                }
            }
        }
        viewModelScope.launch { while (isActive) { publish(); delay(500) } }
    }

    fun play(song: Song) {
        val list = songs.value
        player.setMediaItems(list.map { MediaItem.fromUri(it.localPath) }, list.indexOfFirst { it.id == song.id }.coerceAtLeast(0), 0)
        player.prepare()
        player.play()
        publish()
    }

    fun toggle() {
        if (player.mediaItemCount == 0) {
            val song = currentSong() ?: return
            play(song)
        } else {
            if (player.isPlaying) player.pause() else player.play()
            publish()
        }
    }
    fun seekTo(positionMs: Long) { player.seekTo(positionMs); publish() }
    fun previous() { player.seekToPreviousMediaItem(); publish() }
    fun next() { player.seekToNextMediaItem(); publish() }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.OFF
        }
        updatePlayerRepeatMode()
        publish()
    }

    fun toggleShuffle() {
        _shuffleEnabled.value = !_shuffleEnabled.value
        player.shuffleModeEnabled = _shuffleEnabled.value
        publish()
    }

    fun setBandLevel(index: Int, level: Int) {
        val clampedLevel = level.coerceIn(0, 100)
        val updated = _equalizerState.value.bandLevels.toMutableList()
        if (index !in updated.indices) return
        updated[index] = clampedLevel
        _equalizerState.value = _equalizerState.value.copy(bandLevels = updated, presetName = "Custom")
        applyBandLevels(updated)
    }

    fun applyPreset(name: String) {
        val values = presets[name] ?: return
        _equalizerState.value = _equalizerState.value.copy(bandLevels = values, presetName = name)
        applyBandLevels(values)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        _equalizerState.value = _equalizerState.value.copy(enabled = enabled)
    }

    private fun updatePlayerRepeatMode() {
        player.repeatMode = when (_repeatMode.value) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }

    fun delete(song: Song) { if (currentSong()?.id == song.id) player.stop(); viewModelScope.launch { repository.delete(song) } }
    fun update(song: Song) { viewModelScope.launch { repository.update(song) } }

    private fun currentSong() = songs.value.getOrNull(player.currentMediaItemIndex)

    private fun publish() {
        ensureEqualizer()
        _playback.value = PlaybackState(
            current = currentSong(),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            repeatMode = _repeatMode.value,
            shuffleEnabled = _shuffleEnabled.value
        )
    }

    private fun ensureEqualizer() {
        val sessionId = player.audioSessionId
        if (sessionId == 0) {
            if (equalizer != null) {
                equalizer?.release()
                equalizer = null
            }
            _equalizerState.value = _equalizerState.value.copy(enabled = false)
            return
        }

        if (equalizer == null) {
            try {
                equalizer = Equalizer(0, sessionId)
                equalizer?.enabled = true
                applyBandLevels(_equalizerState.value.bandLevels)
                _equalizerState.value = _equalizerState.value.copy(enabled = true)
            } catch (e: Throwable) {
                android.util.Log.w("PlayerViewModel", "Unable to initialize Equalizer", e)
                _equalizerState.value = _equalizerState.value.copy(enabled = false)
            }
        }
    }

    private fun applyBandLevels(levels: List<Int>) {
        val eq = equalizer ?: return
        val safeLevels = levels.take(eq.numberOfBands.toInt()).ifEmpty { return }
        val range = eq.bandLevelRange
        val min = range[0].toInt()
        val max = range[1].toInt()

        safeLevels.forEachIndexed { index, level ->
            val normalized = level.coerceIn(0, 100) / 100f
            val targetDb = (min + ((max - min) * normalized)).toInt()
            eq.setBandLevel(index.toShort(), targetDb.toShort())
        }
    }

    override fun onCleared() {
        equalizer?.release()
        player.release()
        super.onCleared()
    }
}
