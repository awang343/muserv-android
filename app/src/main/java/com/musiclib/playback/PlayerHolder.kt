package com.musiclib.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Compact info pulled out of a [MediaItem] for UI. */
data class QueueItem(
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
)

/**
 * Wraps a [MediaController] and exposes Compose-friendly [StateFlow]s.
 * No-ops when not yet bound to a controller.
 */
class PlayerHolder {

    private var controller: MediaController? = null

    // MediaController strips localConfiguration (URI) when returning items via
    // getMediaItemAt — that's a security boundary. We keep the originals here so
    // operations like shuffleQueue can rebuild the playlist with real URIs.
    private val itemsById = mutableMapOf<String, MediaItem>()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _current = MutableStateFlow<QueueItem?>(null)
    val current: StateFlow<QueueItem?> = _current.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _repeat = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeat: StateFlow<Int> = _repeat.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var positionJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            syncPosition()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncCurrent()
            syncPosition()
        }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            syncQueue()
            syncCurrent()
            syncPosition()
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeat.value = repeatMode
        }
    }

    fun attach(c: MediaController) {
        detach()
        controller = c
        c.addListener(listener)
        _isPlaying.value = c.isPlaying
        _repeat.value = c.repeatMode
        syncQueue()
        syncCurrent()
        syncPosition()
        positionJob = scope.launch {
            while (true) {
                delay(500)
                if (controller?.isPlaying == true) {
                    syncPosition()
                }
            }
        }
    }

    fun detach() {
        positionJob?.cancel()
        positionJob = null
        controller?.removeListener(listener)
        controller = null
        _positionMs.value = 0L
        _durationMs.value = 0L
    }

    fun playNow(item: MediaItem) {
        val c = controller ?: return
        itemsById[item.mediaId] = item
        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    fun enqueue(item: MediaItem) {
        val c = controller ?: return
        itemsById[item.mediaId] = item
        if (c.mediaItemCount == 0) {
            c.setMediaItem(item)
            c.prepare()
            c.play()
            return
        }
        val alreadyQueued = (0 until c.mediaItemCount).any {
            c.getMediaItemAt(it).mediaId == item.mediaId
        }
        if (alreadyQueued) return
        c.addMediaItem(item)
    }

    /** Appends every item to the queue (deduped, preserves whatever is already playing). */
    fun enqueueAll(items: List<MediaItem>) {
        val c = controller ?: return
        if (items.isEmpty()) return
        val seen = mutableSetOf<String>()
        val deduped = items.filter { seen.add(it.mediaId) }
        deduped.forEach { itemsById[it.mediaId] = it }
        if (c.mediaItemCount == 0) {
            c.setMediaItems(deduped, 0, 0L)
            c.prepare()
            c.play()
            return
        }
        val existingIds = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }.toSet()
        val toAdd = deduped.filter { it.mediaId !in existingIds }
        if (toAdd.isEmpty()) return
        c.addMediaItems(toAdd)
    }

    fun playFromList(items: List<MediaItem>, startIndex: Int = 0) {
        val c = controller ?: return
        if (items.isEmpty()) return
        val seen = mutableSetOf<String>()
        val deduped = items.filter { seen.add(it.mediaId) }
        val targetId = items.getOrNull(startIndex)?.mediaId
        val start = deduped.indexOfFirst { it.mediaId == targetId }.coerceAtLeast(0)
        deduped.forEach { itemsById[it.mediaId] = it }
        c.setMediaItems(deduped, start, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun prev() {
        controller?.seekToPreviousMediaItem()
    }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.removeMediaItem(index)
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        val c = controller ?: return
        if (from == to) return
        if (from !in 0 until c.mediaItemCount) return
        if (to !in 0 until c.mediaItemCount) return
        c.moveMediaItem(from, to)
    }

    /**
     * One-shot reorder of the current queue. Keeps the currently-playing track at
     * index 0 and shuffles the rest. Uses move/remove/add so the current source
     * is never touched — no re-buffer, no audible hang.
     */
    fun shuffleQueue() {
        val c = controller ?: return
        val count = c.mediaItemCount
        if (count < 2) return
        val currentIndex = c.currentMediaItemIndex
        val ids = (0 until count).map { c.getMediaItemAt(it).mediaId }

        val otherIds = ids.filterIndexed { i, _ -> i != currentIndex }.shuffled()
        val otherItems = otherIds.mapNotNull { itemsById[it] }
        if (otherItems.size != otherIds.size) return // missing from cache; bail safely

        if (currentIndex != 0) {
            c.moveMediaItem(currentIndex, 0)
        }
        if (c.mediaItemCount > 1) {
            c.removeMediaItems(1, c.mediaItemCount)
        }
        c.addMediaItems(otherItems)
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun syncCurrent() {
        val c = controller ?: run {
            _current.value = null
            return
        }
        val idx = c.currentMediaItemIndex
        if (idx < 0 || idx >= c.mediaItemCount) {
            _current.value = null
            return
        }
        _current.value = c.getMediaItemAt(idx).toQueueItem(idx)
    }

    private fun syncPosition() {
        val c = controller ?: run {
            _positionMs.value = 0L
            _durationMs.value = 0L
            return
        }
        _positionMs.value = c.currentPosition.coerceAtLeast(0L)
        val d = c.duration
        _durationMs.value = if (d == C.TIME_UNSET || d < 0) 0L else d
    }

    private fun syncQueue() {
        val c = controller ?: run {
            _queue.value = emptyList()
            itemsById.clear()
            return
        }
        val out = ArrayList<QueueItem>(c.mediaItemCount)
        for (i in 0 until c.mediaItemCount) {
            out.add(c.getMediaItemAt(i).toQueueItem(i))
        }
        _queue.value = out
        // Drop cached MediaItems that fell out of the timeline (e.g. via
        // removeFromQueue) so this map doesn't grow unbounded over a session.
        itemsById.keys.retainAll(out.mapTo(mutableSetOf()) { it.mediaId })
    }
}

fun MediaItem.toQueueItem(index: Int): QueueItem {
    val md: MediaMetadata = mediaMetadata
    return QueueItem(
        index = index,
        mediaId = mediaId,
        title = md.title?.toString() ?: "(untitled)",
        artist = md.artist?.toString() ?: "—",
    )
}
