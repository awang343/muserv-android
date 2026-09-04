package com.musiclib.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musiclib.data.AppContainer
import com.musiclib.data.Track
import com.musiclib.data.db.DownloadEntity
import com.musiclib.data.db.DownloadStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SongsScreen(
    viewModel: SongsViewModel,
    libraryId: Long,
    onPlay: (Track) -> Unit,
    onEnqueue: (Track) -> Unit,
    onEnqueueAll: (List<Track>) -> Unit,
    container: AppContainer,
    onMenuClick: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var actionFor by remember { mutableStateOf<Track?>(null) }
    var pickPlaylistFor by remember { mutableStateOf<Track?>(null) }
    var tagsFor by remember { mutableStateOf<Track?>(null) }

    var searchMode by remember { mutableStateOf(false) }
    var tagQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Track>?>(null) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    var currentSort by remember { mutableStateOf(SortKey.ADDED_AT) }
    var sortOpen by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val downloads by container.downloadRepository.downloadsForLibrary(libraryId)
        .collectAsState(initial = emptyList())
    val downloadsByTrackId = remember(downloads) { downloads.associateBy { it.trackId } }

    fun runTagSearch() {
        val q = tagQuery.trim()
        if (q.isEmpty()) return
        scope.launch {
            searchLoading = true
            searchError = null
            try {
                searchResults = container.api.searchTracks(libraryId, q)
            } catch (t: Throwable) {
                searchError = t.message ?: t.javaClass.simpleName
                searchResults = null
            } finally {
                searchLoading = false
            }
        }
    }

    fun exitSearch() {
        searchMode = false
        searchResults = null
        searchError = null
        tagQuery = ""
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (searchMode) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = tagQuery,
                            onValueChange = { tagQuery = it },
                            placeholder = { Text("ns:val -bad:tag") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { runTagSearch() }),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { exitSearch() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { runTagSearch() },
                            enabled = tagQuery.isNotBlank(),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Songs") },
                    navigationIcon = {
                        if (onMenuClick != null) {
                            IconButton(onClick = onMenuClick) {
                                Icon(Icons.Default.Menu, contentDescription = "Open menu")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { sortOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        IconButton(
                            onClick = {
                                val s = state
                                if (s is SongsUiState.Ready) {
                                    onEnqueueAll(s.tracks.sortedWith(comparatorFor(currentSort)))
                                }
                            },
                            enabled = (state as? SongsUiState.Ready)?.tracks?.isNotEmpty() == true,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add all to queue")
                        }
                        IconButton(onClick = { searchMode = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Tag search")
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (searchMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        searchLoading -> CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                        )
                        searchError != null -> Text(
                            searchError ?: "",
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        )
                        searchResults == null -> Text(
                            "Enter a tag query (e.g. mood:chill, -genre:metal)",
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        searchResults!!.isEmpty() -> Text(
                            "No matches.",
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> {
                            val results = searchResults!!
                            val sortedResults = remember(results, currentSort) {
                                results.sortedWith(comparatorFor(currentSort))
                            }
                            TrackList(
                                tracks = sortedResults,
                                sortKey = currentSort,
                                downloadsByTrackId = downloadsByTrackId,
                                onPlay = onPlay,
                                onLongPress = { actionFor = it },
                            )
                        }
                    }
                }
            } else {
                if ((state as? SongsUiState.Ready)?.offline == true) {
                    OfflineBanner()
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Filter") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    when (val s = state) {
                        SongsUiState.Loading -> CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                        )
                        is SongsUiState.Failed -> Text(
                            s.message,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        )
                        is SongsUiState.Ready -> {
                            val visible = remember(s.tracks, query, currentSort) {
                                val filtered = if (query.isBlank()) s.tracks
                                else {
                                    val q = query.trim().lowercase()
                                    s.tracks.filter {
                                        it.displayArtist.lowercase().contains(q) ||
                                            it.displayTitle.lowercase().contains(q) ||
                                            it.displayAlbum.lowercase().contains(q)
                                    }
                                }
                                filtered.sortedWith(comparatorFor(currentSort))
                            }
                            TrackList(
                                tracks = visible,
                                sortKey = currentSort,
                                downloadsByTrackId = downloadsByTrackId,
                                onPlay = onPlay,
                                onLongPress = { actionFor = it },
                            )
                        }
                    }
                }
            }
        }
    }

    if (actionFor != null) {
        val track = actionFor!!
        TrackActionSheet(
            track = track,
            download = downloadsByTrackId[track.id],
            onDismiss = { actionFor = null },
            onPlay = {
                onPlay(track)
                actionFor = null
            },
            onEnqueue = {
                onEnqueue(track)
                actionFor = null
            },
            onAddToPlaylist = {
                pickPlaylistFor = track
                actionFor = null
            },
            onOpenTags = {
                tagsFor = track
                actionFor = null
            },
            onDownload = {
                scope.launch { container.downloadRepository.downloadTrack(libraryId, track) }
                actionFor = null
            },
            onRemoveDownload = {
                scope.launch { container.downloadRepository.removeDownload(libraryId, track.id) }
                actionFor = null
            },
        )
    }

    tagsFor?.let { track ->
        TrackTagsSheet(
            container = container,
            libraryId = libraryId,
            track = track,
            onDismiss = { tagsFor = null },
        )
    }

    if (sortOpen) {
        SortDialog(
            current = currentSort,
            onPick = {
                currentSort = it
                sortOpen = false
            },
            onDismiss = { sortOpen = false },
        )
    }

    pickPlaylistFor?.let { track ->
        PickPlaylistDialog(
            container = container,
            libraryId = libraryId,
            trackId = track.id,
            onPick = { p ->
                scope.launch {
                    try {
                        container.api.addToPlaylist(libraryId, p.id, track.id)
                    } catch (_: Throwable) {
                        // Silent fail; user can verify in Playlists tab.
                    }
                }
                pickPlaylistFor = null
            },
            onDismiss = { pickPlaylistFor = null },
        )
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackList(
    tracks: List<Track>,
    sortKey: SortKey,
    downloadsByTrackId: Map<Long, DownloadEntity>,
    onPlay: (Track) -> Unit,
    onLongPress: (Track) -> Unit,
) {
    val lazyState = rememberLazyListState()
    LaunchedEffect(sortKey) {
        lazyState.scrollToItem(0)
    }
    LazyColumn(state = lazyState, modifier = Modifier.fillMaxSize()) {
        items(tracks, key = { it.id }) { t ->
            TrackRow(t, download = downloadsByTrackId[t.id], onPlay = onPlay, onLongPress = onLongPress)
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: Track,
    download: DownloadEntity?,
    onPlay: (Track) -> Unit,
    onLongPress: (Track) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onPlay(track) },
                onLongClick = { onLongPress(track) },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${track.displayArtist}  —  ${track.displayAlbum}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DownloadStatusGlyph(download)
    }
}

@Composable
private fun DownloadStatusGlyph(download: DownloadEntity?) {
    when (download?.status) {
        null -> {}
        DownloadStatus.QUEUED -> Icon(
            Icons.Default.Download,
            contentDescription = "Queued for download",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DownloadStatus.DOWNLOADING -> {
            val pct = if (download.totalBytes > 0) {
                (download.bytesDownloaded * 100 / download.totalBytes).toInt()
            } else 0
            Text(
                "$pct%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        DownloadStatus.DOWNLOADED -> Icon(
            Icons.Default.DownloadDone,
            contentDescription = "Downloaded",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        DownloadStatus.FAILED -> Icon(
            Icons.Default.ErrorOutline,
            contentDescription = "Download failed",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}
