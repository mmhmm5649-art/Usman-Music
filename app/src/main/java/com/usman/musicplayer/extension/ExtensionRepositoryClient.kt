package com.usman.musicplayer.extension

import com.usman.musicplayer.sdk.PlaybackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Remote GitHub and Bloomee repository client.
 * Discovers and synchronizes extensions from Bloomee and GitHub repositories.
 */
class ExtensionRepositoryClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {

    suspend fun fetchRepositoryExtensions(repo: ExtensionRepository): Result<List<ExtensionPackage>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(repo.url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) USMAN_MUSIC_PLAYER/1.0")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code} fetching ${repo.name}"))
            }

            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                return@withContext Result.success(emptyList())
            }

            // Check if this is a Bloomee repository list
            if (body.contains("\"repositories\"") && body.contains("\"bex-factory.json\"") || repo.url.contains("BloomeeTunes")) {
                return@withContext parseBloomeeRepositoryList(body, repo.name)
            } else if (body.contains("\"plugins\"") && body.contains("content-resolver")) {
                return@withContext parseBloomeeFactory(body, repo.name)
            } else {
                return@withContext parseStandardRepositoryCatalog(body, repo.name)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun parseBloomeeRepositoryList(jsonStr: String, repoName: String): Result<List<ExtensionPackage>> {
        return try {
            val root = JSONObject(jsonStr)
            val repos = root.optJSONArray("repositories") ?: return Result.success(emptyList())
            val allPackages = mutableListOf<ExtensionPackage>()

            for (i in 0 until repos.length()) {
                val rObj = repos.optJSONObject(i) ?: continue
                val factoryUrl = rObj.optString("url")
                if (factoryUrl.isNotBlank()) {
                    val req = Request.Builder()
                        .url(factoryUrl)
                        .header("User-Agent", "Mozilla/5.0 USMAN_MUSIC_PLAYER/1.0")
                        .build()
                    val res = client.newCall(req).execute()
                    if (res.isSuccessful) {
                        val factoryBody = res.body?.string().orEmpty()
                        val subResult = parseBloomeeFactory(factoryBody, repoName)
                        subResult.onSuccess { allPackages.addAll(it) }
                    }
                }
            }

            Result.success(allPackages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseBloomeeFactory(jsonStr: String, repoName: String): Result<List<ExtensionPackage>> {
        return try {
            val root = JSONObject(jsonStr)
            val plugins = root.optJSONArray("plugins") ?: return Result.success(emptyList())
            val list = mutableListOf<ExtensionPackage>()

            for (i in 0 until plugins.length()) {
                val p = plugins.optJSONObject(i) ?: continue
                val id = p.optString("id")
                val name = p.optString("name")
                val version = p.optString("version", "1")
                val desc = p.optString("description")
                val typeStr = p.optString("type")
                val downloadUrl = p.optString("download_url")
                val thumbUrl = p.optString("thumbnail_url")
                val assetName = p.optString("asset_name")
                val publisherObj = p.optJSONObject("publisher")
                val author = publisherObj?.optString("name") ?: "Bloomee Community"

                val extType = when (typeStr) {
                    "content-resolver" -> ExtensionType.CONTENT_RESOLVER
                    "content-importer" -> ExtensionType.CONTENT_IMPORTER
                    "lyrics-provider" -> ExtensionType.LYRICS_PROVIDER
                    "chart-provider" -> ExtensionType.CHART_PROVIDER
                    else -> ExtensionType.COMMUNITY_CATALOG
                }

                val isFull = id.contains("ytmusic") || id.contains("ytvideo") || id.contains("audius")
                val playbackType = if (isFull) PlaybackType.FULL else PlaybackType.EXTERNAL_SERVICE

                val manifest = ExtensionManifest(
                    id = id,
                    name = formatExtensionName(name),
                    version = version,
                    author = author,
                    description = desc,
                    iconUrl = thumbUrl.takeIf { it.isNotBlank() },
                    license = "Open Source / Bex",
                    permissions = listOf("INTERNET", "RESOLVER"),
                    supportedLanguages = if (id.contains("jisaavn")) listOf("Telugu", "Hindi", "Tamil", "Punjabi") else listOf("Global"),
                    playbackType = playbackType,
                    isFullPlaybackSupported = isFull,
                    type = extType,
                    downloadUrl = downloadUrl.takeIf { it.isNotBlank() },
                    packageAsset = assetName.takeIf { it.isNotBlank() },
                    repositoryName = repoName
                )

                list.add(
                    ExtensionPackage(
                        manifest = manifest,
                        isInstalled = false,
                        isEnabled = false,
                        isBuiltIn = false,
                        installedVersion = null,
                        latestVersion = version,
                        hasUpdate = false
                    )
                )
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseStandardRepositoryCatalog(jsonStr: String, repoName: String): Result<List<ExtensionPackage>> {
        return try {
            val root = JSONObject(jsonStr)
            val extensionsArray = root.optJSONArray("extensions")
                ?: root.optJSONArray("plugins")
                ?: return Result.success(emptyList())

            val list = mutableListOf<ExtensionPackage>()
            for (i in 0 until extensionsArray.length()) {
                val obj = extensionsArray.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val name = obj.optString("name")
                val version = obj.optString("version", "1.0.0")
                val author = obj.optString("author", "Community")
                val desc = obj.optString("description")
                val license = obj.optString("license", "MIT")

                val sha256 = obj.optString("sha256").takeIf { it.isNotBlank() }
                val category = obj.optString("category").ifBlank { "Telugu Music" }

                val langs = mutableListOf<String>()
                val langArr = obj.optJSONArray("supportedLanguages")
                if (langArr != null) {
                    for (l in 0 until langArr.length()) langs.add(langArr.optString(l))
                } else {
                    langs.addAll(listOf("Telugu", "Hindi", "English"))
                }

                val manifest = ExtensionManifest(
                    id = id,
                    name = name,
                    version = version,
                    author = author,
                    description = desc,
                    iconUrl = obj.optString("iconUrl").takeIf { it.isNotBlank() },
                    license = license,
                    permissions = listOf("INTERNET", "STREAMING"),
                    supportedLanguages = langs,
                    playbackType = PlaybackType.FULL,
                    isFullPlaybackSupported = true,
                    type = ExtensionType.CONTENT_RESOLVER,
                    downloadUrl = obj.optString("downloadUrl").takeIf { it.isNotBlank() },
                    repositoryName = repoName,
                    sha256 = sha256,
                    category = category
                )

                list.add(
                    ExtensionPackage(
                        manifest = manifest,
                        isInstalled = false,
                        isEnabled = false,
                        isBuiltIn = false,
                        latestVersion = version
                    )
                )
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadPackageBytes(downloadUrl: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        val trimmedUrl = downloadUrl.trim()
        if (trimmedUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Download URL cannot be empty."))
        }

        val isLocal = trimmedUrl.startsWith("http://127.0.0.1") ||
                trimmedUrl.startsWith("http://localhost") ||
                trimmedUrl.contains("127.0.0.1:") ||
                trimmedUrl.contains("localhost:")

        if (!isLocal && !trimmedUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(
                SecurityException("Insecure download URL blocked ('$trimmedUrl'). Extension downloads must use HTTPS.")
            )
        }

        try {
            val request = Request.Builder()
                .url(trimmedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) USMAN_MUSIC_PLAYER/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed package download (HTTP ${response.code}) from ${sanitizedUrl(trimmedUrl)}")
                )
            }

            val responseBody = response.body
                ?: return@withContext Result.failure(Exception("Empty response body from ${sanitizedUrl(trimmedUrl)}"))

            val contentLength = responseBody.contentLength()
            if (contentLength > 25 * 1024 * 1024L) {
                return@withContext Result.failure(
                    IllegalStateException("Package size (${contentLength / (1024 * 1024)} MB) exceeds maximum allowed download limit (25 MB).")
                )
            }

            val bytes = responseBody.bytes()
            if (bytes.size > 25 * 1024 * 1024) {
                return@withContext Result.failure(
                    IllegalStateException("Downloaded package size (${bytes.size / (1024 * 1024)} MB) exceeds maximum allowed limit (25 MB).")
                )
            }

            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sanitizedUrl(url: String): String {
        return try {
            val parsed = java.net.URI(url)
            "${parsed.scheme}://${parsed.host}${if (parsed.port != -1) ":${parsed.port}" else ""}${parsed.path}"
        } catch (e: Exception) {
            url.substringBefore("?")
        }
    }

    private fun formatExtensionName(raw: String): String {
        return raw.split("-", "_", ".").joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
