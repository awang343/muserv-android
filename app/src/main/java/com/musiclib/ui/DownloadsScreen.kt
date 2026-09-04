package com.musiclib.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musiclib.data.AppContainer
import com.musiclib.data.db.DownloadStatus
import com.musiclib.data.db.DownloadWithTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    container: AppContainer,
    libraryId: Long,
    onBack: () -> Unit,
) {
    val vm: DownloadsViewModel = viewModel(
        key = "downloads-$libraryId",
        factory = viewModelFactory {
            initializer { DownloadsViewModel(container.downloadRepository, libraryId) }
        },
    )
    val downloads by vm.downloads.collectAsState()
    val storageUsed by vm.storageUsed.collectAsState()
    var confirmDeleteAll by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    if (downloads.isNotEmpty()) {
                        IconButton(onClick = { confirmDeleteAll = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Delete all downloads")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    "${formatBytes(storageUsed)} used",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (downloads.isEmpty()) {
                    Text(
                        "No downloads yet.",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(downloads, key = { it.trackId }) { d ->
                            DownloadRow(d = d, onDelete = { vm.remove(d.trackId) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all downloads?") },
            text = { Text("This removes all downloaded files from this device. They can be re-downloaded later.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeAll()
                    confirmDeleteAll = false
                }) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DownloadRow(d: DownloadWithTrack, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                d.title ?: "(untitled)",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                d.artist ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                statusText(d),
                style = MaterialTheme.typography.bodySmall,
                color = if (d.status == DownloadStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Remove download", modifier = Modifier.size(22.dp))
        }
    }
}

private fun statusText(d: DownloadWithTrack): String = when (d.status) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.DOWNLOADING -> {
        val pct = if (d.totalBytes > 0) (d.bytesDownloaded * 100 / d.totalBytes).toInt() else 0
        "Downloading… $pct%"
    }
    DownloadStatus.DOWNLOADED -> formatBytes(d.totalBytes)
    DownloadStatus.FAILED -> "Failed" + (d.error?.let { ": $it" } ?: "")
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
