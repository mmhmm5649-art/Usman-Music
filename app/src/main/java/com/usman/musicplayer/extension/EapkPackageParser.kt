package com.usman.musicplayer.extension

import android.content.Context
import android.content.pm.PackageManager
import com.usman.musicplayer.sdk.PlaybackType
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Dedicated parser and inspector for .eapk (Echo Extension Package) files.
 */
object EapkPackageParser {

    private const val MAX_UNCOMPRESSED_SIZE = 35 * 1024 * 1024L
    private const val MAX_ENTRIES = 600

    fun parseEapk(
        context: Context,
        filename: String,
        bytes: ByteArray,
        eapkFile: File
    ): ExtensionValidationResult {
        if (bytes.isEmpty()) {
            return ExtensionValidationResult.Invalid(filename, "Selected .eapk file is empty.")
        }

        // 1. Genuine Echo identification via PackageManager
        val pm = context.packageManager
        val packageInfo = pm.getPackageArchiveInfo(eapkFile.absolutePath, PackageManager.GET_META_DATA)
        
        if (packageInfo != null) {
            val appInfo = packageInfo.applicationInfo
            val metaData = appInfo?.metaData
            val extClass = metaData?.getString("extClass")

            if (extClass != null) {
                val extId = metaData.getString("extId") ?: packageInfo.packageName
                val extName = metaData.getString("extName") ?: filename.substringBeforeLast(".")
                val extAuthor = metaData.getString("extAuthor") ?: "Echo Developer"
                val extDesc = metaData.getString("extDescription") ?: "Genuine Echo extension package."

                val manifest = ExtensionManifest(
                    id = extId,
                    name = extName,
                    version = packageInfo.versionName ?: "1.0.0",
                    author = extAuthor,
                    description = extDesc,
                    playbackType = PlaybackType.FULL,
                    isFullPlaybackSupported = true,
                    type = ExtensionType.CONTENT_RESOLVER,
                    repositoryName = "Genuine Echo EAPK",
                    adapterId = "echo_runtime"
                )

                val pkg = ExtensionPackage(
                    manifest = manifest,
                    isInstalled = false,
                    isEnabled = true,
                    installedVersion = packageInfo.versionName ?: "1.0.0",
                    latestVersion = packageInfo.versionName ?: "1.0.0",
                    installDate = System.currentTimeMillis(),
                    isImported = true,
                    originalFileName = filename,
                    packageFormat = "EAPK (Genuine Echo Runtime)"
                )

                return ExtensionValidationResult.GenuineEcho(
                    pkg = pkg,
                    eapkFile = eapkFile,
                    details = "Detected genuine Echo extension '$extName' ($extId). Ready for runtime execution via USMAN Echo SDK Bridge."
                )
            }
        }

        // 2. Signature inspection: Must start with ZIP header PK\x03\x04
        val isZip = bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x02.toByte() && // Error in check previously? No, 0x03 0x04
                bytes[3] == 0x04.toByte()

        // Wait, standard ZIP is 50 4B 03 04. Correcting.
        val isZipActual = bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() &&
                bytes[3] == 0x04.toByte()

        if (!isZipActual) {
            val text = try { String(bytes, StandardCharsets.UTF_8).trim() } catch (e: Exception) { "" }
            if (text.startsWith("{")) {
                return EpkPackageParser.parseJsonDescriptor(filename, text, "standalone.eapk.json", packageFormatLabel = "EAPK Declarative Descriptor")
            }
            return ExtensionValidationResult.Invalid(filename, "Invalid .eapk signature.")
        }

        // 3. Asset scanning for declarative fallback
        return parseZipEapkForDeclarative(filename, bytes)
    }

    private fun parseZipEapkForDeclarative(filename: String, bytes: ByteArray): ExtensionValidationResult {
        val fileMap = mutableMapOf<String, ByteArray>()
        var hasAndroidManifest = false
        var hasClassesDex = false
        var androidManifestBytes: ByteArray? = null

        try {
            val zis = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zis.nextEntry
            var count = 0
            while (entry != null) {
                if (count++ > MAX_ENTRIES) break
                val name = entry.name.lowercase()
                if (name == "androidmanifest.xml") {
                    hasAndroidManifest = true
                    androidManifestBytes = zis.readBytes()
                }
                if (name.endsWith("classes.dex")) hasClassesDex = true
                if (name.endsWith(".json")) fileMap[entry.name] = zis.readBytes()
                
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()
        } catch (e: Exception) {
            return ExtensionValidationResult.Invalid(filename, "Corrupted .eapk archive.")
        }

        val manifestCandidates = listOf("assets/manifest.json", "assets/extension.json", "manifest.json", "echo.json")
        for (cand in manifestCandidates) {
            val match = fileMap.keys.find { it.equals(cand, ignoreCase = true) }
            if (match != null) {
                val text = String(fileMap[match]!!, StandardCharsets.UTF_8)
                return EpkPackageParser.parseJsonDescriptor(filename, text, match, packageFormatLabel = "EAPK (Declarative)")
            }
        }

        if (hasAndroidManifest || hasClassesDex) {
             return ExtensionValidationResult.UnsupportedFormat(filename, "Echo Android Package", null, null, "No Echo metadata or declarative manifest found.")
        }

        return ExtensionValidationResult.Invalid(filename, "No valid content found in .eapk.")
    }
}
