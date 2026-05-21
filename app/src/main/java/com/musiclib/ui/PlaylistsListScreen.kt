package com.musiclib.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.musiclib.data.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaylistsListViewModel(
    private val api: MusicApi,
    private val libraryId: Long,
) : ViewModel() {
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (libraryId <= 0) {
            _error.value = "no library selected"
            return
        }
        viewModelScope.launch {
            try {
                _playlists.value = api.listPlaylists(libraryId)
                _error.value = null
            } catch (t: Throwable) {
                _error.value = t.message ?: t.javaClass.simpleName
            }
        }
    }

    fun create(name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                api.createPlaylist(libraryId, name)
                refresh()
                onDone()
            } catch (t: Throwable) {
                _error.value = t.message ?: t.javaClass.simpleName
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            try {
                api.deletePlaylist(libraryId, id)
                refresh()
            } catch (t: Throwable) {
                _error.value = t.message ?: t.javaClass.simpleName
            }
        }
    }

    fun rename(id: Long, name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                api.renamePlaylist(libraryId, id, name)
                refresh()
                onDone()
            } catch (t: Throwable) {
                _error.value = t.message ?: t.javaClass.simpleName
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsListScreen(
    container: AppContainer,
    libraryId: Long,
    onOpen: (Long) -> Unit,
) {
    val vm: PlaylistsListViewModel = viewModel(
        key = "playlists-$libraryId",
        factory = viewModelFactory {
            initializer { PlaylistsListViewModel(container.api, libraryId) }
        },
    )
    val playlists by vm.playlists.collectAsState()
    val error by vm.error.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Playlist?>(null) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Playlists") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = "New playlist")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (error != null && playlists.isEmpty()) {
                Text(
                    error ?: "",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (playlists.isEmpty()) {
                Text(
                    "No playlists yet. Tap + to create one.",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(playlists, key = { it.id }) { p ->
                        PlaylistRow(
                            p = p,
                            onOpen = { onOpen(p.id) },
                            onRename = { renameTarget = p },
                            onDelete = { confirmDelete = p },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showCreate) {
        NewPlaylistDialog(
            onDismiss = { showCreate = false },
            onCreate = { name -> vm.create(name) { showCreate = false } },
        )
    }
    renameTarget?.let { p ->
        RenamePlaylistDialog(
            initial = p.name,
            onDismiss = { renameTarget = null },
            onRename = { newName -> vm.rename(p.id, newName) { renameTarget = null } },
        )
    }
    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${p.name}\"?") },
            text = { Text("This removes the playlist and all its track entries on the server.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(p.id)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PlaylistRow(
    p: Playlist,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "${p.track_count} track" + if (p.track_count == 1L) "" else "s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRename) {
            Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun RenamePlaylistDialog(
    initial: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && name.trim() != initial) onRename(name.trim()) },
                enabled = name.isNotBlank() && name.trim() != initial,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
