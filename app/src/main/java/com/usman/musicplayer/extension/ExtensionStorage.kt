package com.usman.musicplayer.extension

import android.content.Context
import com.usman.musicplayer.sdk.PlaybackType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Handles persistent local storage of installed extensions and repositories
 * in internal storage across app restarts.
 */
class ExtensionStorage(val context: Context) {

    private val extensionsDir = File(context.filesDir, "extensions").apply {
        if (!exists()) mkdirs()
    }

    private val installedFile = File(extensionsDir, "installed_extensions.json")
    private val reposFile = File(extensionsDir, "repositories.json")
    private val declarativeSpecsFile = File(extensionsDir, "declarative_specs.json")

    fun loadRepositories(): List<ExtensionRepository> {
        if (!reposFile.exists()) {
            val defaults = getDefaultRepositories()
            saveRepositories(defaults)
            return defaults
        }

        return try {
            val content = reposFile.readText()
            val array = JSONArray(content)
            val list = mutableListOf<ExtensionRepository>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(
                    ExtensionRepository(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        url = obj.optString("url"),
                        description = obj.optString("description"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        isOfficial = obj.optBoolean("isOfficial", false),
                        lastSyncTime = obj.optLong("lastSyncTime", 0L),
                        extensionCount = obj.optInt("extensionCount", 0)
                    )
                )
            }
            if (list.isEmpty()) getDefaultRepositories() else list
        } catch (e: Exception) {
            getDefaultRepositories()
        }
    }

