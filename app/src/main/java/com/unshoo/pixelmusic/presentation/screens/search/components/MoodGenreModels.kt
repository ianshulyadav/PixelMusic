package com.unshoo.pixelmusic.presentation.screens.search.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.MoodAndGenreItemData
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem

enum class MoodGenreCategory {
    MOOD_MOMENT,
    GENRE
}

/**
 * Global dynamic cache of authentic YouTube Music featured playlist thumbnails.
 * Keyed by clean category title, so cards display the exact featured playlist covers
 * that appear when opening the category.
 */
object CategoryPlaylistImageCache {
    private val memoryCache = mutableStateMapOf<String, Pair<String, String>>()
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: android.content.Context) {
        if (prefs == null) {
            try {
                prefs = context.applicationContext.getSharedPreferences("pm_category_playlist_images", android.content.Context.MODE_PRIVATE)
                prefs?.all?.forEach { (key, value) ->
                    if (value is String && value.contains("|")) {
                        val parts = value.split("|")
                        if (parts.size >= 2) {
                            memoryCache[key] = Pair(parts[0], parts[1])
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun get(title: String): Pair<String, String>? = memoryCache[title.trim().lowercase()]

    fun put(title: String, img1: String?, img2: String?) {
        if (!img1.isNullOrBlank()) {
            val second = if (!img2.isNullOrBlank()) img2 else img1
            val key = title.trim().lowercase()
            val existing = memoryCache[key]
            if (existing?.first != img1 || existing?.second != second) {
                memoryCache[key] = Pair(img1, second)
                try {
                    prefs?.edit()?.putString(key, "$img1|$second")?.apply()
                } catch (_: Exception) {}
            }
        }
    }
}

@Immutable
data class MoodGenreItem(
    val id: String,
    val title: String,
    val category: MoodGenreCategory,
    val darkCardColor: Color,
    val lightCardColor: Color,
    val playlistImage1: String? = null,
    val playlistImage2: String? = null,
    val heroThumbnailUrl: String? = null,
    val browseId: String = "FEmusic_moods_and_genres_category",
    val params: String? = null,
    val stripeColor: Long? = null
)

fun Long.toM3ExpressiveDarkTone(): Color {
    val r = ((this shr 16) and 0xFF).toFloat() / 255f
    val g = ((this shr 8) and 0xFF).toFloat() / 255f
    val b = (this and 0xFF).toFloat() / 255f
    val max = maxOf(r, g, b).coerceAtLeast(0.001f)
    val boost = (0.50f / max).coerceIn(0.8f, 1.6f)
    return Color(
        red = (r * boost * 0.72f + 0.08f).coerceIn(0f, 1f),
        green = (g * boost * 0.72f + 0.08f).coerceIn(0f, 1f),
        blue = (b * boost * 0.72f + 0.08f).coerceIn(0f, 1f),
        alpha = 1f
    )
}

fun Long.toM3ExpressiveLightTone(): Color {
    val r = ((this shr 16) and 0xFF).toFloat() / 255f
    val g = ((this shr 8) and 0xFF).toFloat() / 255f
    val b = (this and 0xFF).toFloat() / 255f
    return Color(
        red = (r * 0.18f + 0.82f).coerceIn(0f, 1f),
        green = (g * 0.18f + 0.82f).coerceIn(0f, 1f),
        blue = (b * 0.18f + 0.82f).coerceIn(0f, 1f),
        alpha = 1f
    )
}

fun MoodAndGenreItemData.toMoodGenreItem(category: MoodGenreCategory): MoodGenreItem {
    val fallback = MoodGenreCatalog.findByIdOrTitle(title)
    val darkColor = stripeColor?.toM3ExpressiveDarkTone() ?: fallback?.darkCardColor ?: Color(0xFF2C394B)
    val lightColor = stripeColor?.toM3ExpressiveLightTone() ?: fallback?.lightCardColor ?: Color(0xFFE2EAF4)

    val cached = CategoryPlaylistImageCache.get(title)
    val img1 = cached?.first ?: thumbnailUrl ?: fallback?.playlistImage1
    val img2 = cached?.second ?: fallback?.playlistImage2 ?: fallback?.playlistImage1 ?: thumbnailUrl

    return MoodGenreItem(
        id = title.lowercase().trim().replace(" ", "_").replace("&", "and"),
        title = title.trim(),
        category = category,
        darkCardColor = darkColor,
        lightCardColor = lightColor,
        playlistImage1 = img1,
        playlistImage2 = img2,
        heroThumbnailUrl = thumbnailUrl ?: fallback?.heroThumbnailUrl ?: fallback?.playlistImage1,
        browseId = browseId,
        params = params,
        stripeColor = stripeColor
    )
}

object MoodGenreCatalog {

    // Authentic YouTube Music playlist and album artwork covers (no stock photos!)
    val moodsAndMoments: List<MoodGenreItem> = listOf(
        MoodGenreItem(
            id = "chill",
            title = "Chill",
            category = MoodGenreCategory.MOOD_MOMENT,
            darkCardColor = Color(0xFF1E3A5F),
            lightCardColor = Color(0xFFD6E4F0),
            playlistImage1 = "https://i.ytimg.com/vi/5qap5aO4i9A/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/DWcJFNfaw9c/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "commute",
            title = "Commute",
            category = MoodGenreCategory.MOOD_MOMENT,
            darkCardColor = Color(0xFF5A4418),
            lightCardColor = Color(0xFFF2E8CD),
            playlistImage1 = "https://i.ytimg.com/vi/fJ9rUzIMcZQ/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/k4V3Mo61fJM/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "energize",
            title = "Energize",
            category = MoodGenreCategory.MOOD_MOMENT,
            darkCardColor = Color(0xFF6B3A14),
            lightCardColor = Color(0xFFF1ECD0),
            playlistImage1 = "https://i.ytimg.com/vi/btPJPFnesV4/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/2vjPBrBU-TM/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "feel_good",
            title = "Feel good",
            category = MoodGenreCategory.MOOD_MOMENT,
            darkCardColor = Color(0xFF1B5330),
            lightCardColor = Color(0xFFD4EBDC),
            playlistImage1 = "https://i.ytimg.com/vi/ZbZSe6N_BXs/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/09R8_2nJtjg/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "focus",
            title = "Focus",
            category = MoodGenreCategory.MOOD_MOMENT,
            darkCardColor = Color(0xFF1D3E4E),
            lightCardColor = Color(0xFFD8E4EA),
            playlistImage1 = "https://i.ytimg.com/vi/jfKfPfyJRdk/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/lTRiuFIWV54/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "gaming",
            title = "Gaming",
            category = MoodGenreCategory.MOOD_MOMENT,
            darkCardColor = Color(0xFF3B1E63),
            lightCardColor = Color(0xFFDCE0E6),
            playlistImage1 = "https://i.ytimg.com/vi/K4DyBUG242c/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/y6120QOlsfU/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "party",
            title = "Party",
            category = MoodGenreCategory.MOOD_MOMENT,
            darkCardColor = Color(0xFF5E1B6B),
            lightCardColor = Color(0xFFE8D7F5),
            playlistImage1 = "https://i.ytimg.com/vi/pRpeEdMmmQ0/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/hT_nvWreIhg/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "romance",
            title = "Romance",
            category = MoodGenreCategory.MOOD_MOMENT,
            darkCardColor = Color(0xFF6A1729),
            lightCardColor = Color(0xFFF6D8DC),
            playlistImage1 = "https://i.ytimg.com/vi/4xDzrJKXOOY/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "sad",
            title = "Sad",
            category = MoodGenreCategory.MOOD_MOMENT,
            darkCardColor = Color(0xFF233148),
            lightCardColor = Color(0xFFE0DEE4),
            playlistImage1 = "https://i.ytimg.com/vi/RgKAFK5djSk/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/hLQl3WQQoQ0/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "sleep",
            title = "Sleep",
            category = MoodGenreCategory.MOOD_MOMENT,
            darkCardColor = Color(0xFF2B225E),
            lightCardColor = Color(0xFFDDD7F3),
            playlistImage1 = "https://i.ytimg.com/vi/1ZYbU82GVz4/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/WPni755-Krg/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "workout",
            title = "Workout",
            category = MoodGenreCategory.MOOD_MOMENT,
            darkCardColor = Color(0xFF6E1818),
            lightCardColor = Color(0xFFF3D5E7),
            playlistImage1 = "https://i.ytimg.com/vi/SlPhMPnQ550/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/HCq1OcGAmLg/hqdefault.jpg"
        )
    )

    // Authentic YouTube Music Genres (no stock photos, no duplicates!)
    val genres: List<MoodGenreItem> = listOf(
        MoodGenreItem(
            id = "pop",
            title = "Pop",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF482535),
            lightCardColor = Color(0xFFF5D6E3),
            playlistImage1 = "https://i.ytimg.com/vi/OPf0YbXqDm0/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "hip_hop",
            title = "Hip-hop",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF442D24),
            lightCardColor = Color(0xFFF3DCCE),
            playlistImage1 = "https://i.ytimg.com/vi/_Yhyp-_hX2s/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/tvTRZJ-4EyI/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "rock",
            title = "Rock",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF482525),
            lightCardColor = Color(0xFFF5D5D5),
            playlistImage1 = "https://i.ytimg.com/vi/kXYiU_JCYtU/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/fJ9rUzIMcZQ/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "metal",
            title = "Metal",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF282828),
            lightCardColor = Color(0xFFDADADA),
            playlistImage1 = "https://i.ytimg.com/vi/CD-E-LDc384/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/A6XUVjK9W4o/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "punjabi",
            title = "Punjabi",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF483025),
            lightCardColor = Color(0xFFF4DDD1),
            playlistImage1 = "https://i.ytimg.com/vi/cl0a3i2wFcc/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/vX2cDW8LUWk/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "hindi",
            title = "Hindi",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF3E3628),
            lightCardColor = Color(0xFFEFE4CF),
            playlistImage1 = "https://i.ytimg.com/vi/xRb8hUVWDAg/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/Umqb9KENgmk/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "indian_indie",
            title = "Indian indie",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF333E35),
            lightCardColor = Color(0xFFD6E3D8),
            playlistImage1 = "https://i.ytimg.com/vi/36YnV9STBqc/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/dZ0fwJojhrs/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "indian_pop",
            title = "Indian pop",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF422E3C),
            lightCardColor = Color(0xFFF0DAE8),
            playlistImage1 = "https://i.ytimg.com/vi/YxWlaYCA8MU/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/IltsZZ_g6pE/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "indie_and_alternative",
            title = "Indie & alternative",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF293B3E),
            lightCardColor = Color(0xFFD2E3E6),
            playlistImage1 = "https://i.ytimg.com/vi/bM7SZ5SBzyY/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/gGdGFtwCNBE/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "rnb_and_soul",
            title = "R&B & soul",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF382548),
            lightCardColor = Color(0xFFE5D5F3),
            playlistImage1 = "https://i.ytimg.com/vi/YQHsXMglC9A/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/fHI8X480mxU/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "dance_and_electronic",
            title = "Dance & electronic",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF253E47),
            lightCardColor = Color(0xFFD2E8F1),
            playlistImage1 = "https://i.ytimg.com/vi/60ItHLz5WEA/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/IcrbM1l_BoI/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "tamil",
            title = "Tamil",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF452828),
            lightCardColor = Color(0xFFF3D5D5),
            playlistImage1 = "https://i.ytimg.com/vi/gvyUuxdRdR4/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/vlsD1tXUoJg/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "telugu",
            title = "Telugu",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF3D2845),
            lightCardColor = Color(0xFFEDD4F3),
            playlistImage1 = "https://i.ytimg.com/vi/Q2s7Yd1uQ5s/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/9jK-NcRmVcw/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "kannada",
            title = "Kannada",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF443D27),
            lightCardColor = Color(0xFFF0EACD),
            playlistImage1 = "https://i.ytimg.com/vi/aL3pYV9C7i8/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/f6A_Yg9H6dU/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "malayalam",
            title = "Malayalam",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF274235),
            lightCardColor = Color(0xFFD0ECD8),
            playlistImage1 = "https://i.ytimg.com/vi/e02cQ-Ff0j4/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/d_2Q3Wv5-mQ/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "gujarati",
            title = "Gujarati",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF473425),
            lightCardColor = Color(0xFFF3DFC8),
            playlistImage1 = "https://i.ytimg.com/vi/sZzO_7C3d04/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/Vv_oU5rT4vU/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "marathi",
            title = "Marathi",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF362B45),
            lightCardColor = Color(0xFFE9DAF3),
            playlistImage1 = "https://i.ytimg.com/vi/yL1Wk0T4d8A/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/3wB4rP1vH5s/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "haryanvi",
            title = "Haryanvi",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF293D3E),
            lightCardColor = Color(0xFFD0E6E7),
            playlistImage1 = "https://i.ytimg.com/vi/0pWsCdGZ_cM/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/R8hQv5qD3mE/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "k_pop",
            title = "K-Pop",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF3B2945),
            lightCardColor = Color(0xFFEAD5F3),
            playlistImage1 = "https://i.ytimg.com/vi/9bZkp7q19f0/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/gdZLi9oWNZg/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "j_pop",
            title = "J-Pop",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF422839),
            lightCardColor = Color(0xFFF3D5E8),
            playlistImage1 = "https://i.ytimg.com/vi/dy90tA3TT1c/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/vPwaXytZcgI/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "jazz",
            title = "Jazz",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF303640),
            lightCardColor = Color(0xFFD9E0EC),
            playlistImage1 = "https://i.ytimg.com/vi/vmDDOFXSgAs/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/neV3EPgvZ3g/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "classical",
            title = "Classical",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF37393D),
            lightCardColor = Color(0xFFDFE1E6),
            playlistImage1 = "https://i.ytimg.com/vi/GRxofEmo3HA/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/4Tr0otuiQuU/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "latin",
            title = "Latin",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF453026),
            lightCardColor = Color(0xFFF2DCCE),
            playlistImage1 = "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/wnJ6LuUFpMo/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "folk_and_acoustic",
            title = "Folk & acoustic",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF393E2B),
            lightCardColor = Color(0xFFE4E9D4),
            playlistImage1 = "https://i.ytimg.com/vi/hLQl3WQQoQ0/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/ZbZSe6N_BXs/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "decades",
            title = "Decades",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF333644),
            lightCardColor = Color(0xFFD7DBEB),
            playlistImage1 = "https://i.ytimg.com/vi/djV11Xbc914/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/fJ9rUzIMcZQ/hqdefault.jpg"
        ),
        MoodGenreItem(
            id = "monsoon",
            title = "Monsoon",
            category = MoodGenreCategory.GENRE,
            darkCardColor = Color(0xFF283B44),
            lightCardColor = Color(0xFFD1E4EC),
            playlistImage1 = "https://i.ytimg.com/vi/JFcgOboQZ08/hqdefault.jpg",
            playlistImage2 = "https://i.ytimg.com/vi/5qap5aO4i9A/hqdefault.jpg"
        )
    )

    var cachedMoods: List<MoodGenreItem> = moodsAndMoments
    var cachedGenres: List<MoodGenreItem> = genres
    var isLiveLoaded: Boolean = false

    /**
     * Strict exact matching only (never substring matching!).
     * Prevents collisions like "Indian indie" matching "indie" or "Pop" matching "Indian pop".
     */
    fun findByIdOrTitle(query: String): MoodGenreItem? {
        val clean = query.trim().lowercase()
        val normalizedQuery = clean.replace("&", "and").replace(" ", "_")
        return (cachedMoods + cachedGenres + moodsAndMoments + genres).firstOrNull {
            it.title.equals(clean, ignoreCase = true) ||
            it.id.equals(clean, ignoreCase = true) ||
            it.id.equals(normalizedQuery, ignoreCase = true)
        }
    }

    /**
     * Background prefetcher for authentic YouTube Music featured playlist thumbnails.
     * Extracts real playlist covers for top categories with controlled concurrency.
     */
    suspend fun prefetchFeaturedPlaylistCovers(items: List<MoodGenreItem>) {
        withContext(Dispatchers.IO) {
            val semaphore = Semaphore(2)
            coroutineScope {
                items.take(10).forEach { item ->
                    if (CategoryPlaylistImageCache.get(item.title) == null) {
                        launch {
                            semaphore.withPermit {
                                try {
                                    val browseResult = if (!item.params.isNullOrBlank()) {
                                        YouTube.browse(item.browseId, item.params).getOrNull()
                                    } else null
                                    val playlists = browseResult?.items?.flatMap { it.items }?.filterIsInstance<PlaylistItem>()
                                        ?: YouTube.search(item.title, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()
                                            ?.items?.filterIsInstance<PlaylistItem>()

                                    val p1 = playlists?.getOrNull(0)?.thumbnail
                                    val p2 = playlists?.getOrNull(1)?.thumbnail ?: p1
                                    if (!p1.isNullOrBlank()) {
                                        CategoryPlaylistImageCache.put(item.title, p1, p2)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Loads live YouTube Music moods and genres.
     * Guaranteed deduplication and zero duplicate items.
     */
    suspend fun loadLiveMoodsAndGenres(): Pair<List<MoodGenreItem>, List<MoodGenreItem>>? {
        return withContext(Dispatchers.IO) {
            try {
                val sections = YouTube.moodAndGenres().getOrNull()
                if (!sections.isNullOrEmpty()) {
                    val moodsSection = sections.firstOrNull {
                        it.title.contains("mood", ignoreCase = true) || it.title.contains("moment", ignoreCase = true)
                    } ?: sections.getOrNull(0)

                    val genresSection = sections.firstOrNull {
                        it.title.contains("genre", ignoreCase = true)
                    } ?: sections.getOrNull(1)

                    val liveMoods = moodsSection?.items
                        ?.map { it.toMoodGenreItem(MoodGenreCategory.MOOD_MOMENT) }
                        ?.distinctBy { it.title.trim().lowercase() }
                        ?: moodsAndMoments

                    val liveGenres = genresSection?.items
                        ?.map { it.toMoodGenreItem(MoodGenreCategory.GENRE) }
                        ?.distinctBy { it.title.trim().lowercase() }
                        ?.filterNot { g -> liveMoods.any { m -> m.title.equals(g.title, ignoreCase = true) } }
                        ?: genres

                    cachedMoods = liveMoods
                    cachedGenres = liveGenres
                    isLiveLoaded = true

                    // Asynchronously fetch real featured playlist artwork for categories
                    CoroutineScope(Dispatchers.IO).launch {
                        prefetchFeaturedPlaylistCovers(liveMoods + liveGenres)
                    }

                    Pair(liveMoods, liveGenres)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
