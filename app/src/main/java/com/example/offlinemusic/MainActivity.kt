package com.example.offlinemusic

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.offlinemusic.data.Song
import com.example.offlinemusic.player.PlaybackState
import com.example.offlinemusic.player.PlayerViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()
    private val importAudio = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch { runCatching { (application as MusicApplication).repository.import(uri) } }
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent {
        MaterialTheme { MusicApp(viewModel, { importAudio.launch("audio/*") }) { url -> lifecycleScope.launch { runCatching { (application as MusicApplication).repository.download(url) } } } }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MusicApp(viewModel: PlayerViewModel, onImport: () -> Unit, onDownload: (String) -> Unit) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var showDownload by rememberSaveable { mutableStateOf(false) }
    val filtered = remember(songs, search) { songs.filter { "${it.title} ${it.artist} ${it.album}".contains(search, true) } }
    if (showDownload) DownloadDialog(onDismiss = { showDownload = false }) { url -> onDownload(url); showDownload = false }
    Scaffold(topBar = { TopAppBar(title = { Text("Offline Music") }, actions = { IconButton(onClick = { showDownload = true }) { Icon(Icons.Default.Download, "Download from URL") }; IconButton(onClick = onImport) { Icon(Icons.Default.Add, "Import audio") } }) }, bottomBar = { playback.current?.let { MiniPlayer(it, playback, viewModel) } }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(search, { search = it }, Modifier.padding(16.dp).fillMaxWidth(), label = { Text("Search library") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
            if (filtered.isEmpty()) EmptyLibrary(onImport) else LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) { items(filtered, key = { it.id }) { song -> SongRow(song, playback.current?.id == song.id, { viewModel.play(song) }, { viewModel.delete(song) }) } }
        }
    }
}

@Composable private fun DownloadDialog(onDismiss: () -> Unit, onDownload: (String) -> Unit) { var url by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Download audio") }, text = { OutlinedTextField(url, { url = it }, label = { Text("Audio or YouTube URL") }, singleLine = true) }, confirmButton = { TextButton(onClick = { onDownload(url) }, enabled = url.startsWith("http")) { Text("Download") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }

@Composable private fun EmptyLibrary(onImport: () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.LibraryMusic, null, Modifier.size(56.dp)); Spacer(Modifier.height(12.dp)); Text("Your private library is empty"); Text("Import an audio file to listen offline", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(16.dp)); Button(onClick = onImport) { Text("Import audio") } } }

@Composable private fun SongRow(song: Song, selected: Boolean, onPlay: () -> Unit, onDelete: () -> Unit) { ListItem(modifier = Modifier.clickable(onClick = onPlay), leadingContent = { Artwork(song, Modifier.size(52.dp)) }, headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text("${song.artist} • ${song.album}", maxLines = 1, overflow = TextOverflow.Ellipsis) }, trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { Text(formatTime(song.durationMs)); IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") } } }, colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) }

@Composable private fun MiniPlayer(song: Song, state: PlaybackState, viewModel: PlayerViewModel) { Surface(shadowElevation = 8.dp) { Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Artwork(song, Modifier.size(44.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1) }; IconButton(viewModel::previous) { Icon(Icons.Default.SkipPrevious, "Previous") }; IconButton(viewModel::toggle) { Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.isPlaying) "Pause" else "Play") }; IconButton(viewModel::next) { Icon(Icons.Default.SkipNext, "Next") } }; Slider(value = state.positionMs.toFloat().coerceAtMost(state.durationMs.toFloat()), onValueChange = { viewModel.seekTo(it.toLong()) }, valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat()) } } }

@Composable private fun Artwork(song: Song, modifier: Modifier = Modifier) { val bitmap = remember(song.artworkPath) { song.artworkPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }; if (bitmap != null) Image(bitmap, null, modifier.clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) else Surface(modifier.clip(RoundedCornerShape(8.dp)), color = MaterialTheme.colorScheme.secondaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null) } } }
private fun formatTime(ms: Long): String = "%d:%02d".format(ms / 60_000, (ms / 1_000) % 60)
