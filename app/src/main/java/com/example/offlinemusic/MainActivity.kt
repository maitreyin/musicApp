package com.example.offlinemusic

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.offlinemusic.data.Song
import com.example.offlinemusic.player.PlaybackState
import com.example.offlinemusic.player.PlayerViewModel
import com.example.offlinemusic.player.RepeatMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()
    private val importAudio = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            runCatching { (application as MusicApplication).repository.import(uri) }
                .onFailure { e -> android.util.Log.e("MainActivity", "Import failed", e) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicTheme {
                MusicApp(viewModel, { importAudio.launch("audio/*") }) { url, onStart, onSuccess, onError ->
                    lifecycleScope.launch {
                        onStart()
                        runCatching {
                            (application as MusicApplication).repository.download(url)
                        }.onSuccess { song ->
                            onSuccess("Successfully downloaded: ${song.title}")
                        }.onFailure { e ->
                            onError(e.message ?: "Unknown error occurred during download")
                            android.util.Log.e("MainActivity", "Download failed for URL: $url", e)
                        }
                    }
                }
            }
        }
    }
}

object AppColors {
    val Midnight = Color(0xFF0B1020)
    val Surface = Color(0xFF101827)
    val SurfaceElevated = Color(0xFF1A2433)
    val Panel = Color(0xFF202B3D)
    val PanelStrong = Color(0xFF243145)
    val Border = Color(0xFF2F3F56)
    val Accent = Color(0xFF67E8F9)
    val AccentStrong = Color(0xFF38BDF8)
    val Success = Color(0xFF34D399)
    val TextPrimary = Color(0xFFEAF3FF)
    val TextSecondary = Color(0xFF9AA9BD)
    val TextMuted = Color(0xFF6B7C96)
    val GradientBrush = Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF1D4ED8), Color(0xFF0EA5E9)))
}

@Composable
fun MusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AppColors.Accent,
            secondary = AppColors.AccentStrong,
            tertiary = AppColors.Success,
            surface = AppColors.SurfaceElevated,
            background = AppColors.Midnight,
            onPrimary = AppColors.Midnight,
            onSecondary = AppColors.Midnight,
            onBackground = AppColors.TextPrimary,
            onSurface = AppColors.TextPrimary
        )
    ) {
        content()
    }
}

enum class Screen {
    HOME, LIBRARY, EQUALIZER, PROFILE
}

