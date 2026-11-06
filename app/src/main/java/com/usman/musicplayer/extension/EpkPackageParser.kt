package com.usman.musicplayer.extension

import com.usman.musicplayer.sdk.PlaybackType
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Robust package parser for EPK (Extension Package Kit / Echo Package Kit) files.
 *
 * An .epk file is an extension package container (ZIP-based archive or standalone package descriptor)
 * providing metadata, entry point resolution, and sandboxed REST provider configurations for
 * seamless integration with USMAN's MusicProvider SDK and Media3 player.
 */
object EpkPackageParser {

    private const val MAX_UNCOMPRESSED_SIZE = 30 * 1024 * 1024L // 30 MB safety limit
    private const val MAX_ENTRIES = 500

    /**
     * Inspects and parses an EPK package from raw bytes.
     */
    fun parseEpk(
        filename: String,
        bytes: ByteArray,
        iconOutputDir: File? = null
    ): ExtensionValidationResult {
        if (bytes.isEmpty()) {
            return ExtensionValidationResult.Invalid(filename, "Selected .epk file is empty (0 bytes).")
        }

        // Check for ZIP container magic bytes: PK\x03\x04 (0x50 0x4B 0x03 0x04)
        val isZipContainer = bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() &&
                bytes[3] == 0x04.toByte()

        if (isZipContainer) {
            return parseZipEpkArchive(filename, bytes, iconOutputDir)
        }

        // Check if it's a standalone JSON-formatted EPK package descriptor
        val text = try {
            String(bytes, StandardCharsets.UTF_8).trim()
        } catch (e: Exception) {
            ""
        }

        if (text.startsWith("{")) {
            return parseJsonDescriptor(filename, text, entryPointName = "standalone.epk.json")
        }

        return ExtensionValidationResult.Invalid(
            filename,
            "Invalid .epk package header. Expected ZIP archive container (PK\\x03\\x04) or valid EPK JSON package descriptor."
        )
    }

