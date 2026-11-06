package com.usman.musicplayer.extension

import com.usman.musicplayer.sdk.PlaybackType
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

/**
 * Validates and inspects imported extension files from storage.
 * Detects supported declarative formats vs unsupported compiled binary formats (BEX, WASM, DEX, JAR, APK).
 * For APK-derived packages, safely inspects assets without executing external DEX bytecode.
 */
object ExtensionFileValidator {
    private const val MAX_PACKAGE_SIZE = 25 * 1024 * 1024L

    fun validateFile(
        context: android.content.Context,
        filename: String,
        fileSizeBytes: Long,
        inputStream: InputStream
    ): ExtensionValidationResult {
        if (fileSizeBytes > MAX_PACKAGE_SIZE) {
            return ExtensionValidationResult.Invalid(
                filename = filename,
                error = "File size (${fileSizeBytes / (1024 * 1024)} MB) exceeds the maximum allowed extension limit (25 MB)."
            )
        }

        // Save to temp file for robust APK/ZIP processing and PackageManager access
        val tempDir = java.io.File(context.cacheDir, "extension_temp").apply { if (!exists()) mkdirs() }
        val tempFile = java.io.File(tempDir, "verify_${System.currentTimeMillis()}_$filename")
        
        val bytes = try {
            val content = inputStream.readBytes()
            java.io.FileOutputStream(tempFile).use { it.write(content) }
            content
        } catch (e: Exception) {
            return ExtensionValidationResult.Invalid(
                filename = filename,
                error = "Failed to read file: ${e.localizedMessage ?: "I/O error"}"
            )
        }

        if (bytes.isEmpty()) {
            tempFile.delete()
            return ExtensionValidationResult.Invalid(filename = filename, error = "Selected file is empty (0 bytes).")
        }

        // 1. DEX Bytecode check (DCL policy block)
        if (filename.endsWith(".dex", ignoreCase = true) || (bytes.size >= 4 && bytes[0] == 0x64.toByte() && bytes[1] == 0x65.toByte() && bytes[2] == 0x78.toByte() && bytes[3] == 0x0A.toByte())) {
            return ExtensionValidationResult.UnsupportedFormat(
                filename = filename,
                formatName = "Dalvik Executable (.dex)",
                detectedProvider = filename.substringBeforeLast("."),
                detectedVersion = "1.0",
                reason = "Direct execution of raw DEX bytecode is blocked by Google Play policy and Dynamic Code Loading (DCL) security restrictions. Use declarative JSON or EAPK packages."
            )
        }

        // 2. WASM Binary check
        if (filename.endsWith(".wasm", ignoreCase = true) || (bytes.size >= 4 && bytes[0] == 0x00.toByte() && bytes[1] == 0x61.toByte() && bytes[2] == 0x73.toByte() && bytes[3] == 0x6D.toByte())) {
            return ExtensionValidationResult.UnsupportedFormat(
                filename = filename,
                formatName = "WebAssembly Binary (.wasm)",
                detectedProvider = filename.substringBeforeLast("."),
                detectedVersion = "1.0",
                reason = "WebAssembly (.wasm) binary modules are not supported in the native Android music runtime."
            )
        }

        // 3. Bloomee BEX check (Zstandard magic: 0x28, 0xB5, 0x2F, 0xFD)
        if (filename.endsWith(".bex", ignoreCase = true) || (bytes.size >= 4 && bytes[0] == 0x28.toByte() && bytes[1] == 0xB5.toByte() && bytes[2] == 0x2F.toByte() && bytes[3] == 0xFD.toByte())) {
            return ExtensionValidationResult.UnsupportedFormat(
                filename = filename,
                formatName = "Bloomee Extension Package (.bex)",
                detectedProvider = filename.substringBeforeLast("."),
                detectedVersion = "1.0",
                reason = "Bloomee compressed BEX (Zstandard) packages are not supported by USMAN native runtime. Please use declarative JSON or Echo EAPK packages."
            )
        }

        // 4. EAPK Echo Android Extension Package (.eapk)
        if (filename.endsWith(".eapk", ignoreCase = true)) {
            val result = EapkPackageParser.parseEapk(context, filename, bytes, tempFile)
            if (result !is ExtensionValidationResult.GenuineEcho && result !is ExtensionValidationResult.Supported) {
                 tempFile.delete()
            }
            return result
        }

        // 5. EPK Extension Package (.epk)
        if (filename.endsWith(".epk", ignoreCase = true) ||
            filename.endsWith(".epk.zip", ignoreCase = true)
        ) {
            return EpkPackageParser.parseEpk(filename, bytes)
        }

        // 6. ZIP Archive / APK / JAR check (PK\x03\x04: 0x50 0x4B 0x03 0x04)
        if ((bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()) ||
            filename.endsWith(".zip", ignoreCase = true) ||
            filename.endsWith(".apk", ignoreCase = true) ||
            filename.endsWith(".jar", ignoreCase = true)
        ) {
            return validateZipOrApk(filename, bytes)
        }

        // 7. JSON Manifest / Declarative Extension
        val text = try {
            String(bytes, StandardCharsets.UTF_8).trim()
        } catch (e: Exception) {
            ""
        }

        if (text.startsWith("{") || filename.endsWith(".json", ignoreCase = true)) {
            return validateJsonManifest(filename, text, formatLabel = "USMAN Declarative JSON Extension")
        }

        return ExtensionValidationResult.Invalid(
            filename = filename,
            error = "Unsupported file format. Please select an .eapk package, an .epk package, a .json declarative extension manifest, a .zip package, or an APK with embedded provider assets."
        )
    }

