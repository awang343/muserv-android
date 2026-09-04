package com.musiclib

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.musiclib.data.AppContainer
import com.musiclib.data.Track
import com.musiclib.data.db.DownloadStatus
import com.musiclib.playback.PlaybackService
import com.musiclib.playback.PlayerHolder
import com.musiclib.ui.AppRoot
import com.musiclib.ui.MusicLibTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val player = PlayerHolder()
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = (application as MusicLibApp).container

            MusicLibTheme {
                AppRoot(
                    container = container,
                    player = player,
                    onPlay = { track ->
                        lifecycleScope.launch {
                            val item = mediaItem(track, container)
                            player.playNow(item)
                        }
                    },
                    onPlayList = { tracks, startIndex ->
                        lifecycleScope.launch {
                            val items = tracks.map { mediaItem(it, container) }
                            player.playFromList(items, startIndex)
                        }
                    },
                    onEnqueue = { track ->
                        lifecycleScope.launch {
                            val item = mediaItem(track, container)
                            player.enqueue(item)
                        }
                    },
                    onEnqueueAll = { tracks ->
                        lifecycleScope.launch {
                            val items = tracks.map { mediaItem(it, container) }
                            player.enqueueAll(items)
                        }
                    },
                )
            }
        }
    }

    private suspend fun mediaItem(track: Track, container: AppContainer): MediaItem {
        val libraryId = container.settings.flow.first().selectedLibraryId
        val download = container.database.downloadDao().get(libraryId, track.id)
        val uri = if (download?.status == DownloadStatus.DOWNLOADED &&
            download.localPath != null &&
            java.io.File(download.localPath).exists()
        ) {
            "file://${download.localPath}"
        } else {
            container.api.streamUrlFor(libraryId, track.id)
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.displayTitle)
                    .setArtist(track.displayArtist)
                    .setAlbumTitle(track.displayAlbum)
                    .build(),
            )
            .build()
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        controllerFuture = f
        f.addListener({
            try {
                player.attach(f.get())
            } catch (_: Exception) {
                // controller failed to bind; player is no-op
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        player.detach()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }
}
