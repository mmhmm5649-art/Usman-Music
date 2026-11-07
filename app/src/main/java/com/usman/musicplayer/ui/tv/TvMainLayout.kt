package com.usman.musicplayer.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.screens.PlaybackDiagnosticScreen
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import com.usman.musicplayer.viewmodel.MusicPlayerViewModel

/**
 * Root Composable for Android TV & Google TV devices.
 * Integrates left navigation rail with 10-foot TV content screens.
 */
@Composable
fun TvMainLayout(
    viewModel: MusicPlayerViewModel,
    uiState: MusicPlayerUiState,
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(TvDestination.HOME) }
    var isDiagnosticOpen by remember { mutableStateOf(false) }

    val homeFocusRequester = remember { FocusRequester() }

    // Request initial focus on Home button so D-pad works immediately without touchscreen
    LaunchedEffect(Unit) {
        try {
            homeFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // D-pad Back Button Navigation Handling
    BackHandler(enabled = isDiagnosticOpen) {
        isDiagnosticOpen = false
    }

    BackHandler(enabled = !isDiagnosticOpen && currentDestination != TvDestination.HOME) {
        currentDestination = TvDestination.HOME
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Navigation Sidebar
            TvNavigationRail(
                currentDestination = currentDestination,
                onDestinationSelected = { destination ->
                    currentDestination = destination
                },
                currentSong = uiState.currentSong,
                isPlaying = uiState.isPlaying,
                homeFocusRequester = homeFocusRequester
            )

            // Main TV Content Area
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = currentDestination,
                    transitionSpec = {
                        fadeIn().togetherWith(fadeOut())
                    },
                    label = "tvScreenTransition"
                ) { destination ->
                    when (destination) {
                        TvDestination.HOME -> TvHomeScreen(
                            uiState = uiState,
                            onPlaySong = { song: Song ->
                                viewModel.playSong(song)
                                currentDestination = TvDestination.NOW_PLAYING
                            }
                        )

                        TvDestination.SEARCH -> TvSearchScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onPlaySong = { song: Song ->
                                viewModel.playSong(song)
                                currentDestination = TvDestination.NOW_PLAYING
                            }
                        )

                        TvDestination.LIBRARY -> TvLibraryScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onPlaySong = { song: Song ->
                                viewModel.playSong(song)
                                currentDestination = TvDestination.NOW_PLAYING
                            }
                        )

                        TvDestination.NOW_PLAYING -> TvNowPlayingScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )

                        TvDestination.SETTINGS -> TvSettingsScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onOpenDiagnostics = { isDiagnosticOpen = true }
                        )
                    }
                }
            }
        }

        // Overlay: In-App Playback Diagnostics modal (accessible from TV Settings)
        if (isDiagnosticOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6030712))
            ) {
                PlaybackDiagnosticScreen(
                    onDismiss = { isDiagnosticOpen = false }
                )
            }
        }
    }
}