    private fun validateZipOrApk(filename: String, bytes: ByteArray): ExtensionValidationResult {
        var isApk = false
        var isJar = false
        var hasClassesDex = false
        var manifestJsonBytes: ByteArray? = null
        var manifestEntryName = ""

        try {
            val zis = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (name == "androidmanifest.xml" || name.endsWith("classes.dex")) {
                    isApk = true
                    if (name.endsWith("classes.dex")) hasClassesDex = true
                }
                if (name.endsWith(".class")) {
                    isJar = true
                }
                // Check for embedded provider manifests (in assets/ or root)
                if (manifestJsonBytes == null && (
                    name == "manifest.json" ||
                    name == "provider.json" ||
                    name == "extension.json" ||
                    name.endsWith("/manifest.json") ||
                    name.endsWith("/provider.json") ||
                    name.endsWith("/extension.json")
                )) {
                    manifestJsonBytes = zis.readBytes()
                    manifestEntryName = entry.name
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()
        } catch (e: Exception) {
            return ExtensionValidationResult.Invalid(
                filename = filename,
                error = "Corrupted archive file: ${e.localizedMessage ?: "Invalid zip format"}"
            )
        }

        // Case A: Archive contains an embedded declarative manifest (even inside an APK)
        if (manifestJsonBytes != null) {
            val manifestText = String(manifestJsonBytes, StandardCharsets.UTF_8).trim()
            val formatLabel = if (isApk) "APK-Derived Declarative Provider ($manifestEntryName)" else "ZIP Package (Declarative)"
            val result = validateJsonManifest(filename, manifestText, formatLabel = formatLabel)
            return when (result) {
                is ExtensionValidationResult.Supported -> {
                    result.copy(
                        formatName = formatLabel,
                        details = if (isApk) {
                            "Successfully extracted compatible declarative provider specification from APK package ($manifestEntryName). Sandboxed REST execution without executing external DEX bytecode."
                        } else {
                            "Packaged archive containing verified manifest specification and provider resources."
                        }
                    )
                }
                else -> result
            }
        }

        // Case B: APK without a declarative manifest (pure compiled DEX bytecode)
        if (isApk || filename.endsWith(".apk", ignoreCase = true)) {
            val baseName = filename.substringBeforeLast(".apk")
            return ExtensionValidationResult.UnsupportedFormat(
                filename = filename,
                formatName = "Android Application Package (.apk)",
                detectedProvider = baseName.ifBlank { "External Music App/Provider" },
                detectedVersion = if (hasClassesDex) "DEX Bytecode (No Declarative Manifest)" else "APK",
                reason = "The selected APK contains compiled Dalvik Executable (classes.dex) bytecode without an embedded declarative REST manifest. Dynamic Code Loading (DCL) of external executable code is prohibited for security and policy compliance. To use this provider in USMAN, extract or provide its declarative REST manifest (manifest.json or provider.json)."
            )
        }

        // Case C: JAR bytecode archive
        if (isJar || filename.endsWith(".jar", ignoreCase = true)) {
            val baseName = filename.substringBeforeLast(".jar")
            return ExtensionValidationResult.UnsupportedFormat(
                filename = filename,
                formatName = "Java Archive (.jar)",
                detectedProvider = baseName.ifBlank { "Java Library" },
                detectedVersion = "JAR / Class Bytecode",
                reason = "Compiled Java bytecode archive (.jar) detected. Binary class loading of external code is not supported. Please provide a declarative JSON or packaged REST manifest."
            )
        }

        return ExtensionValidationResult.Invalid(
            filename = filename,
            error = "Archive does not contain a valid 'manifest.json' or 'provider.json' file. Please ensure your extension archive includes a declarative provider manifest."
        )
    }

    private fun validateJsonManifest(
        filename: String,
        jsonStr: String,
        formatLabel: String
    ): ExtensionValidationResult {
        return try {
            val rootObj = JSONObject(jsonStr)
            val manifestObj = rootObj.optJSONObject("manifest") ?: rootObj

            // Validate mandatory fields
            val id = manifestObj.optString("id").takeIf { it.isNotBlank() }
                ?: return ExtensionValidationResult.Invalid(filename, "Manifest missing required 'id' field.")
            val name = manifestObj.optString("name").takeIf { it.isNotBlank() }
                ?: return ExtensionValidationResult.Invalid(filename, "Manifest missing required 'name' field.")
            val version = manifestObj.optString("version", "1.0.0")
            val author = manifestObj.optString("author", "Community")
            val desc = manifestObj.optString("description", "Imported Music Provider")
            val license = manifestObj.optString("license", "Open Source")

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

            // Check if declarative searchConfig is present
            val searchObj = rootObj.optJSONObject("searchConfig") ?: manifestObj.optJSONObject("searchConfig")
            val declarativeSpec = if (searchObj != null) {
                val urlTemplate = searchObj.optString("urlTemplate").ifBlank { searchObj.optString("endpointUrl") }
                if (urlTemplate.isNotBlank()) {
                    val isLocal = urlTemplate.startsWith("http://127.0.0.1") || urlTemplate.startsWith("http://localhost")
                    if (!isLocal && !urlTemplate.startsWith("https://", ignoreCase = true)) {
                        return ExtensionValidationResult.Invalid(
                            filename = filename,
                            error = "Insecure endpoint URL ('$urlTemplate'). Declarative extension endpoint URLs must use HTTPS."
                        )
                    }

                    val headersMap = mutableMapOf<String, String>()
                    val headersObj = searchObj.optJSONObject("headers")
                    if (headersObj != null) {
                        val keys = headersObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            headersMap[k] = headersObj.optString(k)
                        }
                    }

                    val manifest = ExtensionManifest(
                        id = id,
                        name = name,
                        version = version,
                        author = author,
                        description = desc,
                        iconUrl = manifestObj.optString("iconUrl").takeIf { it.isNotBlank() },
                        homepageUrl = manifestObj.optString("homepageUrl").takeIf { it.isNotBlank() },
                        license = license,
                        permissions = perms,
                        supportedLanguages = langs,
                        playbackType = playbackType,
                        isFullPlaybackSupported = playbackType == PlaybackType.FULL,
                        type = type,
                        repositoryName = "Imported Provider"
                    )

                    val searchConfig = DeclarativeSearchConfig(
                        urlTemplate = urlTemplate,
                        httpMethod = searchObj.optString("httpMethod", "GET"),
                        headers = headersMap,
                        tracksJsonPath = searchObj.optString("tracksJsonPath", ""),
                        idField = searchObj.optString("idField", "id"),
                        titleField = searchObj.optString("titleField", "title"),
                        artistField = searchObj.optString("artistField", "artist"),
                        albumField = searchObj.optString("albumField", "album"),
                        durationField = searchObj.optString("durationField", "duration"),
                        durationUnit = searchObj.optString("durationUnit", "seconds"),
                        streamUrlField = searchObj.optString("streamUrlField", "stream_url"),
                        artworkUrlField = searchObj.optString("artworkUrlField", "artwork_url"),
                        streamUrlTemplate = searchObj.optString("streamUrlTemplate").takeIf { it.isNotBlank() }
                    )

                    DeclarativeProviderSpec(manifest, searchConfig)
                } else null
            } else null

            val manifest = declarativeSpec?.manifest ?: ExtensionManifest(
                id = id,
                name = name,
                version = version,
                author = author,
                description = desc,
                iconUrl = manifestObj.optString("iconUrl").takeIf { it.isNotBlank() },
                homepageUrl = manifestObj.optString("homepageUrl").takeIf { it.isNotBlank() },
                license = license,
                permissions = perms,
                supportedLanguages = langs,
                playbackType = playbackType,
                isFullPlaybackSupported = playbackType == PlaybackType.FULL,
                type = type,
                repositoryName = "Imported Provider"
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
                packageFormat = if (declarativeSpec != null) "Declarative REST" else "Manifest Metadata"
            )

            val streamModeDesc = when (playbackType) {
                PlaybackType.FULL -> "Full-Length Audio Streaming"
                PlaybackType.EXTERNAL_SERVICE -> "Official Service Handoff"
                PlaybackType.PREVIEW -> "Preview Stream"
            }

            ExtensionValidationResult.Supported(
                pkg = pkg,
                declarativeSpec = declarativeSpec,
                formatName = formatLabel,
                details = "Mode: $streamModeDesc. ${if (declarativeSpec != null) "Live REST search & stream resolver configured." else "Catalog metadata provider configured."}"
            )
        } catch (e: Exception) {
            ExtensionValidationResult.Invalid(
                filename = filename,
                error = "JSON syntax error: ${e.localizedMessage ?: "Invalid JSON syntax"}"
            )
        }
    }
}