    fun saveRepositories(repos: List<ExtensionRepository>) {
        try {
            val array = JSONArray()
            repos.forEach { repo ->
                val obj = JSONObject().apply {
                    put("id", repo.id)
                    put("name", repo.name)
                    put("url", repo.url)
                    put("description", repo.description)
                    put("isEnabled", repo.isEnabled)
                    put("isOfficial", repo.isOfficial)
                    put("lastSyncTime", repo.lastSyncTime)
                    put("extensionCount", repo.extensionCount)
                }
                array.put(obj)
            }
            reposFile.writeText(array.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadInstalledExtensions(): List<ExtensionPackage> {
        if (!installedFile.exists()) {
            val defaults = getDefaultInstalledExtensions()
            saveInstalledExtensions(defaults)
            return defaults
        }

        return try {
            val content = installedFile.readText()
            val array = JSONArray(content)
            val list = mutableListOf<ExtensionPackage>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val manifestObj = obj.optJSONObject("manifest") ?: continue

                val perms = mutableListOf<String>()
                val permArr = manifestObj.optJSONArray("permissions")
                if (permArr != null) {
                    for (p in 0 until permArr.length()) perms.add(permArr.optString(p))
                }

                val langs = mutableListOf<String>()
                val langArr = manifestObj.optJSONArray("supportedLanguages")
                if (langArr != null) {
                    for (l in 0 until langArr.length()) langs.add(langArr.optString(l))
                }

                val manifest = ExtensionManifest(
                    id = manifestObj.optString("id"),
                    name = manifestObj.optString("name"),
                    version = manifestObj.optString("version", "1.0.0"),
                    author = manifestObj.optString("author", "USMAN"),
                    description = manifestObj.optString("description"),
                    iconUrl = manifestObj.optString("iconUrl").takeIf { it.isNotBlank() },
                    homepageUrl = manifestObj.optString("homepageUrl").takeIf { it.isNotBlank() },
                    license = manifestObj.optString("license", "Open Source"),
                    permissions = if (perms.isEmpty()) listOf("INTERNET", "STREAMING") else perms,
                    supportedLanguages = langs,
                    playbackType = try {
                        PlaybackType.valueOf(manifestObj.optString("playbackType", "FULL"))
                    } catch (e: Exception) {
                        PlaybackType.FULL
                    },
                    isFullPlaybackSupported = manifestObj.optBoolean("isFullPlaybackSupported", true),
                    type = try {
                        ExtensionType.valueOf(manifestObj.optString("type", "CONTENT_RESOLVER"))
                    } catch (e: Exception) {
                        ExtensionType.CONTENT_RESOLVER
                    },
                    downloadUrl = manifestObj.optString("downloadUrl").takeIf { it.isNotBlank() },
                    packageAsset = manifestObj.optString("packageAsset").takeIf { it.isNotBlank() },
                    repositoryName = manifestObj.optString("repositoryName").takeIf { it.isNotBlank() },
                    adapterId = manifestObj.optString("adapterId").takeIf { it.isNotBlank() }
                )

                list.add(
                    ExtensionPackage(
                        manifest = manifest,
                        isInstalled = obj.optBoolean("isInstalled", true),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        isBuiltIn = obj.optBoolean("isBuiltIn", false),
                        installedVersion = obj.optString("installedVersion", manifest.version),
                        latestVersion = obj.optString("latestVersion", manifest.version),
                        installDate = obj.optLong("installDate", System.currentTimeMillis()),
                        lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis()),
                        hasUpdate = obj.optBoolean("hasUpdate", false),
                        errorMessage = obj.optString("errorMessage").takeIf { it.isNotBlank() },
                        isImported = obj.optBoolean("isImported", false),
                        originalFileName = obj.optString("originalFileName").takeIf { it.isNotBlank() },
                        packageFormat = obj.optString("packageFormat").takeIf { it.isNotBlank() }
                    )
                )
            }
            if (list.isEmpty()) getDefaultInstalledExtensions() else list
        } catch (e: Exception) {
            getDefaultInstalledExtensions()
        }
    }

    fun saveInstalledExtensions(packages: List<ExtensionPackage>) {
        try {
            val array = JSONArray()
            packages.forEach { pkg ->
                val obj = JSONObject().apply {
                    val m = pkg.manifest
                    val mObj = JSONObject().apply {
                        put("id", m.id)
                        put("name", m.name)
                        put("version", m.version)
                        put("author", m.author)
                        put("description", m.description)
                        put("iconUrl", m.iconUrl ?: "")
                        put("homepageUrl", m.homepageUrl ?: "")
                        put("license", m.license)
                        put("permissions", JSONArray(m.permissions))
                        put("supportedLanguages", JSONArray(m.supportedLanguages))
                        put("playbackType", m.playbackType.name)
                        put("isFullPlaybackSupported", m.isFullPlaybackSupported)
                        put("type", m.type.name)
                        put("downloadUrl", m.downloadUrl ?: "")
                        put("packageAsset", m.packageAsset ?: "")
                        put("repositoryName", m.repositoryName ?: "")
                        put("adapterId", m.adapterId ?: "")
                    }
                    put("manifest", mObj)
                    put("isInstalled", pkg.isInstalled)
                    put("isEnabled", pkg.isEnabled)
                    put("isBuiltIn", pkg.isBuiltIn)
                    put("installedVersion", pkg.installedVersion ?: m.version)
                    put("latestVersion", pkg.latestVersion ?: m.version)
                    put("installDate", pkg.installDate)
                    put("lastUpdated", pkg.lastUpdated)
                    put("hasUpdate", pkg.hasUpdate)
                    put("errorMessage", pkg.errorMessage ?: "")
                    put("isImported", pkg.isImported)
                    put("originalFileName", pkg.originalFileName ?: "")
                    put("packageFormat", pkg.packageFormat ?: "")
                }
                array.put(obj)
            }
            installedFile.writeText(array.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadDeclarativeSpecs(): List<DeclarativeProviderSpec> {
        if (!declarativeSpecsFile.exists()) return emptyList()
        return try {
            val content = declarativeSpecsFile.readText()
            val array = JSONArray(content)
            parseDeclarativeSpecsJsonArray(array)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveDeclarativeSpecs(specs: List<DeclarativeProviderSpec>) {
        try {
            val array = serializeDeclarativeSpecsToJsonArray(specs)
            declarativeSpecsFile.writeText(array.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseDeclarativeSpecsJsonArray(array: JSONArray): List<DeclarativeProviderSpec> {
        val list = mutableListOf<DeclarativeProviderSpec>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val mObj = obj.optJSONObject("manifest") ?: continue
            val sObj = obj.optJSONObject("searchConfig") ?: continue

            val perms = mutableListOf<String>()
            val permArr = mObj.optJSONArray("permissions")
            if (permArr != null) {
                for (p in 0 until permArr.length()) perms.add(permArr.optString(p))
            }

            val langs = mutableListOf<String>()
            val langArr = mObj.optJSONArray("supportedLanguages")
            if (langArr != null) {
                for (l in 0 until langArr.length()) langs.add(langArr.optString(l))
            }

            val manifest = ExtensionManifest(
                id = mObj.optString("id"),
                name = mObj.optString("name"),
                version = mObj.optString("version", "1.0.0"),
                author = mObj.optString("author", "Community"),
                description = mObj.optString("description"),
                iconUrl = mObj.optString("iconUrl").takeIf { it.isNotBlank() },
                homepageUrl = mObj.optString("homepageUrl").takeIf { it.isNotBlank() },
                license = mObj.optString("license", "Open Source"),
                permissions = if (perms.isEmpty()) listOf("INTERNET", "STREAMING") else perms,
                supportedLanguages = langs,
                playbackType = try {
                    PlaybackType.valueOf(mObj.optString("playbackType", "FULL"))
                } catch (e: Exception) {
                    PlaybackType.FULL
                },
                isFullPlaybackSupported = mObj.optBoolean("isFullPlaybackSupported", true),
                type = try {
                    ExtensionType.valueOf(mObj.optString("type", "CONTENT_RESOLVER"))
                } catch (e: Exception) {
                    ExtensionType.CONTENT_RESOLVER
                },
                downloadUrl = mObj.optString("downloadUrl").takeIf { it.isNotBlank() },
                packageAsset = mObj.optString("packageAsset").takeIf { it.isNotBlank() },
                repositoryName = mObj.optString("repositoryName", "Imported File")
            )

            val headersMap = mutableMapOf<String, String>()
            val headersObj = sObj.optJSONObject("headers")
            if (headersObj != null) {
                val keys = headersObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    headersMap[k] = headersObj.optString(k)
                }
            }

            val searchConfig = DeclarativeSearchConfig(
                urlTemplate = sObj.optString("urlTemplate"),
                httpMethod = sObj.optString("httpMethod", "GET"),
                headers = headersMap,
                tracksJsonPath = sObj.optString("tracksJsonPath", ""),
                idField = sObj.optString("idField", "id"),
                titleField = sObj.optString("titleField", "title"),
                artistField = sObj.optString("artistField", "artist"),
                albumField = sObj.optString("albumField", "album"),
                durationField = sObj.optString("durationField", "duration"),
                durationUnit = sObj.optString("durationUnit", "seconds"),
                streamUrlField = sObj.optString("streamUrlField", "stream_url"),
                artworkUrlField = sObj.optString("artworkUrlField", "artwork_url"),
                streamUrlTemplate = sObj.optString("streamUrlTemplate").takeIf { it.isNotBlank() }
            )

            list.add(DeclarativeProviderSpec(manifest, searchConfig))
        }
        return list
    }

    private fun serializeDeclarativeSpecsToJsonArray(specs: List<DeclarativeProviderSpec>): JSONArray {
        val array = JSONArray()
        specs.forEach { spec ->
            val obj = JSONObject()
            val m = spec.manifest
            val mObj = JSONObject().apply {
                put("id", m.id)
                put("name", m.name)
                put("version", m.version)
                put("author", m.author)
                put("description", m.description)
                put("iconUrl", m.iconUrl ?: "")
                put("homepageUrl", m.homepageUrl ?: "")
                put("license", m.license)
                put("permissions", JSONArray(m.permissions))
                put("supportedLanguages", JSONArray(m.supportedLanguages))
                put("playbackType", m.playbackType.name)
                put("isFullPlaybackSupported", m.isFullPlaybackSupported)
                put("type", m.type.name)
                put("downloadUrl", m.downloadUrl ?: "")
                put("packageAsset", m.packageAsset ?: "")
                put("repositoryName", m.repositoryName ?: "")
            }
            obj.put("manifest", mObj)

            val sc = spec.searchConfig
            val sObj = JSONObject().apply {
                put("urlTemplate", sc.urlTemplate)
                put("httpMethod", sc.httpMethod)
                put("headers", JSONObject(sc.headers))
                put("tracksJsonPath", sc.tracksJsonPath)
                put("idField", sc.idField)
                put("titleField", sc.titleField)
                put("artistField", sc.artistField)
                put("albumField", sc.albumField)
                put("durationField", sc.durationField)
                put("durationUnit", sc.durationUnit)
                put("streamUrlField", sc.streamUrlField)
                put("artworkUrlField", sc.artworkUrlField)
                put("streamUrlTemplate", sc.streamUrlTemplate ?: "")
            }
            obj.put("searchConfig", sObj)
            array.put(obj)
        }
        return array
    }

    private val backupsFile = File(extensionsDir, "declarative_spec_backups.json")

    fun saveSpecBackup(id: String, spec: DeclarativeProviderSpec) {
        try {
            val currentBackups = loadBackupSpecs().filter { it.manifest.id != id }.toMutableList()
            currentBackups.add(spec)
            saveBackupSpecs(currentBackups)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getSpecBackup(id: String): DeclarativeProviderSpec? {
        return loadBackupSpecs().find { it.manifest.id == id }
    }

    fun rollbackDeclarativeSpec(id: String): DeclarativeProviderSpec? {
        val backup = getSpecBackup(id) ?: return null
        addDeclarativeSpec(backup)
        return backup
    }

    private fun loadBackupSpecs(): List<DeclarativeProviderSpec> {
        if (!backupsFile.exists()) return emptyList()
        return try {
            val content = backupsFile.readText()
            val array = JSONArray(content)
            parseDeclarativeSpecsJsonArray(array)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveBackupSpecs(specs: List<DeclarativeProviderSpec>) {
        try {
            val array = serializeDeclarativeSpecsToJsonArray(specs)
            backupsFile.writeText(array.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addDeclarativeSpec(spec: DeclarativeProviderSpec) {
        val currentSpecs = loadDeclarativeSpecs()
        val existing = currentSpecs.find { it.manifest.id == spec.manifest.id }
        if (existing != null) {
            saveSpecBackup(spec.manifest.id, existing)
        }

        val updated = currentSpecs.filter { it.manifest.id != spec.manifest.id }.toMutableList()
        updated.add(spec)
        saveDeclarativeSpecs(updated)
    }

    fun removeDeclarativeSpec(id: String) {
        val current = loadDeclarativeSpecs().filter { it.manifest.id != id }
        saveDeclarativeSpecs(current)
    }

    private fun getDefaultRepositories(): List<ExtensionRepository> {
        return listOf(
            ExtensionRepository(
                id = "repo_usman_community",
                name = "USMAN Music Extension Hub",
                url = "https://raw.githubusercontent.com/usman-music/extensions/main/repo.json",
                description = "Official USMAN verified music providers for Indian, Telugu, Classical, and Global audio streams.",
                isEnabled = true,
                isOfficial = true,
                lastSyncTime = System.currentTimeMillis(),
                extensionCount = 6
            ),
            ExtensionRepository(
                id = "repo_bloomee_factory",
                name = "BloomeeTunes Ecosystem",
                url = "https://hemantkarya.github.io/BloomeeTunes/repositories.json",
                description = "Bloomee open-source package catalog and content resolver ecosystem.",
                isEnabled = true,
                isOfficial = true,
                lastSyncTime = System.currentTimeMillis(),
                extensionCount = 12
            )
        )
    }

    private fun getDefaultInstalledExtensions(): List<ExtensionPackage> {
        val now = System.currentTimeMillis()
        return listOf(
            ExtensionPackage(
                manifest = ExtensionManifest(
                    id = "usman.ext.audius",
                    name = "Audius Streamer",
                    version = "1.3.0",
                    author = "Audius Community",
                    description = "Decentralized audio catalog with verified Telugu hits, Phonk, EDM, Pop, and trending tracks.",
                    license = "Open Protocol",
                    permissions = listOf("INTERNET", "STREAMING"),
                    supportedLanguages = listOf("Telugu", "Hindi", "English", "Global"),
                    playbackType = PlaybackType.FULL,
                    isFullPlaybackSupported = true,
                    type = ExtensionType.CONTENT_RESOLVER,
                    repositoryName = "USMAN Music Extension Hub"
                ),
                isInstalled = true,
                isEnabled = true,
                isBuiltIn = true,
                installedVersion = "1.3.0",
                latestVersion = "1.3.0",
                installDate = now,
                lastUpdated = now
            ),
            ExtensionPackage(
                manifest = ExtensionManifest(
                    id = "usman.ext.ccmixter",
                    name = "ccMixter Open Audio",
                    version = "1.0.0",
                    author = "ccMixter.org",
                    description = "Full-length Creative Commons music, live remixes, and open instrumentals.",
                    license = "Creative Commons",
                    permissions = listOf("INTERNET", "STREAMING"),
                    supportedLanguages = listOf("Global", "Instrumental"),
                    playbackType = PlaybackType.FULL,
                    isFullPlaybackSupported = true,
                    type = ExtensionType.CONTENT_RESOLVER,
                    repositoryName = "USMAN Music Extension Hub"
                ),
                isInstalled = true,
                isEnabled = true,
                isBuiltIn = true,
                installedVersion = "1.0.0",
                latestVersion = "1.0.0",
                installDate = now,
                lastUpdated = now
            ),
            ExtensionPackage(
                manifest = ExtensionManifest(
                    id = "usman.ext.archiveorg",
                    name = "Internet Archive Audio",
                    version = "1.0.0",
                    author = "Internet Archive",
                    description = "Millions of Indian classical, Telugu broadcasts, live concert recordings, and public domain audio tracks.",
                    license = "Public Domain / CC",
                    permissions = listOf("INTERNET", "STREAMING"),
                    supportedLanguages = listOf("Telugu", "Hindi", "Tamil", "Classical", "English"),
                    playbackType = PlaybackType.FULL,
                    isFullPlaybackSupported = true,
                    type = ExtensionType.CONTENT_RESOLVER,
                    repositoryName = "USMAN Music Extension Hub"
                ),
                isInstalled = true,
                isEnabled = true,
                isBuiltIn = false,
                installedVersion = "1.0.0",
                latestVersion = "1.0.0",
                installDate = now,
                lastUpdated = now
            ),
            ExtensionPackage(
                manifest = ExtensionManifest(
                    id = "usman.ext.jiosaavn",
                    name = "JioSaavn Official Hub",
                    version = "1.2.0",
                    author = "USMAN / JioSaavn",
                    description = "Curated Indian music catalog with deep-link official streaming handoff for Bollywood and regional hits.",
                    license = "Proprietary",
                    permissions = listOf("INTERNET", "EXTERNAL_APP"),
                    supportedLanguages = listOf("Telugu", "Hindi", "Tamil", "Kannada", "Malayalam", "Punjabi"),
                    playbackType = PlaybackType.EXTERNAL_SERVICE,
                    isFullPlaybackSupported = false,
                    type = ExtensionType.CONTENT_RESOLVER,
                    repositoryName = "USMAN Music Extension Hub"
                ),
                isInstalled = true,
                isEnabled = true,
                isBuiltIn = false,
                installedVersion = "1.2.0",
                latestVersion = "1.2.0",
                installDate = now,
                lastUpdated = now
            )
        )
    }
}
