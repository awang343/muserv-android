package com.musiclib.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musiclib.data.AppContainer
import com.musiclib.data.MusicApi
import com.musiclib.data.PlaylistTrack
import com.musiclib.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class PlaylistDetailViewModel(
    private val api: MusicApi,
    private val libraryId: Long,
    private val playlistId: Long,
) : ViewModel() {
    private val _tracks = MutableStateFlow<List<PlaylistTrack>>(emptyList())
    val tracks: StateFlow<List<PlaylistTrack>> = _tracks.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (libraryId <= 0) {
            _error.value = "no library selected"
            return
        }
        viewModelScope.launch {
            try {
                _tracks.value = api.getPlaylistTracks(libraryId, playlistId)
                _error.value = null
            } catch (t: Throwable) {
                _error.value = t.message ?: t.javaClass.simpleName
            }
        }
    }

    fun removeTrack(trackId: Long) {
        viewModelScope.launch {
            try {
                api.removeFromPlaylist(libraryId, playlistId, trackId)
                refresh()
            } catch (t: Throwable) {
                _error.value = t.message ?: t.javaClass.simpleName
            }
        }
    }

    fun moveTrackLocal(fromIndex: Int, toIndex: Int) {
        val cur = _tracks.value
        if (fromIndex !in cur.indices || toIndex !in cur.indices || fromIndex == toIndex) return
        val swapped = cur.toMutableList().also {
            val item = it.removeAt(fromIndex)
            it.add(toIndex, item)
        }
        _tracks.value = swapped.mapIndexed { i, pt -> pt.copy(position = i.toLong()) }
    }

    fun commitReorder() {
        val snapshot = _tracks.value
        viewModelScope.launch {
            try {
                api.setPlaylistTracks(libraryId, playlistId, snapshot.map { it.track_id })
            } catch (t: Throwable) {
                _error.value = t.message ?: t.javaClass.simpleName
                refresh()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    container: AppContainer,
    libraryId: Long,
    playlistId: Long,
    onPlayPlaylist: (tracks: List<Track>, startIndex: Int) -> Unit,
    onEnqueueTrack: (Track) -> Unit,
    onBack: () -> Unit,
) {
    val vm: PlaylistDetailViewModel = viewModel(
        key = "playlist-$libraryId-$playlistId",
        factory = viewModelFactory {
            initializer { PlaylistDetailViewModel(container.api, libraryId, playlistId) }
        },
    )
    val tracks by vm.tracks.collectAsState()
    val error by vm.error.collectAsState()

    val lazyState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyState) { from, to ->
        vm.moveTrackLocal(from.index, to.index)
    }

    var wasDragging by remember { mutableStateOf(false) }
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        val now = reorderableState.isAnyItemDragging
        if (wasDragging && !now) {
            vm.commitReorder()
        }
        wasDragging = now
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Playlist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onPlayPlaylist(tracks.map { it.toTrack() }, 0)
                        },
                        enabled = tracks.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play all")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                error != null && tracks.isEmpty() ->
                    Text(error ?: "", modifier = Modifier.align(Alignment.Center).padding(16.dp))
                tracks.isEmpty() ->
                    Text(
                        "Empty playlist. Add tracks from the Songs tab.",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else -> LazyColumn(
                    state = lazyState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(tracks, key = { it.track_id }) { pt ->
                        ReorderableItem(reorderableState, key = pt.track_id) { isDragging ->
                            val index = tracks.indexOf(pt)
                            PlaylistTrackRow(
                                pt = pt,
                                isDragging = isDragging,
                                dragHandleModifier = Modifier.longPressDraggableHandle(),
                                onPlay = { if (index >= 0) onPlayPlaylist(tracks.map { it.toTrack() }, index) },
                                onEnqueue = { onEnqueueTrack(pt.toTrack()) },
                                onRemove = { vm.removeTrack(pt.track_id) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistTrackRow(
    pt: PlaylistTrack,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
    onPlay: () -> Unit,
    onEnqueue: () -> Unit,
    onRemove: () -> Unit,
) {
    val bg = if (isDragging) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(color = bg) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlay)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                modifier = dragHandleModifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    pt.title ?: "(untitled)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${pt.artist ?: pt.album_artist ?: "—"}  —  ${pt.album ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEnqueue) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = "Add to queue",
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(22.dp))
            }
        }
    }
}

private fun PlaylistTrack.toTrack() = Track(
    id = track_id,
    path = "",
    title = title,
    album = album,
    artist = artist,
    album_artist = album_artist,
    duration_ms = duration_ms,
)
