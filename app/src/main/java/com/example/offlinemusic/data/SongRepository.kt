package com.example.offlinemusic.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class SongRepository(private val context: Context, private val dao: SongDao) {
    val songs: Flow<List<Song>> = dao.observeAll()

    /** Copies an audio Uri into filesDir/audio, which is private to this app. */
    suspend fun import(uri: Uri): Song = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to read the selected audio file")
        try {
            importStream(input)
        } finally {
            input.close()
        }
    }

    /** Downloads a direct audio URL or resolves a YouTube URL into an audio stream. */
    suspend fun download(url: String): Song = withContext(Dispatchers.IO) {
        val source = if (isYouTubeUrl(url)) resolveYouTube(url) else DownloadSource(url)
        val input = openConnection(source.audioUrl).inputStream
        try {
            importStream(input, source.metadata)
        } finally {
            input.close()
        }
    }

    private suspend fun importStream(input: java.io.InputStream, metadata: DownloadMetadata? = null): Song {
        val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val output = File(audioDir, "$id.audio")
        output.outputStream().use(input::copyTo)
        var downloadedArtwork: File? = null
        return try {
            val meta = MediaMetadataRetriever().apply { setDataSource(output.absolutePath) }
            val embeddedArtwork = meta.embeddedPicture?.let { bytes ->
                File(context.filesDir, "artwork").apply { mkdirs() }.let { dir ->
                    File(dir, "$id.jpg").also { it.writeBytes(bytes) }.absolutePath
                }
            }
            val artwork = embeddedArtwork ?: metadata?.artworkUrl?.let { artworkUrl ->
                runCatching {
                    File(context.filesDir, "artwork").apply { mkdirs() }.let { directory ->
                        File(directory, "$id.jpg").also { file ->
                            downloadedArtwork = file
                            openConnection(artworkUrl).inputStream.use { input ->
                                file.outputStream().use(input::copyTo)
                            }
                        }.absolutePath
                    }
                }.getOrNull()
            }
            Song(
                id = id,
                title = metadata?.title.orEmpty().ifBlank { meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty().ifBlank { "Unknown title" } },
                artist = metadata?.artist.orEmpty().ifBlank { meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().ifBlank { "Unknown artist" } },
                album = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty().ifBlank { "Unknown album" },
                durationMs = metadata?.durationMs ?: meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                localPath = output.absolutePath,
                artworkPath = artwork
            ).also { dao.upsert(it); meta.release() }
        } catch (error: Exception) { output.delete(); downloadedArtwork?.delete(); throw error }
    }

    private fun resolveYouTube(url: String): DownloadSource {
        val extractor = NewPipe.getServiceByUrl(url).getStreamExtractor(url)
        extractor.fetchPage()
        val audioStream = extractor.audioStreams.maxByOrNull { it.bitrate }
            ?: error("No downloadable audio stream is available for this video")
        return DownloadSource(
            audioUrl = audioStream.url ?: error("The selected audio stream has no download URL"),
            metadata = DownloadMetadata(
                title = extractor.name,
                artist = extractor.uploaderName,
                durationMs = extractor.length * 1_000,
                artworkUrl = extractor.thumbnails.lastOrNull()?.url
            )
        )
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
        File(song.localPath).delete(); song.artworkPath?.let { File(it).delete() }; dao.delete(song.id)
    }
}
