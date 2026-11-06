package com.usman.musicplayer.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.usman.musicplayer.data.remote.YouTubeSearchResult
import com.usman.musicplayer.data.remote.YouTubeSnippet
import com.usman.musicplayer.data.remote.YouTubeThumbnail
import com.usman.musicplayer.data.remote.YouTubeThumbnails
import com.usman.musicplayer.data.remote.YouTubeVideoId
import com.usman.musicplayer.ui.components.GlassButtonSurface
import com.usman.musicplayer.ui.components.LiquidGlassPanel
import com.usman.musicplayer.ui.theme.*
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import kotlinx.coroutines.delay

// Curated featured videos with known embeddable YouTube IDs for immediate discovery and testing
private val FEATURED_MUSIC_VIDEOS = listOf(
    YouTubeSearchResult(
        id = YouTubeVideoId("4NRXx6U8ABQ"),
        snippet = YouTubeSnippet(
            title = "The Weeknd - Blinding Lights (Official Music Video)",
            channelTitle = "The Weeknd",
            thumbnails = YouTubeThumbnails(YouTubeThumbnail("https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg"))
        )
    ),
    YouTubeSearchResult(
        id = YouTubeVideoId("b1kbLwvqugk"),
        snippet = YouTubeSnippet(
            title = "Taylor Swift - Anti-Hero (Official Music Video)",
            channelTitle = "Taylor Swift",
            thumbnails = YouTubeThumbnails(YouTubeThumbnail("https://i.ytimg.com/vi/b1kbLwvqugk/hqdefault.jpg"))
        )
    ),
    YouTubeSearchResult(
        id = YouTubeVideoId("dQw4w9WgXcQ"),
        snippet = YouTubeSnippet(
            title = "Rick Astley - Never Gonna Give You Up (Official Music Video)",
            channelTitle = "Rick Astley",
            thumbnails = YouTubeThumbnails(YouTubeThumbnail("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"))
        )
    ),
    YouTubeSearchResult(
        id = YouTubeVideoId("JGwWNGJdvx8"),
        snippet = YouTubeSnippet(
            title = "Ed Sheeran - Shape of You (Official Music Video)",
            channelTitle = "Ed Sheeran",
            thumbnails = YouTubeThumbnails(YouTubeThumbnail("https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg"))
        )
    ),
    YouTubeSearchResult(
        id = YouTubeVideoId("7wtfhZwyrcc"),
        snippet = YouTubeSnippet(
            title = "Imagine Dragons - Believer (Official Music Video)",
            channelTitle = "Imagine Dragons",
            thumbnails = YouTubeThumbnails(YouTubeThumbnail("https://i.ytimg.com/vi/7wtfhZwyrcc/hqdefault.jpg"))
        )
    ),
    YouTubeSearchResult(
        id = YouTubeVideoId("TUVcZfQe-Kw"),
        snippet = YouTubeSnippet(
            title = "Dua Lipa - Levitating (Official Music Video)",
            channelTitle = "Dua Lipa",
            thumbnails = YouTubeThumbnails(YouTubeThumbnail("https://i.ytimg.com/vi/TUVcZfQe-Kw/hqdefault.jpg"))
        )
    )
)

@Composable
fun YouTubeVideosScreen(
    uiState: MusicPlayerUiState,
    onSearchVideos: (String) -> Unit,
    onVideoClick: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        // Ambient background glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E1B4B).copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header & Search
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Music Videos",
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Watch official videos from YouTube",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Modern Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search YouTube Music...", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = SoftCyan) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, null, tint = TextMuted)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedContainerColor = Color(0x1AFFFFFF),
                        unfocusedContainerColor = Color(0x0DFFFFFF)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        onSearchVideos(searchQuery)
                        focusManager.clearFocus()
                    })
                )
            }

            // Results List
            if (uiState.isYouTubeSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            } else if (uiState.youtubeSearchError != null) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.youtubeSearchError,
                            color = Color.Red.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Showing featured videos below:",
                            color = SoftCyan,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            val displayList = if (uiState.youtubeSearchResults.isNotEmpty()) {
                uiState.youtubeSearchResults
            } else {
                FEATURED_MUSIC_VIDEOS
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.youtubeSearchResults.isEmpty()) {
                    item {
                        Text(
                            text = "FEATURED MUSIC VIDEOS",
                            color = SoftCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                items(displayList) { video ->
                    val vid = video.id.videoId
                    VideoCard(
                        video = video,
                        onClick = {
                            if (!vid.isNullOrBlank()) {
                                onVideoClick(vid)
                            }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(180.dp)) }
            }
        }

        // Floating YouTube Player Modal
        if (uiState.selectedVideoId != null) {
            YouTubePlayerOverlay(
                videoId = uiState.selectedVideoId,
                onClose = { onVideoClick(null) }
            )
        }
    }
}

