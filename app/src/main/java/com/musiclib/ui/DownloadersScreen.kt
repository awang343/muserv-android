package com.musiclib.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musiclib.data.AppContainer
import com.musiclib.data.DownloaderJob

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadersScreen(
    container: AppContainer,
    libraryId: Long,
    onBack: (() -> Unit)? = null,
) {
    val vm: DownloadersViewModel = viewModel(
        key = "downloaders-$libraryId",
        factory = viewModelFactory {
            initializer { DownloadersViewModel(container.api, libraryId) }
        },
    )
    val state by vm.state.collectAsState()
    val job by vm.job.collectAsState()
    val runError by vm.runError.collectAsState()

    var selectedScript by remember { mutableStateOf<String?>(null) }
    var urlsText by rememberSaveable { mutableStateOf("") }

    val running = job?.status == "running"

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Downloaders") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                DownloadersUiState.Loading -> Box(modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is DownloadersUiState.Failed -> Text(
                    "Couldn't load downloader scripts: ${s.message}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                is DownloadersUiState.Ready -> {
                    if (s.scripts.isEmpty()) {
                        Text(
                            "No downloader scripts configured.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("Script", style = MaterialTheme.typography.titleMedium)
                        Column {
                            s.scripts.forEach { d ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !running) { selectedScript = d.name }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = d.name == selectedScript,
                                        onClick = { selectedScript = d.name },
                                        enabled = !running,
                                    )
                                    Text(d.name, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }

                        HorizontalDivider()

                        OutlinedTextField(
                            value = urlsText,
                            onValueChange = { urlsText = it },
                            label = { Text("URLs (one per line)") },
                            enabled = !running,
                            minLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp),
                        )

                        val urls = urlsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        Button(
                            onClick = {
                                val script = selectedScript ?: return@Button
                                vm.run(script, urls)
                            },
                            enabled = !running && selectedScript != null && urls.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (running) "Running…" else "Start download")
                        }

                        runError?.let {
                            Text(
                                "Error: $it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        job?.let { j -> JobStatusView(job = j, modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobStatusView(job: DownloaderJob, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(job.log.size) {
        if (job.log.isNotEmpty()) {
            listState.animateScrollToItem(job.log.size - 1)
        }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val statusLabel = when (job.status) {
            "completed" -> "Completed"
            "failed" -> "Failed"
            else -> "Running"
        }
        Text("Status: $statusLabel", style = MaterialTheme.typography.titleMedium)
        val idx = job.current_index
        if (idx != null) {
            val url = job.urls.getOrNull(idx.toInt())
            Text(
                "Downloading ${idx + 1}/${job.urls.size}" + (url?.let { ": $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .padding(8.dp),
        ) {
            items(job.log) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        job.summary?.let { sum ->
            Text(
                "scanned=${sum.scanned} imported=${sum.imported} " +
                    "duplicates=${sum.duplicates} failed=${sum.failed}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
