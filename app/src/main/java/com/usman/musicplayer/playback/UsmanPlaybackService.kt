package com.usman.musicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
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

        // 1. HTTP DataSource with Diagnostic TransferListener
        val transferListener = object : androidx.media3.datasource.TransferListener {
            override fun onTransferInitializing(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {
            }
            override fun onTransferStart(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {
                Log.d("USMAN_PLAYBACK", "[Stage 9] HTTP Request Start: ${dataSpec.uri}")
            }
            override fun onBytesTransferred(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean, bytesTransferred: Int) {}
            override fun onTransferEnd(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {
                Log.d("USMAN_PLAYBACK", "[Stage 9] HTTP Request End: ${dataSpec.uri}")
                if (source is androidx.media3.datasource.DefaultHttpDataSource) {
                     // DefaultHttpDataSource doesn't expose response code easily here without extending.
                     // Keeping logs as is.
                }
            }
        }
        val baseHttpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(mapOf(
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.9",
                "Connection" to "keep-alive"
            ))
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)
            .setTransferListener(transferListener)

        // ResolvingDataSource dynamically injects domain-specific headers (e.g. Referer for JioSaavn)
        // without polluting cross-origin requests to other CDNs with unwanted Origin/Referer headers.
        val resolvingDataSourceFactory = androidx.media3.datasource.ResolvingDataSource.Factory(
            baseHttpDataSourceFactory,
            androidx.media3.datasource.ResolvingDataSource.Resolver { dataSpec ->
                val uri = dataSpec.uri
                val host = uri.host?.lowercase().orEmpty()
                val headers = mutableMapOf<String, String>()
                if (host.contains("saavn") || host.contains("jio")) {
                    headers["Referer"] = "https://www.jiosaavn.com/"
                } else if (host.contains("music.youtube.com")) {
                    headers["Referer"] = "https://music.youtube.com/"
                }

                val upgradedUri = if (uri.scheme == "http") {
                    uri.buildUpon().scheme("https").build()
                } else {
                    uri
                }

                var builder = dataSpec.buildUpon().setUri(upgradedUri)
                if (headers.isNotEmpty()) {
                    builder = builder.setHttpRequestHeaders(headers)
                }
                builder.build()
            }
        )

        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, resolvingDataSourceFactory)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        AudioEffectsManager.init(this)

        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(AudioEffectsManager.getAudioProcessor()))
                    .build()
            }
        }.setEnableDecoderFallback(true)

        // 2. Tune LoadControl for fast tap-to-play start (1s initial buffer) while keeping 50s headroom
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 1_000, 2_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 3. Build ExoPlayer with decoder fallback enabled for MediaCodec resource reclamation recovery
        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true 
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        exoPlayer = player
        currentAudioSessionId = player.audioSessionId
        if (currentAudioSessionId > 0) {
            AudioEffectsManager.attachAudioSession(this, currentAudioSessionId)
        }

        player.addListener(object : Player.Listener {
            private var lastFailedUri: Uri? = null
            private var failureCountForUri = 0

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                currentAudioSessionId = audioSessionId
                if (audioSessionId > 0) {
                    AudioEffectsManager.attachAudioSession(this@UsmanPlaybackService, audioSessionId)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                Log.d("USMAN_PLAYBACK", "[Stage 9] Playback State Transition: $stateName")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("USMAN_PLAYBACK", "[Stage 9] isPlaying: $isPlaying, playWhenReady: ${player.playWhenReady}")
            }

            override fun onPlayerError(error: PlaybackException) {
                val errorMsg = "ExoPlayer error: ${error.message} (Code: ${error.errorCode}, ${error.errorCodeName})"
                Log.w("USMAN_PLAYBACK", errorMsg)
                PlaybackErrorRepository.setError(errorMsg)

                val currentItem = player.currentMediaItem
                val uri = currentItem?.localConfiguration?.uri

                val isHttpError = error.cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS

                val isCodecError = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                        error.message?.contains("MediaCodec", ignoreCase = true) == true

                if (isHttpError) {
                    val httpCause = error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
                    val responseCode = httpCause?.responseCode ?: -1
                    Log.w("USMAN_PLAYBACK", "HTTP error $responseCode for URI: $uri")

                    // Guard against infinite loop for the same failed URI
                    if (uri != null && uri == lastFailedUri) {
                        failureCountForUri++
                        if (failureCountForUri >= 2) {
                            Log.e("USMAN_PLAYBACK", "Aborting ExoPlayer retry loop for permanently failing URI: $uri")
                            player.playWhenReady = false
                            return
                        }
                    } else {
                        lastFailedUri = uri
                        failureCountForUri = 1
                    }

                    // Recovery attempt: If 403 Forbidden on JioSaavn CDN quality URL (e.g. 320kbps), downgrade quality
                    if (uri != null && responseCode == 403) {
                        val uriStr = uri.toString()
                        val fallbackUriStr = when {
                            uriStr.contains("_320.mp4") -> uriStr.replace("_320.mp4", "_160.mp4")
                            uriStr.contains("_160.mp4") -> uriStr.replace("_160.mp4", "_96.mp4")
                            uriStr.startsWith("http://") -> uriStr.replace("http://", "https://")
                            else -> null
                        }

                        if (fallbackUriStr != null && fallbackUriStr != uriStr) {
                            Log.w("USMAN_PLAYBACK", "Retrying 403 with quality fallback: $fallbackUriStr")
                            val newMediaItem = currentItem.buildUpon().setUri(Uri.parse(fallbackUriStr)).build()
                            player.setMediaItem(newMediaItem)
                            player.prepare()
                            player.play()
                            return
                        }
                    }

                    // If cannot recover at service level, stop playWhenReady so we don't spam errors
                    player.playWhenReady = false
                    return
                }

                if (isCodecError) {
                    try {
                        val currentPos = player.currentPosition
                        val shouldPlayWhenReady = player.playWhenReady
                        player.prepare()
                        if (currentPos > 0) {
                            player.seekTo(currentPos)
                        }
                        if (shouldPlayWhenReady) {
                            player.play()
                        }
                    } catch (e: Exception) {
                        Log.e("USMAN_PLAYBACK", "Error recovering from codec error", e)
                    }
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
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
        AudioEffectsManager.releaseEffects()
        AudioSpectrumManager.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}
