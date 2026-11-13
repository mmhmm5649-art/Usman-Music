package com.usman.musicplayer.model

data class ArtistDetailsData(
    val id: String,
    val name: String,
    val artworkUri: String? = null,
    val subtitle: String? = null,
    val languages: List<String> = emptyList(),
    val sourceProvider: String = "JioSaavn",
    val popularSongs: List<Song> = emptyList(),
    val latestReleases: List<Song> = emptyList(),
    val albums: List<AlbumSearchResult> = emptyList(),
    val playlists: List<CuratedPlaylist> = emptyList()
) {
    val allPlayableSongs: List<Song>
        get() = ArtistMatchingPolicy.deduplicateSongs(popularSongs + latestReleases)
}

object ArtistMatchingPolicy {

    private val DELIMITERS = Regex("[,&]|\\b(?:feat\\.?|ft\\.?|with|and|x)\\b", RegexOption.IGNORE_CASE)
    private val NON_ALNUM = Regex("[^a-z0-9\\s]")
    private val MULTI_SPACE = Regex("\\s+")

    private val ARTIST_ALIASES: Map<String, Set<String>> = mapOf(
        "a r rahman" to setOf("ar rahman", "a r rahman", "rahman", "allah rakha rahman"),
        "vishal shekhar" to setOf("vishal shekhar", "vishal dadlani", "shekhar ravjiani", "vishal & shekhar"),
        "devi sri prasad" to setOf("devi sri prasad", "dsp"),
        "thaman s" to setOf("thaman s", "s thaman", "thaman", "ss thaman"),
        "anirudh ravichander" to setOf("anirudh ravichander", "anirudh"),
        "yuvan shankar raja" to setOf("yuvan shankar raja", "yuvan"),
        "santhosh narayanan" to setOf("santhosh narayanan", "santosh narayanan"),
        "sid sriram" to setOf("sid sriram"),
        "armaan malik" to setOf("armaan malik"),
        "shreya ghoshal" to setOf("shreya ghoshal"),
        "arijit singh" to setOf("arijit singh"),
        "pritam" to setOf("pritam", "pritam chakraborty"),
        "sonu nigam" to setOf("sonu nigam"),
        "neha kakkar" to setOf("neha kakkar"),
        "taylor swift" to setOf("taylor swift"),
        "ed sheeran" to setOf("ed sheeran"),
        "the weeknd" to setOf("the weeknd", "weeknd"),
        "justin bieber" to setOf("justin bieber"),
        "bruno mars" to setOf("bruno mars"),
        "billie eilish" to setOf("billie eilish")
    )

    fun normalizeArtistName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.lowercase()
            .replace("&amp;", " ")
            .replace(NON_ALNUM, " ")
            .replace(MULTI_SPACE, " ")
            .trim()
    }

    private fun aliasesFor(artistName: String): Set<String> {
        val norm = normalizeArtistName(artistName)
        if (norm.isBlank()) return emptySet()
        val aliases = mutableSetOf(norm)
        ARTIST_ALIASES[norm]?.let { aliases.addAll(it) }
        return aliases
    }

    fun artistsMatch(candidateName: String?, targetArtistName: String): Boolean {
        val targetAliases = aliasesFor(targetArtistName)
        if (targetAliases.isEmpty()) return false
        val normCandidate = normalizeArtistName(candidateName)
        if (normCandidate.isBlank() || normCandidate == "unknown artist" || normCandidate == "various artists") {
            return false
        }
        if (normCandidate in targetAliases) return true

        val parts = candidateName.orEmpty()
            .split(DELIMITERS)
            .map { normalizeArtistName(it) }
            .filter { it.isNotBlank() }

        for (part in parts) {
            if (part in targetAliases) return true
            for (alias in targetAliases) {
                if (alias.length >= 5 && (part.contains(alias) || alias.contains(part))) {
                    // Ensure partial overlap is whole-word match to avoid false positives like "Sid" matching "Siddharth"
                    val aliasTokens = alias.split(' ').toSet()
                    val partTokens = part.split(' ').toSet()
                    if (aliasTokens.all { it in partTokens } || partTokens.all { it in aliasTokens }) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun songMatchesArtist(song: Song, targetArtistName: String): Boolean {
        return artistsMatch(song.artist, targetArtistName)
    }

    fun albumMatchesArtist(album: AlbumSearchResult, targetArtistName: String): Boolean {
        return artistsMatch(album.artist, targetArtistName)
    }

    fun deduplicateArtists(artists: List<OnlineArtistResult>): List<OnlineArtistResult> {
        val merged = LinkedHashMap<String, OnlineArtistResult>()
        for (artist in artists) {
            val key = normalizeArtistName(artist.name)
            if (key.isBlank()) continue
            val existing = merged[key]
            if (existing == null) {
                merged[key] = artist
            } else {
                val combinedLangs = (existing.languages + artist.languages)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                merged[key] = existing.copy(
                    id = existing.id.takeIf { isProviderArtistId(it) } ?: artist.id,
                    artworkUri = existing.artworkUri?.takeIf { it.isNotBlank() } ?: artist.artworkUri,
                    subtitle = existing.subtitle?.takeIf { it.isNotBlank() } ?: artist.subtitle,
                    languages = combinedLangs
                )
            }
        }
        return merged.values.toList()
    }

    fun deduplicateSongs(songs: List<Song>): List<Song> {
        val byKey = LinkedHashMap<String, Song>()
        for (song in songs) {
            val idKey = song.filePath.trim().takeIf { it.isNotBlank() && it != "0" }
                ?.let { "pid_$it" }
            val titleKey = "${normalizeArtistName(song.title)}|${normalizeArtistName(song.artist)}"
            val dedupKey = idKey ?: titleKey
            if (!byKey.containsKey(dedupKey)) {
                // Also avoid exact same normalized title+artist appearing twice under different IDs unless album differs clearly
                val duplicateTitleAlreadyPresent = byKey.values.any { existing ->
                    normalizeArtistName(existing.title) == normalizeArtistName(song.title) &&
                        normalizeArtistName(existing.artist) == normalizeArtistName(song.artist)
                }
                if (!duplicateTitleAlreadyPresent) {
                    byKey[dedupKey] = song
                }
            }
        }
        return byKey.values.toList()
    }

    fun deduplicateAlbums(albums: List<AlbumSearchResult>): List<AlbumSearchResult> {
        val seen = LinkedHashSet<String>()
        val result = mutableListOf<AlbumSearchResult>()
        for (album in albums) {
            val idKey = album.id.trim().takeIf { it.isNotBlank() && !it.startsWith("inferred_") }
            val titleKey = normalizeArtistName(album.title)
            val effectiveKey = idKey ?: titleKey
            if (effectiveKey.isNotBlank() && seen.add(effectiveKey) && (titleKey.isBlank() || seen.add("t_$titleKey"))) {
                result.add(album)
            }
        }
        return result
    }

    fun isProviderArtistId(id: String?): Boolean {
        val clean = id?.trim().orEmpty()
        if (clean.isBlank()) return false
        if (clean.contains(' ') || clean.contains('_')) return false
        return clean.all { it.isDigit() || it.isLetterOrDigit() } && !clean.startsWith("seed", ignoreCase = true)
    }
}