@Composable
fun MusicApp(
    viewModel: PlayerViewModel,
    onImport: () -> Unit,
    onDownload: (String, onStart: () -> Unit, onSuccess: (String) -> Unit, onError: (String) -> Unit) -> Unit
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()

    var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var showDownload by rememberSaveable { mutableStateOf(false) }
    var isDownloading by rememberSaveable { mutableStateOf(false) }
    var downloadError by rememberSaveable { mutableStateOf<String?>(null) }
    var downloadSuccess by rememberSaveable { mutableStateOf<String?>(null) }
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }

    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(songs, search) {
        songs.filter { "${it.title} ${it.artist} ${it.album}".contains(search, true) }
    }

    val handleDownload: (String) -> Unit = { url ->
        onDownload(
            url,
            {
                isDownloading = true
                downloadError = null
                downloadSuccess = null
            },
            { message ->
                isDownloading = false
                downloadSuccess = message
                showDownload = false
            },
            { error ->
                isDownloading = false
                downloadError = error
            }
        )
    }

    if (showDownload) {
        DownloadDialog(onDismiss = { showDownload = false }, isLoading = isDownloading) { url ->
            handleDownload(url)
        }
    }

    if (showSearch) {
        SearchDialog(
            query = search,
            onQueryChange = { search = it },
            onDismiss = { showSearch = false }
        )
    }

    if (isDownloading) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Downloading...") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AppColors.Accent)
                    Spacer(Modifier.height(16.dp))
                    Text("Please wait while we download your audio")
                }
            },
            confirmButton = { }
        )
    }

    downloadError?.let { error ->
        AlertDialog(
            onDismissRequest = { downloadError = null },
            title = { Text("Download Failed") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { downloadError = null }) { Text("OK") }
            }
        )
    }

    downloadSuccess?.let { success ->
        AlertDialog(
            onDismissRequest = { downloadSuccess = null },
            title = { Text("Download Successful") },
            text = { Text(success) },
            confirmButton = {
                TextButton(onClick = { downloadSuccess = null }) { Text("OK") }
            }
        )
    }

    if (showFullPlayer && playback.current != null) {
        FullScreenPlayer(playback, viewModel, onDismiss = { showFullPlayer = false })
    }

    Scaffold(
        containerColor = AppColors.Midnight,
        bottomBar = {
            Column {
                if (playback.current != null) {
                    MiniPlayer(playback, viewModel, { showFullPlayer = true }, Modifier.fillMaxWidth())
                }
                NavigationBar(
                    containerColor = AppColors.SurfaceElevated,
                    modifier = Modifier.shadow(8.dp)
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("HOME") },
                        selected = currentScreen == Screen.HOME,
                        onClick = { currentScreen = Screen.HOME },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppColors.Accent,
                            selectedTextColor = AppColors.Accent,
                            unselectedIconColor = AppColors.TextSecondary,
                            unselectedTextColor = AppColors.TextSecondary
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.LibraryMusic, null) },
                        label = { Text("LIBRARY") },
                        selected = currentScreen == Screen.LIBRARY,
                        onClick = { currentScreen = Screen.LIBRARY },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppColors.Accent,
                            selectedTextColor = AppColors.Accent,
                            unselectedIconColor = AppColors.TextSecondary,
                            unselectedTextColor = AppColors.TextSecondary
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Tune, null) },
                        label = { Text("EQUALIZER") },
                        selected = currentScreen == Screen.EQUALIZER,
                        onClick = { currentScreen = Screen.EQUALIZER },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppColors.Accent,
                            selectedTextColor = AppColors.Accent,
                            unselectedIconColor = AppColors.TextSecondary,
                            unselectedTextColor = AppColors.TextSecondary
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Person, null) },
                        label = { Text("PROFILE") },
                        selected = currentScreen == Screen.PROFILE,
                        onClick = { currentScreen = Screen.PROFILE },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppColors.Accent,
                            selectedTextColor = AppColors.Accent,
                            unselectedIconColor = AppColors.TextSecondary,
                            unselectedTextColor = AppColors.TextSecondary
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(AppColors.Midnight)) {
            when (currentScreen) {
                Screen.HOME -> HomeScreen(
                    songs,
                    filtered,
                    viewModel,
                    onImport,
                    { showDownload = true },
                    { showSearch = true },
                    isDownloading
                )
                Screen.LIBRARY -> LibraryScreen(songs, filtered, viewModel, { showSearch = true })
                Screen.EQUALIZER -> EqualizerScreen(viewModel, { showSearch = true })
                Screen.PROFILE -> ProfileScreen({ showSearch = true })
            }
        }
    }
}

@Composable
fun TopBar(title: String, onSearchClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.Midnight,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = AppColors.TextPrimary)
            }
        }
    }
}

@Composable
fun HomeScreen(
    songs: List<Song>,
    filtered: List<Song>,
    viewModel: PlayerViewModel,
    onImport: () -> Unit,
    onDownload: () -> Unit,
    onSearchClick: () -> Unit,
    isLoading: Boolean
) {
    Column(Modifier.fillMaxSize().background(AppColors.Midnight)) {
        TopBar("MUSED", onSearchClick)

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onImport,
                enabled = !isLoading,
                modifier = Modifier.weight(1f).height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.PanelStrong, contentColor = AppColors.TextPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import")
            }
            Button(
                onClick = onDownload,
                enabled = !isLoading,
                modifier = Modifier.weight(1f).height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Panel, contentColor = AppColors.TextPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Download")
            }
        }

        if (songs.isEmpty()) {
            EmptyLibrary(onImport, onDownload, !isLoading)
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                items(filtered, key = { it.id }) { song ->
                    SongRow(
                        song,
                        selected = viewModel.playback.collectAsStateWithLifecycle().value.current?.id == song.id,
                        onPlay = { viewModel.play(song) },
                        onDelete = { viewModel.delete(song) }
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    songs: List<Song>,
    filtered: List<Song>,
    viewModel: PlayerViewModel,
    onSearchClick: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(AppColors.Midnight)) {
        TopBar("Library", onSearchClick)

        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LibraryMusic, null, Modifier.size(56.dp), tint = AppColors.Accent)
                    Spacer(Modifier.height(12.dp))
                    Text("No songs yet", color = AppColors.TextSecondary)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                items(filtered, key = { it.id }) { song ->
                    SongRow(
                        song,
                        selected = false,
                        onPlay = { viewModel.play(song) },
                        onDelete = { viewModel.delete(song) }
                    )
                }
            }
        }
    }
}

