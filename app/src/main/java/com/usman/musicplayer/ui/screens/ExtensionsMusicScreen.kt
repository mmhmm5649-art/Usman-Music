package com.usman.musicplayer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.extension.ExtensionManager
import com.usman.musicplayer.extension.ExtensionPackage
import com.usman.musicplayer.extension.ExtensionValidationResult
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.sdk.MusicProvider
import com.usman.musicplayer.sdk.ProviderRegistry
import com.usman.musicplayer.ui.components.LiquidGlassPanel
import com.usman.musicplayer.ui.components.SongAlbumArtwork
import com.usman.musicplayer.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsMusicScreen(
    onPlaySong: (Song) -> Unit,
    onOpenPluginManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val installedExtensions by ExtensionManager.installedExtensions.collectAsState()
    val activeInstalled = installedExtensions.filter { it.isInstalled }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedPkg by remember { mutableStateOf<ExtensionPackage?>(null) }
    var isValidatingFile by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<ExtensionValidationResult?>(null) }
    var showAddRepoDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isValidatingFile = true
                val result = ExtensionManager.importExtensionFile(context, uri)
                isValidatingFile = false
                validationResult = result
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        if (selectedPkg == null) {
            // Extensions Home Section: Installed extensions, import EAPK, add repo, store
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Music Extensions",
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Discover & stream from genuine Echo providers",
                            color = SoftCyan,
                            fontSize = 12.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x221E2338))
                                .testTag("extensions_import_eapk_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = "Import EAPK",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { showAddRepoDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x221E2338))
                                .testTag("extensions_add_repo_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add GitHub Repository",
                                tint = NeonPurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    // Quick Action Banner
                    item {
                        LiquidGlassPanel(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenPluginManager() },
                            shape = RoundedCornerShape(18.dp),
                            backgroundColor = Color(0x257C3AED)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x44FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Store,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Extension Store & Repositories",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Browse GitHub & Bloomee community repositories",
                                        color = SoftCyan,
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = "INSTALLED EXTENSION MUSIC PROVIDERS",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    if (activeInstalled.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Extension,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "No Extensions Installed",
                                        color = TextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Import an EAPK or install from the Store",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    } else {
                        items(activeInstalled, key = { it.manifest.id }) { pkg ->
                            ExtensionMusicCard(
                                pkg = pkg,
                                onClick = { selectedPkg = pkg },
                                onToggleEnable = { enabled ->
                                    ExtensionManager.toggleExtension(pkg.manifest.id, enabled)
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // Extension Dedicated Music Browsing Screen
            ExtensionMusicBrowserScreen(
                pkg = selectedPkg!!,
                onBack = { selectedPkg = null },
                onPlaySong = onPlaySong
            )
        }

        // Add Repository Dialog
        if (showAddRepoDialog) {
            AddRepositoryDialog(
                onDismiss = { showAddRepoDialog = false },
                onAdd = { name, url ->
                    ExtensionManager.addRepository(name, url)
                    showAddRepoDialog = false
                }
            )
        }

        // Import Validation Result Dialog
        validationResult?.let { result ->
            ImportValidationDialog(
                result = result,
                onDismiss = { validationResult = null },
                onInstall = {
                    when (result) {
                        is ExtensionValidationResult.Supported -> {
                            ExtensionManager.installImportedExtension(result)
                        }
                        is ExtensionValidationResult.GenuineEcho -> {
                            ExtensionManager.installGenuineEchoExtension(context, result)
                        }
                        else -> {}
                    }
                    validationResult = null
                }
            )
        }
    }
}

@Composable
private fun ExtensionMusicCard(
    pkg: ExtensionPackage,
    onClick: () -> Unit,
    onToggleEnable: (Boolean) -> Unit
) {
    val manifest = pkg.manifest

    LiquidGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("extension_card_${manifest.id}"),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = Color(0x2214182E)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0284C7), Color(0xFF7C3AED))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = manifest.name.take(2).uppercase(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = manifest.name,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x3300E5FF))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "v${manifest.version}",
                            color = NeonCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = manifest.description,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap to browse music catalog & search",
                    color = SoftCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = pkg.isEnabled,
                onCheckedChange = onToggleEnable,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NeonCyan,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Color(0x33282C44)
                )
            )
        }
    }
}

