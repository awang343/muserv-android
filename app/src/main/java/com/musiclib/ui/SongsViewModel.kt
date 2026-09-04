package com.musiclib.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musiclib.data.MusicApi
import com.musiclib.data.Track
import com.musiclib.data.db.CacheDao
import com.musiclib.data.db.TrackEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface SongsUiState {
    data object Loading : SongsUiState
    data class Ready(val tracks: List<Track>, val offline: Boolean = false) : SongsUiState
    data class Failed(val message: String) : SongsUiState
}

class SongsViewModel(
    private val api: MusicApi,
    private val cacheDao: CacheDao,
    private val libraryId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow<SongsUiState>(SongsUiState.Loading)
    val state: StateFlow<SongsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = SongsUiState.Loading
        if (libraryId <= 0) {
            _state.value = SongsUiState.Failed("no library selected")
            return
        }
        viewModelScope.launch {
            _state.value = try {
                val tracks = api.listTracks(libraryId)
                cacheDao.replaceTracks(libraryId, tracks.map { TrackEntity.from(libraryId, it) })
                SongsUiState.Ready(tracks)
            } catch (t: Throwable) {
                val cached = cacheDao.tracksForLibrary(libraryId).first().map { it.toTrack() }
                if (cached.isNotEmpty()) {
                    SongsUiState.Ready(cached, offline = true)
                } else {
                    SongsUiState.Failed(t.message ?: t.javaClass.simpleName)
                }
            }
        }
    }
}
