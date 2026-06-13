package com.nndd_re.ui.screen.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nndd_re.data.model.NnddVideo
import com.nndd_re.data.repository.NnddRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Success(val videos: List<NnddVideo>) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

enum class SortField(val label: String) {
    PUB_DATE("投稿日"),
    VIDEO_NAME("タイトル"),
    PLAY_COUNT("再生数"),
    DURATION("長さ"),
    LAST_PLAY("最終再生")
}

enum class SortOrder { ASC, DESC }

enum class ViewMode { GRID, LIST }

class LibraryViewModel(private val repo: NnddRepository) : ViewModel() {

    private val _state = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val state: StateFlow<LibraryUiState> = _state

    val selectedFolder = MutableStateFlow<String?>(null)
    val selectedTag = MutableStateFlow<String?>(null)

    // デフォルトはNNDD-REのサーバー側ソート(pubDate DESC)に合わせる
    val sortField = MutableStateFlow(SortField.PUB_DATE)
    val sortOrder = MutableStateFlow(SortOrder.DESC)
    val viewMode = MutableStateFlow(ViewMode.GRID)

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = LibraryUiState.Loading
            repo.getLibrary()
                .onSuccess { _state.value = LibraryUiState.Success(it) }
                .onFailure { e ->
                    val msg = when (e) {
                        is ConnectException,
                        is SocketTimeoutException,
                        is UnknownHostException -> "NNDD-REが見つかりませんでした"
                        else -> e.message ?: "接続エラー"
                    }
                    _state.value = LibraryUiState.Error(msg)
                }
        }
    }

    fun setFolder(f: String?) { selectedFolder.value = f }
    fun setTag(t: String?) { selectedTag.value = t }

    fun setSort(field: SortField) {
        if (sortField.value == field) {
            sortOrder.value = if (sortOrder.value == SortOrder.DESC) SortOrder.ASC else SortOrder.DESC
        } else {
            sortField.value = field
            sortOrder.value = SortOrder.DESC
        }
    }

    fun toggleViewMode() {
        viewMode.value = if (viewMode.value == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
    }

    fun thumbUrl(id: String) = repo.thumbUrl(id)
}
