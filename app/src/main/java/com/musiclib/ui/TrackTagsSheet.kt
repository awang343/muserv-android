package com.musiclib.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musiclib.data.AppContainer
import com.musiclib.data.Track
import com.musiclib.data.TrackTag
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackTagsSheet(
    container: AppContainer,
    libraryId: Long,
    track: Track,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var tags by remember { mutableStateOf<List<TrackTag>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }

    suspend fun refresh() {
        try {
            tags = container.api.listTrackTags(libraryId, track.id)
            error = null
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
        }
    }

    LaunchedEffect(track.id) {
        loading = true
        refresh()
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                "${track.displayArtist}  —  ${track.displayAlbum}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HorizontalDivider()

            Box(modifier = Modifier.heightIn(min = 80.dp, max = 360.dp).fillMaxWidth()) {
                when {
                    loading -> Text(
                        "Loading…",
                        modifier = Modifier.padding(16.dp).align(Alignment.Center),
                    )
                    error != null && tags.isEmpty() -> Text(
                        error ?: "",
                        modifier = Modifier.padding(16.dp).align(Alignment.Center),
                    )
                    tags.isEmpty() -> Text(
                        "No tags yet.",
                        modifier = Modifier.padding(16.dp).align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(tags, key = { "${it.tag_id}-${it.source}" }) { tag ->
                            TagRow(
                                tag = tag,
                                onRemove = if (tag.source == "user") {
                                    {
                                        scope.launch {
                                            try {
                                                container.api.removeUserTag(libraryId, track.id, tag.tag_id)
                                                refresh()
                                            } catch (t: Throwable) {
                                                error = t.message ?: t.javaClass.simpleName
                                            }
                                        }
                                    }
                                } else null,
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("ns:value or just value") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val parsed = parseTagInput(input)
                        if (parsed != null) {
                            scope.launch {
                                try {
                                    container.api.addUserTag(libraryId, track.id, parsed.first, parsed.second)
                                    input = ""
                                    refresh()
                                } catch (t: Throwable) {
                                    error = t.message ?: t.javaClass.simpleName
                                }
                            }
                        }
                    },
                    enabled = parseTagInput(input) != null,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add tag")
                }
            }
        }
    }
}

@Composable
private fun TagRow(tag: TrackTag, onRemove: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = { /* tap = no-op for now */ },
            label = {
                Text(
                    if (tag.namespace.isEmpty()) ":${tag.value}" else "${tag.namespace}:${tag.value}",
                )
            },
        )
        Text(
            tag.source,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRemove != null) {
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun parseTagInput(s: String): Pair<String, String>? {
    val t = s.trim()
    if (t.isEmpty()) return null
    val (ns, value) = if (t.contains(':')) {
        val (n, v) = t.split(':', limit = 2)
        n.trim() to v.trim()
    } else "" to t
    return if (value.isEmpty()) null else ns to value
}
