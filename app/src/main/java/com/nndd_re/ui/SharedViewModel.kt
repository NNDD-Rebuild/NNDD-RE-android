package com.nndd_re.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.nndd_re.data.model.NnddVideo

enum class PlayerUiState { HIDDEN, FULL, MINI }

class SharedViewModel : ViewModel() {
    private val cache = mutableMapOf<String, NnddVideo>()
    fun put(idKey: String, video: NnddVideo) { cache[idKey] = video }
    fun get(idKey: String): NnddVideo? = cache[idKey]

    var playerUiState by mutableStateOf(PlayerUiState.HIDDEN)
    var currentVideoId by mutableStateOf<String?>(null)
}