@Composable
private fun ExtensionMusicBrowserScreen(
    pkg: ExtensionPackage,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit
) {
    val manifest = pkg.manifest
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("Telugu") }
    var songsList by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun executeSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        isLoading = true
        errorMessage = null

        coroutineScope.launch {
            try {
                val provider = ProviderRegistry.activeProviders.find {
                    it.metadata.id.equals(manifest.id, ignoreCase = true) ||
                    it.metadata.name.equals(manifest.name, ignoreCase = true)
                } ?: ProviderRegistry.providers.find { it.metadata.id.equals(manifest.id, ignoreCase = true) }

                if (provider != null) {
                    val result = provider.search(trimmed, 25)
                    if (result.isSuccess) {
                        songsList = result.getOrNull().orEmpty()
                        if (songsList.isEmpty()) {
                            errorMessage = "No music tracks found for '$trimmed'."
                        }
                    } else {
                        errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to fetch music from ${manifest.name}."
                    }
                } else {
                    errorMessage = "Provider '${manifest.name}' is not currently active or registered."
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "An error occurred during search."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(manifest.id) {
        executeSearch("Telugu Hits")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x221E2338))
                    .testTag("extension_browser_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = manifest.name,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "v${manifest.version} • ${manifest.author}",
                    color = SoftCyan,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search songs, artists, albums...", color = TextMuted, fontSize = 13.sp) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = Color(0x33FFFFFF),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = NeonCyan,
                focusedContainerColor = Color(0x1814182E),
                unfocusedContainerColor = Color(0x1214182E)
            ),
            trailingIcon = {
                IconButton(onClick = { executeSearch(searchQuery) }) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = NeonCyan)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("extension_search_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        val quickQueries = listOf("Telugu Hits", "Trending", "Classical", "Lofi", "Acoustic", "Global")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickQueries.take(4).forEach { q ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (searchQuery == q) Color(0xFF0284C7) else Color(0x221E2338))
                        .clickable {
                            searchQuery = q
                            executeSearch(q)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text = q, color = if (searchQuery == q) Color.White else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Searching ${manifest.name} catalog...", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
                errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(text = errorMessage ?: "Error", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { executeSearch(searchQuery) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Retry", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                songsList.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No songs found. Try a different search query.", color = TextMuted, fontSize = 13.sp)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        items(songsList, key = { it.id }) { song ->
                            ExtensionSongItem(
                                song = song,
                                onClick = { onPlaySong(song) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionSongItem(
    song: Song,
    onClick: () -> Unit
) {
    LiquidGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("extension_song_item_${song.id}"),
        shape = RoundedCornerShape(14.dp),
        backgroundColor = Color(0x1A14182E)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongAlbumArtwork(
                song = song,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp)),
                cornerRadius = 10.dp,
                showGlowBorder = false
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${song.artist} • ${song.album}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x2200E5FF))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(text = song.sourceProvider.orEmpty(), color = NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    if (song.isFullTrackVerified) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x2210B981))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(text = "Full Audio", color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x3300E5FF))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://raw.githubusercontent.com/") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add GitHub Repository", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Repository Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("Raw JSON URL") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, url) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
            ) {
                Text("Add Source", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = Color(0xFF161B2E)
    )
}

@Composable
private fun ImportValidationDialog(
    result: ExtensionValidationResult,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    val title = when (result) {
        is ExtensionValidationResult.Supported -> "Install Extension Package?"
        is ExtensionValidationResult.GenuineEcho -> "Install Genuine Echo EAPK?"
        is ExtensionValidationResult.UnsupportedFormat -> "Unsupported Format"
        is ExtensionValidationResult.Invalid -> "Invalid Package"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                val details = when (result) {
                    is ExtensionValidationResult.Supported -> result.details
                    is ExtensionValidationResult.GenuineEcho -> result.details
                    is ExtensionValidationResult.UnsupportedFormat -> result.reason
                    is ExtensionValidationResult.Invalid -> result.error
                }
                Text(details, color = TextSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = {
            if (result is ExtensionValidationResult.Supported || result is ExtensionValidationResult.GenuineEcho) {
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
                ) {
                    Text("Install", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextMuted)
            }
        },
        containerColor = Color(0xFF161B2E)
    )
}
