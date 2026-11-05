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

        // 1. Build ExoPlayer with automatic audio focus handling and audio attributes
        val player = ExoPlayer.Builder(this)
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

            override fun onPlayerError(error: PlaybackException) {
                Log.e("UsmanPlaybackService", "MediaCodec / Player error: ${error.message}", error)
                try {
                    player.prepare()
                } catch (e: Exception) {
                    Log.e("UsmanPlaybackService", "Error recovering from decoder release", e)
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
