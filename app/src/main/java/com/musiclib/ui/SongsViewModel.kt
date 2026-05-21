package com.musiclib.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musiclib.data.MusicApi
import com.musiclib.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SongsUiState {
    data object Loading : SongsUiState
    data class Ready(val tracks: List<Track>) : SongsUiState
    data class Failed(val message: String) : SongsUiState
}

class SongsViewModel(
    private val api: MusicApi,
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
                SongsUiState.Ready(api.listTracks(libraryId))
            } catch (t: Throwable) {
                SongsUiState.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }
}