@Composable
fun EqualizerScreen(viewModel: PlayerViewModel, onSearchClick: () -> Unit) {
    val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(AppColors.Midnight)) {
        TopBar("Equalizer", onSearchClick)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.SurfaceElevated,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Audio Equalizer", style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)
                        Text("Live output", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                    }
                    Switch(
                        checked = equalizerState.enabled,
                        onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppColors.Accent,
                            checkedTrackColor = AppColors.Accent.copy(alpha = 0.45f),
                            uncheckedThumbColor = AppColors.TextSecondary,
                            uncheckedTrackColor = AppColors.Panel
                        )
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.SurfaceElevated,
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val bands = equalizerState.bandLevels
                    val labels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14.5kHz")

                    bands.forEachIndexed { index, value ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(labels[index], color = AppColors.TextPrimary, style = MaterialTheme.typography.bodySmall)
                                Text("${value}%", color = AppColors.Accent, style = MaterialTheme.typography.bodySmall)
                            }
                            Slider(
                                value = value.toFloat(),
                                onValueChange = { viewModel.setBandLevel(index, it.toInt()) },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = AppColors.Accent,
                                    activeTrackColor = AppColors.Accent,
                                    inactiveTrackColor = AppColors.Panel
                                )
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.SurfaceElevated,
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Presets", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Flat", "Bass", "Treble", "Vocal", "Club").forEach { preset ->
                            val selected = equalizerState.presetName == preset
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.applyPreset(preset) },
                                label = { Text(preset) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.Accent,
                                    selectedLabelColor = AppColors.Midnight,
                                    containerColor = AppColors.Panel,
                                    labelColor = AppColors.TextPrimary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(onSearchClick: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AppColors.Midnight)) {
        TopBar("Profile", onSearchClick)

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Person, null, Modifier.size(64.dp), tint = AppColors.TextSecondary)
                Spacer(Modifier.height(16.dp))
                Text("Profile Placeholder", color = AppColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text("Authentication is not implemented yet.", color = AppColors.TextSecondary)
            }
        }
    }
}

@Composable
fun EmptyLibrary(onImport: () -> Unit, onDownload: () -> Unit, enabled: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.LibraryMusic, null, Modifier.size(56.dp), tint = AppColors.Accent)
            Spacer(Modifier.height(12.dp))
            Text("Your private library is empty", style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)
            Text("Import an audio file or download from YouTube", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onImport,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.PanelStrong),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Import")
                }
                Button(
                    onClick = onDownload,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Panel),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Download")
                }
            }
        }
    }
}