    private fun parseZipEpkArchive(
        filename: String,
        bytes: ByteArray,
        iconOutputDir: File?
    ): ExtensionValidationResult {
        var totalBytesRead = 0L
        var entryCount = 0

        var hasClassesDex = false
        var hasWasm = false
        val fileMap = mutableMapOf<String, ByteArray>()

        try {
            val zis = ZipInputStream(ByteArrayInputStream(bytes))
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > MAX_ENTRIES) {
                    zis.close()
                    return ExtensionValidationResult.Invalid(
                        filename,
                        "EPK package contains too many files ($entryCount > $MAX_ENTRIES limit)."
                    )
                }

                val rawName = entry.name
                if (rawName.startsWith("/") || rawName.contains(":") || rawName.startsWith("\\")) {
                    zis.close()
                    return ExtensionValidationResult.Invalid(
                        filename,
                        "Security error: Absolute file path detected in EPK entry ('$rawName'). Package rejected."
                    )
                }

                val normalizedName = rawName.replace("\\", "/").trimStart('/')
                if (normalizedName.contains("../") || normalizedName.startsWith("..") || normalizedName.contains("/..")) {
                    zis.close()
                    return ExtensionValidationResult.Invalid(
                        filename,
                        "Security error: Unsafe path traversal detected in EPK entry ('$rawName'). Package rejected."
                    )
                }

                if (fileMap.containsKey(normalizedName)) {
                    zis.close()
                    return ExtensionValidationResult.Invalid(
                        filename,
                        "Security error: Duplicate entry path detected in archive ('$normalizedName'). Package rejected."
                    )
                }

                val lowerName = normalizedName.lowercase()

                if (lowerName.endsWith("classes.dex") || lowerName.endsWith(".dex")) {
                    hasClassesDex = true
                }
                if (lowerName.endsWith(".wasm")) {
                    hasWasm = true
                }

                if (!entry.isDirectory) {
                    val entryBytes = zis.readBytes()
                    totalBytesRead += entryBytes.size
                    if (totalBytesRead > MAX_UNCOMPRESSED_SIZE) {
                        zis.close()
                        return ExtensionValidationResult.Invalid(
                            filename,
                            "EPK uncompressed package size exceeds safety limit of 30 MB."
                        )
                    }
                    fileMap[normalizedName] = entryBytes
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()
        } catch (e: Exception) {
            return ExtensionValidationResult.Invalid(
                filename,
                "Corrupted .epk package archive: ${e.localizedMessage ?: "ZIP read error"}"
            )
        }

        // Locate manifest file within archive
        val manifestCandidates = listOf(
            "epk.json",
            "manifest.json",
            "extension.json",
            "provider.json",
            "package.json",
            "assets/epk.json",
            "assets/manifest.json",
            "assets/provider.json"
        )

        var manifestEntryName: String? = null
        var manifestBytes: ByteArray? = null

        for (candidate in manifestCandidates) {
            val foundEntry = fileMap.keys.find { it.equals(candidate, ignoreCase = true) }
            if (foundEntry != null) {
                manifestEntryName = foundEntry
                manifestBytes = fileMap[foundEntry]
                break
            }
        }

        // If no primary manifest was found by name, check any root .json file
        if (manifestBytes == null) {
            val anyJson = fileMap.keys.find { it.endsWith(".json", ignoreCase = true) && !it.contains("/") }
            if (anyJson != null) {
                manifestEntryName = anyJson
                manifestBytes = fileMap[anyJson]
            }
        }

        // Check for pure compiled DEX with no declarative manifest
        if (hasClassesDex && manifestBytes == null) {
            val baseName = filename.substringBeforeLast(".epk")
            return ExtensionValidationResult.UnsupportedFormat(
                filename = filename,
                formatName = "Compiled EPK (Raw DEX Bytecode)",
                detectedProvider = baseName.ifBlank { "External Android DEX Provider" },
                detectedVersion = "DEX 035/039",
                reason = "The selected .epk package contains compiled Dalvik Executable (classes.dex) bytecode without an authorized declarative REST entry point. Dynamic Code Loading (DCL) of raw executable bytecode is blocked to protect device security and comply with Google Play Developer Program Policies. Use a declarative REST .epk package or provide its provider manifest."
            )
        }

        // Check for pure WASM with no declarative manifest
        if (hasWasm && manifestBytes == null) {
            val baseName = filename.substringBeforeLast(".epk")
            return ExtensionValidationResult.UnsupportedFormat(
                filename = filename,
                formatName = "WebAssembly EPK (.wasm)",
                detectedProvider = baseName.ifBlank { "Wasm Audio Provider" },
                detectedVersion = "WASM 1.0",
                reason = "The selected .epk package contains raw WebAssembly binary bytecode without a declarative REST entry point. Native WASM execution is unbundled for device safety."
            )
        }

        if (manifestBytes == null) {
            return ExtensionValidationResult.Invalid(
                filename,
                "The .epk package does not contain a recognizable manifest (expected 'epk.json', 'manifest.json', or 'provider.json')."
            )
        }

        val manifestText = try {
            String(manifestBytes, StandardCharsets.UTF_8).trim()
        } catch (e: Exception) {
            return ExtensionValidationResult.Invalid(filename, "Unable to read manifest text: ${e.message}")
        }

        val manifestObj = try {
            JSONObject(manifestText)
        } catch (e: Exception) {
            return ExtensionValidationResult.Invalid(filename, "Invalid JSON in '$manifestEntryName': ${e.message}")
        }

        // Entry point resolution: check if manifest points to a secondary entry point file
        val entryPointPath = manifestObj.optString("entryPoint")
            .ifBlank { manifestObj.optString("main") }
            .ifBlank { manifestObj.optString("source") }
            .takeIf { it.isNotBlank() }

        var resolvedEntryPointName = manifestEntryName ?: "manifest.json"
        var entryPointObj: JSONObject? = null

        if (entryPointPath != null) {
            val matchedKey = fileMap.keys.find { it.equals(entryPointPath, ignoreCase = true) || it.endsWith(entryPointPath, ignoreCase = true) }
            if (matchedKey != null && matchedKey.endsWith(".json", ignoreCase = true)) {
                val entryBytes = fileMap[matchedKey]
                if (entryBytes != null) {
                    try {
                        val entryText = String(entryBytes, StandardCharsets.UTF_8).trim()
                        entryPointObj = JSONObject(entryText)
                        resolvedEntryPointName = matchedKey
                    } catch (e: Exception) {
                        // Keep manifestObj if entry point failed to parse
                    }
                }
            }
        }

        // Extract bundled icon if present
        var localIconUrl: String? = null
        val iconDeclared = manifestObj.optString("icon").takeIf { it.isNotBlank() }
        val iconCandidates = listOfNotNull(iconDeclared, "icon.png", "icon.jpg", "assets/icon.png", "assets/icon.jpg")
        for (cand in iconCandidates) {
            val iconKey = fileMap.keys.find { it.equals(cand, ignoreCase = true) || it.endsWith(cand, ignoreCase = true) }
            if (iconKey != null) {
                val iconBytes = fileMap[iconKey]
                if (iconBytes != null && iconBytes.isNotEmpty() && iconOutputDir != null) {
                    try {
                        if (!iconOutputDir.exists()) iconOutputDir.mkdirs()
                        val extId = manifestObj.optString("id", "ext_${System.currentTimeMillis()}").replace("[^a-zA-Z0-9_.]".toRegex(), "_")
                        val iconExt = if (iconKey.endsWith(".jpg", ignoreCase = true)) "jpg" else "png"
                        val targetFile = File(iconOutputDir, "${extId}_icon.$iconExt")
                        FileOutputStream(targetFile).use { it.write(iconBytes) }
                        localIconUrl = "file://${targetFile.absolutePath}"
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        return parseJsonDescriptor(
            filename = filename,
            jsonText = manifestText,
            entryPointName = resolvedEntryPointName,
            secondaryJsonObj = entryPointObj,
            localIconUrl = localIconUrl,
            packageFormatLabel = "EPK (Extension Package Kit)"
        )
    }

    /**
     * Parses the JSON manifest and search/streaming config for an EPK extension.
     */
    fun parseJsonDescriptor(
        filename: String,
        jsonText: String,
        entryPointName: String,
        secondaryJsonObj: JSONObject? = null,
        localIconUrl: String? = null,
        packageFormatLabel: String = "EPK Declarative Descriptor"
    ): ExtensionValidationResult {
        val rootObj = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            return ExtensionValidationResult.Invalid(filename, "Invalid JSON descriptor: ${e.message}")
        }

        val manifestObj = rootObj.optJSONObject("manifest") ?: rootObj

        val id = manifestObj.optString("id").takeIf { it.isNotBlank() }
            ?: return ExtensionValidationResult.Invalid(filename, "EPK manifest missing required 'id' field.")
        val name = manifestObj.optString("name").takeIf { it.isNotBlank() }
            ?: return ExtensionValidationResult.Invalid(filename, "EPK manifest missing required 'name' field.")
        val version = manifestObj.optString("version", "1.0.0")
        val author = manifestObj.optString("author", "EPK Developer")
        val desc = manifestObj.optString("description", "Imported EPK Music Provider")
        val license = manifestObj.optString("license", "Open Audio License")

        val perms = mutableListOf<String>()
        val permArr = manifestObj.optJSONArray("permissions")
        if (permArr != null) {
            for (i in 0 until permArr.length()) perms.add(permArr.optString(i))
        } else {
            perms.add("INTERNET")
            perms.add("STREAMING")
        }

        val langs = mutableListOf<String>()
        val langArr = manifestObj.optJSONArray("supportedLanguages")
        if (langArr != null) {
            for (i in 0 until langArr.length()) langs.add(langArr.optString(i))
        }

        val playbackTypeStr = manifestObj.optString("playbackType", "FULL")
        val playbackType = try {
            PlaybackType.valueOf(playbackTypeStr.uppercase())
        } catch (e: Exception) {
            PlaybackType.FULL
        }

        val typeStr = manifestObj.optString("type", "CONTENT_RESOLVER")
        val type = try {
            ExtensionType.valueOf(typeStr.uppercase())
        } catch (e: Exception) {
            ExtensionType.CONTENT_RESOLVER
        }

        // Resolve search and streaming configuration: check secondary entry point first, then rootObj, then manifestObj
        val searchObj = secondaryJsonObj?.optJSONObject("searchConfig")
            ?: secondaryJsonObj?.optJSONObject("endpoints")
            ?: secondaryJsonObj?.optJSONObject("providerConfig")
            ?: rootObj.optJSONObject("searchConfig")
            ?: rootObj.optJSONObject("endpoints")
            ?: rootObj.optJSONObject("providerConfig")
            ?: manifestObj.optJSONObject("searchConfig")
            ?: secondaryJsonObj

        val declarativeSpec = if (searchObj != null) {
            val urlTemplate = searchObj.optString("urlTemplate")
                .ifBlank { searchObj.optString("endpointUrl") }
                .ifBlank { searchObj.optString("searchUrl") }
                .ifBlank { searchObj.optString("url") }

            if (urlTemplate.isNotBlank()) {
                val headersMap = mutableMapOf<String, String>()
                val headersObj = searchObj.optJSONObject("headers")
                if (headersObj != null) {
                    val keys = headersObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        headersMap[k] = headersObj.optString(k)
                    }
                }

                val iconToUse = localIconUrl
                    ?: manifestObj.optString("iconUrl").takeIf { it.isNotBlank() }
                    ?: manifestObj.optString("icon").takeIf { it.startsWith("http://") || it.startsWith("https://") }

                val manifest = ExtensionManifest(
                    id = id,
                    name = name,
                    version = version,
                    author = author,
                    description = desc,
                    iconUrl = iconToUse,
                    homepageUrl = manifestObj.optString("homepageUrl").takeIf { it.isNotBlank() },
                    license = license,
                    permissions = perms,
                    supportedLanguages = langs,
                    playbackType = playbackType,
                    isFullPlaybackSupported = playbackType == PlaybackType.FULL,
                    type = type,
                    repositoryName = "Imported EPK"
                )

                val tracksPath = searchObj.optString("tracksJsonPath")
                    .ifBlank { searchObj.optString("tracksPath") }
                    .ifBlank { searchObj.optString("resultsPath") }

                val searchConfig = DeclarativeSearchConfig(
                    urlTemplate = urlTemplate,
                    httpMethod = searchObj.optString("httpMethod", "GET"),
                    headers = headersMap,
                    tracksJsonPath = tracksPath,
                    idField = searchObj.optString("idField", "id"),
                    titleField = searchObj.optString("titleField", "title"),
                    artistField = searchObj.optString("artistField", "artist"),
                    albumField = searchObj.optString("albumField", "album"),
                    durationField = searchObj.optString("durationField", "duration"),
                    durationUnit = searchObj.optString("durationUnit", "seconds"),
                    streamUrlField = searchObj.optString("streamUrlField", "stream_url"),
                    artworkUrlField = searchObj.optString("artworkUrlField", "artwork_url"),
                    streamUrlTemplate = searchObj.optString("streamUrlTemplate")
                        .ifBlank { searchObj.optString("streamTemplate") }
                        .takeIf { it.isNotBlank() }
                )

                DeclarativeProviderSpec(manifest, searchConfig)
            } else null
        } else null

        val iconToUse = localIconUrl
            ?: manifestObj.optString("iconUrl").takeIf { it.isNotBlank() }
            ?: manifestObj.optString("icon").takeIf { it.startsWith("http://") || it.startsWith("https://") }

        val manifest = declarativeSpec?.manifest ?: ExtensionManifest(
            id = id,
            name = name,
            version = version,
            author = author,
            description = desc,
            iconUrl = iconToUse,
            homepageUrl = manifestObj.optString("homepageUrl").takeIf { it.isNotBlank() },
            license = license,
            permissions = perms,
            supportedLanguages = langs,
            playbackType = playbackType,
            isFullPlaybackSupported = playbackType == PlaybackType.FULL,
            type = type,
            repositoryName = "Imported EPK"
        )

        val pkg = ExtensionPackage(
            manifest = manifest,
            isInstalled = false,
            isEnabled = true,
            isBuiltIn = false,
            installedVersion = version,
            latestVersion = version,
            installDate = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis(),
            isImported = true,
            originalFileName = filename,
            packageFormat = packageFormatLabel
        )

        val detailsText = if (declarativeSpec != null) {
            "Verified .epk package '$id' with entry point '$entryPointName'. Declarative REST music search and full-length stream resolver are configured for playback."
        } else {
            "Verified .epk metadata package '$id' with entry point '$entryPointName'."
        }

        return ExtensionValidationResult.Supported(
            pkg = pkg,
            declarativeSpec = declarativeSpec,
            formatName = packageFormatLabel,
            details = detailsText
        )
    }
}
