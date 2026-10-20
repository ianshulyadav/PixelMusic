package com.unshoo.pixelmusic.utils

import com.unshoo.pixelmusic.data.feed.GeneratedTrack
import com.unshoo.pixelmusic.data.feed.RecentTrack
import com.unshoo.pixelmusic.data.feed.YouTubeMusicTrack
import com.unshoo.pixelmusic.data.model.Song

object ContentFilterUtils {

    val DEFAULT_FILTER_KEYWORDS = listOf("cover", "covers", "lofi", "lo-fi", "chillhop", "slowed", "reverb")

    private val LOFI_REGEX = Regex("""(?i)\b(lo-?fi|lo\s+fi|chillhop)\b|\b(slowed\s*(\+|&|and)\s*reverb|slowed\s+down)\b""")

    private val COVER_CONTAINED_REGEX = Regex(
        """(?i)(\(([^)]*?\bcovers?\b[^)]*?)\)|\[([^\]]*?\bcovers?\b[^\]]*?)\])"""
    )
    private val COVER_PHRASE_REGEX = Regex(
        """(?i)(-\s*|\s+\|\s*|\s+/\s*|:\s*)\bcovers?\b|\bcovers?\s+(by|version|song)\b|\b(song|acoustic|piano|guitar|violin|drum|female|male|rock|metal|jazz|punk|orchestral|orchestra|choral|choir|harp|flute|cello|fingerstyle|lo-?fi|slowed|chill)\s+covers?\b"""
    )
    private val COVER_SUFFIX_REGEX = Regex("""(?i)\bcovers?\s*$""")
    private val FALSE_POSITIVE_COVER_REGEX = Regex("""(?i)\b(under(\s+the)?|run\s+for|from\s+the)\s+covers?\s*$""")
    private val COVER_ARTIST_REGEX = Regex("""(?i)\b(cover|covers|tribute)\b""")

    private fun isBuiltInLofi(text: String): Boolean = LOFI_REGEX.containsMatchIn(text)

    private fun isFalsePositiveCover(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.equals("cover", ignoreCase = true) || trimmed.equals("covers", ignoreCase = true)) return true
        return FALSE_POSITIVE_COVER_REGEX.containsMatchIn(trimmed)
    }

    private fun isBuiltInCover(title: String, artist: String, album: String): Boolean {
        if (COVER_CONTAINED_REGEX.containsMatchIn(title) || COVER_PHRASE_REGEX.containsMatchIn(title)) return true
        if (COVER_SUFFIX_REGEX.containsMatchIn(title) && !isFalsePositiveCover(title)) return true
        if (COVER_ARTIST_REGEX.containsMatchIn(artist)) return true
        if (COVER_CONTAINED_REGEX.containsMatchIn(album) || COVER_PHRASE_REGEX.containsMatchIn(album)) return true
        if (COVER_SUFFIX_REGEX.containsMatchIn(album) && !isFalsePositiveCover(album)) return true
        return false
    }

    /**
     * Checks whether a track's metadata matches cover, lo-fi, or any custom user-configured filter keywords.
     */
    fun isCoverOrLofi(
        title: String?,
        artist: String?,
        album: String? = null,
        filterKeywords: Collection<String>? = null
    ): Boolean {
        val t = title.orEmpty()
        val a = artist.orEmpty()
        val al = album.orEmpty()

        val keywords = filterKeywords ?: DEFAULT_FILTER_KEYWORDS

        val hasCoverKeyword = keywords.any { it.equals("cover", ignoreCase = true) || it.equals("covers", ignoreCase = true) }
        val hasLofiKeyword = keywords.any {
            it.equals("lofi", ignoreCase = true) || it.equals("lo-fi", ignoreCase = true) ||
                it.equals("chillhop", ignoreCase = true) || it.equals("slowed", ignoreCase = true) ||
                it.equals("reverb", ignoreCase = true)
        }

        if (hasLofiKeyword && (isBuiltInLofi(t) || isBuiltInLofi(a) || isBuiltInLofi(al))) {
            return true
        }

        if (hasCoverKeyword && isBuiltInCover(t, a, al)) {
            return true
        }

        // Check any other user-defined keywords with word boundaries
        val customOtherKeywords = keywords.filterNot {
            it.equals("cover", ignoreCase = true) || it.equals("covers", ignoreCase = true) ||
                it.equals("lofi", ignoreCase = true) || it.equals("lo-fi", ignoreCase = true) ||
                it.equals("chillhop", ignoreCase = true) || it.equals("slowed", ignoreCase = true) ||
                it.equals("reverb", ignoreCase = true)
        }

        if (customOtherKeywords.isNotEmpty()) {
            for (keyword in customOtherKeywords) {
                val trimmed = keyword.trim()
                if (trimmed.isEmpty()) continue
                val pattern = Regex("""(?i)\b${Regex.escape(trimmed)}\b""")
                if (pattern.containsMatchIn(t) || pattern.containsMatchIn(a) || pattern.containsMatchIn(al)) {
                    return true
                }
            }
        }

        return false
    }

    fun isCoverOrLofi(song: Song, filterKeywords: Collection<String>? = null): Boolean =
        isCoverOrLofi(song.title, song.artist, song.album, filterKeywords)

    fun isCoverOrLofi(track: YouTubeMusicTrack, filterKeywords: Collection<String>? = null): Boolean =
        isCoverOrLofi(track.title, track.artist, track.album, filterKeywords)

    fun isCoverOrLofi(track: RecentTrack, filterKeywords: Collection<String>? = null): Boolean =
        isCoverOrLofi(track.name, track.artist.displayName, track.album.displayName, filterKeywords)

    fun isCoverOrLofi(track: GeneratedTrack, filterKeywords: Collection<String>? = null): Boolean =
        isCoverOrLofi(track.name, track.artist, track.album, filterKeywords)

    /**
     * Checks whether a track's metadata contains any of the user's custom filter keywords.
     * Case-insensitive match on title, artist, or album.
     */
    fun matchesFilterKeywords(
        title: String?,
        artist: String?,
        album: String? = null,
        filterKeywords: Collection<String>? = null
    ): Boolean {
        val keywords = filterKeywords ?: return false
        if (keywords.isEmpty()) return false
        val t = title.orEmpty().lowercase()
        val a = artist.orEmpty().lowercase()
        val al = album.orEmpty().lowercase()

        for (keyword in keywords) {
            val kw = keyword.trim().lowercase()
            if (kw.isEmpty()) continue
            if (t.contains(kw) || a.contains(kw) || al.contains(kw)) {
                return true
            }
        }
        return false
    }

    fun matchesFilterKeywords(song: Song, filterKeywords: Collection<String>?): Boolean =
        matchesFilterKeywords(song.title, song.artist, song.album, filterKeywords)

    fun matchesFilterKeywords(track: YouTubeMusicTrack, filterKeywords: Collection<String>?): Boolean =
        matchesFilterKeywords(track.title, track.artist, track.album, filterKeywords)
}
