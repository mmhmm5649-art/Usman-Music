package com.usman.musicplayer.model

/**
 * Independent per-device audio streaming quality preference.
 * Switching quality only changes the stream variant fetched by this device
 * and never alters other participants in a Listen Together room.
 */
enum class StreamingQuality(
    val key: String,
    val displayName: String,
    val shortLabel: String,
    val description: String,
    val targetBitrateKbps: Int,
    val preferenceOrder: List<String>
) {
    DATA_SAVER(
        key = "data_saver",
        displayName = "Data Saver",
        shortLabel = "Data Saver",
        description = "Lowest data usage (~96 kbps)",
        targetBitrateKbps = 96,
        preferenceOrder = listOf("96kbps", "48kbps", "160kbps", "12kbps", "320kbps")
    ),
    LOW(
        key = "low",
        displayName = "Low Quality",
        shortLabel = "Low",
        description = "Balanced mobile playback (~160 kbps)",
        targetBitrateKbps = 160,
        preferenceOrder = listOf("160kbps", "96kbps", "320kbps", "48kbps", "12kbps")
    ),
    HD(
        key = "hd",
        displayName = "HD Quality",
        shortLabel = "HD",
        description = "Highest available bitrate (up to 320 kbps)",
        targetBitrateKbps = 320,
        preferenceOrder = listOf("320kbps", "160kbps", "96kbps", "48kbps", "12kbps")
    );

    companion object {
        fun fromKey(key: String?): StreamingQuality {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: HD
        }
    }
}

/**
 * Represents an honestly resolved audio stream URL along with the actual bitrate
 * offered by the provider so the UI never fakes HD when only a lower tier exists.
 */
data class ResolvedStream(
    val url: String,
    val requestedQuality: StreamingQuality,
    val actualBitrateKbps: Int?,
    val isExactMatch: Boolean
) {
    val codecLabel: String?
        get() = StreamingQualitySelector.detectCodecFromUrl(url)

    val actualQualityLabel: String
        get() {
            val base = when {
                actualBitrateKbps == null -> requestedQuality.shortLabel
                actualBitrateKbps >= 320 -> "320 kbps HD"
                actualBitrateKbps >= 160 -> "160 kbps"
                actualBitrateKbps >= 96 -> "96 kbps"
                else -> "$actualBitrateKbps kbps"
            }
            val codec = codecLabel
            return if (!codec.isNullOrBlank()) "$base • $codec" else base
        }

    val isDowngradedFromHd: Boolean
        get() = requestedQuality == StreamingQuality.HD &&
            actualBitrateKbps != null &&
            actualBitrateKbps < 320

    val isFallbackVariant: Boolean
        get() = actualBitrateKbps != null && actualBitrateKbps != requestedQuality.targetBitrateKbps

    val fallbackNotice: String?
        get() = when {
            isDowngradedFromHd ->
                "Requested HD (320 kbps) • Provider supplied $actualBitrateKbps kbps"
            isFallbackVariant ->
                "Requested ${requestedQuality.displayName} (~${requestedQuality.targetBitrateKbps} kbps) • Playing $actualBitrateKbps kbps"
            else -> null
        }
}

object StreamingQualitySelector {

    private val bitrateRegex = Regex("""(?:_|/|-)(\d{2,3})(?:\.mp4|\.m4a|\.aac|kbps)""", RegexOption.IGNORE_CASE)

    fun parseBitrateKbps(qualityTag: String?): Int? {
        if (qualityTag.isNullOrBlank()) return null
        val digits = qualityTag.filter { it.isDigit() }
        return digits.toIntOrNull()?.takeIf { it in 12..1411 }
    }

    fun detectBitrateFromUrl(url: String?): Int? {
        if (url.isNullOrBlank()) return null
        val match = bitrateRegex.find(url) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()?.takeIf { it in 12..1411 }
    }

    fun detectCodecFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".m4a") || path.endsWith(".mp4") || path.endsWith(".aac") -> "AAC"
            path.endsWith(".mp3") -> "MP3"
            path.endsWith(".flac") -> "FLAC"
            path.endsWith(".ogg") || path.endsWith(".opus") -> "OGG"
            path.endsWith(".wav") -> "WAV"
            else -> null
        }
    }

    fun normalizeHttps(url: String): String {
        return if (url.startsWith("http://")) url.replaceFirst("http://", "https://") else url
    }

    /**
     * Selects the closest truthful provider stream variant for [requested].
     * Never invents or fakes a bitrate that is absent from [variants].
     */
    fun selectFromVariants(
        variants: List<Pair<String, String>>,
        requested: StreamingQuality
    ): ResolvedStream? {
        if (variants.isEmpty()) return null
        val normalized = variants
            .filter { (_, url) -> url.isNotBlank() }
            .map { (q, url) -> q.trim().lowercase() to normalizeHttps(url.trim()) }
        if (normalized.isEmpty()) return null

        for (preferredTag in requested.preferenceOrder) {
            val match = normalized.firstOrNull { (tag, _) -> tag == preferredTag }
            if (match != null) {
                val actualBitrate = parseBitrateKbps(match.first) ?: detectBitrateFromUrl(match.second)
                return ResolvedStream(
                    url = match.second,
                    requestedQuality = requested,
                    actualBitrateKbps = actualBitrate,
                    isExactMatch = actualBitrate == requested.targetBitrateKbps
                )
            }
        }

        // Fallback to best available matching target direction
        val sortedByBitrate = normalized
            .map { (tag, url) -> Triple(parseBitrateKbps(tag) ?: detectBitrateFromUrl(url), tag, url) }
            .sortedBy { it.first ?: Int.MAX_VALUE }

        val chosen = when (requested) {
            StreamingQuality.DATA_SAVER -> sortedByBitrate.first()
            StreamingQuality.LOW -> sortedByBitrate.minByOrNull { kotlin.math.abs((it.first ?: 160) - 160) }
                ?: sortedByBitrate.first()
            StreamingQuality.HD -> sortedByBitrate.last()
        }
        return ResolvedStream(
            url = chosen.third,
            requestedQuality = requested,
            actualBitrateKbps = chosen.first,
            isExactMatch = chosen.first == requested.targetBitrateKbps
        )
    }

    /**
     * Adapts a known JioSaavn CDN URL ONLY downward (never upward blindly) when
     * the caller has a verified high-bitrate URL and wants Data Saver or Low Quality.
     */
    fun adaptVerifiedCdnUrlDownwardOnly(
        url: String,
        requested: StreamingQuality
    ): ResolvedStream {
        val httpsUrl = normalizeHttps(url)
        val detected = detectBitrateFromUrl(httpsUrl)

        if (detected != null && requested.targetBitrateKbps < detected) {
            val targetSuffix = "_${requested.targetBitrateKbps}.mp4"
            val currentSuffix = "_${detected}.mp4"
            if (httpsUrl.contains(currentSuffix)) {
                val adapted = httpsUrl.replace(currentSuffix, targetSuffix)
                return ResolvedStream(
                    url = adapted,
                    requestedQuality = requested,
                    actualBitrateKbps = requested.targetBitrateKbps,
                    isExactMatch = true
                )
            }
        }

        return ResolvedStream(
            url = httpsUrl,
            requestedQuality = requested,
            actualBitrateKbps = detected,
            isExactMatch = detected == requested.targetBitrateKbps
        )
    }
}
