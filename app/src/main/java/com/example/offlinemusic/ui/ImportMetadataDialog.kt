package com.example.offlinemusic.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.offlinemusic.data.SongMetadata
import com.example.offlinemusic.data.SongRepository
import com.example.offlinemusic.ui.theme.MusedColors
import kotlinx.coroutines.launch

@Composable
fun ImportMetadataDialog(
    uris: List<Uri>,
    repository: SongRepository,
    onDismiss: () -> Unit
) {
    val c = MusedColors.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var isImporting by remember { mutableStateOf(false) }
    var metadataList by remember { mutableStateOf<List<SongMetadata>>(emptyList()) }

    LaunchedEffect(uris) {
        isLoading = true
        val list = uris.map { uri ->
            repository.extractMetadata(uri)
        }
        metadataList = list
        isLoading = false
    }

    Dialog(
        onDismissRequest = { if (!isImporting && !isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = c.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text("Import Songs", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.accent)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(metadataList) { index, meta ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = c.card,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Song ${index + 1}", color = c.textSecondary, style = MaterialTheme.typography.labelMedium)
                                    OutlinedTextField(
                                        value = meta.title,
                                        onValueChange = { newTitle ->
                                            metadataList = metadataList.toMutableList().apply { this[index] = meta.copy(title = newTitle) }
                                        },
                                        label = { Text("Title") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = meta.artist,
                                        onValueChange = { newArtist ->
                                            metadataList = metadataList.toMutableList().apply { this[index] = meta.copy(artist = newArtist) }
                                        },
                                        label = { Text("Artist") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = meta.album,
                                        onValueChange = { newAlbum ->
                                            metadataList = metadataList.toMutableList().apply { this[index] = meta.copy(album = newAlbum) }
                                        },
                                        label = { Text("Album") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = meta.genre,
                                        onValueChange = { newGenre ->
                                            metadataList = metadataList.toMutableList().apply { this[index] = meta.copy(genre = newGenre) }
                                        },
                                        label = { Text("Genre") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(color = c.accent, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    TextButton(onClick = onDismiss, enabled = !isImporting) {
                        Text("Cancel", color = c.textSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isImporting = true
                                try {
                                    uris.forEachIndexed { index, uri ->
                                        repository.import(uri, metadataList[index])
                                    }
                                } finally {
                                    isImporting = false
                                    onDismiss()
                                }
                            }
                        },
                        enabled = !isLoading && !isImporting,
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.background)
                    ) {
                        Text("Import")
                    }
                }
            }
        }
    }
}
