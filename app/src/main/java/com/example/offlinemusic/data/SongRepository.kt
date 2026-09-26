package com.example.offlinemusic.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.offlinemusic.auth.AuthUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SongRepository(
    private val context: Context,
    private val dao: SongDao,
    private val authState: StateFlow<AuthUiState>
) {
    val userId: Flow<String?> = authState.map { it.userId }.distinctUntilChanged()
    val songs: Flow<List<Song>> = userId.flatMapLatest { userId ->
        userId?.let(dao::observeAll) ?: flowOf(emptyList())
    }

    /** Copies an audio Uri into filesDir/audio, which is private to this app. */
    suspend fun import(uri: Uri): Song = withContext(Dispatchers.IO) {
        val userId = requireCurrentUserId()
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to read the selected audio file")
        try {
            importStream(input, userId)
        } finally {
            input.close()
        }
    }

    /** Downloads a direct audio URL or resolves a YouTube URL into an audio stream. */
    suspend fun download(url: String): Song = withContext(Dispatchers.IO) {
        val userId = requireCurrentUserId()
        try {
            val source = if (isYouTubeUrl(url)) {
                try {
                    resolveYouTube(url)
                } catch (e: Exception) {
                    android.util.Log.e("SongRepository", "Failed to resolve YouTube URL: $url", e)
                    throw Exception("Failed to resolve YouTube URL: ${e.message ?: e.javaClass.simpleName}", e)
                }
            } else {
                DownloadSource(url)
            }
            val input = openConnection(source.audioUrl).inputStream
            try {
                importStream(input, userId, source.metadata)
            } finally {
                input.close()
            }
        } catch (e: Exception) {
            android.util.Log.e("SongRepository", "Download failed for URL: $url", e)
            throw e
        }
    }

    private suspend fun importStream(
        input: java.io.InputStream,
        userId: String,
        metadata: DownloadMetadata? = null
    ): Song {
        val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val output = File(audioDir, "$id.audio")
        var artworkFile: File? = null
        var retriever: MediaMetadataRetriever? = null
        return try {
            output.outputStream().use(input::copyTo)
            val meta = MediaMetadataRetriever().also {
                retriever = it
                it.setDataSource(output.absolutePath)
            }
            val embeddedArtwork = meta.embeddedPicture?.let { bytes ->
                File(context.filesDir, "artwork").apply { mkdirs() }.let { dir ->
                    File(dir, "$id.jpg").also {
                        artworkFile = it
                        it.writeBytes(bytes)
                    }.absolutePath
                }
            }
            val artwork = embeddedArtwork ?: metadata?.artworkUrl?.let { artworkUrl ->
                try {
                    File(context.filesDir, "artwork").apply { mkdirs() }.let { directory ->
                        File(directory, "$id.jpg").also { file ->
                            artworkFile = file
                            openConnection(artworkUrl).inputStream.use { input ->
                                file.outputStream().use(input::copyTo)
                            }
                        }.absolutePath
                    }
                } catch (e: Exception) {
                    artworkFile?.delete()
                    artworkFile = null
                    android.util.Log.w("SongRepository", "Failed to download artwork from: $artworkUrl", e)
                    null
                }
            }
            Song(
                id = id,
                userId = userId,
                title = metadata?.title.orEmpty().ifBlank { meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty().ifBlank { "Unknown title" } },
                artist = metadata?.artist.orEmpty().ifBlank { meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().ifBlank { "Unknown artist" } },
                album = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty().ifBlank { "Unknown album" },
                durationMs = metadata?.durationMs ?: meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                localPath = output.absolutePath,
                artworkPath = artwork
            ).also { dao.upsert(it) }
        } catch (error: Exception) {
            android.util.Log.e("SongRepository", "Failed to import stream, cleaning up files", error)
            output.delete()
            artworkFile?.delete()
            throw error
        } finally {
            retriever?.release()
        }
    }

    private fun resolveYouTube(url: String): DownloadSource {
        try {
            val extractor = NewPipe.getServiceByUrl(url).getStreamExtractor(url)
            extractor.fetchPage()
            
            // Log extraction details
            android.util.Log.d("SongRepository", "=== YouTube Extraction Start ===")
            android.util.Log.d("SongRepository", "URL: $url")
            android.util.Log.d("SongRepository", "Video: ${extractor.name}")
            android.util.Log.d("SongRepository", "Uploader: ${extractor.uploaderName}")
            android.util.Log.d("SongRepository", "Duration: ${extractor.length}s")
            
            // Attempt 1: Try standard audio streams
            val audioStreams = try {
                extractor.audioStreams
            } catch (e: Exception) {
                android.util.Log.w("SongRepository", "Exception getting audioStreams: ${e.message}")
                null
            } ?: emptyList()
            
            android.util.Log.d("SongRepository", "Audio streams: ${audioStreams.size}")
            
            // Select the best audio stream by bitrate
            var selectedStream = audioStreams.maxByOrNull { it.bitrate }
            
            if (selectedStream != null) {
                val streamUrl = selectedStream.url
                if (streamUrl != null) {
                    android.util.Log.d("SongRepository", "Selected audio stream, bitrate: ${selectedStream.bitrate}")
                    android.util.Log.d("SongRepository", "=== YouTube Extraction Success ===")
                    
                    return DownloadSource(
                        audioUrl = streamUrl,
                        metadata = DownloadMetadata(
                            title = extractor.name,
                            artist = extractor.uploaderName,
                            durationMs = extractor.length * 1_000,
                            artworkUrl = extractor.thumbnails.lastOrNull()?.url
                        )
                    )
                }
            }
            
            // Fallback: Try video streams (they usually include audio)
            android.util.Log.d("SongRepository", "No audio streams found, trying video streams as fallback...")
            val videoStreams = try {
                extractor.videoStreams
            } catch (e: Exception) {
                android.util.Log.w("SongRepository", "Exception getting videoStreams: ${e.message}")
                null
            } ?: emptyList()
            
            android.util.Log.d("SongRepository", "Video streams: ${videoStreams.size}")
            
            // Try to find the best video stream
            val videoStream = videoStreams.maxByOrNull { it.bitrate }
            if (videoStream != null) {
                val streamUrl = videoStream.url
                if (streamUrl != null) {
                    android.util.Log.d("SongRepository", "Falling back to video stream, bitrate: ${videoStream.bitrate}")
                    android.util.Log.d("SongRepository", "=== YouTube Extraction Success (Video Fallback) ===")
                    
                    return DownloadSource(
                        audioUrl = streamUrl,
                        metadata = DownloadMetadata(
                            title = extractor.name,
                            artist = extractor.uploaderName,
                            durationMs = extractor.length * 1_000,
                            artworkUrl = extractor.thumbnails.lastOrNull()?.url
                        )
                    )
                }
            }
            
            // No streams found at all
            android.util.Log.e("SongRepository", "=== YouTube Extraction Failed: No streams available ===")
            android.util.Log.e("SongRepository", "Audio streams count: ${audioStreams.size}, Video streams count: ${videoStreams.size}")
            
            throw Exception(
                "No audio or video streams could be extracted from this YouTube video.\n\n" +
                "This may occur if:\n" +
                "• The video is age-restricted or requires authentication\n" +
                "• The video is private, deleted, or unavailable in your region\n" +
                "• The video is from YouTube Music Premium with DRM protection\n" +
                "• YouTube has blocked automated downloading for this content\n\n" +
                "Try using a different video or a direct audio file URL."
            )
        } catch (e: Exception) {
            android.util.Log.e("SongRepository", "YouTube extraction exception: ${e.javaClass.simpleName}: ${e.message}", e)
            if (e.message?.startsWith("No audio or video streams") == true) {
                throw e  // Re-throw our custom error messages
            }
            throw Exception("Failed to extract audio from YouTube: ${e.message}", e)
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }

    private fun isYouTubeUrl(url: String): Boolean = runCatching {
        URL(url).host.lowercase().let { host -> host == "youtu.be" || host.endsWith(".youtube.com") || host == "youtube.com" }
    }.getOrDefault(false)

    private data class DownloadSource(val audioUrl: String, val metadata: DownloadMetadata? = null)
    private data class DownloadMetadata(val title: String?, val artist: String?, val durationMs: Long?, val artworkUrl: String?)

    suspend fun delete(song: Song) = withContext(Dispatchers.IO) {
        val userId = requireCurrentUserId()
        check(song.userId == userId) { "Cannot delete a song owned by another account" }
        dao.delete(song.id, userId)
        listOfNotNull(song.localPath, song.artworkPath).forEach { path ->
            val file = File(path)
            if (file.exists() && !file.delete()) {
                android.util.Log.w("SongRepository", "Could not remove deleted song file: $path")
            }
        }
    }

    private fun requireCurrentUserId(): String =
        authState.value.userId ?: error("Sign in before changing the music library")
}
