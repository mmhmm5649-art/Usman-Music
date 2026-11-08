package com.usman.musicplayer

import com.usman.musicplayer.data.repository.providers.InternetArchiveMusicProvider
import com.usman.musicplayer.data.repository.providers.JioSaavnHubExtensionProvider
import com.usman.musicplayer.extension.ExtensionManifest
import com.usman.musicplayer.extension.ExtensionPackage
import com.usman.musicplayer.extension.ExtensionRepository
import com.usman.musicplayer.extension.ExtensionType
import com.usman.musicplayer.sdk.MusicProvider
import com.usman.musicplayer.sdk.PlaybackType
import com.usman.musicplayer.sdk.ProviderMetadata
import com.usman.musicplayer.sdk.ProviderRegistry
import com.usman.musicplayer.sdk.Song
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.content.Context
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
class ExtensionEcosystemUnitTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testProviderRegistryDynamicManagement() {
        val testProvider = object : MusicProvider {
            override val metadata = ProviderMetadata(
                id = "test_custom_ext",
                name = "Test Custom Extension",
                version = "1.0.0",
                isFullPlaybackSupported = true,
                authRequired = false
            )

            override suspend fun search(query: String, limit: Int): Result<List<Song>> {
                return Result.success(emptyList())
            }

            override suspend fun getStreamUrl(song: Song): Result<String> {
                return Result.success("https://cdn.example.com/audio.mp3")
            }
        }

        // Register provider
        ProviderRegistry.registerProvider(testProvider, enabled = true)
        assertTrue(ProviderRegistry.providers.any { it.metadata.id == "test_custom_ext" })
        assertTrue(ProviderRegistry.activeProviders.any { it.metadata.id == "test_custom_ext" })

        // Toggle disable
        ProviderRegistry.setProviderEnabled("test_custom_ext", false)
        assertFalse(ProviderRegistry.isProviderEnabled("test_custom_ext"))
        assertFalse(ProviderRegistry.activeProviders.any { it.metadata.id == "test_custom_ext" })

        // Toggle enable
        ProviderRegistry.setProviderEnabled("test_custom_ext", true)
        assertTrue(ProviderRegistry.isProviderEnabled("test_custom_ext"))
        assertTrue(ProviderRegistry.activeProviders.any { it.metadata.id == "test_custom_ext" })

        // Test stream URL routing
        runBlocking {
            val song = Song(
                id = 999L,
                title = "Test Song",
                artist = "Test Artist",
                album = "Test Album",
                durationMs = 180000L,
                filePath = "https://cdn.example.com/audio.mp3",
                sourceProvider = "Test Custom Extension"
            )
            val streamResult = ProviderRegistry.getStreamUrl(song)
            assertTrue(streamResult.isSuccess)
            assertEquals("https://cdn.example.com/audio.mp3", streamResult.getOrNull())
        }

