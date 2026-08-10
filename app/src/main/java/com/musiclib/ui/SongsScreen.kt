package com.musiclib.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
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
                                onPlay = onPlay,
                                onLongPress = { actionFor = it },
                            )
                        }
                    }
                }
            } else {
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
        TrackActionSheet(
            track = actionFor!!,
            onDismiss = { actionFor = null },
            onPlay = {
                onPlay(actionFor!!)
                actionFor = null
            },
            onEnqueue = {
                onEnqueue(actionFor!!)
                actionFor = null
            },
            onAddToPlaylist = {
                pickPlaylistFor = actionFor
                actionFor = null
            },
            onOpenTags = {
                tagsFor = actionFor
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
    onPlay: (Track) -> Unit,
    onLongPress: (Track) -> Unit,
) {
    val lazyState = rememberLazyListState()
    LaunchedEffect(sortKey) {
        lazyState.scrollToItem(0)
    }
    LazyColumn(state = lazyState, modifier = Modifier.fillMaxSize()) {
        items(tracks, key = { it.id }) { t ->
            TrackRow(t, onPlay = onPlay, onLongPress = onLongPress)
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: Track,
    onPlay: (Track) -> Unit,
    onLongPress: (Track) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onPlay(track) },
                onLongClick = { onLongPress(track) },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
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
}
