package com.usman.musicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.usman.musicplayer.MainActivity

@UnstableApi
class UsmanPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    companion object {
        @Volatile
        var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // Configure HTTP DataSource factory with modern browser User-Agent and Referer to support CDN audio streams
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.jiosaavn.com/"))
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

        // 1. Build ExoPlayer with automatic audio focus handling and audio attributes
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true // Automatic pause/duck on incoming calls & interruptions
            )
            .setHandleAudioBecomingNoisy(true) // Automatic pause when headphones are disconnected
            .setWakeMode(C.WAKE_MODE_LOCAL) // Prevent CPU from sleeping during background playback
            .build()

        exoPlayer = player
        currentAudioSessionId = player.audioSessionId

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                currentAudioSessionId = audioSessionId
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                Log.d("Media3Validation", "Playback State: $stateName")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("Media3Validation", "Is Playing: $isPlaying")
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                Log.d("Media3Validation", "Media Item Transition: ${mediaItem?.mediaMetadata?.title} (Reason: $reason)")
                Log.d("USMAN_PLAYBACK", "[Stage 7: MediaSession current metadata] Title: '${mediaItem?.mediaMetadata?.title}', Artist: '${mediaItem?.mediaMetadata?.artist}', Album: '${mediaItem?.mediaMetadata?.albumTitle}', ArtworkUri: ${mediaItem?.mediaMetadata?.artworkUri}")
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    Log.d("Media3Validation", "Seek detected: ${oldPosition.positionMs}ms -> ${newPosition.positionMs}ms")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("USMAN_PLAYBACK", "ExoPlayer playback error: ${error.message} (Code: ${error.errorCode}, ErrorName: ${error.errorCodeName})", error)
                Log.e("Media3Validation", "Playback Failure: ${error.localizedMessage}")
                try {
                    player.prepare()
                } catch (e: Exception) {
                    Log.e("USMAN_PLAYBACK", "Error recovering from player error", e)
                }
            }
        })

        // 2. PendingIntent to reopen MainActivity when notification is tapped
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 3. MediaSession wrapping the player for system/notification/lockscreen controls
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = exoPlayer
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        super.onDestroy()
    }
}
