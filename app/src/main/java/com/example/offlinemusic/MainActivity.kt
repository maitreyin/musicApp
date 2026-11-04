package com.example.offlinemusic

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.offlinemusic.auth.AuthUiState
import com.example.offlinemusic.auth.ProfileUiState
import com.example.offlinemusic.data.DownloadCancellation
import com.example.offlinemusic.data.PendingDownloadedSong
import com.example.offlinemusic.data.Playlist
import com.example.offlinemusic.data.Song
import com.example.offlinemusic.player.PlaybackState
import com.example.offlinemusic.player.PlayerViewModel
import com.example.offlinemusic.player.RepeatMode
import com.example.offlinemusic.ui.theme.*
import com.example.offlinemusic.ui.DownloadMetadataDialog
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.Locale
import java.util.UUID

/* ═══════════════════════════════════════════════════════════════
   Activity
   ═══════════════════════════════════════════════════════════════ */

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeManager = remember { ThemeManager(this) }
            var themeKey by remember { mutableStateOf(themeManager.selectedKey()) }
            val palette = remember(themeKey) { BuiltInThemes[themeKey] ?: themeManager.resolve() }
            
            var importUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
            val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
                if (uris.isNotEmpty()) {
                    importUris = uris
                }
            }

            MusicTheme(palette) {
                if (importUris.isNotEmpty()) {
                    com.example.offlinemusic.ui.ImportMetadataDialog(
                        uris = importUris,
                        repository = (application as MusicApplication).repository,
                        onDismiss = { importUris = emptyList() }
                    )
                }

                MusicApp(
                    viewModel = viewModel,
                    authManager = (application as MusicApplication).authManager,
                    onImport = { importAudio.launch("audio/*") },
                    themeKey = themeKey,
                    onThemeChange = { key ->
                        themeManager.select(key)
                        themeKey = key
                    }
                )
            }
        }
    }
}

    /* ═══════════════════════════════════════════════════════════════
   Theme wrapper
   ═══════════════════════════════════════════════════════════════ */

@Composable
fun MusicTheme(palette: MusedPalette = LocalMusedColors.current, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMusedColors provides palette) {
        val c = palette
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = c.accent,
                secondary = c.accentDark,
                tertiary = c.success,
                surface = c.surfaceElevated,
                background = c.background,
                onPrimary = c.background,
                onSecondary = c.background,
                onBackground = c.textPrimary,
                onSurface = c.textPrimary
            ),
            typography = Typography(
                displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp),
                headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                bodyLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp),
                bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
                bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp),
                labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.5.sp),
                labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp)
            ),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(22.dp),
                extraLarge = RoundedCornerShape(28.dp)
            )
        ) {
            content()
        }
    }
}

/* ═══════════════════════════════════════════════════════════════
   Navigation
   ═══════════════════════════════════════════════════════════════ */

enum class Screen { AUTH, HOME, LIBRARY, EQUALIZER, PROFILE }

/* ═══════════════════════════════════════════════════════════════
   Root composable
   ═══════════════════════════════════════════════════════════════ */

