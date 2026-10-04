package com.example.offlinemusic.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.offlinemusic.MainActivity
import com.example.offlinemusic.MusicApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val persistPosition = object : Runnable {
        override fun run() {
            persistPlayback()
            handler.postDelayed(this, 1_000)
        }
    }
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) recordCurrentSong()
            persistPlayback()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (player.isPlaying) recordCurrentSong()
            persistPlayback()
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = (application as MusicApplication).player
        val activityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val session = MediaSession.Builder(this, player)
            .setSessionActivity(activityIntent)
            .build()
        mediaSession = session
        addSession(session)
        player.addListener(playerListener)
        handler.post(persistPosition)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        handler.removeCallbacks(persistPosition)
        persistPlayback()
        player.removeListener(playerListener)
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun persistPlayback() {
        val app = application as MusicApplication
        val userId = app.playbackOwnerUserId ?: return
        if (app.playerViewModelAttached || app.authManager.authState.value.userId != userId || player.mediaItemCount == 0) return
        val currentItem = player.currentMediaItem ?: return
        val currentSongId = currentItem.mediaId.takeIf(String::isNotBlank) ?: return
        val queue = JSONArray().apply {
            repeat(player.mediaItemCount) { index -> put(player.getMediaItemAt(index).mediaId) }
        }
        val state = JSONObject()
            .put("queue", queue)
            .put("current", currentSongId)
            .put("positionMs", player.currentPosition.coerceAtLeast(0))
        getSharedPreferences("mused_playback", Context.MODE_PRIVATE)
            .edit()
            .putString("state:$userId", state.toString())
            .apply()
    }

    private fun recordCurrentSong() {
        val app = application as MusicApplication
        val userId = app.playbackOwnerUserId ?: return
        val songId = player.currentMediaItem?.mediaId?.takeIf(String::isNotBlank) ?: return
        serviceScope.launch {
            val song = runCatching { app.repository.songsForUser(userId).firstOrNull { it.id == songId } }
                .getOrNull() ?: return@launch
            if (app.playbackOwnerUserId == userId && app.authManager.authState.value.userId == userId) {
                runCatching { app.repository.recordRecentlyPlayed(song) }
            }
        }
    }
}