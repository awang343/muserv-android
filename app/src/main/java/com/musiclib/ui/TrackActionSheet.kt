package com.musiclib.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musiclib.data.Track
import com.musiclib.data.db.DownloadEntity
import com.musiclib.data.db.DownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionSheet(
    track: Track,
    download: DownloadEntity?,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onEnqueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenTags: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${track.displayArtist}  —  ${track.displayAlbum}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ActionItem(Icons.Default.PlayArrow, "Play now", onPlay)
            ActionItem(Icons.Default.Add, "Add to queue", onEnqueue)
            ActionItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist", onAddToPlaylist)
            ActionItem(Icons.Default.LocalOffer, "Tags", onOpenTags)
            when (download?.status) {
                DownloadStatus.DOWNLOADED ->
                    ActionItem(Icons.Default.Delete, "Remove download", onRemoveDownload)
                DownloadStatus.DOWNLOADING -> {
                    val pct = if (download.totalBytes > 0) {
                        (download.bytesDownloaded * 100 / download.totalBytes).toInt()
                    } else 0
                    ActionItem(Icons.Default.Download, "Downloading… $pct%", {})
                }
                DownloadStatus.QUEUED -> ActionItem(Icons.Default.Download, "Queued…", {})
                DownloadStatus.FAILED -> ActionItem(Icons.Default.Download, "Retry download", onDownload)
                null -> ActionItem(Icons.Default.Download, "Download track", onDownload)
            }
        }
    }
}

@Composable
private fun ActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.combinedClickableForBottomSheet(onClick),
    )
}

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.combinedClickableForBottomSheet(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)
