package com.usman.musicplayer.extension

import com.usman.musicplayer.sdk.PlaybackType

enum class ExtensionType {
    CONTENT_RESOLVER,
    CONTENT_IMPORTER,
    LYRICS_PROVIDER,
    CHART_PROVIDER,
    COMMUNITY_CATALOG
}

data class ExtensionManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val iconUrl: String? = null,
    val homepageUrl: String? = null,
    val license: String = "Open Source",
    val permissions: List<String> = listOf("INTERNET", "STREAMING"),
    val supportedLanguages: List<String> = emptyList(),
    val playbackType: PlaybackType = PlaybackType.FULL,
    val isFullPlaybackSupported: Boolean = true,
    val type: ExtensionType = ExtensionType.CONTENT_RESOLVER,
    val downloadUrl: String? = null,
    val packageAsset: String? = null,
    val repositoryName: String? = null,
    val adapterId: String? = null,
    val sha256: String? = null,
    val category: String = "General"
) {
    val capabilityList: List<String>
        get() {
            val list = mutableListOf<String>()
            if (isFullPlaybackSupported && playbackType == PlaybackType.FULL) {
                list.add("Full-Length Audio Streaming")
            } else if (playbackType == PlaybackType.EXTERNAL_SERVICE) {
                list.add("Official App Handoff")
            } else {
                list.add("Catalog Search / Metadata Only")
            }
            if (permissions.contains("INTERNET")) list.add("REST API (HTTPS)")
            if (permissions.contains("STREAMING")) list.add("Direct Stream Resolver")
            if (supportedLanguages.isNotEmpty()) list.add("Languages: ${supportedLanguages.joinToString(", ")}")
            list.add("License: $license")
            return list
        }

    val capabilitySummary: String
        get() = capabilityList.joinToString(" • ")
}

data class DeclarativeSearchConfig(
    val urlTemplate: String,
    val httpMethod: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val tracksJsonPath: String = "",
    val idField: String = "id",
    val titleField: String = "title",
    val artistField: String = "artist",
    val albumField: String = "album",
    val durationField: String = "duration",
    val durationUnit: String = "seconds", // "seconds" or "milliseconds"
    val streamUrlField: String = "stream_url",
    val artworkUrlField: String = "artwork_url",
    val streamUrlTemplate: String? = null
)

data class DeclarativeProviderSpec(
    val manifest: ExtensionManifest,
    val searchConfig: DeclarativeSearchConfig
)

data class ExtensionPackage(
    val manifest: ExtensionManifest,
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = false,
    val isBuiltIn: Boolean = false,
    val installedVersion: String? = null,
    val latestVersion: String? = null,
    val installDate: Long = 0L,
    val lastUpdated: Long = 0L,
    val hasUpdate: Boolean = false,
    val errorMessage: String? = null,
    val isImported: Boolean = false,
    val originalFileName: String? = null,
    val packageFormat: String? = null,
    val isDownloading: Boolean = false
)

data class ExtensionRepository(
    val id: String,
    val name: String,
    val url: String,
    val description: String,
    val isEnabled: Boolean = true,
    val isOfficial: Boolean = false,
    val lastSyncTime: Long = 0L,
    val extensionCount: Int = 0
)

sealed class ExtensionValidationResult {
    data class Supported(
        val pkg: ExtensionPackage,
        val declarativeSpec: DeclarativeProviderSpec?,
        val formatName: String,
        val details: String,
        val customProvider: com.usman.musicplayer.sdk.MusicProvider? = null
    ) : ExtensionValidationResult()

    data class UnsupportedFormat(
        val filename: String,
        val formatName: String,
        val detectedProvider: String?,
        val detectedVersion: String?,
        val reason: String
    ) : ExtensionValidationResult()

    data class Invalid(
        val filename: String,
        val error: String
    ) : ExtensionValidationResult()

    data class GenuineEcho(
        val pkg: ExtensionPackage,
        val eapkFile: java.io.File,
        val details: String
    ) : ExtensionValidationResult()
}
