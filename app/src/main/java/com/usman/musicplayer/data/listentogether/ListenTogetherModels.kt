package com.usman.musicplayer.data.listentogether

import com.usman.musicplayer.model.StreamingQuality
import com.usman.musicplayer.sdk.PlaybackType
import com.usman.musicplayer.sdk.Song

enum class ListenTogetherControlMode(val wireValue: String, val label: String, val description: String) {
    HOST_ONLY("HOST_ONLY", "Host Controls Only", "Only the host can control playback & queue"),
    EVERYONE("EVERYONE", "Everyone Can Control", "All participants can play, pause, skip & queue");

    companion object {
        fun fromWire(value: String?): ListenTogetherControlMode {
            return entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: HOST_ONLY
        }
    }
}

enum class ListenTogetherListeningMode(val wireValue: String, val label: String, val description: String) {
    NORMAL(
        wireValue = "NORMAL",
        label = "Normal Listening",
        description = "Up to 10 devices • Full original stereo audio on every phone"
    ),
    STEREO_SPLIT(
        wireValue = "STEREO_SPLIT",
        label = "Stereo Split Speaker",
        description = "2 devices only • Phone A = LEFT channel, Phone B = RIGHT channel"
    );

    companion object {
        fun fromWire(value: String?): ListenTogetherListeningMode {
            return entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: NORMAL
        }
    }
}

enum class ListenTogetherSpeakerRole(val wireValue: String, val badgeLabel: String) {
    STEREO("STEREO", "STEREO"),
    LEFT("LEFT", "LEFT"),
    RIGHT("RIGHT", "RIGHT");

    companion object {
        fun fromWire(value: String?): ListenTogetherSpeakerRole {
            return entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: STEREO
        }
    }
}

enum class ListenTogetherConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

enum class ParticipantConnectionState {
    CONNECTED,
    RECONNECTING;

    companion object {
        fun fromWire(value: String?): ParticipantConnectionState {
            return if (value.equals("RECONNECTING", ignoreCase = true)) RECONNECTING else CONNECTED
        }
    }
}

data class ListenTogetherTrack(
    val trackId: String,
    val provider: String = "JIO_SAAVN",
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String = "",
    val durationMs: Long = 0L,
    val addedByParticipantId: String = "",
    val addedByName: String = ""
) {
    fun toSong(resolvedStreamUrl: String? = null): Song {
        return Song(
            id = trackId.hashCode().toLong() and 0x7FFFFFFFL,
            title = title,
            artist = artist,
            album = album.ifBlank { "Listen Together" },
            durationMs = durationMs,
            filePath = trackId,
            folderName = "JioSaavn",
            albumArtUri = artworkUrl.takeIf { it.isNotBlank() },
            contentUriString = resolvedStreamUrl,
            isOnline = true,
            playbackType = PlaybackType.FULL,
            sourceProvider = "JioSaavn",
            isStreamable = true,
            isFullTrackVerified = true
        )
    }

    companion object {
        fun fromSong(song: Song, addedByParticipantId: String = "", addedByName: String = ""): ListenTogetherTrack? {
            if (!song.isOnline) return null
            if (song.playbackType != PlaybackType.FULL) return null
            val provider = song.sourceProvider.orEmpty()
            if (provider.equals("iTunes", ignoreCase = true) ||
                provider.equals("YouTube", ignoreCase = true)
            ) {
                return null
            }
            val canonicalId = song.filePath.takeIf { it.isNotBlank() && !it.startsWith("http") }
                ?: song.id.toString().takeIf { it.isNotBlank() && it != "0" }
                ?: return null
            return ListenTogetherTrack(
                trackId = canonicalId,
                provider = "JIO_SAAVN",
                title = song.title,
                artist = song.artist,
                album = song.album,
                artworkUrl = song.albumArtUri.orEmpty(),
                durationMs = song.durationMs.coerceAtLeast(0L),
                addedByParticipantId = addedByParticipantId,
                addedByName = addedByName
            )
        }
    }
}

data class ListenTogetherParticipant(
    val participantId: String,
    val displayName: String,
    val isHost: Boolean,
    val connectionState: ParticipantConnectionState,
    val joinedAt: Long,
    val qualityLabel: String = "HD",
    val speakerRole: ListenTogetherSpeakerRole = ListenTogetherSpeakerRole.STEREO
)

data class AuthoritativePlaybackState(
    val isPlaying: Boolean = false,
    val basePositionMs: Long = 0L,
    val anchorServerTimeMs: Long = 0L,
    val scheduledStartServerTimeMs: Long = 0L,
    val effectivePositionMs: Long = 0L,
    val playbackRate: Float = 1.0f,
    val stateRevision: Long = 0L,
    val queueRevision: Long = 0L,
    val lastCommandId: String? = null,
    val lastUpdatedBy: String? = null,
    val lastAction: String? = null
)

data class ListenTogetherRoomState(
    val roomId: String,
    val roomCode: String,
    val createdAt: Long,
    val expiresAt: Long,
    val hostId: String,
    val controlMode: ListenTogetherControlMode,
    val listeningMode: ListenTogetherListeningMode = ListenTogetherListeningMode.NORMAL,
    val maxParticipants: Int = 10,
    val currentIndex: Int,
    val currentTrack: ListenTogetherTrack?,
    val queue: List<ListenTogetherTrack>,
    val participants: List<ListenTogetherParticipant>,
    val playback: AuthoritativePlaybackState
)

data class SyncDiagnostics(
    val estimatedRttMs: Long = 0L,
    val clockOffsetMs: Long = 0L,
    val lastMeasuredDriftMs: Long = 0L,
    val isClockSynced: Boolean = false,
    val syncStatusLabel: String = "Waiting to sync"
)

data class ListenTogetherUiState(
    val connectionStatus: ListenTogetherConnectionStatus = ListenTogetherConnectionStatus.DISCONNECTED,
    val serverUrl: String = "",
    val isServerConfigured: Boolean = false,
    val displayName: String = "Listener",
    val participantId: String? = null,
    val room: ListenTogetherRoomState? = null,
    val selectedQuality: StreamingQuality = StreamingQuality.HD,
    val activeStreamQualityLabel: String = "HD",
    val isQualitySwitching: Boolean = false,
    val diagnostics: SyncDiagnostics = SyncDiagnostics(),
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val infoBanner: String? = null,
    val pendingDeepLinkRoomCode: String? = null,
    val localSpeakerRole: ListenTogetherSpeakerRole = ListenTogetherSpeakerRole.STEREO,
    val isCurrentTrackMono: Boolean = false,
    val stereoSplitNotice: String? = null
) {
    val isInRoom: Boolean get() = room != null
    val isHost: Boolean get() = room != null && participantId != null && room.hostId == participantId
    val canControlPlayback: Boolean
        get() = room != null && (isHost || room.controlMode == ListenTogetherControlMode.EVERYONE)
    val isStereoSplitMode: Boolean
        get() = room?.listeningMode == ListenTogetherListeningMode.STEREO_SPLIT
}
