package com.usman.musicplayer.model

data class PlaybackProgress(
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val updateTimeRealtimeMs: Long = android.os.SystemClock.elapsedRealtime()
) {
    /**
     * Computes the exact real-time playback position at the current display frame timestamp.
     * When playing, smoothly interpolates between authoritative Media3 updates so frame-rate
     * animations at 60Hz, 90Hz, and 120Hz never experience freezing or step-jumps.
     */
    fun interpolatedPositionMs(): Long {
        if (!isPlaying) return currentPositionMs
        val now = android.os.SystemClock.elapsedRealtime()
        val delta = (now - updateTimeRealtimeMs).coerceAtLeast(0L)
        val estimated = currentPositionMs + delta
        return if (durationMs > 0) estimated.coerceIn(0L, durationMs) else estimated.coerceAtLeast(0L)
    }
}

