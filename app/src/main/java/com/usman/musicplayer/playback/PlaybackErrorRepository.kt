package com.usman.musicplayer.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlaybackErrorRepository {
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError = _playbackError.asStateFlow()

    fun setError(error: String) {
        _playbackError.value = error
    }

    fun clearError() {
        _playbackError.value = null
    }
}