@Composable
fun MusicApp(
    viewModel: PlayerViewModel,
    authManager: com.example.offlinemusic.auth.SupabaseAuthManager,
    onImport: () -> Unit,
    themeKey: String,
    onThemeChange: (String) -> Unit
) {
    val c = MusedColors.current
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val selectedPlaylistSongs by viewModel.selectedPlaylistSongs.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val authState by authManager.authState.collectAsStateWithLifecycle()
    val profileState by authManager.profileState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val repository = remember(context) { (context.applicationContext as MusicApplication).repository }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleScope = (context as? ComponentActivity)?.lifecycleScope
    val startupScreen = if (authState.signedIn) Screen.HOME else Screen.AUTH

    // restoreSignedInState is now handled in SplashActivity

    val googleSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scope = (context as? ComponentActivity)?.lifecycleScope ?: return@rememberLauncherForActivityResult
        scope.launch { authManager.handleSignInResult(result) }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(playback.isPlaying) {
        if (
            playback.isPlaying &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationPermissionRequested &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionRequested = true
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var currentScreen by rememberSaveable { mutableStateOf(startupScreen) }
    var showDownload by rememberSaveable { mutableStateOf(false) }
    var isDownloading by rememberSaveable { mutableStateOf(false) }
    var downloadError by rememberSaveable { mutableStateOf<String?>(null) }
    var downloadSuccess by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDownload by remember { mutableStateOf<PendingDownloadedSong?>(null) }
    var isSavingDownload by remember { mutableStateOf(false) }
    var downloadCancellation by remember { mutableStateOf<DownloadCancellation?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(authState.userId) {
        songToAddToPlaylist = null
    }

    LaunchedEffect(authState.signedIn) {
        if (authState.signedIn) {
            if (currentScreen == Screen.AUTH) currentScreen = Screen.HOME
        } else if (currentScreen == Screen.PROFILE) {
            currentScreen = Screen.AUTH
        }
    }

    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(songs, search) {
        songs.filter { "${it.title} ${it.artist} ${it.album}".contains(search, true) }
    }

    val handleDownload: (String) -> Unit = { url ->
        val cancellation = DownloadCancellation()
        downloadCancellation = cancellation
        isDownloading = true
        downloadError = null
        downloadSuccess = null
        val job = coroutineScope.launch {
            try {
                pendingDownload = repository.download(url, cancellation)
                showDownload = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                downloadError = e.message ?: "Unknown error occurred during download"
                android.util.Log.e("MusicApp", "Download failed for URL: $url", e)
            } finally {
                if (downloadCancellation === cancellation) {
                    downloadCancellation = null
                    downloadJob = null
                    isDownloading = false
                }
            }
        }
        downloadJob = job
        job.invokeOnCompletion { cause ->
            if (cause is CancellationException) cancellation.cancel()
        }
    }

    /* ─── Dialogs ─── */
    if (showDownload) {
        DownloadDialog(onDismiss = { showDownload = false }, isLoading = isDownloading) { url -> handleDownload(url) }
    }
    if (showSearch) {
        SearchDialog(
            songs = songs,
            onPlay = { viewModel.play(it) },
            onDismiss = { showSearch = false }
        )
    }
    songToAddToPlaylist?.let { song ->
        AlertDialog(
            onDismissRequest = { songToAddToPlaylist = null },
            title = { Text("Add to playlist", color = c.textPrimary) },
            text = {
                if (playlists.isEmpty()) {
                    Text("Create a playlist in Library first", color = c.textSecondary)
                } else {
                    Column {
                        playlists.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    viewModel.addSongToPlaylist(playlist.id, song)
                                    songToAddToPlaylist = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(playlist.name, color = c.textPrimary, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { songToAddToPlaylist = null }) { Text("Cancel") } },
            containerColor = c.surfaceElevated
        )
    }
    if (isDownloading) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Downloading…", color = c.textPrimary) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(color = c.accent, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Please wait while we download your audio", color = c.textSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    downloadCancellation?.cancel()
                    downloadJob?.cancel()
                    downloadCancellation = null
                    downloadJob = null
                    isDownloading = false
                    showDownload = false
                }) { Text("Cancel", color = c.textSecondary) }
            },
            containerColor = c.surfaceElevated,
            shape = RoundedCornerShape(24.dp)
        )
    }
    downloadError?.takeIf { pendingDownload == null }?.let { error ->
        AlertDialog(
            onDismissRequest = { downloadError = null },
            title = { Text("Download Failed", color = c.textPrimary) },
            text = { Text(error, color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { downloadError = null }) { Text("OK", color = c.accent) } },
            containerColor = c.surfaceElevated,
            shape = RoundedCornerShape(24.dp)
        )
    }
    pendingDownload?.let { pending ->
        DownloadMetadataDialog(
            metadata = pending.metadata,
            isSaving = isSavingDownload,
            errorMessage = downloadError,
            onCancel = {
                pendingDownload = null
                downloadError = null
                coroutineScope.launch { repository.discardDownloaded(pending) }
            },
            onSave = { metadata ->
                coroutineScope.launch {
                    isSavingDownload = true
                    downloadError = null
                    try {
                        val song = repository.saveDownloaded(pending, metadata)
                        pendingDownload = null
                        downloadSuccess = "Successfully downloaded: ${song.title}"
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        downloadError = e.message ?: "Unable to save downloaded song"
                        android.util.Log.e("MusicApp", "Failed to save downloaded song", e)
                    } finally {
                        isSavingDownload = false
                    }
                }
            }
        )
    }
    downloadSuccess?.let { success ->
        AlertDialog(
            onDismissRequest = { downloadSuccess = null },
            title = { Text("Download Successful", color = c.textPrimary) },
            text = { Text(success, color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { downloadSuccess = null }) { Text("OK", color = c.accent) } },
            containerColor = c.surfaceElevated,
            shape = RoundedCornerShape(24.dp)
        )
    }
    if (showFullPlayer && playback.current != null) {
        FullScreenPlayer(playback, viewModel, onDismiss = { showFullPlayer = false })
    }

    /* ─── Scaffold ─── */
    val bottomBarVisible = currentScreen != Screen.AUTH

    Scaffold(
        containerColor = c.background,
        bottomBar = {
            if (bottomBarVisible) {
                Column {
                    if (playback.current != null) {
                        MiniPlayer(playback, viewModel, { showFullPlayer = true }, Modifier.fillMaxWidth())
                    }
                    NavigationBar(
                        containerColor = c.surface,
                        contentColor = c.textSecondary,
                        tonalElevation = 0.dp,
                        modifier = Modifier.shadow(12.dp)
                    ) {
                        data class NavItem(val icon: @Composable () -> Unit, val label: String, val screen: Screen)
                        val items = listOf(
                            NavItem({ Icon(if (currentScreen == Screen.HOME) Icons.Filled.Home else Icons.Outlined.Home, null) }, "Home", Screen.HOME),
                            NavItem({ Icon(if (currentScreen == Screen.LIBRARY) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic, null) }, "Library", Screen.LIBRARY),
                            NavItem({ Icon(if (currentScreen == Screen.EQUALIZER) Icons.Filled.Tune else Icons.Outlined.Tune, null) }, "EQ", Screen.EQUALIZER),
                            NavItem({ Icon(if (currentScreen == Screen.PROFILE) Icons.Filled.Person else Icons.Outlined.Person, null) }, "Profile", Screen.PROFILE)
                        )
                        items.forEach { item ->
                            NavigationBarItem(
                                icon = item.icon,
                                label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                                selected = currentScreen == item.screen,
                                onClick = { currentScreen = item.screen },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = c.accent,
                                    selectedTextColor = c.accent,
                                    unselectedIconColor = c.textMuted,
                                    unselectedTextColor = c.textMuted,
                                    indicatorColor = c.accent.copy(alpha = 0.12f)
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(c.background)
        ) {
            when (currentScreen) {
                Screen.AUTH -> AuthScreen(
                    authState = authState,
                    onGoogleSignIn = {
                        authManager.beginSignIn()
                        googleSignInLauncher.launch(authManager.getSignInIntent())
                    }
                )
                Screen.HOME -> HomeScreen(
                    songs, filtered, viewModel, favoriteIds, onImport,
                    { showDownload = true }, { showSearch = true }, { songToAddToPlaylist = it }, isDownloading
                )
                Screen.LIBRARY -> LibraryScreen(
                    songs = songs,
                    filtered = filtered,
                    favorites = favoriteSongs,
                    favoriteIds = favoriteIds,
                    playlists = playlists,
                    selectedPlaylistId = selectedPlaylistId,
                    selectedPlaylistSongs = selectedPlaylistSongs,
                    viewModel = viewModel,
                    onSearchClick = { showSearch = true },
                    onAddToPlaylist = { songToAddToPlaylist = it }
                )
                Screen.EQUALIZER -> EqualizerScreen(viewModel, { showSearch = true })
                Screen.PROFILE -> ProfileScreen(
                    authState = authState,
                    profileState = profileState,
                    themeKey = themeKey,
                    onThemeChange = onThemeChange,
                    onLoadProfile = { lifecycleScope?.launch { authManager.loadProfile() } },
                    onSaveUsername = { username -> lifecycleScope?.launch { authManager.saveUsername(username) } },
                    onUpdateAvatar = { uri ->
                        lifecycleScope?.launch {
                            try {
                                authManager.uploadProfilePicture(uri)
                            } finally {
                                if (uri.scheme == "file") uri.path?.let(::File)?.delete()
                            }
                        }
                    },
                    onRemoveAvatar = { lifecycleScope?.launch { authManager.removeProfilePicture() } },
                    onGoogleSignIn = {
                        authManager.beginSignIn()
                        googleSignInLauncher.launch(authManager.getSignInIntent())
                    },
                    onSignOut = {
                        val scope = (context as? ComponentActivity)?.lifecycleScope
                        scope?.launch { authManager.signOut() }
                    },
                    onDeleteAccount = {
                        lifecycleScope?.launch { authManager.deleteAccount() }
                    }
                )
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════
   Top App Bar
   ═══════════════════════════════════════════════════════════════ */

@Composable
fun TopBar(title: String, onSearchClick: () -> Unit) {
    val c = MusedColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = c.background,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary
            )
            Spacer(Modifier.weight(1f))
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = c.card
            ) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search", tint = c.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════
   Auth Screen
   ═══════════════════════════════════════════════════════════════ */

@Composable
fun AuthScreen(authState: AuthUiState, onGoogleSignIn: () -> Unit) {
    val c = MusedColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(c.background),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = c.surfaceElevated,
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .padding(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = c.accent.copy(alpha = 0.12f),
                    shape = CircleShape,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(40.dp), tint = c.accent)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Welcome to MUSED", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, textAlign = TextAlign.Center)
                Text(
                    "Sign in with Google to access your library and profile.",
                    color = c.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onGoogleSignIn,
                    enabled = !authState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.background),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (authState.isLoading) {
                        CircularProgressIndicator(color = c.background, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                }

                if (!authState.error.isNullOrBlank()) {
                    Surface(
                        color = c.error.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(authState.error, color = c.textPrimary, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onGoogleSignIn, contentPadding = PaddingValues(0.dp)) {
                                Text("Try again", color = c.accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════
   Home Screen
   ═══════════════════════════════════════════════════════════════ */

@Composable
fun HomeScreen(
    songs: List<Song>,
    filtered: List<Song>,
    viewModel: PlayerViewModel,
    favoriteIds: Set<String>,
    onImport: () -> Unit,
    onDownload: () -> Unit,
    onSearchClick: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    isLoading: Boolean
) {
    val c = MusedColors.current
    Column(Modifier.fillMaxSize()) {
        TopBar("MUSED", onSearchClick)

        /* Action buttons */
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onImport,
                enabled = !isLoading,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.card, contentColor = c.textPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.FileOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import", fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = onDownload,
                enabled = !isLoading,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent.copy(alpha = 0.15f), contentColor = c.accent),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Download", fontWeight = FontWeight.Medium)
            }
        }

        if (songs.isEmpty()) {
            EmptyLibrary(onImport, onDownload, !isLoading)
        } else {
            Text(
                "All Songs",
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered, key = { it.id }) { song ->
                    SongRow(
                        song,
                        selected = viewModel.playback.collectAsStateWithLifecycle().value.current?.id == song.id,
                        onPlay = { viewModel.play(song) },
                        onDelete = { viewModel.delete(song) },
                        onEdit = { viewModel.update(it) },
                        isFavorite = song.id in favoriteIds,
                        onFavoriteToggle = { viewModel.toggleFavorite(song) },
                        onAddToPlaylist = onAddToPlaylist
                    )
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════
   Library Screen
   ═══════════════════════════════════════════════════════════════ */

@Composable
fun LibraryScreen(
    songs: List<Song>,
    filtered: List<Song>,
    favorites: List<Song>,
    favoriteIds: Set<String>,
    playlists: List<Playlist>,
    selectedPlaylistId: String?,
    selectedPlaylistSongs: List<Song>,
    viewModel: PlayerViewModel,
    onSearchClick: () -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    val c = MusedColors.current
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        TopBar("Library", onSearchClick)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    "${songs.size} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                )
            }
            item { SectionHeading("Favorites", favorites.size) }
            if (favorites.isEmpty()) {
                item { Text("No favorites yet", color = c.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) }
            } else {
                items(favorites, key = { "favorite-${it.id}" }) { song ->
                    SongRow(
                        song = song,
                        selected = viewModel.playback.collectAsStateWithLifecycle().value.current?.id == song.id,
                        onPlay = { viewModel.play(song) },
                        onDelete = { viewModel.delete(song) },
                        onEdit = { viewModel.update(it) },
                        isFavorite = true,
                        onFavoriteToggle = { viewModel.toggleFavorite(song) },
                        onAddToPlaylist = onAddToPlaylist
                    )
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionHeading("Playlists", playlists.size)
                    IconButton(onClick = { playlistName = ""; showCreatePlaylist = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Create playlist", tint = c.accent)
                    }
                }
            }
            if (playlists.isEmpty()) {
                item { Text("No playlists yet", color = c.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) }
            } else {
                items(playlists, key = { "playlist-${it.id}" }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        selected = selectedPlaylistId == playlist.id,
                        onSelect = { viewModel.selectPlaylist(if (selectedPlaylistId == playlist.id) null else playlist.id) },
                        onPlay = { viewModel.playPlaylist(playlist) },
                        onRename = { viewModel.renamePlaylist(playlist.id, it) },
                        onDelete = { viewModel.deletePlaylist(playlist.id) }
                    )
                }
            }
            playlists.firstOrNull { it.id == selectedPlaylistId }?.let { playlist ->
                item { SectionHeading(playlist.name, selectedPlaylistSongs.size) }
                if (selectedPlaylistSongs.isEmpty()) {
                    item { Text("This playlist is empty", color = c.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) }
                } else {
                    items(selectedPlaylistSongs, key = { "${playlist.id}-${it.id}" }) { song ->
                        SongRow(
                            song = song,
                            selected = viewModel.playback.collectAsStateWithLifecycle().value.current?.id == song.id,
                            onPlay = { viewModel.playPlaylist(playlist, song.id) },
                            onDelete = { viewModel.delete(song) },
                            onEdit = { viewModel.update(it) },
                            isFavorite = song.id in favoriteIds,
                            onFavoriteToggle = { viewModel.toggleFavorite(song) },
                            onAddToPlaylist = onAddToPlaylist,
                            onRemoveFromPlaylist = { viewModel.removeSongFromPlaylist(playlist.id, song.id) },
                            allowLibraryDeletion = false
                        )
                    }
                }
            }
            item { SectionHeading("All Songs", filtered.size) }
            if (songs.isEmpty()) {
                item { Text("Import or download to get started", color = c.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) }
            } else {
                items(filtered, key = { "song-${it.id}" }) { song ->
                    SongRow(
                        song = song,
                        selected = viewModel.playback.collectAsStateWithLifecycle().value.current?.id == song.id,
                        onPlay = { viewModel.play(song) },
                        onDelete = { viewModel.delete(song) },
                        onEdit = { viewModel.update(it) },
                        isFavorite = song.id in favoriteIds,
                        onFavoriteToggle = { viewModel.toggleFavorite(song) },
                        onAddToPlaylist = onAddToPlaylist
                    )
                }
            }
        }
    }

    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text("Create playlist", color = c.textPrimary) },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    singleLine = true,
                    label = { Text("Playlist name") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = playlistName.isNotBlank(),
                    onClick = { viewModel.createPlaylist(playlistName); showCreatePlaylist = false }
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancel") } },
            containerColor = c.surfaceElevated
        )
    }
}

@Composable
private fun SectionHeading(title: String, count: Int) {
    val c = MusedColors.current
    Text(
        "$title  $count",
        style = MaterialTheme.typography.titleMedium,
        color = c.textPrimary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    selected: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    val c = MusedColors.current
    var expanded by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var showRename by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var showDelete by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var name by rememberSaveable(playlist.id) { mutableStateOf(playlist.name) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        color = if (selected) c.accent.copy(alpha = 0.10f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.QueueMusic, null, tint = c.accent, modifier = Modifier.padding(horizontal = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.name, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Playlist", color = c.textMuted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play playlist", tint = c.accent)
            }
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Playlist options", tint = c.textMuted)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { expanded = false; name = playlist.name; showRename = true })
                    DropdownMenuItem(text = { Text("Delete", color = c.error) }, onClick = { expanded = false; showDelete = true })
                }
            }
        }
    }
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename playlist", color = c.textPrimary) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Playlist name") }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { onRename(name); showRename = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } },
            containerColor = c.surfaceElevated
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete playlist?", color = c.textPrimary) },
            text = { Text(playlist.name, color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { onDelete(); showDelete = false }) { Text("Delete", color = c.error) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
            containerColor = c.surfaceElevated
        )
    }
}

/* ═══════════════════════════════════════════════════════════════
   Equalizer Screen
   ═══════════════════════════════════════════════════════════════ */

@Composable
fun EqualizerScreen(viewModel: PlayerViewModel, onSearchClick: () -> Unit) {
    val c = MusedColors.current
    val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopBar("Equalizer", onSearchClick)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            /* Header card */
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = c.card,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Audio Equalizer", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (equalizerState.enabled) "Active • ${equalizerState.presetName}" else "Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )
                    }
                    Switch(
                        checked = equalizerState.enabled,
                        onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = c.accent,
                            checkedTrackColor = c.accent.copy(alpha = 0.35f),
                            uncheckedThumbColor = c.textMuted,
                            uncheckedTrackColor = c.card
                        )
                    )
                }
            }

            /* Band sliders */
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = c.card,
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val bands = equalizerState.bandLevels
                    val labels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")

                    bands.forEachIndexed { index, value ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(labels[index], color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                                Text("${value}%", color = c.accent, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                            Slider(
                                value = value.toFloat(),
                                onValueChange = { viewModel.setBandLevel(index, it.toInt()) },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = c.accent,
                                    activeTrackColor = c.accent,
                                    inactiveTrackColor = c.border
                                )
                            )
                        }
                    }
                }
            }

            /* Presets */
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = c.card,
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Presets", color = c.textPrimary, style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("Flat", "Bass", "Treble", "Vocal", "Club").forEach { preset ->
                            val selected = equalizerState.presetName == preset
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.applyPreset(preset) },
                                label = { Text(preset, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = c.accent,
                                    selectedLabelColor = c.background,
                                    containerColor = c.cardStrong,
                                    labelColor = c.textPrimary
                                ),
                                border = null
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════
   Profile Screen
   ═══════════════════════════════════════════════════════════════ */

@Composable
fun ProfileScreen(
    authState: AuthUiState,
    profileState: ProfileUiState,
    themeKey: String,
    onThemeChange: (String) -> Unit,
    onLoadProfile: () -> Unit,
    onSaveUsername: (String) -> Unit,
    onUpdateAvatar: (android.net.Uri) -> Unit,
    onRemoveAvatar: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val c = MusedColors.current

    LaunchedEffect(authState.signedIn) {
        if (authState.signedIn) onLoadProfile()
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val avatarUrl = profileState.avatarUrl ?: authState.avatarUrl
    val hasCustomAvatar = !profileState.avatarUrl.isNullOrBlank()
    var cameraCaptureFile by remember { mutableStateOf<File?>(null) }
    var cameraCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var cropSourceFile by remember { mutableStateOf<File?>(null) }
    var cropOutputFile by remember { mutableStateOf<File?>(null) }
    var isPreparingCrop by remember { mutableStateOf(false) }
    var showAvatarPreview by rememberSaveable { mutableStateOf(false) }
    var showAvatarOptions by rememberSaveable { mutableStateOf(false) }
    var showImageSources by rememberSaveable { mutableStateOf(false) }
    var showRemoveAvatarConfirm by rememberSaveable { mutableStateOf(false) }

    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val outputUri = result.data?.let(UCrop::getOutput)
            if (outputUri != null) {
                onUpdateAvatar(outputUri)
            } else {
                Toast.makeText(context, "Could not load the cropped image", Toast.LENGTH_SHORT).show()
                cropOutputFile?.delete()
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val message = result.data?.let(UCrop::getError)?.localizedMessage ?: "Could not crop this image"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            cropOutputFile?.delete()
        } else {
            cropOutputFile?.delete()
        }
        cropSourceFile?.delete()
        cropSourceFile = null
        cropOutputFile = null
    }
    val launchCrop: (Uri, File?) -> Unit = { inputUri, sourceFile ->
        val outputFile = File(context.cacheDir, "avatar-${UUID.randomUUID()}.jpg")
        try {
            val cropIntent = UCrop.of(inputUri, Uri.fromFile(outputFile))
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(1024, 1024)
                .getIntent(context)
            cropSourceFile = sourceFile
            cropOutputFile = outputFile
            cropLauncher.launch(cropIntent)
        } catch (exception: Exception) {
            sourceFile?.delete()
            outputFile.delete()
            cropSourceFile = null
            cropOutputFile = null
            Toast.makeText(context, exception.localizedMessage ?: "Could not open image cropper", Toast.LENGTH_SHORT).show()
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isPreparingCrop = true
            coroutineScope.launch {
                var sourceFile: File? = null
                try {
                    val copiedFile = withContext(Dispatchers.IO) {
                        val mimeType = context.contentResolver.getType(uri)
                        require(mimeType == null || mimeType.startsWith("image/", ignoreCase = true)) {
                            "Selected file is not a supported image"
                        }
                        val file = File.createTempFile("avatar-gallery-", ".img", context.cacheDir)
                        try {
                            val input = context.contentResolver.openInputStream(uri)
                                ?: error("Could not read selected image")
                            input.use { stream -> file.outputStream().use(stream::copyTo) }
                            check(file.length() > 0L) { "Selected image is empty" }
                            file
                        } catch (exception: Exception) {
                            file.delete()
                            throw exception
                        }
                    }
                    sourceFile = copiedFile
                    launchCrop(Uri.fromFile(copiedFile), copiedFile)
                } catch (exception: Exception) {
                    sourceFile?.delete()
                    Toast.makeText(
                        context,
                        exception.localizedMessage ?: "Could not load selected image",
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    isPreparingCrop = false
                }
            }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val sourceFile = cameraCaptureFile
        val sourceUri = cameraCaptureUri
        cameraCaptureFile = null
        cameraCaptureUri = null
        if (captured && sourceFile?.exists() == true && sourceUri != null) {
            launchCrop(sourceUri, sourceFile)
        } else {
            sourceFile?.delete()
        }
    }

    fun launchCamera() {
        runCatching {
            val directory = File(context.cacheDir, "avatar-captures").apply { mkdirs() }
            val captureFile = File.createTempFile("capture-", ".jpg", directory)
            val captureUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                captureFile
            )
            cameraCaptureFile = captureFile
            cameraCaptureUri = captureUri
            cameraLauncher.launch(captureUri)
        }.onFailure { throwable ->
            cameraCaptureFile?.delete()
            cameraCaptureFile = null
            cameraCaptureUri = null
            Toast.makeText(context, throwable.localizedMessage ?: "Could not open camera", Toast.LENGTH_SHORT).show()
        }
    }

    fun editCurrentAvatar() {
        val sourceUrl = avatarUrl ?: return
        isPreparingCrop = true
        coroutineScope.launch {
            val sourceFile = runCatching {
                withContext(Dispatchers.IO) {
                    val file = File.createTempFile("avatar-source-", ".img", context.cacheDir)
                    try {
                        URL(sourceUrl).openConnection().apply {
                            connectTimeout = 15_000
                            readTimeout = 30_000
                        }.getInputStream().use { input -> file.outputStream().use(input::copyTo) }
                        file
                    } catch (throwable: Throwable) {
                        file.delete()
                        throw throwable
                    }
                }
            }.getOrElse { throwable ->
                Toast.makeText(context, throwable.localizedMessage ?: "Could not load profile picture", Toast.LENGTH_SHORT).show()
                null
            }
            isPreparingCrop = false
            sourceFile?.let { launchCrop(Uri.fromFile(it), it) }
        }
    }

    var isEditing by rememberSaveable { mutableStateOf(false) }
    var usernameDraft by rememberSaveable { mutableStateOf("") }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(profileState.username) {
        if (!isEditing) usernameDraft = profileState.username
    }

    /* Delete account confirmation dialog */
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account?", color = c.textPrimary) },
            text = {
                Text(
                    "This will permanently delete your account and all associated data. This action cannot be undone.",
                    color = c.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteAccount()
                }) {
                    Text("Delete", color = c.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = c.textSecondary)
                }
            },
            containerColor = c.surfaceElevated,
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showAvatarPreview && !avatarUrl.isNullOrBlank()) {
        Dialog(onDismissRequest = { showAvatarPreview = false }) {
            Surface(color = c.surfaceElevated, shape = RoundedCornerShape(16.dp)) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Enlarged profile picture",
                    modifier = Modifier
                        .size(320.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showAvatarPreview = false },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }

    if (showAvatarOptions) {
        AlertDialog(
            onDismissRequest = { showAvatarOptions = false },
            title = { Text("Profile picture", color = c.textPrimary) },
            text = {
                Column {
                    TextButton(onClick = { showAvatarOptions = false; showImageSources = true }) {
                        Text("Replace", color = c.textPrimary)
                    }
                    if (!avatarUrl.isNullOrBlank()) {
                        TextButton(
                            enabled = !profileState.isSaving && !isPreparingCrop,
                            onClick = { showAvatarOptions = false; editCurrentAvatar() }
                        ) { Text("Edit/Crop", color = c.textPrimary) }
                    }
                    if (hasCustomAvatar) {
                        TextButton(onClick = { showAvatarOptions = false; showRemoveAvatarConfirm = true }) {
                            Text("Remove", color = c.error)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAvatarOptions = false }) { Text("Cancel") } },
            containerColor = c.surfaceElevated
        )
    }

    if (showImageSources) {
        AlertDialog(
            onDismissRequest = { showImageSources = false },
            title = { Text("Change profile picture", color = c.textPrimary) },
            text = {
                Column {
                    TextButton(
                        enabled = !profileState.isSaving && !isPreparingCrop,
                        onClick = { showImageSources = false; launchCamera() }
                    ) { Text("Camera", color = c.textPrimary) }
                    TextButton(
                        enabled = !profileState.isSaving && !isPreparingCrop,
                        onClick = {
                            showImageSources = false
                            imagePicker.launch("image/*")
                        }
                    ) { Text("Gallery", color = c.textPrimary) }
                }
            },
            confirmButton = { TextButton(onClick = { showImageSources = false }) { Text("Cancel") } },
            containerColor = c.surfaceElevated
        )
    }

    if (showRemoveAvatarConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveAvatarConfirm = false },
            title = { Text("Remove profile picture?", color = c.textPrimary) },
            text = { Text("Your Google profile photo may be shown instead.", color = c.textSecondary) },
            confirmButton = {
                TextButton(
                    enabled = !profileState.isSaving,
                    onClick = { showRemoveAvatarConfirm = false; onRemoveAvatar() }
                ) { Text("Remove", color = c.error) }
            },
            dismissButton = { TextButton(onClick = { showRemoveAvatarConfirm = false }) { Text("Cancel") } },
            containerColor = c.surfaceElevated
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        /* Header */
        Surface(modifier = Modifier.fillMaxWidth(), color = c.background) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = c.textPrimary)
            }
        }

        if (authState.signedIn) {
            /* ─── Profile card ─── */
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                color = c.card,
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    /* Avatar */
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier.size(96.dp)
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "Profile photo",
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                                    .clickable { showAvatarPreview = true },
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = c.accent.copy(alpha = 0.12f),
                                modifier = Modifier.size(88.dp).clickable { showAvatarOptions = true }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, null, Modifier.size(44.dp), tint = c.accent)
                                }
                            }
                        }

                        IconButton(
                            onClick = { showAvatarOptions = true },
                            enabled = !profileState.isSaving && !isPreparingCrop,
                            modifier = Modifier
                                .size(32.dp)
                                .offset(x = 2.dp, y = 2.dp)
                                .background(c.accent, CircleShape)
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Profile picture options", Modifier.size(16.dp), tint = c.background)
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    /* Display name */
                    Text(
                        profileState.username.ifBlank { authState.displayName ?: "MUSED User" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary
                    )

                    /* Email */
                    if (!authState.email.isNullOrBlank()) {
                        Text(authState.email, color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    }

                    if (profileState.isLoading) {
                        CircularProgressIndicator(color = c.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }

                    if (!profileState.error.isNullOrBlank()) {
                        Text(profileState.error, color = c.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            /* ─── Edit profile section ─── */
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                color = c.card,
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Account", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)

                    if (isEditing) {
                        OutlinedTextField(
                            value = usernameDraft,
                            onValueChange = { usernameDraft = it },
                            singleLine = true,
                            label = { Text("Username") },
                            enabled = !profileState.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = c.accent,
                                unfocusedBorderColor = c.border,
                                focusedLabelColor = c.accent,
                                unfocusedLabelColor = c.textSecondary,
                                focusedTextColor = c.textPrimary,
                                unfocusedTextColor = c.textPrimary,
                                cursorColor = c.accent
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { usernameDraft = profileState.username; isEditing = false },
                                enabled = !profileState.isSaving,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
                                border = BorderStroke(1.dp, c.border)
                            ) { Text("Cancel") }
                            Button(
                                onClick = { onSaveUsername(usernameDraft); isEditing = false },
                                enabled = usernameDraft.trim().isNotEmpty() && !profileState.isSaving,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.background)
                            ) {
                                if (profileState.isSaving) {
                                    CircularProgressIndicator(color = c.background, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Save", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = { usernameDraft = profileState.username; isEditing = true },
                            enabled = !profileState.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent.copy(alpha = 0.15f), contentColor = c.accent)
                        ) {
                            Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Edit Profile", fontWeight = FontWeight.Medium)
                        }
                    }

                    Button(
                        onClick = onSignOut,
                        enabled = !profileState.isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = c.cardStrong, contentColor = c.textPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Outlined.Logout, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out")
                    }

                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.error),
                        border = BorderStroke(1.dp, c.error.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Outlined.DeleteForever, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Account")
                    }
                }
            }

            /* ─── Theme picker ─── */
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                color = c.card,
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text("Choose your accent color", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        BuiltInThemes.values.forEach { palette ->
                            val isSelected = palette.key == themeKey
                            Surface(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable { onThemeChange(palette.key) },
                                shape = CircleShape,
                                color = palette.accent,
                                border = if (isSelected) BorderStroke(3.dp, c.textPrimary) else null
                            ) {
                                if (isSelected) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        } else {
            /* ─── Not signed in ─── */
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = c.accent.copy(alpha = 0.10f),
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, Modifier.size(40.dp), tint = c.accent)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Welcome to MUSED", color = c.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Sign in to access your profile.", color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = onGoogleSignIn,
                        enabled = !authState.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.background),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (authState.isLoading) {
                            CircularProgressIndicator(color = c.background, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                    }

                    if (!authState.error.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(authState.error, color = c.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════
   Shared UI components
   ═══════════════════════════════════════════════════════════════ */

@Composable
fun EmptyLibrary(onImport: () -> Unit, onDownload: () -> Unit, enabled: Boolean) {
    val c = MusedColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = CircleShape,
                color = c.accent.copy(alpha = 0.10f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.LibraryMusic, null, Modifier.size(42.dp), tint = c.accent)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Your library is empty", style = MaterialTheme.typography.titleLarge, color = c.textPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Import a file or download from a URL", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onImport,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.card, contentColor = c.textPrimary),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Import") }
                Button(
                    onClick = onDownload,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent.copy(alpha = 0.15f), contentColor = c.accent),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Download") }
            }
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    selected: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (Song) -> Unit,
    isFavorite: Boolean = false,
    onFavoriteToggle: (Song) -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {},
    onRemoveFromPlaylist: (() -> Unit)? = null,
    allowLibraryDeletion: Boolean = true
) {
    val c = MusedColors.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var showEdit by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .clickable(onClick = onPlay)
            .fillMaxWidth(),
        color = if (selected) c.accent.copy(alpha = 0.10f) else Color.Transparent,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(song, Modifier.size(52.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) c.accent else c.textPrimary
                )
                Text(
                    "${song.artist} • ${song.album}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
            }
            Text(formatTime(song.durationMs), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            IconButton(onClick = { onFavoriteToggle(song) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) c.accent else c.textMuted
                )
            }
            Box {
                IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = c.textMuted)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(c.surfaceElevated)
                ) {
                    DropdownMenuItem(text = { Text("Play") }, onClick = { expanded = false; onPlay() })
                    DropdownMenuItem(text = { Text("Add to playlist") }, onClick = {
                        expanded = false
                        onAddToPlaylist(song)
                    })
                    if (onRemoveFromPlaylist != null) {
                        DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = {
                            expanded = false
                            onRemoveFromPlaylist()
                        })
                    }
                    DropdownMenuItem(text = { Text("Song info") }, onClick = { expanded = false; showInfo = true })
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { expanded = false; showEdit = true })
                    if (allowLibraryDeletion) {
                        DropdownMenuItem(text = { Text("Delete", color = c.error) }, onClick = { expanded = false; onDelete() })
                    }
                }
            }
        }
    }

    if (showInfo) {
        SongInfoDialog(song, onDismiss = { showInfo = false }, onEdit = { showInfo = false; showEdit = true })
    }

    if (showEdit) {
        com.example.offlinemusic.ui.EditSongDialog(song, onDismiss = { showEdit = false }, onSave = { onEdit(it) })
    }
}

