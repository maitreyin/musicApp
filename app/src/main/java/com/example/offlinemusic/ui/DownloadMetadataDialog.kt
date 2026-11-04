package com.example.offlinemusic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.offlinemusic.data.SongMetadata
import com.example.offlinemusic.ui.theme.MusedColors

@Composable
fun DownloadMetadataDialog(
    metadata: SongMetadata,
    isSaving: Boolean,
    errorMessage: String?,
    onCancel: () -> Unit,
    onSave: (SongMetadata) -> Unit
) {
    val c = MusedColors.current
    var title by remember(metadata) { mutableStateOf(metadata.title) }
    var artist by remember(metadata) { mutableStateOf(metadata.artist) }
    var album by remember(metadata) { mutableStateOf(metadata.album) }
    var genre by remember(metadata) { mutableStateOf(metadata.genre) }

    Dialog(
        onDismissRequest = { if (!isSaving) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = c.surfaceElevated,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Edit Download Details", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Genre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(errorMessage, color = c.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = c.accent)
                        Spacer(Modifier.width(12.dp))
                    }
                    TextButton(onClick = onCancel, enabled = !isSaving) {
                        Text("Cancel", color = c.textSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onSave(metadata.copy(title = title, artist = artist, album = album, genre = genre))
                        },
                        enabled = !isSaving
                    ) {
                        Text("Save", color = c.accent)
                    }
                }
            }
        }
    }
}