        // Unregister
        ProviderRegistry.unregisterProvider("test_custom_ext")
        assertFalse(ProviderRegistry.providers.any { it.metadata.id == "test_custom_ext" })
    }

    @Test
    fun testJioSaavnHubCompliantSearch() = runBlocking {
        val jioProvider = JioSaavnHubExtensionProvider()
        assertEquals("jiosaavn_hub_provider", jioProvider.metadata.id)
        assertFalse(jioProvider.metadata.isFullPlaybackSupported)

        val result = jioProvider.search("Pushpa", 5)
        assertTrue(result.isSuccess)
        val songs = result.getOrNull().orEmpty()
        assertTrue(songs.isNotEmpty())

        val pushpaSong = songs.first()
        assertEquals(PlaybackType.EXTERNAL_SERVICE, pushpaSong.playbackType)
        assertFalse(pushpaSong.isStreamable)
        assertTrue(pushpaSong.externalUrl?.contains("jiosaavn.com") == true)
    }

    @Test
    fun testExtensionModels() {
        val manifest = ExtensionManifest(
            id = "usman.ext.archiveorg",
            name = "Internet Archive Audio",
            version = "1.0.0",
            author = "Internet Archive",
            description = "Telugu & classical broadcasts",
            supportedLanguages = listOf("Telugu", "Hindi"),
            playbackType = PlaybackType.FULL,
            type = ExtensionType.CONTENT_RESOLVER
        )

        val pkg = ExtensionPackage(
            manifest = manifest,
            isInstalled = true,
            isEnabled = true,
            installedVersion = "1.0.0",
            latestVersion = "1.1.0",
            hasUpdate = true
        )

        assertEquals("Internet Archive Audio", pkg.manifest.name)
        assertTrue(pkg.isInstalled)
        assertTrue(pkg.isEnabled)
        assertTrue(pkg.hasUpdate)
        assertEquals("1.1.0", pkg.latestVersion)
    }

    @Test
    fun testRepositoryModel() {
        val repo = ExtensionRepository(
            id = "repo_bloomee",
            name = "BloomeeTunes Ecosystem",
            url = "https://hemantkarya.github.io/BloomeeTunes/repositories.json",
            description = "Official Bloomee package ecosystem",
            isEnabled = true,
            isOfficial = true,
            extensionCount = 12
        )

        assertEquals("repo_bloomee", repo.id)
        assertTrue(repo.isOfficial)
        assertEquals(12, repo.extensionCount)
    }

    @Test
    fun testValidateDeclarativeJsonFile() {
        val validJson = """
            {
              "manifest": {
                "id": "ext.local.test",
                "name": "Local Test Music",
                "version": "1.0.0",
                "author": "Community",
                "description": "Test declarative music provider",
                "supportedLanguages": ["Telugu", "Hindi"],
                "playbackType": "FULL"
              },
              "searchConfig": {
                "endpointUrl": "https://api.example.com/search?q={query}",
                "tracksJsonPath": "results",
                "idField": "id",
                "titleField": "name",
                "artistField": "singer",
                "albumField": "collection",
                "durationField": "length_seconds",
                "durationUnit": "SECONDS",
                "artworkUrlField": "thumb",
                "streamUrlField": "audio_url"
              }
            }
        """.trimIndent()

        val bytes = validJson.toByteArray(Charsets.UTF_8)
        val result = com.usman.musicplayer.extension.ExtensionFileValidator.validateFile(
            context = context,
            filename = "provider.json",
            fileSizeBytes = bytes.size.toLong(),
            inputStream = java.io.ByteArrayInputStream(bytes)
        )

        assertTrue(result is com.usman.musicplayer.extension.ExtensionValidationResult.Supported)
        val supported = result as com.usman.musicplayer.extension.ExtensionValidationResult.Supported
        assertEquals("ext.local.test", supported.pkg.manifest.id)
        assertEquals("Local Test Music", supported.pkg.manifest.name)
        assertEquals(com.usman.musicplayer.sdk.PlaybackType.FULL, supported.pkg.manifest.playbackType)
    }

    @Test
    fun testValidateDexBytecodeFileIsBlocked() {
        // DEX magic: 'd', 'e', 'x', '\n'
        val dexBytes = byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x39, 0x00)
        val result = com.usman.musicplayer.extension.ExtensionFileValidator.validateFile(
            context = context,
            filename = "plugin.dex",
            fileSizeBytes = dexBytes.size.toLong(),
            inputStream = java.io.ByteArrayInputStream(dexBytes)
        )

        assertTrue(result is com.usman.musicplayer.extension.ExtensionValidationResult.UnsupportedFormat)
        val unsupported = result as com.usman.musicplayer.extension.ExtensionValidationResult.UnsupportedFormat
        assertTrue(unsupported.reason.contains("DCL") || unsupported.reason.contains("bytecode") || unsupported.reason.contains("policy"))
    }

    @Test
    fun testValidateWasmFileIsBlocked() {
        // WASM magic: 0x00, 'a', 's', 'm'
        val wasmBytes = byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00)
        val result = com.usman.musicplayer.extension.ExtensionFileValidator.validateFile(
            context = context,
            filename = "plugin.wasm",
            fileSizeBytes = wasmBytes.size.toLong(),
            inputStream = java.io.ByteArrayInputStream(wasmBytes)
        )

        assertTrue(result is com.usman.musicplayer.extension.ExtensionValidationResult.UnsupportedFormat)
        val unsupported = result as com.usman.musicplayer.extension.ExtensionValidationResult.UnsupportedFormat
        assertTrue(unsupported.reason.contains("WASM") || unsupported.reason.contains("WebAssembly"))
    }

    @Test
    fun testValidateBloomeeBexFileIsBlockedWithExplanation() {
        // Zstandard magic: 0x28, 0xB5, 0x2F, 0xFD
        val bexBytes = byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte(), 0x00, 0x00)
        val result = com.usman.musicplayer.extension.ExtensionFileValidator.validateFile(
            context = context,
            filename = "bloomee_plugin.bex",
            fileSizeBytes = bexBytes.size.toLong(),
            inputStream = java.io.ByteArrayInputStream(bexBytes)
        )

        assertTrue(result is com.usman.musicplayer.extension.ExtensionValidationResult.UnsupportedFormat)
        val unsupported = result as com.usman.musicplayer.extension.ExtensionValidationResult.UnsupportedFormat
        assertTrue(unsupported.reason.contains("Bloomee") || unsupported.reason.contains("Zstandard") || unsupported.reason.contains("WASM"))
    }

    @Test
    fun testValidateApkWithEmbeddedManifestIsSupported() {
        // Create an in-memory ZIP / APK containing assets/manifest.json
        val manifestJson = """
            {
              "manifest": {
                "id": "apk.provider.tseries",
                "name": "APK T-Series Adapter",
                "version": "2.1.0",
                "author": "Community APK",
                "description": "Embedded adapter from APK archive",
                "supportedLanguages": ["Hindi", "Punjabi"],
                "playbackType": "EXTERNAL_SERVICE",
                "externalHandoffPackage": "com.tseries.music"
              },
              "searchConfig": {
                "endpointUrl": "https://api.example.com/search?q={query}",
                "tracksJsonPath": "data.tracks",
                "idField": "track_id",
                "titleField": "track_name",
                "artistField": "artist_name"
              }
            }
        """.trimIndent()

        val byteOut = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(byteOut).use { zip ->
            // Add classes.dex entry as well to simulate real APK
            val dexEntry = java.util.zip.ZipEntry("classes.dex")
            zip.putNextEntry(dexEntry)
            zip.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A))
            zip.closeEntry()

            // Add embedded declarative manifest
            val manifestEntry = java.util.zip.ZipEntry("assets/manifest.json")
            zip.putNextEntry(manifestEntry)
            zip.write(manifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val apkBytes = byteOut.toByteArray()
        val result = com.usman.musicplayer.extension.ExtensionFileValidator.validateFile(
            context = context,
            filename = "music_plugin.apk",
            fileSizeBytes = apkBytes.size.toLong(),
            inputStream = java.io.ByteArrayInputStream(apkBytes)
        )

        assertTrue(result is com.usman.musicplayer.extension.ExtensionValidationResult.Supported)
        val supported = result as com.usman.musicplayer.extension.ExtensionValidationResult.Supported
        assertEquals("apk.provider.tseries", supported.pkg.manifest.id)
        assertEquals("APK T-Series Adapter", supported.pkg.manifest.name)
        assertEquals(com.usman.musicplayer.sdk.PlaybackType.EXTERNAL_SERVICE, supported.pkg.manifest.playbackType)
    }

    @Test
    fun testValidateApkWithoutManifestIsRejectedSafely() {
        // Create an APK with only classes.dex and AndroidManifest.xml
        val byteOut = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(byteOut).use { zip ->
            val dexEntry = java.util.zip.ZipEntry("classes.dex")
            zip.putNextEntry(dexEntry)
            zip.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A))
            zip.closeEntry()

            val xmlEntry = java.util.zip.ZipEntry("AndroidManifest.xml")
            zip.putNextEntry(xmlEntry)
            zip.write("binary xml".toByteArray())
            zip.closeEntry()
        }

        val apkBytes = byteOut.toByteArray()
        val result = com.usman.musicplayer.extension.ExtensionFileValidator.validateFile(
            context = context,
            filename = "unsupported_pure_binary.apk",
            fileSizeBytes = apkBytes.size.toLong(),
            inputStream = java.io.ByteArrayInputStream(apkBytes)
        )

        assertTrue(result is com.usman.musicplayer.extension.ExtensionValidationResult.UnsupportedFormat)
        val unsupported = result as com.usman.musicplayer.extension.ExtensionValidationResult.UnsupportedFormat
        assertTrue(unsupported.reason.contains("DEX") || unsupported.reason.contains("manifest") || unsupported.reason.contains("DCL"))
    }

    @Test
    fun testValidateEpkZipPackageWithEntryPoint() {
        val epkManifestJson = """
            {
              "id": "epk.telugu.archive",
              "name": "Telugu Audio Archive",
              "version": "1.0.0",
              "author": "Open Audio Community",
              "description": "Authentic Telugu and regional music provider",
              "playbackType": "FULL",
              "supportedLanguages": ["Telugu", "Hindi", "Tamil"],
              "entryPoint": "endpoints.json"
            }
        """.trimIndent()

        val endpointsJson = """
            {
              "searchConfig": {
                "urlTemplate": "https://archive.org/advancedsearch.php?q={query}&output=json",
                "tracksJsonPath": "response.docs",
                "idField": "identifier",
                "titleField": "title",
                "artistField": "creator",
                "albumField": "collection",
                "durationField": "length",
                "durationUnit": "seconds",
                "streamUrlTemplate": "https://archive.org/download/{id}/{id}.mp3"
              }
            }
        """.trimIndent()

        val byteOut = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(byteOut).use { zip ->
            val manifestEntry = java.util.zip.ZipEntry("epk.json")
            zip.putNextEntry(manifestEntry)
            zip.write(epkManifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val entryPointEntry = java.util.zip.ZipEntry("endpoints.json")
            zip.putNextEntry(entryPointEntry)
            zip.write(endpointsJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val iconEntry = java.util.zip.ZipEntry("icon.png")
            zip.putNextEntry(iconEntry)
            zip.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
            zip.closeEntry()
        }

        val epkBytes = byteOut.toByteArray()
        val result = com.usman.musicplayer.extension.ExtensionFileValidator.validateFile(
            context = context,
            filename = "telugu_archive.epk",
            fileSizeBytes = epkBytes.size.toLong(),
            inputStream = java.io.ByteArrayInputStream(epkBytes)
        )

        assertTrue("Expected Supported result for valid .epk package", result is com.usman.musicplayer.extension.ExtensionValidationResult.Supported)
        val supported = result as com.usman.musicplayer.extension.ExtensionValidationResult.Supported
        assertEquals("epk.telugu.archive", supported.pkg.manifest.id)
        assertEquals("Telugu Audio Archive", supported.pkg.manifest.name)
        assertEquals(com.usman.musicplayer.sdk.PlaybackType.FULL, supported.pkg.manifest.playbackType)
        assertTrue(supported.formatName.contains("EPK"))

        val spec = supported.declarativeSpec
        assertTrue("Declarative spec should be resolved via entryPoint", spec != null)
        assertEquals("https://archive.org/download/{id}/{id}.mp3", spec!!.searchConfig.streamUrlTemplate)
    }

    @Test
    fun testEpkMusicProviderSearchAndStreamResolution() = runBlocking {
        val manifest = com.usman.musicplayer.extension.ExtensionManifest(
            id = "epk.telugu.test",
            name = "Telugu Classical Streamer",
            version = "1.0.0",
            author = "USMAN EPK",
            description = "Telugu stream resolver",
            playbackType = PlaybackType.FULL,
            isFullPlaybackSupported = true
        )

        val searchConfig = com.usman.musicplayer.extension.DeclarativeSearchConfig(
            urlTemplate = "https://example.com/search?q={query}",
            streamUrlTemplate = "https://archive.org/download/{id}/{id}.mp3"
        )

        val spec = com.usman.musicplayer.extension.DeclarativeProviderSpec(manifest, searchConfig)
        val epkProvider = com.usman.musicplayer.data.repository.providers.DeclarativeMusicProvider(spec)

        // Register EPK provider
        ProviderRegistry.registerProvider(epkProvider, enabled = true)
        assertTrue(ProviderRegistry.activeProviders.any { it.metadata.id == "epk.telugu.test" })

        // Full-length stream resolution verification
        val testSong = Song(
            id = 778899L,
            title = "Annamayya Keerthana",
            artist = "Balamuralikrishna",
            album = "Telugu Classical Gems",
            durationMs = 312000L,
            filePath = "",
            sourceProvider = "Telugu Classical Streamer",
            playbackType = PlaybackType.FULL,
            isStreamable = true,
            isFullTrackVerified = true
        )

        val streamUrlResult = ProviderRegistry.getStreamUrl(testSong)
        assertTrue("Stream URL should resolve successfully for EPK song", streamUrlResult.isSuccess)
        val resolvedUrl = streamUrlResult.getOrNull()
        assertEquals("https://archive.org/download/778899/778899.mp3", resolvedUrl)

        // Verify Media3 MediaItem construction compatibility
        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setMediaId(testSong.id.toString())
            .setUri(android.net.Uri.parse(resolvedUrl))
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(testSong.title)
                    .setArtist(testSong.artist)
                    .build()
            )
            .build()

        assertEquals("778899", mediaItem.mediaId)
        assertEquals("https://archive.org/download/778899/778899.mp3", mediaItem.localConfiguration?.uri.toString())

        // Clean up
        ProviderRegistry.unregisterProvider("epk.telugu.test")
    }

    @Test
    fun testValidateCorruptedEpkReturnsInvalid() {
        val corruptedBytes = "corrupted non-zip content".toByteArray(Charsets.UTF_8)
        val result = com.usman.musicplayer.extension.ExtensionFileValidator.validateFile(
            context = context,
            filename = "broken.epk",
            fileSizeBytes = corruptedBytes.size.toLong(),
            inputStream = java.io.ByteArrayInputStream(corruptedBytes)
        )

        assertTrue(result is com.usman.musicplayer.extension.ExtensionValidationResult.Invalid)
    }

    @Test
    fun testValidatePureDexEpkIsBlockedSafely() {
        val byteOut = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(byteOut).use { zip ->
            val dexEntry = java.util.zip.ZipEntry("classes.dex")
            zip.putNextEntry(dexEntry)
            zip.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A))
            zip.closeEntry()
        }

        val epkBytes = byteOut.toByteArray()
        val result = com.usman.musicplayer.extension.ExtensionFileValidator.validateFile(
            context = context,
            filename = "binary_bytecode_only.epk",
            fileSizeBytes = epkBytes.size.toLong(),
            inputStream = java.io.ByteArrayInputStream(epkBytes)
        )

        assertTrue(result is com.usman.musicplayer.extension.ExtensionValidationResult.UnsupportedFormat)
        val unsupported = result as com.usman.musicplayer.extension.ExtensionValidationResult.UnsupportedFormat
        assertTrue(unsupported.reason.contains("DCL") || unsupported.reason.contains("DEX") || unsupported.reason.contains("bytecode"))
    }

    @Test
    fun testValidateEapkWithEmbeddedDeclarativeManifest() {
        val eapkManifestJson = """
            {
              "id": "echo.provider.teluguhits",
              "name": "Telugu Hits Provider",
              "version": "1.2.0",
              "author": "Echo Community",
              "description": "Telugu stream resolver for Echo EAPK ecosystem",
              "playbackType": "FULL",
              "supportedLanguages": ["Telugu", "Hindi"],
              "searchConfig": {
                "urlTemplate": "https://api.example.com/search?q={query}",
                "tracksJsonPath": "results",
                "idField": "song_id",
                "titleField": "song_name",
                "artistField": "singer",
                "albumField": "movie",
                "durationField": "seconds",
                "durationUnit": "seconds",
                "streamUrlTemplate": "https://cdn.example.com/stream/{id}.mp3"
              }
            }
        """.trimIndent()

        val byteOut = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(byteOut).use { zip ->
            // AndroidManifest.xml
            val manifestEntry = java.util.zip.ZipEntry("AndroidManifest.xml")
            zip.putNextEntry(manifestEntry)
            zip.write("""<?xml version="1.0" encoding="utf-8"?><manifest package="echo.brahmkshatriya.ytm"/>""".toByteArray())
            zip.closeEntry()

            // classes.dex
            val dexEntry = java.util.zip.ZipEntry("classes.dex")
            zip.putNextEntry(dexEntry)
            zip.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A))
            zip.closeEntry()

            // assets/manifest.json
            val assetEntry = java.util.zip.ZipEntry("assets/manifest.json")
            zip.putNextEntry(assetEntry)
            zip.write(eapkManifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val eapkBytes = byteOut.toByteArray()
        val result = com.usman.musicplayer.extension.ExtensionFileValidator.validateFile(
            context = context,
            filename = "telugu_extension.eapk",
            fileSizeBytes = eapkBytes.size.toLong(),
            inputStream = java.io.ByteArrayInputStream(eapkBytes)
        )

        assertTrue("Expected Supported for EAPK with declarative manifest", result is com.usman.musicplayer.extension.ExtensionValidationResult.Supported)
        val supported = result as com.usman.musicplayer.extension.ExtensionValidationResult.Supported
        assertEquals("echo.provider.teluguhits", supported.pkg.manifest.id)
        assertEquals("Telugu Hits Provider", supported.pkg.manifest.name)
        assertEquals(com.usman.musicplayer.sdk.PlaybackType.FULL, supported.pkg.manifest.playbackType)
        assertTrue(supported.formatName.contains("EAPK"))

        val spec = supported.declarativeSpec
        assertTrue("Declarative spec should be populated", spec != null)
        assertEquals("https://cdn.example.com/stream/{id}.mp3", spec!!.searchConfig.streamUrlTemplate)
    }

    @Test
    fun testUniversalEapkHostSupportsAnyUnknownExtensionPackage() {
        val byteOut = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(byteOut).use { zip ->
            val manifestEntry = java.util.zip.ZipEntry("AndroidManifest.xml")
            zip.putNextEntry(manifestEntry)
            zip.write("""<?xml version="1.0" encoding="utf-8"?><manifest package="com.unknown.randomapp"/>""".toByteArray())
            zip.closeEntry()

            val dexEntry = java.util.zip.ZipEntry("classes.dex")
            zip.putNextEntry(dexEntry)
            zip.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A))
            zip.closeEntry()
        }

        val eapkBytes = byteOut.toByteArray()
        val result = com.usman.musicplayer.extension.ExtensionFileValidator.validateFile(
            context = context,
            filename = "unknown_app.eapk",
            fileSizeBytes = eapkBytes.size.toLong(),
            inputStream = java.io.ByteArrayInputStream(eapkBytes)
        )

        assertTrue("Expected Supported via Universal Echo Adapter for any unknown EAPK", result is com.usman.musicplayer.extension.ExtensionValidationResult.Supported)
        val supported = result as com.usman.musicplayer.extension.ExtensionValidationResult.Supported
        assertEquals("com.unknown.randomapp", supported.pkg.manifest.id)
        assertTrue(supported.customProvider != null)
    }

    @Test
    fun testValidateCorruptedEapkReturnsInvalid() {
        val corruptedBytes = "corrupted non-zip content".toByteArray(Charsets.UTF_8)
        val result = com.usman.musicplayer.extension.ExtensionFileValidator.validateFile(
            context = context,
            filename = "broken.eapk",
            fileSizeBytes = corruptedBytes.size.toLong(),
            inputStream = java.io.ByteArrayInputStream(corruptedBytes)
        )

        assertTrue(result is com.usman.musicplayer.extension.ExtensionValidationResult.Invalid)
    }

    @Test
    fun testExecuteEapkNativeAdapterWorkflow() = runBlocking {
        // Simulate importing Youtube_music-ba0fa73.eapk package
        val byteOut = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(byteOut).use { zip ->
            val manifestEntry = java.util.zip.ZipEntry("AndroidManifest.xml")
            zip.putNextEntry(manifestEntry)
            zip.write("""<?xml version="1.0" encoding="utf-8"?><manifest package="echo.brahmkshatriya.ytm"/>""".toByteArray())
            zip.closeEntry()

            val dexEntry = java.util.zip.ZipEntry("classes.dex")
            zip.putNextEntry(dexEntry)
            zip.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A))
            zip.closeEntry()
        }

        val eapkBytes = byteOut.toByteArray()
        val result = com.usman.musicplayer.extension.ExtensionFileValidator.validateFile(
            context = context,
            filename = "Youtube_music-ba0fa73.eapk",
            fileSizeBytes = eapkBytes.size.toLong(),
            inputStream = java.io.ByteArrayInputStream(eapkBytes)
        )

        assertTrue("Expected Supported via Native Echo Adapter", result is com.usman.musicplayer.extension.ExtensionValidationResult.Supported)
        val supported = result as com.usman.musicplayer.extension.ExtensionValidationResult.Supported
        assertEquals("echo.brahmkshatriya.ytm", supported.pkg.manifest.id)
        assertTrue(supported.customProvider != null)

        // Test installation and execution workflow
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.usman.musicplayer.extension.ExtensionManager.init(context)
        com.usman.musicplayer.extension.ExtensionManager.installImportedExtension(supported)

        assertTrue(ProviderRegistry.activeProviders.any { it.metadata.id == "echo.brahmkshatriya.ytm" })

        // Test search execution through the installed native adapter provider
        val provider = ProviderRegistry.activeProviders.find { it.metadata.id == "echo.brahmkshatriya.ytm" }
        assert(provider != null)
        val searchResult = provider!!.search("Pushpa 2", 3)
        assertTrue(searchResult.isSuccess)
        val songs = searchResult.getOrNull().orEmpty()
        assertTrue(songs.isNotEmpty())
        assertEquals(PlaybackType.EXTERNAL_SERVICE, songs.first().playbackType)
        assertTrue(songs.first().externalUrl?.contains("youtube.com") == true)

        // Clean up
        ProviderRegistry.unregisterProvider("echo.brahmkshatriya.ytm")
    }

    @Test
    fun testUniversalEapkExtensionHostWorkflow() = runBlocking {
        // Test 1: Import Spotify extension package
        val byteOut1 = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(byteOut1).use { zip ->
            val manifestEntry = java.util.zip.ZipEntry("AndroidManifest.xml")
            zip.putNextEntry(manifestEntry)
            zip.write("""<?xml version="1.0" encoding="utf-8"?><manifest package="com.spotify.extension"/>""".toByteArray())
            zip.closeEntry()
            val dexEntry = java.util.zip.ZipEntry("classes.dex")
            zip.putNextEntry(dexEntry)
            zip.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A))
            zip.closeEntry()
        }

        val result1 = com.usman.musicplayer.extension.ExtensionFileValidator.validateFile(
            context = context,
            filename = "spotify-extension.eapk",
            fileSizeBytes = byteOut1.size().toLong(),
            inputStream = java.io.ByteArrayInputStream(byteOut1.toByteArray())
        )

        assertTrue(result1 is com.usman.musicplayer.extension.ExtensionValidationResult.Supported)
        val supp1 = result1 as com.usman.musicplayer.extension.ExtensionValidationResult.Supported
        assertEquals("com.spotify.extension", supp1.pkg.manifest.id)
        assertTrue(supp1.customProvider != null)

        // Test 2: Import SoundCloud extension package from a different developer
        val byteOut2 = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(byteOut2).use { zip ->
            val manifestEntry = java.util.zip.ZipEntry("AndroidManifest.xml")
            zip.putNextEntry(manifestEntry)
            zip.write("""<?xml version="1.0" encoding="utf-8"?><manifest package="com.soundcloud.extension"/>""".toByteArray())
            zip.closeEntry()
            val dexEntry = java.util.zip.ZipEntry("classes.dex")
            zip.putNextEntry(dexEntry)
            zip.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A))
            zip.closeEntry()
        }

        val result2 = com.usman.musicplayer.extension.ExtensionFileValidator.validateFile(
            context = context,
            filename = "soundcloud-extension.eapk",
            fileSizeBytes = byteOut2.size().toLong(),
            inputStream = java.io.ByteArrayInputStream(byteOut2.toByteArray())
        )

        assertTrue(result2 is com.usman.musicplayer.extension.ExtensionValidationResult.Supported)
        val supp2 = result2 as com.usman.musicplayer.extension.ExtensionValidationResult.Supported
        assertEquals("com.soundcloud.extension", supp2.pkg.manifest.id)
        assertTrue(supp2.customProvider != null)

        // Register both universal extension providers
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.usman.musicplayer.extension.ExtensionManager.init(context)
        com.usman.musicplayer.extension.ExtensionManager.installImportedExtension(supp1)
        com.usman.musicplayer.extension.ExtensionManager.installImportedExtension(supp2)

        assertTrue(ProviderRegistry.activeProviders.any { it.metadata.id == "com.spotify.extension" })
        assertTrue(ProviderRegistry.activeProviders.any { it.metadata.id == "com.soundcloud.extension" })

        // Clean up
        ProviderRegistry.unregisterProvider("com.spotify.extension")
        ProviderRegistry.unregisterProvider("com.soundcloud.extension")
    }

    @Test
    fun testDeclarativeMusicProviderRealHttpMockWebServer() = runBlocking {
        // Spin up a real HTTP local server using MockWebServer
        val server = MockWebServer()
        val mockJsonResponse = """
            {
              "response": {
                "docs": [
                  {
                    "identifier": "telugu_song_101",
                    "title": "Samayam Telugu Hit",
                    "creator": "Anirudh",
                    "collection": "Movie Melodies",
                    "length": 240,
                    "stream_url": "https://archive.org/download/telugu_song_101/telugu_song_101.mp3"
                  }
                ]
              }
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mockJsonResponse)
        )
        server.start()

        val baseUrl = server.url("/search?q={query}").toString()

        val manifest = ExtensionManifest(
            id = "test.ext.mockhttp",
            name = "Mock HTTP Provider",
            version = "1.0.0",
            author = "Test Suite",
            description = "Real HTTP MockWebServer integration test",
            playbackType = PlaybackType.FULL,
            isFullPlaybackSupported = true
        )

        val searchConfig = com.usman.musicplayer.extension.DeclarativeSearchConfig(
            urlTemplate = baseUrl,
            tracksJsonPath = "response.docs",
            idField = "identifier",
            titleField = "title",
            artistField = "creator",
            albumField = "collection",
            durationField = "length",
            durationUnit = "seconds",
            streamUrlField = "stream_url",
            streamUrlTemplate = "https://archive.org/download/{id}/{id}.mp3"
        )

        val spec = com.usman.musicplayer.extension.DeclarativeProviderSpec(manifest, searchConfig)
        val provider = com.usman.musicplayer.data.repository.providers.DeclarativeMusicProvider(spec)

        // Execute real search over network socket to local MockWebServer
        val result = provider.search("Samayam", 5)
        assertTrue("Real HTTP search should succeed", result.isSuccess)

        val songs = result.getOrNull().orEmpty()
        assertEquals(1, songs.size)
        val song = songs.first()
        assertEquals("Samayam Telugu Hit", song.title)
        assertEquals("Anirudh", song.artist)
        assertEquals(240000L, song.durationMs)

        // Verify request received by MockWebServer
        val recordedRequest = server.takeRequest()
        assertTrue(recordedRequest.path?.contains("/search?q=Samayam") == true)
        assertEquals("GET", recordedRequest.method)
        assertNotNull(recordedRequest.getHeader("User-Agent"))

        // Stream URL resolution test
        val streamResult = provider.getStreamUrl(song)
        assertTrue(streamResult.isSuccess)
        assertEquals("https://archive.org/download/telugu_song_101/telugu_song_101.mp3", streamResult.getOrNull())

        server.shutdown()
    }

    @Test
    fun testInsecureHttpEndpointBlocked() = runBlocking {
        val manifest = ExtensionManifest(
            id = "test.ext.insecure",
            name = "Insecure Provider",
            version = "1.0.0",
            author = "Test",
            description = "Insecure HTTP test",
            playbackType = PlaybackType.FULL
        )

        val searchConfig = com.usman.musicplayer.extension.DeclarativeSearchConfig(
            urlTemplate = "http://insecure-external-domain.com/search?q={query}"
        )

        val spec = com.usman.musicplayer.extension.DeclarativeProviderSpec(manifest, searchConfig)
        val provider = com.usman.musicplayer.data.repository.providers.DeclarativeMusicProvider(spec)

        val result = provider.search("test", 5)
        assertTrue("Insecure HTTP endpoint should fail with security exception", result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun testExtensionCapabilityReporting() {
        val manifest = ExtensionManifest(
            id = "ext.capability.test",
            name = "Capability Test Provider",
            version = "2.0.0",
            author = "USMAN",
            description = "Capability test",
            license = "MIT",
            permissions = listOf("INTERNET", "STREAMING"),
            supportedLanguages = listOf("Telugu", "Hindi"),
            playbackType = PlaybackType.FULL,
            isFullPlaybackSupported = true
        )

        val capabilities = manifest.capabilityList
        assertTrue(capabilities.contains("Full-Length Audio Streaming"))
        assertTrue(capabilities.contains("REST API (HTTPS)"))
        assertTrue(capabilities.contains("Direct Stream Resolver"))
        assertTrue(capabilities.contains("Languages: Telugu, Hindi"))
        assertTrue(capabilities.contains("License: MIT"))
        assertTrue(manifest.capabilitySummary.contains("Full-Length Audio Streaming"))
    }

    @Test
    fun testExtensionLifecyclePersistenceDisableUninstall() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.usman.musicplayer.extension.ExtensionManager.init(context)

        val manifest = ExtensionManifest(
            id = "ext.lifecycle.test",
            name = "Lifecycle Provider",
            version = "1.0.0",
            author = "Tester",
            description = "Lifecycle test",
            playbackType = PlaybackType.FULL,
            isFullPlaybackSupported = true
        )

        val pkg = ExtensionPackage(
            manifest = manifest,
            isInstalled = true,
            isEnabled = true,
            installedVersion = "1.0.0",
            isImported = true
        )

        val supp = com.usman.musicplayer.extension.ExtensionValidationResult.Supported(
            pkg = pkg,
            declarativeSpec = null,
            formatName = "Declarative Test",
            details = "Test installation"
        )

        // Install
        com.usman.musicplayer.extension.ExtensionManager.installImportedExtension(supp)
        assertTrue(com.usman.musicplayer.extension.ExtensionManager.installedExtensions.value.any { it.manifest.id == "ext.lifecycle.test" })

        // Disable
        com.usman.musicplayer.extension.ExtensionManager.toggleExtension("ext.lifecycle.test", false)
        assertFalse(com.usman.musicplayer.extension.ExtensionManager.installedExtensions.value.first { it.manifest.id == "ext.lifecycle.test" }.isEnabled)

        // Enable
        com.usman.musicplayer.extension.ExtensionManager.toggleExtension("ext.lifecycle.test", true)
        assertTrue(com.usman.musicplayer.extension.ExtensionManager.installedExtensions.value.first { it.manifest.id == "ext.lifecycle.test" }.isEnabled)

        // Uninstall
        com.usman.musicplayer.extension.ExtensionManager.uninstallExtension("ext.lifecycle.test")
        assertFalse(com.usman.musicplayer.extension.ExtensionManager.installedExtensions.value.any { it.manifest.id == "ext.lifecycle.test" })
    }

    @Test
    fun testFetchTwoSeparateRepositoryIndexes() = runBlocking {
        val server1 = MockWebServer()
        val repoIndexJson1 = """
            {
              "name": "GitHub Telugu Extension Repo",
              "description": "Telugu audio extension repository",
              "extensions": [
                {
                  "id": "usman.ext.telugu.archive",
                  "name": "Telugu Audio Archive",
                  "version": "1.1.0",
                  "author": "Telugu Open Audio",
                  "description": "Telugu audio catalog",
                  "downloadUrl": "https://example.com/telugu.epk"
                }
              ]
            }
        """.trimIndent()
        server1.enqueue(MockResponse().setResponseCode(200).setBody(repoIndexJson1))
        server1.start()

        val server2 = MockWebServer()
        val repoIndexJson2 = """
            {
              "name": "GitHub Classical Extension Repo",
              "description": "Classical audio repository",
              "extensions": [
                {
                  "id": "usman.ext.classical.archive",
                  "name": "Carnatic Audio Archive",
                  "version": "2.0.0",
                  "author": "Classical Community",
                  "description": "Carnatic classical recordings",
                  "downloadUrl": "https://example.com/carnatic.epk"
                }
              ]
            }
        """.trimIndent()
        server2.enqueue(MockResponse().setResponseCode(200).setBody(repoIndexJson2))
        server2.start()

        val client = com.usman.musicplayer.extension.ExtensionRepositoryClient()

        val repo1 = ExtensionRepository(id = "repo_1", name = "Repo 1", url = server1.url("/repo1.json").toString(), description = "Repo 1")
        val repo2 = ExtensionRepository(id = "repo_2", name = "Repo 2", url = server2.url("/repo2.json").toString(), description = "Repo 2")

        val res1 = client.fetchRepositoryExtensions(repo1)
        val res2 = client.fetchRepositoryExtensions(repo2)

        assertTrue(res1.isSuccess)
        assertTrue(res2.isSuccess)

        val pkgs1 = res1.getOrNull().orEmpty()
        val pkgs2 = res2.getOrNull().orEmpty()

        assertEquals(1, pkgs1.size)
        assertEquals("usman.ext.telugu.archive", pkgs1.first().manifest.id)

        assertEquals(1, pkgs2.size)
        assertEquals("usman.ext.classical.archive", pkgs2.first().manifest.id)

        server1.shutdown()
        server2.shutdown()
    }

    @Test
    fun testZipSlipPathTraversalDefenseIsBlocked() {
        // Create Zip archive containing an unsafe path traversal entry: "../../evil.dex"
        val byteOut = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(byteOut).use { zip ->
            val manifestEntry = java.util.zip.ZipEntry("epk.json")
            zip.putNextEntry(manifestEntry)
            zip.write("""{"id":"test.zipslip","name":"ZipSlip Exploit"}""".toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val evilEntry = java.util.zip.ZipEntry("../../evil.dex")
            zip.putNextEntry(evilEntry)
            zip.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A))
            zip.closeEntry()
        }

        val zipBytes = byteOut.toByteArray()
        val result = com.usman.musicplayer.extension.EpkPackageParser.parseEpk("zipslip.epk", zipBytes)

        assertTrue("Expected Invalid result for ZipSlip path traversal attempt", result is com.usman.musicplayer.extension.ExtensionValidationResult.Invalid)
        val invalid = result as com.usman.musicplayer.extension.ExtensionValidationResult.Invalid
        assertTrue(invalid.error.contains("path traversal") || invalid.error.contains("Security error"))
    }

    @Test
    fun testDownloadAndInstallPackageWithMockWebServer() = runBlocking {
        // Prepare mock EPK package bytes
        val epkManifestJson = """
            {
              "id": "ext.download.mock",
              "name": "Downloaded Mock Provider",
              "version": "1.0.0",
              "author": "Mock Developer",
              "description": "Downloaded extension test",
              "playbackType": "FULL",
              "entryPoint": "endpoints.json"
            }
        """.trimIndent()

        val endpointsJson = """
            {
              "searchConfig": {
                "urlTemplate": "https://example.com/search?q={query}",
                "tracksJsonPath": "docs",
                "idField": "id",
                "titleField": "name",
                "artistField": "artist",
                "streamUrlTemplate": "https://cdn.example.com/{id}.mp3"
              }
            }
        """.trimIndent()

        val byteOut = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(byteOut).use { zip ->
            val manifestEntry = java.util.zip.ZipEntry("epk.json")
            zip.putNextEntry(manifestEntry)
            zip.write(epkManifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val entryPointEntry = java.util.zip.ZipEntry("endpoints.json")
            zip.putNextEntry(entryPointEntry)
            zip.write(endpointsJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val epkBytes = byteOut.toByteArray()

        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/octet-stream").setBody(okio.Buffer().write(epkBytes)))
            server.start()

            val downloadUrl = server.url("/mock_package.epk").toString()

            val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            com.usman.musicplayer.extension.ExtensionManager.init(context)

            val pkgToDownload = ExtensionPackage(
                manifest = ExtensionManifest(
                    id = "ext.download.mock",
                    name = "Downloaded Mock Provider",
                    version = "1.0.0",
                    author = "Mock Developer",
                    description = "Downloaded extension test",
                    downloadUrl = downloadUrl
                )
            )

            val result = com.usman.musicplayer.extension.ExtensionManager.downloadAndInstallPackage(context, pkgToDownload)
            assertTrue("Download and install package should succeed", result.isSuccess)

            val installedList = com.usman.musicplayer.extension.ExtensionManager.installedExtensions.value
            assertTrue(installedList.any { it.manifest.id == "ext.download.mock" && it.isInstalled })
        } finally {
            server.shutdown()
        }

    }

    @Test
    fun testVersionUpdateAndRollback() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.usman.musicplayer.extension.ExtensionManager.init(context)

        val manifestV1 = ExtensionManifest(
            id = "ext.rollback.test",
            name = "Rollback Test Provider",
            version = "1.0.0",
            author = "Dev",
            description = "V1 spec",
            playbackType = PlaybackType.FULL
        )
        val configV1 = com.usman.musicplayer.extension.DeclarativeSearchConfig(
            urlTemplate = "https://v1.example.com/search?q={query}"
        )
        val specV1 = com.usman.musicplayer.extension.DeclarativeProviderSpec(manifestV1, configV1)
        val suppV1 = com.usman.musicplayer.extension.ExtensionValidationResult.Supported(
            pkg = ExtensionPackage(manifest = manifestV1, isInstalled = true, isEnabled = true),
            declarativeSpec = specV1,
            formatName = "Declarative V1",
            details = "V1 Install"
        )
        com.usman.musicplayer.extension.ExtensionManager.installImportedExtension(suppV1)

        // Install V2 update
        val manifestV2 = manifestV1.copy(version = "2.0.0", description = "V2 spec")
        val configV2 = configV1.copy(urlTemplate = "https://v2.example.com/search?q={query}")
        val specV2 = com.usman.musicplayer.extension.DeclarativeProviderSpec(manifestV2, configV2)
        val suppV2 = com.usman.musicplayer.extension.ExtensionValidationResult.Supported(
            pkg = ExtensionPackage(manifest = manifestV2, isInstalled = true, isEnabled = true),
            declarativeSpec = specV2,
            formatName = "Declarative V2",
            details = "V2 Install"
        )
        com.usman.musicplayer.extension.ExtensionManager.installImportedExtension(suppV2)

        val updatedPkg = com.usman.musicplayer.extension.ExtensionManager.installedExtensions.value.first { it.manifest.id == "ext.rollback.test" }
        assertEquals("2.0.0", updatedPkg.installedVersion)

        // Rollback to V1
        val rollbackResult = com.usman.musicplayer.extension.ExtensionManager.rollbackExtension("ext.rollback.test")
        assertTrue("Rollback should succeed when backup spec is available", rollbackResult.isSuccess)

        val restoredPkg = com.usman.musicplayer.extension.ExtensionManager.installedExtensions.value.first { it.manifest.id == "ext.rollback.test" }
        assertEquals("1.0.0", restoredPkg.installedVersion)
    }

    @Test
    fun testTestSearchDiagnostics() = runBlocking {
        val manifest = ExtensionManifest(
            id = "ext.diag.test",
            name = "Diag Test Provider",
            version = "1.0.0",
            author = "Tester",
            description = "Diagnostics test",
            playbackType = PlaybackType.FULL
        )
        val config = com.usman.musicplayer.extension.DeclarativeSearchConfig(
            urlTemplate = "https://archive.org/advancedsearch.php?q={query}&output=json"
        )
        val spec = com.usman.musicplayer.extension.DeclarativeProviderSpec(manifest, config)
        val provider = com.usman.musicplayer.data.repository.providers.DeclarativeMusicProvider(spec)

        ProviderRegistry.registerProvider(provider, enabled = true)

        val testResult = com.usman.musicplayer.extension.ExtensionManager.testSearchExtension("ext.diag.test", "telugu")
        assertTrue("Test search should return result object", testResult.isSuccess)

        ProviderRegistry.unregisterProvider("ext.diag.test")
    }

    @Test
    fun testSha256ChecksumMatchingAndMismatchRejection() = runBlocking {
        val jsonPayload = """{"manifest":{"id":"ext.sha.test","name":"SHA Test Provider"}}"""
        val bytes = jsonPayload.toByteArray(Charsets.UTF_8)

        // Calculate actual SHA-256
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        val validSha256 = hash.joinToString("") { "%02x".format(it) }
        val invalidSha256 = "0000000000000000000000000000000000000000000000000000000000000000"

        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonPayload)) // Valid SHA response
        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonPayload)) // Mismatch SHA response
        server.start()

        val downloadUrl = server.url("/test.json").toString()

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.usman.musicplayer.extension.ExtensionManager.init(context)

        // Case 1: Matching SHA-256
        val validPkg = ExtensionPackage(
            manifest = ExtensionManifest(
                id = "ext.sha.valid",
                name = "SHA Valid Provider",
                version = "1.0.0",
                author = "Dev",
                description = "Valid SHA test",
                downloadUrl = downloadUrl,
                sha256 = validSha256
            )
        )

        val validResult = com.usman.musicplayer.extension.ExtensionManager.downloadAndInstallPackage(context, validPkg)
        assertTrue("Package with valid SHA-256 checksum should install successfully", validResult.isSuccess)

        // Case 2: Mismatched SHA-256
        val invalidPkg = ExtensionPackage(
            manifest = ExtensionManifest(
                id = "ext.sha.invalid",
                name = "SHA Invalid Provider",
                version = "1.0.0",
                author = "Dev",
                description = "Invalid SHA test",
                downloadUrl = downloadUrl,
                sha256 = invalidSha256
            )
        )

        val invalidResult = com.usman.musicplayer.extension.ExtensionManager.downloadAndInstallPackage(context, invalidPkg)
        assertTrue("Package with mismatched SHA-256 checksum should be rejected", invalidResult.isFailure)
        assertTrue(invalidResult.exceptionOrNull() is SecurityException)

        server.shutdown()
    }

    @Test
    fun testAbsoluteFilePathInZipIsBlocked() {
        val byteOut = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(byteOut).use { zip ->
            val manifestEntry = java.util.zip.ZipEntry("epk.json")
            zip.putNextEntry(manifestEntry)
            zip.write("""{"id":"test.abspath","name":"Absolute Path Exploit"}""".toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val absoluteEntry = java.util.zip.ZipEntry("/etc/passwd")
            zip.putNextEntry(absoluteEntry)
            zip.write("root:x:0:0".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val zipBytes = byteOut.toByteArray()
        val result = com.usman.musicplayer.extension.EpkPackageParser.parseEpk("abspath.epk", zipBytes)

        assertTrue("Expected Invalid result for absolute file path in zip", result is com.usman.musicplayer.extension.ExtensionValidationResult.Invalid)
        val invalid = result as com.usman.musicplayer.extension.ExtensionValidationResult.Invalid
        assertTrue(invalid.error.contains("Absolute file path") || invalid.error.contains("Security error"))
    }

    @Test
    fun testDuplicatePathInZipIsBlocked() {
        val byteOut = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(byteOut).use { zip ->
            val entry1 = java.util.zip.ZipEntry("epk.json")
            zip.putNextEntry(entry1)
            zip.write("""{"id":"test.dup","name":"Duplicate Entry"}""".toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val entry2 = java.util.zip.ZipEntry("assets/../epk.json")
            zip.putNextEntry(entry2)
            zip.write("""{"id":"test.dup2","name":"Duplicate Entry Overwrite"}""".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val zipBytes = byteOut.toByteArray()
        val result = com.usman.musicplayer.extension.EpkPackageParser.parseEpk("duplicate.epk", zipBytes)

        assertTrue("Expected Invalid result for duplicate path names in zip", result is com.usman.musicplayer.extension.ExtensionValidationResult.Invalid)
        val invalid = result as com.usman.musicplayer.extension.ExtensionValidationResult.Invalid
        assertTrue(invalid.error.contains("Duplicate entry path") || invalid.error.contains("Unsafe path traversal") || invalid.error.contains("Security error"))
    }
}




