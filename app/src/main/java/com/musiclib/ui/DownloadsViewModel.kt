package com.musiclib.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musiclib.data.db.DownloadWithTrack
import com.musiclib.data.download.DownloadRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val repository: DownloadRepository,
    private val libraryId: Long,
) : ViewModel() {
    val downloads: StateFlow<List<DownloadWithTrack>> = repository.downloadsWithTrackInfo(libraryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val storageUsed: StateFlow<Long> = repository.storageUsedForLibrary(libraryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun remove(trackId: Long) {
        viewModelScope.launch { repository.removeDownload(libraryId, trackId) }
    }

    fun removeAll() {
        viewModelScope.launch { repository.removeAllDownloads(libraryId) }
    }
}
