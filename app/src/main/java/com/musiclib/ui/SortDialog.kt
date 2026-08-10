package com.musiclib.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musiclib.data.Track

enum class SortKey(val label: String) {
    DEFAULT("Default (artist / album / track)"),
    TITLE("Title (A–Z)"),
    ARTIST("Artist (A–Z)"),
    ALBUM("Album (A–Z)"),
    DURATION("Duration (shortest first)"),
    YEAR("Year (newest first)"),
    ADDED_AT("Date added (newest first)"),
}

fun comparatorFor(key: SortKey): Comparator<Track> = when (key) {
    SortKey.DEFAULT -> compareBy(
        { (it.album_artist ?: "").lowercase() },
        { (it.album ?: "").lowercase() },
        { it.disc_no ?: 0 },
        { it.track_no ?: 0 },
        { it.displayTitle.lowercase() },
    )
    SortKey.TITLE -> compareBy { it.displayTitle.lowercase() }
    SortKey.ARTIST -> compareBy(
        { it.displayArtist.lowercase() },
        { it.displayAlbum.lowercase() },
        { it.disc_no ?: 0 },
        { it.track_no ?: 0 },
    )
    SortKey.ALBUM -> compareBy(
        { it.displayAlbum.lowercase() },
        { it.disc_no ?: 0 },
        { it.track_no ?: 0 },
    )
    SortKey.DURATION -> compareBy { it.duration_ms ?: Long.MAX_VALUE }
    SortKey.YEAR -> compareByDescending<Track> { it.year ?: Long.MIN_VALUE }
        .thenBy { it.displayAlbum.lowercase() }
        .thenBy { it.disc_no ?: 0 }
        .thenBy { it.track_no ?: 0 }
    SortKey.ADDED_AT -> compareByDescending { it.added_at }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortDialog(
    current: SortKey,
    onPick: (SortKey) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort by") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SortKey.entries.forEach { k ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickableForBottomSheet { onPick(k) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = k == current,
                            onClick = { onPick(k) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(k.label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
