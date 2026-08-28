package com.musiclib.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musiclib.data.Library
import com.musiclib.data.MusicApi
import com.musiclib.data.Settings
import com.musiclib.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repo: SettingsRepository,
    api: MusicApi,
    onSaved: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var selectedLibraryName by rememberSaveable { mutableStateOf("") }
    var selectedLibraryId by rememberSaveable { mutableStateOf(0L) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var libraries by remember { mutableStateOf<List<Library>>(emptyList()) }
    var libsLoading by remember { mutableStateOf(false) }
    var libsError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (initialized) return@LaunchedEffect
        val saved = repo.flow.first()
        url = saved.serverUrl
        token = saved.authToken
        selectedLibraryName = saved.selectedLibraryName
        selectedLibraryId = saved.selectedLibraryId
        initialized = true
    }

    suspend fun refreshLibraries() {
        libsLoading = true
        libsError = null
        try {
            val list = api.listLibraries()
            libraries = list
            // Reconcile current selection.
            val match = list.firstOrNull { it.name == selectedLibraryName }
                ?: list.firstOrNull { it.id == selectedLibraryId }
                ?: list.firstOrNull()
            if (match != null) {
                selectedLibraryName = match.name
                selectedLibraryId = match.id
            } else {
                selectedLibraryName = ""
                selectedLibraryId = 0L
            }
        } catch (t: Throwable) {
            libsError = t.message ?: t.javaClass.simpleName
        } finally {
            libsLoading = false
        }
    }

    // Fetch libraries whenever the URL/token changes (after they're loaded).
    LaunchedEffect(initialized, url, token) {
        if (!initialized) return@LaunchedEffect
        if (url.isBlank()) return@LaunchedEffect
        refreshLibraries()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (onBack != null) {
                        TextButton(onClick = onBack) { Text("Back") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server URL") },
                singleLine = true,
                placeholder = { Text("http://192.168.1.10:7700") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Auth token (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    scope.launch {
                        repo.save(
                            Settings(
                                serverUrl = url,
                                authToken = token,
                                selectedLibraryName = selectedLibraryName,
                                selectedLibraryId = selectedLibraryId,
                            )
                        )
                        onSaved()
                    }
                },
                enabled = url.isNotBlank() && selectedLibraryId > 0,
            ) { Text("Save") }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text("Library", style = MaterialTheme.typography.titleMedium)
            when {
                libsLoading -> Text("Loading libraries…")
                libsError != null && libraries.isEmpty() ->
                    Text("Couldn't load libraries: $libsError")
                libraries.isEmpty() ->
                    Text(
                        "No libraries on this server. Add a [[library]] in the server config.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else -> Column {
                    libraries.forEach { lib ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedLibraryName = lib.name
                                    selectedLibraryId = lib.id
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = lib.id == selectedLibraryId,
                                onClick = {
                                    selectedLibraryName = lib.name
                                    selectedLibraryId = lib.id
                                },
                            )
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(lib.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    lib.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
