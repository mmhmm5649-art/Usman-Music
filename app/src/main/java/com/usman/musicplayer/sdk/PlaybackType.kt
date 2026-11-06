package com.usman.musicplayer.sdk

enum class PlaybackType {
    FULL,            // Full track playback (local audio, authorized full stream)
    PREVIEW,         // High-quality official audio preview stream (30s) playable in-app via ExoPlayer
    EXTERNAL_SERVICE // Deep link to official service (YouTube, Spotify, Apple Music)
}
