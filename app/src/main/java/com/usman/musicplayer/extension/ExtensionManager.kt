package com.usman.musicplayer.extension

import android.content.Context
import android.util.Log
import android.net.Uri
import android.provider.OpenableColumns
import com.usman.musicplayer.data.repository.providers.DeclarativeMusicProvider
import com.usman.musicplayer.sdk.ProviderRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Central orchestrator for the USMAN Music Player extension ecosystem.
 * Coordinates discovery, installation, updates, persistence, and registration
 * with the underlying ProviderRegistry.
 */
object ExtensionManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var storage: ExtensionStorage? = null
    private val client = ExtensionRepositoryClient()

    private val _installedExtensions = MutableStateFlow<List<ExtensionPackage>>(emptyList())
    val installedExtensions: StateFlow<List<ExtensionPackage>> = _installedExtensions.asStateFlow()

    private val _availableExtensions = MutableStateFlow<List<ExtensionPackage>>(emptyList())
    val availableExtensions: StateFlow<List<ExtensionPackage>> = _availableExtensions.asStateFlow()

    private val _repositories = MutableStateFlow<List<ExtensionRepository>>(emptyList())
    val repositories: StateFlow<List<ExtensionRepository>> = _repositories.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val s = ExtensionStorage(context.applicationContext)
        storage = s

        // Ensure ProviderRegistry is initialized
        ProviderRegistry.init(context.applicationContext)

        // Restore imported declarative REST music providers
        val declarativeSpecs = s.loadDeclarativeSpecs()
        for (spec in declarativeSpecs) {
            val provider = DeclarativeMusicProvider(spec)
            ProviderRegistry.registerProvider(provider, enabled = true)
        }

        val installed = s.loadInstalledExtensions()
        _installedExtensions.value = installed

        // Restore native adapter music providers (e.g. Echo EAPK adapters)
        for (pkg in installed) {
            if (pkg.isEnabled && pkg.isInstalled) {
                val adapterId = pkg.manifest.adapterId
                if (!adapterId.isNullOrBlank() && adapterId != "echo_runtime") {
                    val nativeProvider = EchoExtensionAdapterRegistry.createProvider(adapterId, pkg.manifest.version)
                    if (nativeProvider != null) {
                        ProviderRegistry.registerProvider(nativeProvider, enabled = true)
                    }
                }
            }
        }

        // Restore Genuine Echo Runtime extensions (DEX-loaded)
        val echoDir = java.io.File(context.filesDir, "echo_extensions")
        if (echoDir.exists()) {
            echoDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".eapk")) {
                    val extId = file.nameWithoutExtension
                    val pkgMatch = installed.find { it.manifest.id == extId }
                    if (pkgMatch != null && pkgMatch.isInstalled) {
                        EchoExtensionLoader.loadEchoExtension(context, file).onSuccess { provider ->
                            ProviderRegistry.registerProvider(provider, enabled = pkgMatch.isEnabled)
                        }.onFailure {
                            android.util.Log.e("ExtensionManager", "Failed to restore Echo runtime extension: $extId", it)
                        }
                    }
                }
            }
        }

        val repos = s.loadRepositories()
        _repositories.value = repos

        // Sync ProviderRegistry enablement states with installed extensions
        syncProviderRegistry(installed)

        // Initial background sync for store extensions
        scope.launch {
            syncRepositories()
        }
    }

    private fun syncProviderRegistry(installed: List<ExtensionPackage>) {
        val mapping = mapOf(
            "usman.ext.audius" to "audius_provider",
            "usman.ext.ccmixter" to "ccmixter_provider",
            "usman.ext.archiveorg" to "archive_org_provider",
            "usman.ext.jiosaavn" to "jiosaavn_hub_provider"
        )

        for (pkg in installed) {
            val providerId = mapping[pkg.manifest.id] ?: pkg.manifest.id
            ProviderRegistry.setProviderEnabled(providerId, pkg.isEnabled && pkg.isInstalled)
        }
    }

    fun toggleExtension(extensionId: String, enabled: Boolean) {
        _installedExtensions.update { list ->
            list.map { pkg ->
                if (pkg.manifest.id == extensionId) {
                    pkg.copy(isEnabled = enabled)
                } else {
                    pkg
                }
            }
        }

        storage?.saveInstalledExtensions(_installedExtensions.value)
        syncProviderRegistry(_installedExtensions.value)
    }

    fun installExtension(extension: ExtensionPackage) {
        val now = System.currentTimeMillis()
        val installedPkg = extension.copy(
            isInstalled = true,
            isEnabled = true,
            installedVersion = extension.latestVersion ?: extension.manifest.version,
            installDate = now,
            lastUpdated = now,
            hasUpdate = false
        )

        _installedExtensions.update { current ->
            val updated = current.filter { it.manifest.id != extension.manifest.id }.toMutableList()
            updated.add(installedPkg)
            updated
        }

        storage?.saveInstalledExtensions(_installedExtensions.value)
        syncProviderRegistry(_installedExtensions.value)
    }

    fun uninstallExtension(extensionId: String) {
        _installedExtensions.update { current ->
            current.map { pkg ->
                if (pkg.manifest.id == extensionId) {
                    if (pkg.isBuiltIn) {
                        // Built-ins cannot be deleted, just disabled and marked uninstalled
                        pkg.copy(isInstalled = false, isEnabled = false)
                    } else {
                        null
                    }
                } else {
                    pkg
                }
            }.filterNotNull()
        }

        // Clean up declarative provider if this was an imported provider
        storage?.removeDeclarativeSpec(extensionId)
        ProviderRegistry.unregisterProvider(extensionId)

        // Clean up physical Echo extension file if it exists
        try {
            val context = storage?.context // Assuming storage has context
            if (context != null) {
                val echoDir = File(context.filesDir, "echo_extensions")
                val file = File(echoDir, "$extensionId.eapk")
                if (file.exists()) {
                    file.delete()
                    android.util.Log.i("ExtensionManager", "Deleted Echo extension file: $extensionId.eapk")
                }
                
                // Clean up settings
                context.getSharedPreferences("echo_settings_$extensionId", Context.MODE_PRIVATE)
                    .edit().clear().apply()
            }
        } catch (e: Exception) {
            android.util.Log.e("ExtensionManager", "Failed to delete Echo extension file", e)
        }

        storage?.saveInstalledExtensions(_installedExtensions.value)
        syncProviderRegistry(_installedExtensions.value)
    }

    fun importExtensionFile(context: Context, uri: Uri): ExtensionValidationResult {
        var fileName = "unknown_extension"
        var fileSize = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            fileName = uri.lastPathSegment ?: fileName
        }

        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ExtensionValidationResult.Invalid(fileName, "Unable to open input stream for selected file.")
            inputStream.use { stream ->
                ExtensionFileValidator.validateFile(context, fileName, fileSize, stream)
            }
        } catch (e: Exception) {
            ExtensionValidationResult.Invalid(fileName, "Failed to read file: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    fun installImportedExtension(result: ExtensionValidationResult.Supported) {
        val now = System.currentTimeMillis()
        val pkg = result.pkg.copy(
            isInstalled = true,
            isEnabled = true,
            installDate = now,
            lastUpdated = now
        )

        installExtension(pkg)

        if (result.declarativeSpec != null) {
            storage?.addDeclarativeSpec(result.declarativeSpec)
            val provider = DeclarativeMusicProvider(result.declarativeSpec)
            ProviderRegistry.registerProvider(provider, enabled = true)
        }

        if (result.customProvider != null) {
            ProviderRegistry.registerProvider(result.customProvider, enabled = true)
        }
    }

    fun installGenuineEchoExtension(context: Context, result: ExtensionValidationResult.GenuineEcho) {
        val now = System.currentTimeMillis()
        
        // 1. Move EAPK to permanent storage
        val extDir = File(context.filesDir, "echo_extensions").apply { if (!exists()) mkdirs() }
        val permanentFile = File(extDir, "${result.pkg.manifest.id}.eapk")
        result.eapkFile.copyTo(permanentFile, overwrite = true)
        
        // 2. Load and Register
        val loadResult = EchoExtensionLoader.loadEchoExtension(context, permanentFile)
        loadResult.onSuccess { provider ->
            val pkg = result.pkg.copy(
                isInstalled = true,
                isEnabled = true,
                installDate = now,
                lastUpdated = now,
                originalFileName = permanentFile.absolutePath // Store local path
            )
            installExtension(pkg)
            ProviderRegistry.registerProvider(provider, enabled = true)
        }.onFailure {
            // Handle failure? Maybe update UI state
            it.printStackTrace()
        }
    }

    fun updateExtension(extension: ExtensionPackage) {
        val now = System.currentTimeMillis()
        _installedExtensions.update { current ->
            current.map { pkg ->
                if (pkg.manifest.id == extension.manifest.id) {
                    pkg.copy(
                        manifest = extension.manifest,
                        installedVersion = extension.latestVersion ?: extension.manifest.version,
                        latestVersion = extension.latestVersion ?: extension.manifest.version,
                        lastUpdated = now,
                        hasUpdate = false
                    )
                } else {
                    pkg
                }
            }
        }

        storage?.saveInstalledExtensions(_installedExtensions.value)
        syncProviderRegistry(_installedExtensions.value)
    }

    fun addRepository(name: String, url: String, description: String = "Community GitHub Repository") {
        val newRepo = ExtensionRepository(
            id = "repo_${System.currentTimeMillis()}",
            name = name.ifBlank { "Custom Repository" },
            url = url.trim(),
            description = description,
            isEnabled = true,
            isOfficial = false,
            lastSyncTime = 0L,
            extensionCount = 0
        )

        _repositories.update { current -> current + newRepo }
        storage?.saveRepositories(_repositories.value)

        scope.launch {
            syncRepositories()
        }
    }

    fun removeRepository(repoId: String) {
        _repositories.update { current -> current.filter { it.id != repoId } }
        storage?.saveRepositories(_repositories.value)
    }

    fun toggleRepository(repoId: String, enabled: Boolean) {
        _repositories.update { current ->
            current.map { if (it.id == repoId) it.copy(isEnabled = enabled) else it }
        }
        storage?.saveRepositories(_repositories.value)
        scope.launch {
            syncRepositories()
        }
    }

    fun syncRepositories() {
        if (_isSyncing.value) return
        _isSyncing.value = true

        scope.launch {
            val allDiscovered = mutableListOf<ExtensionPackage>()

            val repos = _repositories.value.filter { it.isEnabled }
            for (repo in repos) {
                val result = client.fetchRepositoryExtensions(repo)
                result.onSuccess { discovered ->
                    allDiscovered.addAll(discovered)
                    // Update repo sync stats
                    _repositories.update { rList ->
                        rList.map { r ->
                            if (r.id == repo.id) {
                                r.copy(
                                    lastSyncTime = System.currentTimeMillis(),
                                    extensionCount = discovered.size
                                )
                            } else r
                        }
                    }
                }
            }

            // Atomically check for updates on the latest installed extensions state so concurrent
            // installs/uninstalls during network sync are never overwritten by a stale snapshot.
            var latestUpdatedInstalled: List<ExtensionPackage> = emptyList()
            _installedExtensions.update { currentInstalled ->
                val updated = currentInstalled.map { installed ->
                    val match = allDiscovered.find { it.manifest.id == installed.manifest.id }
                    if (match != null && match.latestVersion != null && isNewerVersion(match.latestVersion, installed.installedVersion)) {
                        installed.copy(hasUpdate = true, latestVersion = match.latestVersion)
                    } else {
                        installed
                    }
                }
                latestUpdatedInstalled = updated
                updated
            }
            storage?.saveInstalledExtensions(latestUpdatedInstalled)

            // Deduplicate discovered extensions and mark installed states
            val distinctAvailable = allDiscovered.distinctBy { it.manifest.id }.map { ext ->
                val isInst = latestUpdatedInstalled.any { it.manifest.id == ext.manifest.id && it.isInstalled }
                ext.copy(isInstalled = isInst)
            }

            _availableExtensions.value = distinctAvailable
            _isSyncing.value = false
        }
    }

    suspend fun downloadAndInstallPackage(context: Context, pkg: ExtensionPackage): Result<ExtensionValidationResult.Supported> = withContext(Dispatchers.IO) {
        val url = pkg.manifest.downloadUrl
            ?: return@withContext Result.failure(IllegalArgumentException("Extension '${pkg.manifest.name}' has no download URL declared."))

        val downloadResult = client.downloadPackageBytes(url)
        if (downloadResult.isFailure) {
            return@withContext Result.failure(downloadResult.exceptionOrNull() ?: Exception("Package download failed."))
        }

        val bytes = downloadResult.getOrThrow()

        // SHA-256 Checksum Verification
        val expectedSha = pkg.manifest.sha256?.trim()?.lowercase()
        if (!expectedSha.isNullOrBlank()) {
            val calculatedSha = calculateSha256(bytes)
            if (!calculatedSha.equals(expectedSha, ignoreCase = true)) {
                return@withContext Result.failure(
                    SecurityException("SHA-256 checksum mismatch for '${pkg.manifest.name}'. Expected '$expectedSha', but calculated '$calculatedSha'. Package installation rejected.")
                )
            }
        }

        val filename = url.substringAfterLast("/").substringBefore("?").ifBlank { "${pkg.manifest.id}.epk" }

        val valResult = ExtensionFileValidator.validateFile(context, filename, bytes.size.toLong(), java.io.ByteArrayInputStream(bytes))
        if (valResult !is ExtensionValidationResult.Supported) {
            val reason = when (valResult) {
                is ExtensionValidationResult.UnsupportedFormat -> valResult.reason
                is ExtensionValidationResult.Invalid -> valResult.error
                else -> "Validation failed for downloaded package."
            }
            return@withContext Result.failure(IllegalStateException(reason))
        }

        installImportedExtension(valResult)
        Result.success(valResult)
    }

    private fun calculateSha256(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun rollbackExtension(extensionId: String): Result<Unit> {
        val backupSpec = storage?.rollbackDeclarativeSpec(extensionId)
            ?: return Result.failure(IllegalStateException("No backup version available for extension '$extensionId'."))

        val restoredProvider = DeclarativeMusicProvider(backupSpec)
        ProviderRegistry.registerProvider(restoredProvider, enabled = true)

        _installedExtensions.update { current ->
            current.map { pkg ->
                if (pkg.manifest.id == extensionId) {
                    pkg.copy(
                        manifest = backupSpec.manifest,
                        installedVersion = backupSpec.manifest.version,
                        hasUpdate = true
                    )
                } else pkg
            }
        }
        storage?.saveInstalledExtensions(_installedExtensions.value)
        return Result.success(Unit)
    }

    suspend fun testSearchExtension(extensionId: String, query: String = "telugu"): Result<List<com.usman.musicplayer.sdk.Song>> = withContext(Dispatchers.IO) {
        val provider = ProviderRegistry.providers.find { it.metadata.id == extensionId }
            ?: return@withContext Result.failure(IllegalStateException("Extension provider '$extensionId' is not active."))

        provider.search(query, 5)
    }

    private fun isNewerVersion(remote: String, installed: String?): Boolean {
        if (installed == null) return true
        val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val iParts = installed.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(rParts.size, iParts.size)) {
            val r = rParts.getOrElse(i) { 0 }
            val inst = iParts.getOrElse(i) { 0 }
            if (r > inst) return true
            if (r < inst) return false
        }
        return false
    }
}