@Composable
fun SongInfoDialog(song: Song, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val c = MusedColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(song.title, color = c.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Artist: ${song.artist}", color = c.textSecondary)
                Text("Album: ${song.album}", color = c.textSecondary)
                Text("Genre: ${song.genre}", color = c.textSecondary)
                Text("Duration: ${formatTime(song.durationMs)}", color = c.textSecondary)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = c.textSecondary) }
        },
        dismissButton = {
            TextButton(onClick = onEdit) { Text("Edit", color = c.accent) }
        },
        containerColor = c.surfaceElevated,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun SearchDialog(
    songs: List<Song>,
    onPlay: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    val c = MusedColors.current
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(songs, query) {
        if (query.isBlank()) emptyList()
        else songs.filter { "${it.title} ${it.artist} ${it.album}".contains(query, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = c.background) {
            Column(Modifier.fillMaxSize()) {
                /* ── Search bar row ── */
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, null, tint = c.textSecondary)
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Search title, artist, album…", color = c.textMuted) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.accent,
                            unfocusedBorderColor = c.border,
                            cursorColor = c.accent,
                            focusedTextColor = c.textPrimary,
                            unfocusedTextColor = c.textPrimary
                        )
                    )
                }

                HorizontalDivider(color = c.border)

                /* ── Results ── */
                when {
                    query.isBlank() -> {
                        /* prompt state */
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Search, null, Modifier.size(52.dp), tint = c.textMuted)
                                Spacer(Modifier.height(12.dp))
                                Text("Search your library", color = c.textSecondary, style = MaterialTheme.typography.bodyLarge)
                                Text("by title, artist or album", color = c.textMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    results.isEmpty() -> {
                        /* empty state */
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.SearchOff, null, Modifier.size(52.dp), tint = c.textMuted)
                                Spacer(Modifier.height(12.dp))
                                Text("No results for \"$query\"", color = c.textSecondary, style = MaterialTheme.typography.bodyLarge)
                                Text("Try a different keyword", color = c.textMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    else -> {
                        Text(
                            "${results.size} result${if (results.size == 1) "" else "s"}",
                            color = c.textMuted,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
                        )
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(results, key = { it.id }) { song ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPlay(song); onDismiss() },
                                    color = Color.Transparent
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Artwork(song, Modifier.size(48.dp))
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                song.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = c.textPrimary
                                            )
                                            Text(
                                                "${song.artist} · ${song.album}",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = c.textSecondary
                                            )
                                        }
                                        Icon(Icons.Default.PlayArrow, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun DownloadDialog(onDismiss: () -> Unit, isLoading: Boolean = false, onDownload: (String) -> Unit) {
    val c = MusedColors.current
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = if (!isLoading) onDismiss else { {} },
        title = { Text("Download Audio", color = c.textPrimary) },
        text = {
            OutlinedTextField(
                url, { url = it },
                label = { Text("Audio or YouTube URL") },
                singleLine = true,
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                textStyle = LocalTextStyle.current.copy(color = c.textPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent,
                    unfocusedBorderColor = c.border,
                    focusedLabelColor = c.accent,
                    unfocusedLabelColor = c.textSecondary,
                    cursorColor = c.accent
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onDownload(url) }, enabled = url.startsWith("http") && !isLoading) {
                Text("Download", color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel", color = c.textSecondary)
            }
        },
        containerColor = c.surfaceElevated,
        shape = RoundedCornerShape(24.dp)
    )
}

/* ═══════════════════════════════════════════════════════════════
   Mini Player
   ═══════════════════════════════════════════════════════════════ */

@Composable
fun MiniPlayer(state: PlaybackState, viewModel: PlayerViewModel, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val c = MusedColors.current
    Surface(
        modifier = modifier.clickable(onClick = onTap),
        color = c.surface,
        tonalElevation = 0.dp
    ) {
        Column {
            /* Progress bar at top */
            if (state.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = c.accent,
                    trackColor = c.border
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.current?.let { song ->
                    Artwork(song, Modifier.size(44.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.textPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { viewModel.previous() }, Modifier.size(36.dp)) {
                        Icon(Icons.Default.SkipPrevious, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { viewModel.toggle() }, Modifier.size(40.dp)) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null,
                            tint = c.textPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.next() }, Modifier.size(36.dp)) {
                        Icon(Icons.Default.SkipNext, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════
   Full-screen Player
   ═══════════════════════════════════════════════════════════════ */

@Composable
fun FullScreenPlayer(state: PlaybackState, viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val c = MusedColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showLyricsEditor by remember { mutableStateOf(false) }
    var isSavingLyrics by remember { mutableStateOf(false) }

    // Track cumulative horizontal drag to trigger skip
    var dragAccumulator by remember { mutableStateOf(0f) }
    val swipeThreshold = 120f

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                dragAccumulator < -swipeThreshold -> viewModel.safeNext()
                                dragAccumulator > swipeThreshold -> viewModel.safePrevious()
                            }
                            dragAccumulator = 0f
                        },
                        onDragCancel = { dragAccumulator = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            dragAccumulator += dragAmount
                        }
                    )
                },
            color = c.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                /* ── Top bar ── */
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = c.textSecondary, modifier = Modifier.size(28.dp))
                    }
                    Text("Now Playing", color = c.textSecondary, style = MaterialTheme.typography.labelLarge)

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = c.surfaceElevated
                        ) {
                            DropdownMenuItem(
                                text = { Text("Song info", color = c.textPrimary) },
                                onClick = { showMenu = false; showInfo = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit", color = c.textPrimary) },
                                onClick = { showMenu = false; showEdit = true }
                            )
                            DropdownMenuItem(
                                text = { Text("View queue", color = c.textPrimary) },
                                onClick = { showMenu = false; showQueue = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Play next", color = c.textPrimary) },
                                onClick = {
                                    showMenu = false
                                    state.current?.let { viewModel.next() }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = c.error) },
                                onClick = {
                                    showMenu = false
                                    state.current?.let { song ->
                                        viewModel.delete(song)
                                        onDismiss()
                                    }
                                }
                            )
                        }
                    }
                }

                state.current?.let { song ->
                    Spacer(Modifier.height(16.dp))

                    /* ── Large artwork ── */
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(horizontal = 8.dp),
                        color = c.card,
                        shape = RoundedCornerShape(24.dp),
                        shadowElevation = 16.dp
                    ) {
                        Artwork(song, Modifier.fillMaxSize())
                    }

                    Spacer(Modifier.height(28.dp))

                    /* ── Title & artist ── */
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(song.artist, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                    }

                    Spacer(Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = c.surface,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Lyrics",
                                    color = c.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { showLyrics = true }) {
                                    Text("View", color = c.accent)
                                }
                                TextButton(onClick = { showLyricsEditor = true }) {
                                    Text(if (song.lyrics.isBlank()) "Add" else "Edit", color = c.accent)
                                }
                            }
                            val activeLineIndex = activeLyricsLineIndex(song.lyrics, state.positionMs)
                            val lyricPreview = activeLineIndex?.let { index ->
                                parseLyrics(song.lyrics).getOrNull(index)?.text
                            }?.takeIf(String::isNotBlank)
                            Text(
                                lyricPreview ?: if (song.lyrics.isBlank()) "Add lyrics for this song" else "Lyrics will appear here",
                                color = if (lyricPreview == null) c.textSecondary else c.accent,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    /* ── Seek bar ── */
                    Column(Modifier.fillMaxWidth()) {
                        Slider(
                            value = state.positionMs.toFloat(),
                            onValueChange = { viewModel.seekTo(it.toLong()) },
                            valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = c.accent,
                                activeTrackColor = c.accent,
                                inactiveTrackColor = c.border
                            )
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(state.positionMs), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                            Text(formatTime(state.durationMs), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    /* ── Controls ── */
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.toggleShuffle() }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.Shuffle, null,
                                tint = if (state.shuffleEnabled) c.accent else c.textMuted,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { viewModel.previous() }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Default.SkipPrevious, null, tint = c.textPrimary, modifier = Modifier.size(32.dp))
                        }

                        /* Play/Pause circle */
                        Surface(
                            modifier = Modifier.size(68.dp),
                            shape = CircleShape,
                            color = c.accent
                        ) {
                            IconButton(onClick = { viewModel.toggle() }, Modifier.fillMaxSize()) {
                                Icon(
                                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    null,
                                    tint = c.background,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        IconButton(onClick = { viewModel.next() }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Default.SkipNext, null, tint = c.textPrimary, modifier = Modifier.size(32.dp))
                        }
                        IconButton(onClick = { viewModel.cycleRepeatMode() }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                when (state.repeatMode) {
                                    RepeatMode.ONE -> Icons.Default.RepeatOne
                                    else -> Icons.Default.Repeat
                                },
                                null,
                                tint = if (state.repeatMode == RepeatMode.OFF) c.textMuted else c.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    /* ── Dialogs triggered from three-dot menu ── */
    val song = state.current
    if (showInfo && song != null) {
        SongInfoDialog(song, onDismiss = { showInfo = false }, onEdit = { showInfo = false; showEdit = true })
    }
    if (showEdit && song != null) {
        com.example.offlinemusic.ui.EditSongDialog(
            song = song,
            onDismiss = { showEdit = false },
            onSave = { viewModel.update(it) }
        )
    }
    if (showQueue) {
        QueueDialog(
            state = state,
            onDismiss = { showQueue = false },
            onPlay = { viewModel.play(it) }
        )
    }
    state.current?.let { currentSong ->
        if (showLyrics) {
            LyricsDialog(
                song = currentSong,
                positionMs = state.positionMs,
                onDismiss = { showLyrics = false },
                onEdit = {
                    showLyrics = false
                    showLyricsEditor = true
                }
            )
        }
        if (showLyricsEditor) {
            LyricsEditorDialog(
                song = currentSong,
                positionMs = state.positionMs,
                isSaving = isSavingLyrics,
                onDismiss = { showLyricsEditor = false },
                onSave = { lyrics ->
                    isSavingLyrics = true
                    coroutineScope.launch {
                        try {
                            viewModel.saveLyrics(currentSong, lyrics)
                            showLyricsEditor = false
                            showLyrics = true
                        } catch (exception: Exception) {
                            Toast.makeText(
                                context,
                                exception.localizedMessage ?: "Could not save lyrics",
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isSavingLyrics = false
                        }
                    }
                }
            )
        }
    }
}

private data class ParsedLyricsLine(val text: String, val timestampMs: Long?)

private fun parseLyrics(lyrics: String): List<ParsedLyricsLine> {
    val timestampPattern = Regex("""\[(\d+):([0-5]\d)(?:[.:](\d{1,3}))?]""")
    return lyrics.lineSequence().flatMap { line ->
        val matches = timestampPattern.findAll(line).toList()
        val text = timestampPattern.replace(line, "").trim()
        if (matches.isEmpty()) {
            sequenceOf(ParsedLyricsLine(line.trim(), null))
        } else {
            matches.asSequence().map { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fractionText = match.groupValues[3]
                val millis = fractionText.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                ParsedLyricsLine(text, (minutes * 60 + seconds) * 1000 + millis)
            }
        }
    }.filter { it.text.isNotBlank() }.toList()
}

private fun activeLyricsLineIndex(lyrics: String, positionMs: Long): Int? {
    val lines = parseLyrics(lyrics)
    val activeTimedIndex = lines.indices
        .filter { index -> lines[index].timestampMs?.let { it <= positionMs } == true }
        .maxByOrNull { index -> lines[index].timestampMs ?: Long.MIN_VALUE }
    return activeTimedIndex ?: lines.indexOfFirst { it.timestampMs == null }
        .takeIf { it >= 0 }
}

@Composable
private fun LyricsDialog(
    song: Song,
    positionMs: Long,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val c = MusedColors.current
    val lines = remember(song.lyrics) { parseLyrics(song.lyrics) }
    val activeIndex = activeLyricsLineIndex(song.lyrics, positionMs)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = c.surfaceElevated,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .padding(20.dp)
            ) {
                Text(song.title, color = c.textPrimary, style = MaterialTheme.typography.titleLarge)
                Text(song.artist, color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                if (lines.isEmpty()) {
                    Text("No lyrics yet. Add them manually.", color = c.textSecondary)
                } else {
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        lines.forEachIndexed { index, line ->
                            Text(
                                line.text,
                                color = if (index == activeIndex) c.accent else c.textPrimary,
                                fontWeight = if (index == activeIndex) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Close", color = c.textSecondary) }
                    TextButton(onClick = onEdit) { Text("Edit lyrics", color = c.accent) }
                }
            }
        }
    }
}

@Composable
private fun LyricsEditorDialog(
    song: Song,
    positionMs: Long,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val c = MusedColors.current
    var draft by rememberSaveable(song.id) { mutableStateOf(song.lyrics) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Edit lyrics", color = c.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Add plain lyrics or timestamped lines in [mm:ss.xx] format.",
                    color = c.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 360.dp),
                    enabled = !isSaving,
                    placeholder = { Text("[00:12.50] First line") },
                    textStyle = LocalTextStyle.current.copy(color = c.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent,
                        unfocusedBorderColor = c.border,
                        cursorColor = c.accent
                    )
                )
                TextButton(
                    enabled = !isSaving,
                    onClick = {
                        val timeMs = positionMs.coerceAtLeast(0)
                        val minute = timeMs / 60_000
                        val second = timeMs / 1_000 % 60
                        val centisecond = timeMs % 1_000 / 10
                        val timestamp = "[%02d:%02d.%02d] ".format(Locale.ROOT, minute, second, centisecond)
                        draft = buildString {
                            append(draft)
                            if (isNotEmpty() && last() != '\n') append('\n')
                            append(timestamp)
                        }
                    }
                ) {
                    Text("Insert current timestamp", color = c.accent)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !isSaving, onClick = { onSave(draft) }) {
                Text(if (isSaving) "Saving..." else "Save", color = c.accent)
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) {
                Text("Cancel", color = c.textSecondary)
            }
        },
        containerColor = c.surfaceElevated,
        shape = RoundedCornerShape(24.dp)
    )
}


/* ═══════════════════════════════════════════════════════════════
   Queue Dialog
   ═══════════════════════════════════════════════════════════════ */

@Composable
fun QueueDialog(state: PlaybackState, onDismiss: () -> Unit, onPlay: (Song) -> Unit) {
    val c = MusedColors.current
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Scroll to current song when dialog opens
    LaunchedEffect(state.currentIndex) {
        if (state.currentIndex >= 0) {
            lazyListState.animateScrollToItem(state.currentIndex)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = c.background) {
            Column(Modifier.fillMaxSize()) {
                /* Header */
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = c.textSecondary)
                    }
                    Text(
                        "Queue",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${state.queue.size} songs",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textMuted,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }

                HorizontalDivider(color = c.border)

                if (state.queue.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.QueueMusic, null, Modifier.size(52.dp), tint = c.textMuted)
                            Spacer(Modifier.height(12.dp))
                            Text("Queue is empty", color = c.textSecondary, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(state.queue, key = { _, song -> song.id }) { index, song ->
                            val isCurrent = index == state.currentIndex
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlay(song); onDismiss() },
                                color = if (isCurrent) c.accent.copy(alpha = 0.10f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    /* Playing indicator or index */
                                    Box(
                                        modifier = Modifier.size(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCurrent) {
                                            Icon(
                                                Icons.Default.VolumeUp, null,
                                                tint = c.accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Text(
                                                "${index + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = c.textMuted
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Artwork(song, Modifier.size(44.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            song.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) c.accent else c.textPrimary
                                        )
                                        Text(
                                            song.artist,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = c.textSecondary
                                        )
                                    }
                                    Text(
                                        formatTime(song.durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = c.textMuted
                                    )
                                }
                            }
                        }
                    }
            }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════
   Artwork helper
   ═══════════════════════════════════════════════════════════════ */

@Composable
fun Artwork(song: Song, modifier: Modifier = Modifier) {
    val c = MusedColors.current
    val bitmap = remember(song.artworkPath) {
        song.artworkPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    if (bitmap != null) {
        Image(bitmap, null, modifier.clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
    } else {
        Image(
            androidx.compose.ui.res.painterResource(id = R.drawable.default_cover),
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

/* ═══════════════════════════════════════════════════════════════
   Utilities
   ═══════════════════════════════════════════════════════════════ */

private fun formatTime(ms: Long): String = "%d:%02d".format(ms / 60_000, (ms / 1_000) % 60)
