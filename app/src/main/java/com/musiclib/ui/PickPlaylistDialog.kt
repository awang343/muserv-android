package com.musiclib.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musiclib.data.AppContainer
import com.musiclib.data.Playlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickPlaylistDialog(
    container: AppContainer,
    libraryId: Long,
    trackId: Long,
    onPick: (Playlist) -> Unit,
    onDismiss: () -> Unit,
) {
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var containingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(trackId) {
        try {
            playlists = container.api.listPlaylists(libraryId)
            containingIds = try {
                container.api.playlistsContainingTrack(libraryId, trackId).toSet()
            } catch (_: Throwable) {
                emptySet()
            }
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
        } finally {
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            when {
                loading -> Text("Loading…")
                error != null -> Text("Couldn't load: $error")
                playlists.isEmpty() -> Text("No playlists yet — create one in the Playlists tab.")
                else -> Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    playlists.forEach { p ->
                        val isMember = p.id in containingIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickableForBottomSheet { onPick(p) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isMember) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Already in playlist",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Spacer(modifier = Modifier.width(32.dp))
                            }
                            Text(p.name, modifier = Modifier.weight(1f))
                            Text(
                                "${p.track_count}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
