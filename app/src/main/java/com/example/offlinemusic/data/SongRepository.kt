package com.example.offlinemusic.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.UUID

class SongRepository(private val context: Context, private val dao: SongDao) {
    val songs: Flow<List<Song>> = dao.observeAll()

    /** Copies an audio Uri into filesDir/audio, which is private to this app. */
    suspend fun import(uri: Uri): Song = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input -> importStream(input) }
            ?: error("Unable to read the selected audio file")
    }

    /** Downloads a remote audio URL directly into the same private library folder. */
    suspend fun download(url: String): Song = withContext(Dispatchers.IO) {
        URL(url).openConnection().apply { connectTimeout = 15_000; readTimeout = 30_000 }.getInputStream().use(::importStream)
    }

    private suspend fun importStream(input: java.io.InputStream): Song {
        val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val output = File(audioDir, "$id.audio")
        output.outputStream().use(input::copyTo)
        try {
            val meta = MediaMetadataRetriever().apply { setDataSource(output.absolutePath) }
            val artwork = meta.embeddedPicture?.let { bytes ->
                File(context.filesDir, "artwork").apply { mkdirs() }.let { dir ->
                    File(dir, "$id.jpg").also { it.writeBytes(bytes) }.absolutePath
                }
            }
            Song(
                id = id,
                title = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty().ifBlank { "Unknown title" },
                artist = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().ifBlank { "Unknown artist" },
                album = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty().ifBlank { "Unknown album" },
                durationMs = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                localPath = output.absolutePath,
                artworkPath = artwork
            ).also { dao.upsert(it); meta.release() }
        } catch (error: Exception) { output.delete(); throw error }
    }

    suspend fun delete(song: Song) = withContext(Dispatchers.IO) {
        File(song.localPath).delete(); song.artworkPath?.let { File(it).delete() }; dao.delete(song.id)
    }
}
