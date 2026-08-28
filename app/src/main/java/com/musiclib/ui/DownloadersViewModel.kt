package com.musiclib.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musiclib.data.DownloaderInfo
import com.musiclib.data.DownloaderJob
import com.musiclib.data.MusicApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DownloadersUiState {
    data object Loading : DownloadersUiState
    data class Ready(val scripts: List<DownloaderInfo>) : DownloadersUiState
    data class Failed(val message: String) : DownloadersUiState
}

class DownloadersViewModel(
    private val api: MusicApi,
    private val libraryId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow<DownloadersUiState>(DownloadersUiState.Loading)
    val state: StateFlow<DownloadersUiState> = _state.asStateFlow()

    private val _job = MutableStateFlow<DownloaderJob?>(null)
    val job: StateFlow<DownloaderJob?> = _job.asStateFlow()

    private val _runError = MutableStateFlow<String?>(null)
    val runError: StateFlow<String?> = _runError.asStateFlow()

    private var pollJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        _state.value = DownloadersUiState.Loading
        if (libraryId <= 0) {
            _state.value = DownloadersUiState.Failed("no library selected")
            return
        }
        viewModelScope.launch {
            _state.value = try {
                DownloadersUiState.Ready(api.listDownloaders(libraryId))
            } catch (t: Throwable) {
                DownloadersUiState.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    fun run(script: String, urls: List<String>) {
        if (libraryId <= 0 || urls.isEmpty()) return
        _runError.value = null
        _job.value = null
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val jobId = try {
                api.runDownloader(libraryId, script, urls)
            } catch (t: Throwable) {
                _runError.value = t.message ?: t.javaClass.simpleName
                return@launch
            }
            while (true) {
                val j = try {
                    api.getDownloaderJob(libraryId, jobId)
                } catch (t: Throwable) {
                    _runError.value = t.message ?: t.javaClass.simpleName
                    return@launch
                }
                _job.value = j
                if (j.status != "running") return@launch
                delay(1000)
            }
        }
    }
}
