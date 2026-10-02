package com.example.offlinemusic.player

import android.app.Application
import android.content.Context
import android.media.audiofx.Equalizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.offlinemusic.MusicApplication
import com.example.offlinemusic.data.Playlist
import com.example.offlinemusic.data.Song
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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
    val shuffleEnabled: Boolean = false,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1
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
    private val playbackPreferences = application.getSharedPreferences("mused_playback", Context.MODE_PRIVATE)
    private var activeUserId: String? = null
    private var activeQueue: List<Song> = emptyList()
    private var equalizer: Equalizer? = null

    val favoriteSongs: StateFlow<List<Song>> = repository.favorites.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val favoriteIds: StateFlow<Set<String>> = repository.favoriteIds.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet()
    )
    val playlists: StateFlow<List<Playlist>> = repository.playlists.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val recentlyPlayed: StateFlow<List<Song>> = repository.recentlyPlayed.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylistId = _selectedPlaylistId.asStateFlow()
    val selectedPlaylistSongs: StateFlow<List<Song>> = _selectedPlaylistId
        .flatMapLatest { playlistId -> playlistId?.let(repository::observePlaylistSongs) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) recordCurrentSong()
                publish()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (player.isPlaying) recordCurrentSong()
                publish()
            }
            override fun onPlaybackStateChanged(playbackState: Int) = publish()
        })
        // A playing queue is also account-owned. Stop it before a different account's library
        // becomes observable so a switch cannot continue exposing the previous user's music.
        viewModelScope.launch {
            var previousUserId: String? = null
            repository.userId.collectLatest { userId ->
                if (previousUserId != userId) {
                    persistPlayback(previousUserId)
                    activeQueue = emptyList()
                    player.stop()
                    player.clearMediaItems()
                    activeUserId = userId
                    _selectedPlaylistId.value = null
                    _playback.value = PlaybackState()
                    previousUserId = userId
                }
                if (userId != null) {
                    val library = repository.songsForUser(userId)
                    if (activeUserId == userId && player.mediaItemCount == 0) {
                        restorePlayback(userId, library)
                    }
                }
            }
        }
        viewModelScope.launch { while (isActive) { publish(); delay(500) } }
    }

    fun play(song: Song) {
        if (song.userId != activeUserId) return
        val list = availableSongs()
        val songIndex = list.indexOfFirst { it.id == song.id }
        if (songIndex < 0) return
        activeQueue = list
        player.setMediaItems(list.map(::mediaItem), songIndex, 0)
        player.prepare()
        player.play()
        publish()
    }

    fun toggle() {
        if (player.mediaItemCount == 0) {
            // No queue loaded yet – load the full library and play the first song
            val list = availableSongs()
            if (list.isEmpty()) return
            activeQueue = list
            player.setMediaItems(list.map(::mediaItem), 0, 0)
            player.prepare()
            player.play()
        } else {
            if (player.isPlaying) player.pause() else player.play()
        }
        publish()
    }

    /** Navigate to previous, loading the queue first if the player hasn't been initialised. */
    fun safePrevious() {
        if (player.mediaItemCount == 0) ensureQueueLoaded()
        player.seekToPreviousMediaItem()
        publish()
    }

    /** Navigate to next, loading the queue first if the player hasn't been initialised. */
    fun safeNext() {
        if (player.mediaItemCount == 0) ensureQueueLoaded()
        player.seekToNextMediaItem()
        publish()
    }

    /** Load the full song library into ExoPlayer without starting playback. */
    private fun ensureQueueLoaded() {
        val list = availableSongs()
        if (list.isEmpty()) return
        activeQueue = list
        player.setMediaItems(list.map(::mediaItem), 0, 0)
        player.prepare()
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

    fun toggleFavorite(song: Song) {
        viewModelScope.launch { runCatching { repository.toggleFavorite(song) } }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { runCatching { repository.createPlaylist(name) } }
    }

    fun renamePlaylist(playlistId: String, name: String) {
        viewModelScope.launch { runCatching { repository.renamePlaylist(playlistId, name) } }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            runCatching { repository.deletePlaylist(playlistId) }
            if (_selectedPlaylistId.value == playlistId) _selectedPlaylistId.value = null
        }
    }

    fun addSongToPlaylist(playlistId: String, song: Song) {
        viewModelScope.launch { runCatching { repository.addSongToPlaylist(playlistId, song) } }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch { runCatching { repository.removeSongFromPlaylist(playlistId, songId) } }
    }

    fun selectPlaylist(playlistId: String?) {
        _selectedPlaylistId.value = playlistId
    }

    fun playPlaylist(playlist: Playlist, startSongId: String? = null) {
        viewModelScope.launch {
            val userId = activeUserId ?: return@launch
            if (playlist.userId != userId) return@launch
            val list = repository.playlistSongsForUser(userId, playlist.id)
            if (activeUserId != userId || list.isEmpty()) return@launch
            activeQueue = list
            val startIndex = startSongId?.let { id -> list.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 } ?: 0
            player.setMediaItems(list.map(::mediaItem), startIndex, 0)
            player.prepare()
            player.play()
            publish()
        }
    }

    private fun recordCurrentSong() {
        val song = currentSong() ?: return
        if (song.userId != activeUserId) return
        viewModelScope.launch { runCatching { repository.recordRecentlyPlayed(song) } }
    }

    private fun currentSong(): Song? {
        val idx = player.currentMediaItemIndex
        return activeQueue.getOrNull(idx)
    }

    private fun availableSongs(): List<Song> = songs.value.filter { it.userId == activeUserId }

    private fun publish() {
        ensureEqualizer()
        val idx = player.currentMediaItemIndex
        persistPlayback(activeUserId)
        _playback.value = PlaybackState(
            current = activeQueue.getOrNull(idx),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            repeatMode = _repeatMode.value,
            shuffleEnabled = _shuffleEnabled.value,
            queue = activeQueue,
            currentIndex = if (player.mediaItemCount > 0) idx else -1
        )
    }

    private fun mediaItem(song: Song): MediaItem =
        MediaItem.Builder().setMediaId(song.id).setUri(song.localPath).build()

    private fun restorePlayback(userId: String, library: List<Song>) {
        activeQueue = library
        val savedState = playbackPreferences.getString(playbackKey(userId), null)
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
            ?: run {
                publish()
                return
            }
        val savedIds = savedState.optJSONArray("queue") ?: JSONArray()
        val libraryById = library.associateBy(Song::id)
        val restoredQueue = (0 until savedIds.length())
            .mapNotNull { index -> libraryById[savedIds.optString(index)] }
        if (restoredQueue.isEmpty()) {
            publish()
            return
        }

        val currentId = savedState.optString("current", "")
        val currentIndex = restoredQueue.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val positionMs = savedState.optLong("positionMs", 0L).coerceAtLeast(0L)
        activeQueue = restoredQueue
        player.setMediaItems(restoredQueue.map(::mediaItem), currentIndex, positionMs)
        player.prepare()
        publish()
    }

    private fun persistPlayback(userId: String?) {
        if (userId == null || player.mediaItemCount == 0) return
        val currentSong = activeQueue.getOrNull(player.currentMediaItemIndex) ?: return
        val queue = JSONArray().apply { activeQueue.forEach { put(it.id) } }
        val state = JSONObject()
            .put("queue", queue)
            .put("current", currentSong.id)
            .put("positionMs", player.currentPosition.coerceAtLeast(0))
        playbackPreferences.edit().putString(playbackKey(userId), state.toString()).apply()
    }

    private fun playbackKey(userId: String): String = "state:$userId"

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
        persistPlayback(activeUserId)
        equalizer?.release()
        player.release()
        super.onCleared()
    }
}
