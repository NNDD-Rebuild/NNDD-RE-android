package com.nndd_re.ui.screen.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nndd_re.data.model.NnddComment
import com.nndd_re.data.model.NnddVideo
import com.nndd_re.data.repository.NnddRepository
import com.nndd_re.ui.SharedViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val repo: NnddRepository,
    private val videoId: String,
    private val sharedVm: SharedViewModel
) : ViewModel() {

    private val _comments = MutableStateFlow<List<NnddComment>>(emptyList())
    val comments: StateFlow<List<NnddComment>> = _comments

    private val _video = MutableStateFlow<NnddVideo?>(sharedVm.get(videoId))
    val video: StateFlow<NnddVideo?> = _video

    val streamUrl: String = repo.streamUrl(videoId)
    val thumbUrl: String = repo.thumbUrl(videoId)

    init {
        viewModelScope.launch {
            repo.getComments(videoId)
                .onSuccess { _comments.value = it.sortedBy { c -> c.vposMs } }
        }
        // APIで取得できれば上書き、失敗してもsharedVmのキャッシュが残る
        viewModelScope.launch {
            repo.getVideo(videoId)
                .onSuccess { _video.value = it }
        }
    }
}