@Composable
fun SongRow(song: Song, selected: Boolean, onPlay: () -> Unit, onDelete: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .clickable(onClick = onPlay)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = if (selected) AppColors.PanelStrong else AppColors.SurfaceElevated,
        shadowElevation = if (selected) 6.dp else 0.dp
    ) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(song, Modifier.size(52.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextPrimary)
                    Text("${song.artist} • ${song.album}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                }
                Text(formatTime(song.durationMs), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                Spacer(Modifier.width(8.dp))
                Box {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = AppColors.TextPrimary)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(AppColors.SurfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play") },
                            onClick = {
                                expanded = false
                                onPlay()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to playlist") },
                            onClick = {
                                expanded = false
                                Toast.makeText(context, "Add to playlist coming soon", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Song info") },
                            onClick = {
                                expanded = false
                                showInfo = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                expanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showInfo) {
        SongInfoDialog(song, onDismiss = { showInfo = false })
    }
}

@Composable
fun SearchDialog(query: String, onQueryChange: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = AppColors.SurfaceElevated,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Search", style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search library") },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Accent,
                        unfocusedBorderColor = AppColors.Border,
                        cursorColor = AppColors.Accent,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun SongInfoDialog(song: Song, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(song.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Artist: ${song.artist}")
                Text("Album: ${song.album}")
                Text("Duration: ${formatTime(song.durationMs)}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun MiniPlayer(state: PlaybackState, viewModel: PlayerViewModel, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .shadow(12.dp)
            .clickable(onClick = onTap),
        color = Color.Transparent
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(AppColors.GradientBrush)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.current?.let { song ->
                Artwork(song, Modifier.size(42.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AppColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AppColors.TextPrimary.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(
                    onClick = { viewModel.toggle() },
                    Modifier.size(40.dp)
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null,
                        tint = AppColors.TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun FullScreenPlayer(state: PlaybackState, viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppColors.Midnight
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(AppColors.GradientBrush)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = AppColors.TextPrimary)
                    }
                    Text("Now Playing", color = AppColors.TextPrimary, style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.MoreVert, null, tint = AppColors.TextPrimary)
                    }
                }

                state.current?.let { song ->
                    Surface(
                        modifier = Modifier
                            .size(300.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .shadow(16.dp),
                        color = AppColors.SurfaceElevated
                    ) {
                        Artwork(song, Modifier.fillMaxSize())
                    }

                    Spacer(Modifier.height(24.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(song.title, style = MaterialTheme.typography.headlineSmall, color = AppColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(song.artist, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextPrimary.copy(alpha = 0.8f))
                    }

                    Spacer(Modifier.height(24.dp))

                    Column(Modifier.fillMaxWidth()) {
                        Slider(
                            value = state.positionMs.toFloat(),
                            onValueChange = { viewModel.seekTo(it.toLong()) },
                            valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = AppColors.TextPrimary,
                                activeTrackColor = AppColors.TextPrimary,
                                inactiveTrackColor = AppColors.TextPrimary.copy(alpha = 0.2f)
                            )
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(state.positionMs), style = MaterialTheme.typography.bodySmall, color = AppColors.TextPrimary)
                            Text(formatTime(state.durationMs), style = MaterialTheme.typography.bodySmall, color = AppColors.TextPrimary)
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.toggleShuffle() }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Shuffle, null, tint = if (state.shuffleEnabled) AppColors.TextPrimary else AppColors.TextPrimary.copy(alpha = 0.5f), modifier = Modifier.size(26.dp))
                        }
                        IconButton(onClick = { viewModel.previous() }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.SkipPrevious, null, tint = AppColors.TextPrimary, modifier = Modifier.size(32.dp))
                        }
                        Surface(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(32.dp)),
                            color = AppColors.TextPrimary
                        ) {
                            IconButton(onClick = { viewModel.toggle() }, Modifier.fillMaxSize()) {
                                Icon(
                                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    null,
                                    tint = AppColors.Midnight,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.next() }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.SkipNext, null, tint = AppColors.TextPrimary, modifier = Modifier.size(32.dp))
                        }
                        IconButton(onClick = { viewModel.cycleRepeatMode() }, modifier = Modifier.size(48.dp)) {
                            Text(
                                when (state.repeatMode) {
                                    RepeatMode.OFF -> "↻"
                                    RepeatMode.ONE -> "↻1"
                                    RepeatMode.ALL -> "↻"
                                },
                                color = if (state.repeatMode == RepeatMode.OFF) AppColors.TextPrimary.copy(alpha = 0.5f) else AppColors.TextPrimary,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Artwork(song: Song, modifier: Modifier = Modifier) {
    val bitmap = remember(song.artworkPath) {
        song.artworkPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    if (bitmap != null) {
        Image(bitmap, null, modifier.clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
    } else {
        Surface(modifier.clip(RoundedCornerShape(10.dp)), color = AppColors.AccentStrong) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Default.MusicNote, null, tint = AppColors.Midnight)
            }
        }
    }
}

@Composable
fun DownloadDialog(onDismiss: () -> Unit, isLoading: Boolean = false, onDownload: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = if (!isLoading) onDismiss else { {} },
        title = { Text("Download audio") },
        text = {
            OutlinedTextField(
                url, { url = it },
                label = { Text("Audio or YouTube URL") },
                singleLine = true,
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                textStyle = LocalTextStyle.current.copy(color = AppColors.TextPrimary)
            )
        },
        confirmButton = {
            TextButton(onClick = { onDownload(url) }, enabled = url.startsWith("http") && !isLoading) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel")
            }
        }
    )
}

private fun formatTime(ms: Long): String = "%d:%02d".format(ms / 60_000, (ms / 1_000) % 60)