@Composable
private fun VideoCard(video: YouTubeSearchResult, onClick: () -> Unit) {
    LiquidGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0x1AFFFFFF)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                AsyncImage(
                    model = video.snippet.thumbnails.high?.url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Play Button Icon Overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("HD", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = video.snippet.title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.snippet.channelTitle,
                    color = SoftCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun YouTubePlayerOverlay(videoId: String, onClose: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val activity = context as? Activity
    val cleanVideoId = remember(videoId) { extractYouTubeVideoId(videoId) }

    var playerError by remember { mutableStateOf<String?>(null) }
    var isPlayerLoading by remember { mutableStateOf(true) }
    var playerStateText by remember { mutableStateOf("Initializing YouTube Player...") }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Intercept Back button: Exit fullscreen first if in fullscreen, else close overlay
    androidx.activity.compose.BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onClose()
        }
    }

    // Orientation management when toggling fullscreen
    DisposableEffect(isFullscreen) {
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Timeout Safeguard: If iframe initialization stalls for > 10 seconds, inform user and highlight Official App launch
    LaunchedEffect(cleanVideoId, retryTrigger) {
        playerError = null
        isPlayerLoading = true
        playerStateText = "Initializing USMAN Music Player Video Engine..."
        delay(10000L)
        if (isPlayerLoading && playerError == null) {
            Log.w("YouTubePlayer", "Iframe initialization delayed after 10s for video ID: $cleanVideoId")
            playerError = "In-app playback engine is taking longer than expected. You can wait or tap 'Watch on YouTube App' for instant playback."
            isPlayerLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isFullscreen) 1.0f else 0.92f))
            .clickable(enabled = !isFullscreen) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = if (isFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable(enabled = false) { }
            }
        ) {
            if (!isFullscreen) {
                // Top Action Row (when not in fullscreen)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://www.youtube.com/watch?v=$cleanVideoId")
                                ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                                try { context.startActivity(intent) } catch (e: Exception) { }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF0000),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open YouTube", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "ID: $cleanVideoId",
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButtonSurface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            backgroundColor = Color(0x33FFFFFF)
                        ) {
                            IconButton(onClick = { isFullscreen = true }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White
                                )
                            }
                        }

                        GlassButtonSurface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            backgroundColor = Color(0x33FFFFFF)
                        ) {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Default.Close, null, tint = Color.White)
                            }
                        }
                    }
                }
            }

            // Player Container
            Box(
                modifier = if (isFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                }.background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (cleanVideoId.isBlank()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color.Red, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Invalid or empty Video ID",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (playerError != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFF5252), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = playerError ?: "Playback Error",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    Log.d("YouTubePlayer", "User tapped Retry button for video ID: $cleanVideoId")
                                    retryTrigger++
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyan,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://www.youtube.com/watch?v=$cleanVideoId")
                                    ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                                    try { context.startActivity(intent) } catch (e: Exception) { }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF0000),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open App", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    key(cleanVideoId, retryTrigger) {
                        AndroidView(
                            factory = { ctx ->
                                Log.d("YouTubePlayer", "Creating YouTubePlayerView for video ID: $cleanVideoId (attempt #$retryTrigger)")
                                YouTubePlayerView(ctx).apply {
                                    enableAutomaticInitialization = false

                                    val options = IFramePlayerOptions.Builder()
                                        .controls(1)
                                        .autoplay(1)
                                        .rel(0)
                                        .ivLoadPolicy(3)
                                        .ccLoadPolicy(0)
                                        .build()

                                    initialize(
                                        youTubePlayerListener = object : AbstractYouTubePlayerListener() {
                                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                                Log.d("YouTubePlayer", "onReady received for video ID: $cleanVideoId")
                                                playerStateText = "Player Ready • Loading Video..."
                                                youTubePlayer.loadVideo(cleanVideoId, 0f)
                                            }

                                            override fun onError(
                                                youTubePlayer: YouTubePlayer,
                                                error: PlayerConstants.PlayerError
                                            ) {
                                                Log.e("YouTubePlayer", "onError received: $error for video ID: $cleanVideoId")
                                                playerError = when (error) {
                                                    PlayerConstants.PlayerError.VIDEO_NOT_FOUND ->
                                                        "Video not found ($cleanVideoId)"
                                                    PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER ->
                                                        "Embedded playback disabled for this video by owner"
                                                    PlayerConstants.PlayerError.INVALID_PARAMETER_IN_REQUEST ->
                                                        "Invalid video ID parameter ($cleanVideoId)"
                                                    PlayerConstants.PlayerError.HTML_5_PLAYER ->
                                                        "YouTube HTML5 player error"
                                                    else ->
                                                        "YouTube Playback Error: ${error.name}"
                                                }
                                                isPlayerLoading = false
                                            }

                                            override fun onStateChange(
                                                youTubePlayer: YouTubePlayer,
                                                state: PlayerConstants.PlayerState
                                            ) {
                                                Log.d("YouTubePlayer", "onStateChange: $state for video ID: $cleanVideoId")
                                                playerStateText = "State: ${state.name}"
                                                when (state) {
                                                    PlayerConstants.PlayerState.PLAYING,
                                                    PlayerConstants.PlayerState.PAUSED,
                                                    PlayerConstants.PlayerState.ENDED,
                                                    PlayerConstants.PlayerState.VIDEO_CUED -> {
                                                        Log.d("YouTubePlayer", "State reached $state for video ID: $cleanVideoId")
                                                        isPlayerLoading = false
                                                        playerError = null
                                                    }
                                                    PlayerConstants.PlayerState.BUFFERING -> {
                                                        playerStateText = "Buffering video stream..."
                                                    }
                                                    else -> {}
                                                }
                                            }
                                        },
                                        handleNetworkEvents = false,
                                        playerOptions = options
                                    )

                                    addFullscreenListener(object : com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener {
                                        override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                                            isFullscreen = true
                                        }

                                        override fun onExitFullscreen() {
                                            isFullscreen = false
                                        }
                                    })

                                    // Add lifecycle observer after initialization
                                    lifecycleOwner.lifecycle.addObserver(this)

                                    // Safely configure inner WebView settings WITHOUT overwriting library's internal WebViewClient bridge
                                    post {
                                        val childWebView = findWebViewInView(this)
                                        childWebView?.settings?.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            mediaPlaybackRequiresUserGesture = false
                                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            onRelease = { view ->
                                Log.d("YouTubePlayer", "Releasing YouTubePlayerView for video ID: $cleanVideoId")
                                val childWebView = findWebViewInView(view)
                                childWebView?.apply {
                                    try {
                                        stopLoading()
                                        loadUrl("about:blank")
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                                view.release()
                                lifecycleOwner.lifecycle.removeObserver(view)
                            }
                        )
                    }
                }

                if (isPlayerLoading && playerError == null && cleanVideoId.isNotBlank()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        CircularProgressIndicator(color = NeonCyan)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = playerStateText,
                            color = SoftCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Fullscreen Exit Overlay Button
                if (isFullscreen) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        IconButton(
                            onClick = { isFullscreen = false },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.FullscreenExit,
                                contentDescription = "Exit Fullscreen",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            if (!isFullscreen) {
                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Powered by USMAN Music Player Video Engine",
                    color = SoftCyan.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

private fun findWebViewInView(view: View): WebView? {
    if (view is WebView) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = findWebViewInView(view.getChildAt(i))
            if (child != null) return child
        }
    }
    return null
}

fun extractYouTubeVideoId(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.contains("v=") -> trimmed.substringAfter("v=").substringBefore("&")
        trimmed.contains("youtu.be/") -> trimmed.substringAfter("youtu.be/").substringBefore("?")
        trimmed.contains("embed/") -> trimmed.substringAfter("embed/").substringBefore("?")
        else -> trimmed
    }
}
