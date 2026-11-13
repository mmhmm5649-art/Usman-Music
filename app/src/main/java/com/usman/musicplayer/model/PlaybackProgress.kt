package com.usman.musicplayer.model

data class PlaybackProgress(
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val isBuffering: Boolean = false,
    val updateTimeRealtimeMs: Long = android.os.SystemClock.elapsedRealtime(),
    val updateTimeRealtimeNanos: Long = android.os.SystemClock.elapsedRealtimeNanos()
) {
    /**
     * Computes the exact real-time playback position at the current display frame timestamp.
     * When playing (and not buffering), smoothly interpolates between authoritative Media3 updates
     * scaled by [playbackSpeed] so frame-rate animations at 60Hz, 90Hz, and 120Hz never experience
     * freezing, drift, or step-jumps.
     */
    fun interpolatedPositionMs(): Long {
        if (!isPlaying || isBuffering) return currentPositionMs
        val now = android.os.SystemClock.elapsedRealtime()
        val delta = ((now - updateTimeRealtimeMs).coerceAtLeast(0L) * playbackSpeed.coerceAtLeast(0f)).toLong()
        val estimated = currentPositionMs + delta
        return if (durationMs > 0) estimated.coerceIn(0L, durationMs) else estimated.coerceAtLeast(0L)
    }

    /**
     * Sub-millisecond continuous position for fluid, zero-stutter 60Hz/120Hz AMLL word & syllable reveals.
     */
    fun interpolatedPositionMsFloat(): Float {
        if (!isPlaying || isBuffering) return currentPositionMs.toFloat()
        val nowNanos = android.os.SystemClock.elapsedRealtimeNanos()
        val deltaMs = ((nowNanos - updateTimeRealtimeNanos) / 1_000_000.0f).coerceAtLeast(0f) * playbackSpeed.coerceAtLeast(0f)
        val estimated = currentPositionMs.toFloat() + deltaMs
        return if (durationMs > 0) estimated.coerceIn(0f, durationMs.toFloat()) else estimated.coerceAtLeast(0f)
    }
}
