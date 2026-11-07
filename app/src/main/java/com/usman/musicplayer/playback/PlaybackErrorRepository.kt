package com.usman.musicplayer.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PlaybackErrorRepository {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun setError(error: String) {
        log(error)
    }

    fun log(message: String) {
        val timestamp = formatter.format(Date())
        val entry = "[$timestamp] $message"
        _logs.value = (_logs.value + entry).takeLast(500)
    }

    fun clearError() {
        _logs.value = emptyList()
    }
}
