package com.unshoo.pixelmusic.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey // Added import
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.Player
import com.unshoo.pixelmusic.data.model.PlaybackQueueSnapshot
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.SortOption // Added import
import com.unshoo.pixelmusic.data.model.FolderSource
import com.unshoo.pixelmusic.data.model.LyricsSourcePreference
import com.unshoo.pixelmusic.data.model.TransitionSettings
import com.unshoo.pixelmusic.data.equalizer.EqualizerPreset // Added import
import com.unshoo.pixelmusic.data.model.StorageFilter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.text.get
import kotlin.text.set
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.unshoo.pixelmusic.data.diagnostics.AdvancedPerformanceDiagnostics
import com.unshoo.pixelmusic.data.remote.saavn.SaavnAudioQuality

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object ThemePreference {
    const val DEFAULT = "default"
    const val DYNAMIC = "dynamic"
    const val ALBUM_ART = "album_art"
    const val GLOBAL = "global"
}

object PlayerBackgroundStyle {
    const val GRADIENT = "gradient"
    const val SOLID = "solid"
}

object AppThemeMode {
    const val FOLLOW_SYSTEM = "follow_system"
    const val LIGHT = "light"
    const val DARK = "dark"
}

object PlayerThemeMode {
    const val FOLLOW_APP = "follow_app"
    const val LIGHT = "light"
    const val DARK = "dark"
    const val LIGHT_GREY = "light_grey"
}

object AppFontMode {
    const val APP_DEFAULT = "app_default"
    const val SYSTEM = "system"
}

const val MIN_NAV_BAR_CORNER_RADIUS = 0
const val MAX_NAV_BAR_CORNER_RADIUS = 60

internal fun sanitizeNavBarCornerRadius(radius: Int): Int =
        radius.coerceIn(MIN_NAV_BAR_CORNER_RADIUS, MAX_NAV_BAR_CORNER_RADIUS)

/**
 * Album art quality settings for developer options.
 * Controls maximum resolution for album artwork in player view.
 * Thumbnails in lists always use low resolution for performance.
 *
 * @property maxSize Maximum size in pixels (0 = original size)
 * @property label Human-readable label for UI
 */
enum class AlbumArtQuality(val maxSize: Int, val label: String) {
    LOW(256, "Low (256px) - Better performance"),
    MEDIUM(512, "Medium (512px) - Balanced"),
    HIGH(800, "High (800px) - Best quality"),
    ORIGINAL(0, "Original - Maximum quality")
}

data class AdvancedPerformanceDiagnosticsSettings(
    val enabled: Boolean,
    val sessionStartedEpochMs: Long?,
    val expiresAtEpochMs: Long?
) {
    fun isActive(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        enabled && expiresAtEpochMs?.let { nowEpochMs < it } == true
}

enum class PlayerStreamClient {
    ANDROID_VR,
    WEB_REMIX,
}

enum class PlaylistSuggestionSource {
    PLAYLIST_TITLE, PLAYLIST_CONTENT, BOTH
}

enum class QuickPicks {
    QUICK_PICKS, LAST_LISTEN, DONT_SHOW
}

enum class QuickPicksDisplayMode {
    CARD, CARD_CLASSIC, LIST, UNCONTAINED
}

enum class SearchSource {
    LOCAL, ONLINE
}

val LanguageCodeToName = mapOf(
    "en" to "English",
    "es" to "Español",
    "fr" to "Français",
    "de" to "Deutsch",
    "it" to "Italiano",
    "pt" to "Português",
    "ja" to "日本語",
    "ko" to "한국어",
    "zh" to "中文",
    "ru" to "Русский",
    "hi" to "हिन्दी"
)

val CountryCodeToName = mapOf(
    "US" to "United States",
    "GB" to "United Kingdom",
    "CA" to "Canada",
    "AU" to "Australia",
    "DE" to "Germany",
    "FR" to "France",
    "JP" to "Japan",
    "KR" to "South Korea",
    "IN" to "India",
    "BR" to "Brazil",
    "RU" to "Russia"
)


@Singleton
class UserPreferencesRepository
@Inject
constructor(
        private val dataStore: DataStore<Preferences>,
        private val json: Json // Inyectar Json para serialización
) {

    private val backupExcludedKeyNames = setOf(
        PreferencesKeys.INITIAL_SETUP_DONE.name,
        PreferencesKeys.LAST_DAILY_MIX_UPDATE.name,
        PreferencesKeys.DAILY_MIX_SONG_IDS.name,
        PreferencesKeys.YOUR_MIX_SONG_IDS.name,
        // Lossless sources: device-bound caches (the pool account cache is sealed with an
        // Android Keystore key and cannot be read on another device; instance health and
        // probe-track caches are transient). User credentials/settings ARE backed up.
        "poolTidalAccounts",
        "poolQobuzAccounts",
        "poolDeezerAccounts",
        "poolAppleMusicAccounts",
        "poolAmazonAccounts",
        "tidalVerifiedInstances",
        "tidalLastProbeTrack",
        "qobuzVerifiedInstances",
        "qobuzVerifiedTokens",
        "qobuzLastProbeTrack"
    )

    private object PreferencesKeys {
        val ADVANCED_PERFORMANCE_DIAGNOSTICS_ENABLED =
            booleanPreferencesKey("advanced_performance_diagnostics_enabled")
        val ADVANCED_PERFORMANCE_DIAGNOSTICS_STARTED_AT =
            longPreferencesKey("advanced_performance_diagnostics_started_at")
        val ADVANCED_PERFORMANCE_DIAGNOSTICS_EXPIRES_AT =
            longPreferencesKey("advanced_performance_diagnostics_expires_at")
        val APP_REBRAND_DIALOG_SHOWN = booleanPreferencesKey("app_rebrand_dialog_shown")
        val BETA_05_CLEAN_INSTALL_DISCLAIMER_DISMISSED =
            booleanPreferencesKey("beta_05_clean_install_disclaimer_dismissed")
        val WHATS_NEW_1608_DISMISSED =
            booleanPreferencesKey("whats_new_1608_dismissed")
        val ALLOWED_DIRECTORIES = stringSetPreferencesKey("allowed_directories")
        val BLOCKED_DIRECTORIES = stringSetPreferencesKey("blocked_directories")
        val INITIAL_SETUP_DONE = booleanPreferencesKey("initial_setup_done")
        // val GLOBAL_THEME_PREFERENCE = stringPreferencesKey("global_theme_preference_v2") //
        // Removed
        val PLAYER_THEME_PREFERENCE = stringPreferencesKey("player_theme_preference_v2")
        val ALBUM_ART_PALETTE_STYLE = stringPreferencesKey("album_art_palette_style_v1")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val FAVORITE_SONG_IDS = stringSetPreferencesKey("favorite_song_ids")
        val LIKED_ALBUM_IDS = stringSetPreferencesKey("liked_album_ids")
        val USER_PLAYLISTS = stringPreferencesKey("user_playlists_json_v1")
        val PLAYLIST_SONG_ORDER_MODES = stringPreferencesKey("playlist_song_order_modes")

        // Sort Option Keys
        val SONGS_SORT_OPTION = stringPreferencesKey("songs_sort_option")
        val SONGS_SORT_OPTION_MIGRATED = booleanPreferencesKey("songs_sort_option_migrated_v2")
        val ALBUMS_SORT_OPTION = stringPreferencesKey("albums_sort_option")
        val ARTISTS_SORT_OPTION = stringPreferencesKey("artists_sort_option")
        val PLAYLISTS_SORT_OPTION = stringPreferencesKey("playlists_sort_option")
        val FOLDERS_SORT_OPTION = stringPreferencesKey("folders_sort_option")
        val LIKED_SONGS_SORT_OPTION = stringPreferencesKey("liked_songs_sort_option")

        // UI State Keys
        val LAST_LIBRARY_TAB_INDEX =
                intPreferencesKey("last_library_tab_index") // Corrected: Add intPreferencesKey here
        val LAST_STORAGE_FILTER = stringPreferencesKey("last_storage_filter")
        val MOCK_GENRES_ENABLED = booleanPreferencesKey("mock_genres_enabled")
        val LAST_DAILY_MIX_UPDATE = longPreferencesKey("last_daily_mix_update")
        val DAILY_MIX_SONG_IDS = stringPreferencesKey("daily_mix_song_ids")
        val YOUR_MIX_SONG_IDS = stringPreferencesKey("your_mix_song_ids")
        val NAV_BAR_CORNER_RADIUS = intPreferencesKey("nav_bar_corner_radius")
        val NAV_BAR_STYLE = stringPreferencesKey("nav_bar_style")
        val NAV_BAR_COMPACT_MODE = booleanPreferencesKey("nav_bar_compact_mode")
        val NAV_BAR_HEIGHT_OFFSET = intPreferencesKey("nav_bar_height_offset")
        val CAROUSEL_STYLE = stringPreferencesKey("carousel_style")
        val LIBRARY_NAVIGATION_MODE = stringPreferencesKey("library_navigation_mode")
        val LAUNCH_TAB = stringPreferencesKey("launch_tab")

        // Transition Settings
        val GLOBAL_TRANSITION_SETTINGS = stringPreferencesKey("global_transition_settings_json")
        val LIBRARY_TABS_ORDER = stringPreferencesKey("library_tabs_order")
        val IS_FOLDER_FILTER_ACTIVE = booleanPreferencesKey("is_folder_filter_active")
        val IS_FOLDERS_PLAYLIST_VIEW = booleanPreferencesKey("is_folders_playlist_view")
        val SHOW_TELEGRAM_CLOUD_PLAYLISTS = booleanPreferencesKey("show_telegram_cloud_playlists")
        val HIDE_LOCAL_MEDIA = booleanPreferencesKey("hide_local_media")
        val TELEGRAM_TOPIC_DISPLAY_MODE = stringPreferencesKey("telegram_topic_display_mode")
        val FOLDERS_SOURCE = stringPreferencesKey("folders_source")
        val FOLDER_BACK_GESTURE_NAVIGATION = booleanPreferencesKey("folder_back_gesture_navigation")
        val USE_SMOOTH_CORNERS = booleanPreferencesKey("use_smooth_corners")
        val KEEP_PLAYING_IN_BACKGROUND = booleanPreferencesKey("keep_playing_in_background")
        val IS_CROSSFADE_ENABLED = booleanPreferencesKey("is_crossfade_enabled")
        val HI_FI_MODE_ENABLED = booleanPreferencesKey("hi_fi_mode_enabled")
        val CROSSFADE_DURATION = intPreferencesKey("crossfade_duration")
        val CUSTOM_GENRES = androidx.datastore.preferences.core.stringSetPreferencesKey("custom_genres")
        val CUSTOM_GENRE_ICONS = stringPreferencesKey("custom_genre_icons") // JSON Map<String, Int>
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val IS_SHUFFLE_ON = booleanPreferencesKey("is_shuffle_on")
        val PERSISTENT_SHUFFLE_ENABLED = booleanPreferencesKey("persistent_shuffle_enabled")
        val DISABLE_CAST_AUTOPLAY = booleanPreferencesKey("disable_cast_autoplay")
        val RESUME_ON_HEADSET_RECONNECT = booleanPreferencesKey("resume_on_headset_reconnect")
        val DYNAMIC_ISLAND_ENABLED = booleanPreferencesKey("dynamic_island_enabled")
        val SHOW_QUEUE_HISTORY = booleanPreferencesKey("show_queue_history")
        val PLAYBACK_QUEUE_SNAPSHOT = stringPreferencesKey("playback_queue_snapshot_v1")
        val FULL_PLAYER_SHOW_FILE_INFO = booleanPreferencesKey("full_player_show_file_info")
        val FULL_PLAYER_DELAY_ALL = booleanPreferencesKey("full_player_delay_all")
        val FULL_PLAYER_DELAY_ALBUM = booleanPreferencesKey("full_player_delay_album")
        val FULL_PLAYER_DELAY_METADATA = booleanPreferencesKey("full_player_delay_metadata")
        val FULL_PLAYER_DELAY_PROGRESS = booleanPreferencesKey("full_player_delay_progress")
        val FULL_PLAYER_DELAY_CONTROLS = booleanPreferencesKey("full_player_delay_controls")
        val FULL_PLAYER_PLACEHOLDERS = booleanPreferencesKey("full_player_placeholders")
        val FULL_PLAYER_PLACEHOLDER_TRANSPARENT = booleanPreferencesKey("full_player_placeholder_transparent")
        val FULL_PLAYER_PLACEHOLDERS_ON_CLOSE = booleanPreferencesKey("full_player_placeholders_on_close")
        val FULL_PLAYER_SWITCH_ON_DRAG_RELEASE = booleanPreferencesKey("full_player_switch_on_drag_release")
        val FULL_PLAYER_DELAY_THRESHOLD = intPreferencesKey("full_player_delay_threshold_percent")
        val FULL_PLAYER_CLOSE_THRESHOLD = intPreferencesKey("full_player_close_threshold_percent")
        // Deprecated experiment key kept only for one-time cleanup after removing the legacy player sheet.
        val USE_PLAYER_SHEET_V2 = booleanPreferencesKey("use_player_sheet_v2")
        val ADVANCED_EXPLORE_PAGE = booleanPreferencesKey("advanced_explore_page")

        // Multi-Artist Settings
        val ARTIST_DELIMITERS = stringPreferencesKey("artist_delimiters")
        val ARTIST_WORD_DELIMITERS = stringPreferencesKey("artist_word_delimiters")
        val EXTRACT_ARTISTS_FROM_TITLE = booleanPreferencesKey("extract_artists_from_title")
        val GROUP_BY_ALBUM_ARTIST = booleanPreferencesKey("group_by_album_artist")
        val ARTIST_SETTINGS_RESCAN_REQUIRED =
                booleanPreferencesKey("artist_settings_rescan_required")

        // Equalizer Settings
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val EQUALIZER_CUSTOM_BANDS = stringPreferencesKey("equalizer_custom_bands")
        val BASS_BOOST_STRENGTH = intPreferencesKey("bass_boost_strength")
        val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength")
        val BASS_BOOST_ENABLED = booleanPreferencesKey("bass_boost_enabled")
        val VIRTUALIZER_ENABLED = booleanPreferencesKey("virtualizer_enabled")
        val LOUDNESS_ENHANCER_ENABLED = booleanPreferencesKey("loudness_enhancer_enabled")
        val LOUDNESS_ENHANCER_STRENGTH = intPreferencesKey("loudness_enhancer_strength")

        // Dismissed Warning States
        val BASS_BOOST_DISMISSED = booleanPreferencesKey("bass_boost_dismissed")
        val VIRTUALIZER_DISMISSED = booleanPreferencesKey("virtualizer_dismissed")
        val LOUDNESS_DISMISSED = booleanPreferencesKey("loudness_dismissed")
        val BACKUP_INFO_DISMISSED = booleanPreferencesKey("backup_info_dismissed")

        // View Mode
        // val IS_GRAPH_VIEW = booleanPreferencesKey("is_graph_view") // Deprecated
        val VIEW_MODE = stringPreferencesKey("equalizer_view_mode")

        // Custom Presets
        val CUSTOM_PRESETS = stringPreferencesKey("custom_presets_json") // List<EqualizerPreset>
        val PINNED_PRESETS = stringPreferencesKey("pinned_presets_json") // List<String> (names)

        // Library Sync
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        val DIRECTORY_RULES_VERSION = intPreferencesKey("directory_rules_version")
        val LAST_APPLIED_DIRECTORY_RULES_VERSION =
            intPreferencesKey("last_applied_directory_rules_version")

        // Lyrics Sync Offset per song (Map<songId, offsetMs> as JSON)
        val LYRICS_SYNC_OFFSETS = stringPreferencesKey("lyrics_sync_offsets_json")

        // Lyrics Source Preference
        val LYRICS_SOURCE_PREFERENCE = stringPreferencesKey("lyrics_source_preference")
        val AUTO_SCAN_LRC_FILES = booleanPreferencesKey("auto_scan_lrc_files")

        // Developer Options
        val ALBUM_ART_QUALITY = stringPreferencesKey("album_art_quality")
        val ALBUM_ART_CACHE_LIMIT_MB = intPreferencesKey("album_art_cache_limit_mb")
        val EXO_CACHE_SIZE_BYTES = longPreferencesKey("exo_cache_size_bytes")
        val LAST_SUCCESSFUL_YOUTUBE_CLIENT_KEY = stringPreferencesKey("last_successful_youtube_client_key")
        val TAP_BACKGROUND_CLOSES_PLAYER = booleanPreferencesKey("tap_background_closes_player")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val IMMERSIVE_LYRICS_ENABLED = booleanPreferencesKey("immersive_lyrics_enabled")
        val IMMERSIVE_LYRICS_TIMEOUT = longPreferencesKey("immersive_lyrics_timeout")
        val USE_ANIMATED_LYRICS = booleanPreferencesKey("use_animated_lyrics")
        val ANIMATED_LYRICS_BLUR_ENABLED = booleanPreferencesKey("animated_lyrics_blur_enabled")
        val ANIMATED_LYRICS_BLUR_STRENGTH = androidx.datastore.preferences.core.floatPreferencesKey("animated_lyrics_blur_strength")
        val FLOATING_HEADER_BAR_ENABLED = booleanPreferencesKey("floating_header_bar_enabled")

        // Genre View Preference
        val IS_GENRE_GRID_VIEW = booleanPreferencesKey("is_genre_grid_view")

        // Album View Preference
        val IS_ALBUMS_LIST_VIEW = booleanPreferencesKey("is_albums_list_view")

        // Collage Pattern
        val COLLAGE_PATTERN = stringPreferencesKey("collage_pattern")
        val COLLAGE_AUTO_ROTATE = booleanPreferencesKey("collage_auto_rotate")
        val COLLAGE_ENABLED = booleanPreferencesKey("collage_enabled")

        // Quick Settings / Last Playlist
        val LAST_PLAYLIST_ID = stringPreferencesKey("last_playlist_id")
        val LAST_PLAYLIST_NAME = stringPreferencesKey("last_playlist_name")

        // Smart Duration Filtering
        val MIN_SONG_DURATION = intPreferencesKey("min_song_duration_ms")

        // Album Tracks Filtering
        val MIN_TRACKS_PER_ALBUM = intPreferencesKey("min_tracks_per_album")

        // ReplayGain
        val REPLAYGAIN_ENABLED = booleanPreferencesKey("replaygain_enabled")
        val REPLAYGAIN_USE_ALBUM_GAIN = booleanPreferencesKey("replaygain_use_album_gain")

        // Streaming & Data Optimization
        val STREAMING_AUDIO_QUALITY_WIFI = stringPreferencesKey("streaming_audio_quality_wifi")
        val STREAMING_AUDIO_QUALITY_MOBILE = stringPreferencesKey("streaming_audio_quality_mobile")
        val FORCE_HIGH_QUALITY_ON_MOBILE = booleanPreferencesKey("force_high_quality_on_mobile")
        val ENABLE_SAAVN_STREAMING = booleanPreferencesKey("enable_saavn_streaming")
        val ALBUM_ART_QUALITY_MOBILE = stringPreferencesKey("album_art_quality_mobile")
        val CACHE_LIKED_SONGS_OFFLINE = booleanPreferencesKey("cache_liked_songs_offline")
        val CACHE_MOST_PLAYED_SONGS_OFFLINE = booleanPreferencesKey("cache_most_played_songs_offline")
        val STORAGE_LIMIT_MB = intPreferencesKey("storage_limit_mb") // 0 = unlimited
        val FOLDER_ARTWORK_PREFERENCE = stringPreferencesKey("folder_artwork_preference")
        val SUBSCRIBED_ARTIST_IDS = stringSetPreferencesKey("subscribed_artist_ids")
        val PLAYER_STREAM_CLIENT = stringPreferencesKey("player_stream_client")
        val PURE_YT_MUSIC_ONLY = booleanPreferencesKey("pure_yt_music_only")
        val LOCAL_MODE_ENABLED = booleanPreferencesKey("local_mode_enabled")
        val CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        val CONTENT_COUNTRY = stringPreferencesKey("content_country")
        val PLAYLIST_SUGGESTION_SOURCE = stringPreferencesKey("playlist_suggestion_source")
        val HIDE_EXPLICIT = booleanPreferencesKey("hide_explicit")
        val HIDE_VIDEO = booleanPreferencesKey("hide_video")
        val TOP_SIZE = stringPreferencesKey("top_size")
        val DISCOVER = stringPreferencesKey("discover")
        val QUICK_PICKS_DISPLAY_MODE = stringPreferencesKey("quick_picks_display_mode")
        val SEARCH_SOURCE = stringPreferencesKey("search_source")
        val PERFORMANCE_MODE_ENABLED = booleanPreferencesKey("performance_mode_enabled")
        val AUDIO_OFFLOAD_ENABLED = booleanPreferencesKey("audio_offload_enabled")
        val DEDUPLICATE_QUEUE_ENTRIES = booleanPreferencesKey("deduplicate_queue_entries")
        val PREFER_TELEGRAM_ALTERNATIVE = booleanPreferencesKey("prefer_telegram_alternative")
        val TELEGRAM_USE_ONLINE_ALBUM_ART = booleanPreferencesKey("telegram_use_online_album_art")
        val LASTFM_SESSION = stringPreferencesKey("lastfm_session")
        val LASTFM_USERNAME = stringPreferencesKey("lastfm_username")
        val LASTFM_API_KEY = stringPreferencesKey("lastfm_api_key_v1")
        val LASTFM_API_SECRET = stringPreferencesKey("lastfm_api_secret_v1")
        val LASTFM_SCROBBLING_ENABLED = booleanPreferencesKey("lastfm_scrobbling_enabled")
        val LASTFM_USE_NOW_PLAYING = booleanPreferencesKey("lastfm_use_now_playing")
        val SCROBBLE_DELAY_PERCENT = androidx.datastore.preferences.core.floatPreferencesKey("scrobble_delay_percent")
        val SCROBBLE_MIN_SONG_DURATION = intPreferencesKey("scrobble_min_song_duration")
        val SCROBBLE_DELAY_SECONDS = intPreferencesKey("scrobble_delay_seconds")
        val GENERATED_PLAYLISTS_RETENTION_PERIOD = stringPreferencesKey("generated_playlists_retention_period")
        val YOUTUBE_PLAYLIST_UPLOAD_SYNC_ENABLED = booleanPreferencesKey("youtube_playlist_upload_sync_enabled")
        val SHOW_SMART_MIX_PLAYLISTS = booleanPreferencesKey("show_smart_mix_playlists")
        val ARTIST_LIBRARY_FILTER = stringPreferencesKey("artist_library_filter")
        val LASTFM_SMART_MIX_ENABLED = booleanPreferencesKey("lastfm_smart_mix_enabled")
        val EXPLORE_LASTFM_ENABLED = booleanPreferencesKey("explore_lastfm_enabled")
        val FILTER_COVER_AND_LOFI = booleanPreferencesKey("filter_cover_and_lofi")
        val FILTER_KEYWORDS = stringPreferencesKey("filter_keywords")
        val DOWNLOAD_AUDIO_QUALITY = stringPreferencesKey("download_audio_quality")
        val NOT_INTERESTED_SONG_IDS = stringSetPreferencesKey("not_interested_song_ids")
        val BLOCKED_RELEASE_IDS = stringSetPreferencesKey("blocked_release_ids")

        // YouTube Granular Sync & Personalization Options
        val YOUTUBE_SYNC_PLAYLISTS_AND_LIKES = booleanPreferencesKey("youtube_sync_playlists_and_likes")
        val YOUTUBE_SYNC_LISTENING_ACTIVITY = booleanPreferencesKey("youtube_sync_listening_activity")
        val YOUTUBE_PERSONALIZED_EXPLORE = booleanPreferencesKey("youtube_personalized_explore")
        val YOUTUBE_PERSONALIZED_QUEUE = booleanPreferencesKey("youtube_personalized_queue")

        // App Updates
        val AUTO_UPDATE_CHECK_ENABLED = booleanPreferencesKey("auto_update_check_enabled")
        val LAST_UPDATE_CHECK_TIME = longPreferencesKey("last_update_check_time")
        val LAST_UPDATE_ETAG = stringPreferencesKey("last_update_etag")
        val IGNORED_UPDATE_VERSION = stringPreferencesKey("ignored_update_version")
        val CACHED_RELEASE_INFO = stringPreferencesKey("cached_release_info")

        // Blur Settings
        val USE_BLUR_EFFECTS = booleanPreferencesKey("use_blur_effects")
        val NOW_PLAYING_BLUR_ENABLED = booleanPreferencesKey("now_playing_blur_enabled")
        val PLAYER_EXPANSION_BLUR_ENABLED = booleanPreferencesKey("player_expansion_blur_enabled")
        val NAV_BAR_BLUR_ENABLED = booleanPreferencesKey("nav_bar_blur_enabled")
        val BOTTOM_SHEET_BLUR_ENABLED = booleanPreferencesKey("bottom_sheet_blur_enabled")
        val STORY_CARD_BLUR_ENABLED = booleanPreferencesKey("story_card_blur_enabled")
        val LYRICS_SCREEN_BLUR_ENABLED = booleanPreferencesKey("lyrics_screen_blur_enabled")
    }

    val useBlurEffectsFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.USE_BLUR_EFFECTS] ?: true
        }.distinctUntilChanged()

    suspend fun setUseBlurEffects(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_BLUR_EFFECTS] = enabled
        }
    }

    val nowPlayingBlurEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.NOW_PLAYING_BLUR_ENABLED] ?: true
        }.distinctUntilChanged()

    suspend fun setNowPlayingBlurEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOW_PLAYING_BLUR_ENABLED] = enabled
        }
    }

    val playerExpansionBlurEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.PLAYER_EXPANSION_BLUR_ENABLED] ?: true
        }.distinctUntilChanged()

    suspend fun setPlayerExpansionBlurEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PLAYER_EXPANSION_BLUR_ENABLED] = enabled
        }
    }

    val navBarBlurEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.NAV_BAR_BLUR_ENABLED] ?: false
        }.distinctUntilChanged()

    suspend fun setNavBarBlurEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NAV_BAR_BLUR_ENABLED] = enabled
        }
    }

    val bottomSheetBlurEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.BOTTOM_SHEET_BLUR_ENABLED] ?: false
        }.distinctUntilChanged()

    suspend fun setBottomSheetBlurEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BOTTOM_SHEET_BLUR_ENABLED] = enabled
        }
    }

    val storyCardBlurEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.STORY_CARD_BLUR_ENABLED] ?: true
        }.distinctUntilChanged()

    suspend fun setStoryCardBlurEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.STORY_CARD_BLUR_ENABLED] = enabled
        }
    }

    val lyricsScreenBlurEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LYRICS_SCREEN_BLUR_ENABLED] ?: true
        }.distinctUntilChanged()

    suspend fun setLyricsScreenBlurEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LYRICS_SCREEN_BLUR_ENABLED] = enabled
        }
    }

    val deduplicateQueueEntriesFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.DEDUPLICATE_QUEUE_ENTRIES] ?: true
        }.distinctUntilChanged()

    suspend fun setDeduplicateQueueEntries(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEDUPLICATE_QUEUE_ENTRIES] = enabled
        }
    }

    val preferTelegramAlternativeFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.PREFER_TELEGRAM_ALTERNATIVE] ?: false
            }

    suspend fun setPreferTelegramAlternative(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PREFER_TELEGRAM_ALTERNATIVE] = enabled
        }
    }

    val telegramUseOnlineAlbumArtFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.TELEGRAM_USE_ONLINE_ALBUM_ART] ?: false
        }.distinctUntilChanged()

    suspend fun setTelegramUseOnlineAlbumArt(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TELEGRAM_USE_ONLINE_ALBUM_ART] = enabled
        }
    }

    val performanceModeEnabledFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.PERFORMANCE_MODE_ENABLED] ?: false
            }

    val youtubePlaylistUploadSyncEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.YOUTUBE_PLAYLIST_UPLOAD_SYNC_ENABLED] ?: true
        }

    suspend fun setYoutubePlaylistUploadSyncEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.YOUTUBE_PLAYLIST_UPLOAD_SYNC_ENABLED] = enabled
        }
    }

    val showSmartMixPlaylistsFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SHOW_SMART_MIX_PLAYLISTS] ?: true
        }

    suspend fun setShowSmartMixPlaylists(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_SMART_MIX_PLAYLISTS] = show
        }
    }

    val artistLibraryFilterFlow: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.ARTIST_LIBRARY_FILTER] ?: "SUBSCRIBED"
        }.distinctUntilChanged()

    suspend fun setArtistLibraryFilter(filter: String) {
        val safeFilter = if (filter == "ALL_5_PLUS") "ALL_5_PLUS" else "SUBSCRIBED"
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ARTIST_LIBRARY_FILTER] = safeFilter
        }
    }

    suspend fun setPerformanceModeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PERFORMANCE_MODE_ENABLED] = enabled
            if (enabled) {
                // Make the low-end toggle actually aggressive: reduce decoding, cache,
                // full-player composition work, and background player CPU usage in one switch.
                preferences[PreferencesKeys.AUDIO_OFFLOAD_ENABLED] = true
                preferences[PreferencesKeys.ALBUM_ART_QUALITY] = AlbumArtQuality.LOW.name
                preferences[PreferencesKeys.ALBUM_ART_QUALITY_MOBILE] = AlbumArtQuality.LOW.name
                preferences[PreferencesKeys.ALBUM_ART_CACHE_LIMIT_MB] = 50
                preferences[PreferencesKeys.FULL_PLAYER_SHOW_FILE_INFO] = false
                preferences[PreferencesKeys.FULL_PLAYER_DELAY_ALBUM] = true
                preferences[PreferencesKeys.FULL_PLAYER_DELAY_METADATA] = true
                preferences[PreferencesKeys.FULL_PLAYER_DELAY_PROGRESS] = true
                preferences[PreferencesKeys.FULL_PLAYER_DELAY_CONTROLS] = true
                preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDERS] = true
                preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDER_TRANSPARENT] = true
                preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDERS_ON_CLOSE] = true
                preferences[PreferencesKeys.FULL_PLAYER_SWITCH_ON_DRAG_RELEASE] = true
                preferences[PreferencesKeys.FULL_PLAYER_DELAY_THRESHOLD] = 100
                preferences[PreferencesKeys.FULL_PLAYER_CLOSE_THRESHOLD] = 10
            }
        }
    }

    val audioOffloadEnabledFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.AUDIO_OFFLOAD_ENABLED] ?: false
            }

    suspend fun setAudioOffloadEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUDIO_OFFLOAD_ENABLED] = enabled
        }
    }

    val appRebrandDialogShownFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.APP_REBRAND_DIALOG_SHOWN] ?: false
            }

    suspend fun setAppRebrandDialogShown(wasShown: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_REBRAND_DIALOG_SHOWN] = wasShown
        }
    }

    val folderArtworkPreferenceFlow: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.FOLDER_ARTWORK_PREFERENCE] ?: "recently_added"
        }

    suspend fun setFolderArtworkPreference(preference: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FOLDER_ARTWORK_PREFERENCE] = preference
        }
    }

    val beta05CleanInstallDisclaimerDismissedFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            // v1.6.08+: use the new key so returning users see the updated popup
            preferences[PreferencesKeys.WHATS_NEW_1608_DISMISSED] ?: false
        }

    suspend fun setBeta05CleanInstallDisclaimerDismissed(dismissed: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WHATS_NEW_1608_DISMISSED] = dismissed
        }
    }

    val isCrossfadeEnabledFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.IS_CROSSFADE_ENABLED] ?: false
            }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_CROSSFADE_ENABLED] = enabled
        }
    }

    val hiFiModeEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.HI_FI_MODE_ENABLED] ?: false
        }

    suspend fun setHiFiModeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HI_FI_MODE_ENABLED] = enabled
        }
    }

    val backupInfoDismissedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BACKUP_INFO_DISMISSED] ?: false
    }

    suspend fun setBackupInfoDismissed(dismissed: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.BACKUP_INFO_DISMISSED] = dismissed }
    }

    enum class EqualizerViewMode {
        SLIDERS, GRAPH, HYBRID
    }

    val crossfadeDurationFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                (preferences[PreferencesKeys.CROSSFADE_DURATION] ?: 2000).coerceIn(1000, 12000)
            }

    suspend fun setCrossfadeDuration(duration: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CROSSFADE_DURATION] = duration.coerceIn(1000, 12000)
        }
    }

    // Custom Genres Names
    val customGenresFlow: Flow<Set<String>> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.CUSTOM_GENRES] ?: emptySet()
        }

    // Custom Genres Icons (JSON Map: Name -> ResId)
    val customGenreIconsFlow: Flow<Map<String, Int>> =
        dataStore.data.map { preferences ->
            val jsonString = preferences[PreferencesKeys.CUSTOM_GENRE_ICONS]
            if (jsonString != null) {
                try {
                    json.decodeFromString<Map<String, Int>>(jsonString)
                } catch (e: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }
        }

    suspend fun addCustomGenre(genre: String, iconResId: Int? = null) {
        dataStore.edit { preferences ->
            val currentGenres = preferences[PreferencesKeys.CUSTOM_GENRES] ?: emptySet()
            preferences[PreferencesKeys.CUSTOM_GENRES] = currentGenres + genre

            if (iconResId != null) {
                val currentIconsJson = preferences[PreferencesKeys.CUSTOM_GENRE_ICONS]
                val currentIcons = if (currentIconsJson != null) {
                    try {
                        json.decodeFromString<Map<String, Int>>(currentIconsJson)
                    } catch (e: Exception) {
                        emptyMap()
                    }
                } else {
                    emptyMap()
                }

                val newIcons = currentIcons.toMutableMap()
                newIcons[genre] = iconResId
                preferences[PreferencesKeys.CUSTOM_GENRE_ICONS] = json.encodeToString(newIcons)
            }
        }
    }
    val repeatModeFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.REPEAT_MODE] ?: Player.REPEAT_MODE_OFF
            }

    suspend fun setRepeatMode(@Player.RepeatMode mode: Int) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.REPEAT_MODE] = mode }
    }

    val isShuffleOnFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.IS_SHUFFLE_ON] ?: false
            }

    suspend fun setShuffleOn(on: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.IS_SHUFFLE_ON] = on }
    }

    val persistentShuffleEnabledFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.PERSISTENT_SHUFFLE_ENABLED] ?: false
            }

    suspend fun setPersistentShuffleEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.PERSISTENT_SHUFFLE_ENABLED] = enabled }
    }

    val playbackQueueSnapshotFlow: Flow<PlaybackQueueSnapshot?> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.PLAYBACK_QUEUE_SNAPSHOT]?.let { raw ->
                    runCatching { json.decodeFromString<PlaybackQueueSnapshot>(raw) }.getOrNull()
                }
            }

    suspend fun getPlaybackQueueSnapshotOnce(): PlaybackQueueSnapshot? {
        return playbackQueueSnapshotFlow.first()
    }

    suspend fun setPlaybackQueueSnapshot(snapshot: PlaybackQueueSnapshot?) {
        dataStore.edit { preferences ->
            if (snapshot == null || snapshot.items.isEmpty()) {
                preferences.remove(PreferencesKeys.PLAYBACK_QUEUE_SNAPSHOT)
            } else {
                preferences[PreferencesKeys.PLAYBACK_QUEUE_SNAPSHOT] = json.encodeToString(snapshot)
            }
        }
    }

    // ===== Multi-Artist Settings =====

    val artistDelimitersFlow: Flow<List<String>> =
            dataStore.data.map { preferences ->
                val stored = preferences[PreferencesKeys.ARTIST_DELIMITERS]
                if (stored != null) {
                    try {
                        json.decodeFromString<List<String>>(stored)
                    } catch (e: Exception) {
                        DEFAULT_ARTIST_DELIMITERS
                    }
                } else {
                    DEFAULT_ARTIST_DELIMITERS
                }
            }

    suspend fun setArtistDelimiters(delimiters: List<String>) {
        // Ensure at least one delimiter is always maintained
        if (delimiters.isEmpty()) {
            return
        }

        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ARTIST_DELIMITERS] = json.encodeToString(delimiters)
            // Mark rescan as required when delimiters change
            preferences[PreferencesKeys.ARTIST_SETTINGS_RESCAN_REQUIRED] = true
        }
    }

    suspend fun resetArtistDelimitersToDefault() {
        setArtistDelimiters(DEFAULT_ARTIST_DELIMITERS)
    }

    val floatingHeaderBarEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.FLOATING_HEADER_BAR_ENABLED] ?: true
    }

    suspend fun setFloatingHeaderBarEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FLOATING_HEADER_BAR_ENABLED] = enabled
        }
    }

    val artistWordDelimitersFlow: Flow<List<String>> =
        dataStore.data.map { preferences ->
            val stored = preferences[PreferencesKeys.ARTIST_WORD_DELIMITERS]
            if (stored != null) {
                try {
                    json.decodeFromString<List<String>>(stored)
                } catch (e: Exception) {
                    DEFAULT_ARTIST_WORD_DELIMITERS
                }
            } else {
                DEFAULT_ARTIST_WORD_DELIMITERS
            }
        }

    suspend fun setArtistWordDelimiters(delimiters: List<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ARTIST_WORD_DELIMITERS] = json.encodeToString(delimiters)
            preferences[PreferencesKeys.ARTIST_SETTINGS_RESCAN_REQUIRED] = true
        }
    }

    suspend fun resetArtistWordDelimitersToDefault() {
        setArtistWordDelimiters(DEFAULT_ARTIST_WORD_DELIMITERS)
    }

    val extractArtistsFromTitleFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.EXTRACT_ARTISTS_FROM_TITLE] ?: true // Enabled by default
        }

    suspend fun setExtractArtistsFromTitle(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.EXTRACT_ARTISTS_FROM_TITLE] = enabled
            preferences[PreferencesKeys.ARTIST_SETTINGS_RESCAN_REQUIRED] = true
        }
    }

    val groupByAlbumArtistFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.GROUP_BY_ALBUM_ARTIST] ?: false
            }

    suspend fun setGroupByAlbumArtist(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.GROUP_BY_ALBUM_ARTIST] = enabled
            // Mark rescan as required when this setting changes
            preferences[PreferencesKeys.ARTIST_SETTINGS_RESCAN_REQUIRED] = true
        }
    }

    val artistSettingsRescanRequiredFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.ARTIST_SETTINGS_RESCAN_REQUIRED] ?: false
            }

    suspend fun clearArtistSettingsRescanRequired() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ARTIST_SETTINGS_RESCAN_REQUIRED] = false
        }
    }

    // ===== Library Sync Settings =====

    val lastSyncTimestampFlow: Flow<Long> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] ?: 0L
            }

    val directoryRulesVersionFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.DIRECTORY_RULES_VERSION] ?: 0
            }

    val lastAppliedDirectoryRulesVersionFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LAST_APPLIED_DIRECTORY_RULES_VERSION] ?: 0
            }

    suspend fun getLastSyncTimestamp(): Long {
        return lastSyncTimestampFlow.first()
    }

    suspend fun getDirectoryRulesVersion(): Int {
        return directoryRulesVersionFlow.first()
    }

    suspend fun getLastAppliedDirectoryRulesVersion(): Int {
        return lastAppliedDirectoryRulesVersionFlow.first()
    }

    suspend fun setLastSyncTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = timestamp
        }
    }

    suspend fun markDirectoryRulesVersionApplied(version: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_APPLIED_DIRECTORY_RULES_VERSION] = version
        }
    }

    // ===== End Library Sync Settings =====

    // ===== Lyrics Sync Offset Settings =====

    /**
     * Lyrics sync offset per song in milliseconds.
     * Stored as a JSON map: { "songId": offsetMs, ... }
     * Positive values = lyrics appear later (use when lyrics are ahead of audio)
     * Negative values = lyrics appear earlier (use when lyrics are behind audio)
     */
    private val lyricsSyncOffsetsFlow: Flow<Map<String, Int>> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LYRICS_SYNC_OFFSETS]?.let { jsonString ->
                    try {
                        json.decodeFromString<Map<String, Int>>(jsonString)
                    } catch (e: Exception) {
                        emptyMap()
                    }
                } ?: emptyMap()
            }

    fun getLyricsSyncOffsetFlow(songId: String): Flow<Int> {
        return lyricsSyncOffsetsFlow.map { offsets -> offsets[songId] ?: 0 }
    }

    suspend fun getLyricsSyncOffset(songId: String): Int {
        return getLyricsSyncOffsetFlow(songId).first()
    }

    suspend fun setLyricsSyncOffset(songId: String, offsetMs: Int) {
        dataStore.edit { preferences ->
            val currentOffsets = preferences[PreferencesKeys.LYRICS_SYNC_OFFSETS]?.let { jsonString ->
                try {
                    json.decodeFromString<Map<String, Int>>(jsonString).toMutableMap()
                } catch (e: Exception) {
                    mutableMapOf()
                }
            } ?: mutableMapOf()

            if (offsetMs == 0) {
                currentOffsets.remove(songId) // Don't store default value
            } else {
                currentOffsets[songId] = offsetMs
            }

            preferences[PreferencesKeys.LYRICS_SYNC_OFFSETS] = json.encodeToString(currentOffsets)
        }
    }

    // ===== End Lyrics Sync Offset Settings =====

    // ===== Lyrics Source Preference Settings =====

    val lyricsSourcePreferenceFlow: Flow<LyricsSourcePreference> =
            dataStore.data.map { preferences ->
                LyricsSourcePreference.fromName(preferences[PreferencesKeys.LYRICS_SOURCE_PREFERENCE])
            }

    suspend fun setLyricsSourcePreference(preference: LyricsSourcePreference) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LYRICS_SOURCE_PREFERENCE] = preference.name
        }
    }

    val autoScanLrcFilesFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.AUTO_SCAN_LRC_FILES] ?: false
            }

    suspend fun setAutoScanLrcFiles(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_SCAN_LRC_FILES] = enabled
        }
    }

    val immersiveLyricsEnabledFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.IMMERSIVE_LYRICS_ENABLED] ?: false
            }

    val immersiveLyricsTimeoutFlow: Flow<Long> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.IMMERSIVE_LYRICS_TIMEOUT] ?: 4000L
            }

    suspend fun setImmersiveLyricsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IMMERSIVE_LYRICS_ENABLED] = enabled
        }
    }

    suspend fun setImmersiveLyricsTimeout(timeout: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IMMERSIVE_LYRICS_TIMEOUT] = timeout
        }
    }

    // ===== End Lyrics Source Preference Settings =====

    // ===== End Multi-Artist Settings =====

    val globalTransitionSettingsFlow: Flow<TransitionSettings> =
            dataStore.data.map { preferences ->
                val duration = (preferences[PreferencesKeys.CROSSFADE_DURATION] ?: 2000).coerceIn(1000, 12000)
                val settings =
                        preferences[PreferencesKeys.GLOBAL_TRANSITION_SETTINGS]?.let { jsonString ->
                            try {
                                json.decodeFromString<TransitionSettings>(jsonString)
                            } catch (e: Exception) {
                                TransitionSettings() // Return default on error
                            }
                        }
                                ?: TransitionSettings() // Return default if not set

                settings.copy(durationMs = duration)
            }

    suspend fun saveGlobalTransitionSettings(settings: TransitionSettings) {
        dataStore.edit { preferences ->
            val jsonString = json.encodeToString(settings)
            preferences[PreferencesKeys.GLOBAL_TRANSITION_SETTINGS] = jsonString
        }
    }

    val dailyMixSongIdsFlow: Flow<List<String>> =
            dataStore.data.map { preferences ->
                val jsonString = preferences[PreferencesKeys.DAILY_MIX_SONG_IDS]
                if (jsonString != null) {
                    try {
                        json.decodeFromString<List<String>>(jsonString)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }

    suspend fun saveDailyMixSongIds(songIds: List<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_MIX_SONG_IDS] = json.encodeToString(songIds)
        }
    }

    val yourMixSongIdsFlow: Flow<List<String>> =
            dataStore.data.map { preferences ->
                val jsonString = preferences[PreferencesKeys.YOUR_MIX_SONG_IDS]
                if (jsonString != null) {
                    try {
                        json.decodeFromString<List<String>>(jsonString)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }

    suspend fun saveYourMixSongIds(songIds: List<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.YOUR_MIX_SONG_IDS] = json.encodeToString(songIds)
        }
    }

    val isGenreGridViewFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.IS_GENRE_GRID_VIEW] ?: true // Default to Grid (true)
        }

    suspend fun setGenreGridView(isGrid: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_GENRE_GRID_VIEW] = isGrid
        }
    }

    val isAlbumsListViewFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.IS_ALBUMS_LIST_VIEW] ?: false // Default to Grid (false)
        }

    suspend fun setAlbumsListView(isList: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_ALBUMS_LIST_VIEW] = isList
        }
    }

    val lastDailyMixUpdateFlow: Flow<Long> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LAST_DAILY_MIX_UPDATE] ?: 0L
            }

    suspend fun saveLastDailyMixUpdateTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_DAILY_MIX_UPDATE] = timestamp
        }
    }

    suspend fun clearDailyMixData() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.DAILY_MIX_SONG_IDS)
            preferences.remove(PreferencesKeys.YOUR_MIX_SONG_IDS)
            preferences[PreferencesKeys.LAST_DAILY_MIX_UPDATE] = 0L
        }
    }

    // ===== Smart Duration Filtering =====

    /** Minimum song duration in milliseconds. Default 10000ms (10 seconds). */
    val minSongDurationFlow: Flow<Int> =
        dataStore.data.map { preferences ->
            (preferences[PreferencesKeys.MIN_SONG_DURATION] ?: 10000).coerceIn(0, 120000)
        }

    suspend fun setMinSongDuration(durationMs: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MIN_SONG_DURATION] = durationMs.coerceIn(0, 120000)
        }
    }

    suspend fun getMinSongDuration(): Int {
        return minSongDurationFlow.first()
    }

    // ===== End Smart Duration Filtering =====

    // ===== Album Tracks Filtering =====

    val minTracksPerAlbumFlow: Flow<Int> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.MIN_TRACKS_PER_ALBUM] ?: 1
        }

    suspend fun setMinTracksPerAlbum(minTracks: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MIN_TRACKS_PER_ALBUM] = minTracks
        }
    }

    // ===== End Album Tracks Filtering =====

    // ===== ReplayGain =====

    val replayGainEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.REPLAYGAIN_ENABLED] ?: false
        }

    val replayGainUseAlbumGainFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.REPLAYGAIN_USE_ALBUM_GAIN] ?: false
        }

    suspend fun setReplayGainEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.REPLAYGAIN_ENABLED] = enabled
        }
    }

    suspend fun setReplayGainUseAlbumGain(useAlbumGain: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.REPLAYGAIN_USE_ALBUM_GAIN] = useAlbumGain
        }
    }

    // ===== End ReplayGain =====

    // ===== Streaming & Data Optimization =====

    /** Audio quality when on WiFi. Default: HIGH (256 kbps). */
    val streamingAudioQualityWifiFlow: Flow<StreamingAudioQuality> =
        dataStore.data.map { preferences ->
            StreamingAudioQuality.fromName(preferences[PreferencesKeys.STREAMING_AUDIO_QUALITY_WIFI])
        }

    suspend fun setStreamingAudioQualityWifi(quality: StreamingAudioQuality) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.STREAMING_AUDIO_QUALITY_WIFI] = quality.name
        }
    }
    /** Audio quality when on mobile data. Default: LOW (fast start on weak networks). */
    val streamingAudioQualityMobileFlow: Flow<StreamingAudioQuality> =
        dataStore.data.map { preferences ->
            StreamingAudioQuality.fromNameOrLow(
                preferences[PreferencesKeys.STREAMING_AUDIO_QUALITY_MOBILE]
            )
        }

    suspend fun setStreamingAudioQualityMobile(quality: StreamingAudioQuality) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.STREAMING_AUDIO_QUALITY_MOBILE] = quality.name
        }
    }

    /** If true, ignore metered network and stream at WiFi quality regardless. Default: false. */
    val forceHighQualityOnMobileFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.FORCE_HIGH_QUALITY_ON_MOBILE] ?: false
        }

    suspend fun setForceHighQualityOnMobile(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FORCE_HIGH_QUALITY_ON_MOBILE] = enabled
        }
    }

    val pureYtMusicOnlyFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.PURE_YT_MUSIC_ONLY] ?: false
        }

    suspend fun setPureYtMusicOnly(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PURE_YT_MUSIC_ONLY] = enabled
        }
    }

    val localModeEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LOCAL_MODE_ENABLED] ?: false
        }.distinctUntilChanged()

    suspend fun setLocalModeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOCAL_MODE_ENABLED] = enabled
        }
    }

    /** JioSaavn high quality streaming toggle. Disabled by default. */
    val enableSaavnStreamingFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.ENABLE_SAAVN_STREAMING] ?: false
        }

    suspend fun setEnableSaavnStreaming(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENABLE_SAAVN_STREAMING] = enabled
        }
    }

    /**
     * JioSaavn streaming audio quality level.
     * Universally wired to the existing streaming quality settings:
     * mirrors [streamingAudioQualityWifiFlow] so quality is universal for both YouTube and JioSaavn.
     */
    val saavnAudioQualityFlow: Flow<SaavnAudioQuality> =
        streamingAudioQualityWifiFlow.map { quality ->
            SaavnAudioQuality.fromStreamingQuality(quality)
        }

    suspend fun setSaavnAudioQuality(quality: SaavnAudioQuality) {
        val streamingQuality = when (quality) {
            SaavnAudioQuality.QUALITY_320 -> StreamingAudioQuality.HIGH
            SaavnAudioQuality.QUALITY_160 -> StreamingAudioQuality.MEDIUM
            SaavnAudioQuality.QUALITY_96 -> StreamingAudioQuality.LOW
            SaavnAudioQuality.AUTO -> StreamingAudioQuality.AUTO
        }
        setStreamingAudioQualityWifi(streamingQuality)
    }

    /** Album art quality on mobile data. Default: LOW (256px). */
    val albumArtQualityMobileFlow: Flow<AlbumArtQuality> =
        dataStore.data.map { preferences ->
            try {
                AlbumArtQuality.valueOf(
                    preferences[PreferencesKeys.ALBUM_ART_QUALITY_MOBILE] ?: "ORIGINAL"
                )
            } catch (e: Exception) {
                AlbumArtQuality.ORIGINAL
            }
        }

    suspend fun setAlbumArtQualityMobile(quality: AlbumArtQuality) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALBUM_ART_QUALITY_MOBILE] = quality.name
        }
    }

    /** If true, automatically download audio of liked YouTube songs. Default: false. */
    val cacheLikedSongsOfflineFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.CACHE_LIKED_SONGS_OFFLINE] ?: false
        }

    suspend fun setCacheLikedSongsOffline(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CACHE_LIKED_SONGS_OFFLINE] = enabled
        }
    }

    val cacheMostPlayedSongsOfflineFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.CACHE_MOST_PLAYED_SONGS_OFFLINE] ?: false
        }.distinctUntilChanged()

    suspend fun setCacheMostPlayedSongsOffline(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CACHE_MOST_PLAYED_SONGS_OFFLINE] = enabled
        }
    }

    /**
     * Storage limit in MB for downloaded songs and album art.
     * 0 = unlimited. Default: 1536 MB (1.5 GB).
     */
    val storageLimitMbFlow: Flow<Int> =
        dataStore.data.map { preferences ->
            (preferences[PreferencesKeys.STORAGE_LIMIT_MB] ?: 1536).coerceIn(0, 20480)
        }

    suspend fun setStorageLimitMb(limitMb: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.STORAGE_LIMIT_MB] = limitMb.coerceIn(0, 20480)
        }
    }

    /** Download audio quality. Default: HIGH (320 kbps from JioSaavn). */
    val downloadAudioQualityFlow: Flow<DownloadAudioQuality> =
        dataStore.data.map { preferences ->
            DownloadAudioQuality.fromName(preferences[PreferencesKeys.DOWNLOAD_AUDIO_QUALITY])
        }.distinctUntilChanged()

    suspend fun setDownloadAudioQuality(quality: DownloadAudioQuality) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DOWNLOAD_AUDIO_QUALITY] = quality.name
        }
    }

    val playerStreamClientFlow: Flow<PlayerStreamClient> =
        dataStore.data.map { preferences ->
            val name = preferences[PreferencesKeys.PLAYER_STREAM_CLIENT] ?: PlayerStreamClient.ANDROID_VR.name
            try { PlayerStreamClient.valueOf(name) } catch (_: Exception) { PlayerStreamClient.ANDROID_VR }
        }

    suspend fun setPlayerStreamClient(client: PlayerStreamClient) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PLAYER_STREAM_CLIENT] = client.name
        }
    }

    // ===== End Streaming & Data Optimization =====

    val allowedDirectoriesFlow: Flow<Set<String>> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.ALLOWED_DIRECTORIES] ?: emptySet()
            }.distinctUntilChanged()

    val blockedDirectoriesFlow: Flow<Set<String>> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.BLOCKED_DIRECTORIES] ?: emptySet()
            }.distinctUntilChanged()

    val initialSetupDoneFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.INITIAL_SETUP_DONE] ?: false
            }

    val keepPlayingInBackgroundFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.KEEP_PLAYING_IN_BACKGROUND] ?: true
            }

    val disableCastAutoplayFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.DISABLE_CAST_AUTOPLAY] ?: false
            }

    val resumeOnHeadsetReconnectFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.RESUME_ON_HEADSET_RECONNECT] ?: false
            }

    val dynamicIslandEnabledFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.DYNAMIC_ISLAND_ENABLED] ?: false
            }

    val showQueueHistoryFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.SHOW_QUEUE_HISTORY] ?: false  // Default to false for performance
            }

    suspend fun setShowQueueHistory(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_QUEUE_HISTORY] = show
        }
    }

    val showPlayerFileInfoFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.FULL_PLAYER_SHOW_FILE_INFO] ?: true
            }

    suspend fun setShowPlayerFileInfo(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_SHOW_FILE_INFO] = show
        }
    }

    val fullPlayerLoadingTweaksFlow: Flow<FullPlayerLoadingTweaks> = dataStore.data
        .map { preferences ->
            val delayAlbum = preferences[PreferencesKeys.FULL_PLAYER_DELAY_ALBUM] ?: true
            val delayMetadata = preferences[PreferencesKeys.FULL_PLAYER_DELAY_METADATA] ?: true
            val delayProgress = preferences[PreferencesKeys.FULL_PLAYER_DELAY_PROGRESS] ?: true
            val delayControls = preferences[PreferencesKeys.FULL_PLAYER_DELAY_CONTROLS] ?: true

            val delayAll = delayAlbum && delayMetadata && delayProgress && delayControls

            FullPlayerLoadingTweaks(
                delayAll = delayAll,
                delayAlbumCarousel = delayAlbum,
                delaySongMetadata = delayMetadata,
                delayProgressBar = delayProgress,
                delayControls = delayControls,
                showPlaceholders = preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDERS] ?: true,
                transparentPlaceholders = preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDER_TRANSPARENT] ?: false,
                applyPlaceholdersOnClose = preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDERS_ON_CLOSE] ?: false,
                switchOnDragRelease = preferences[PreferencesKeys.FULL_PLAYER_SWITCH_ON_DRAG_RELEASE] ?: true,
                contentAppearThresholdPercent = preferences[PreferencesKeys.FULL_PLAYER_DELAY_THRESHOLD] ?: 98,
                contentCloseThresholdPercent = preferences[PreferencesKeys.FULL_PLAYER_CLOSE_THRESHOLD] ?: 0
            )
        }

    val favoriteSongIdsFlow: Flow<Set<String>> =
            dataStore.data // Nuevo flujo para favoritos
                    .map { preferences ->
                preferences[PreferencesKeys.FAVORITE_SONG_IDS] ?: emptySet()
            }

    val likedAlbumIdsFlow: Flow<Set<String>> =
            dataStore.data
                    .map { preferences ->
                preferences[PreferencesKeys.LIKED_ALBUM_IDS] ?: emptySet()
            }

    val playlistSongOrderModesFlow: Flow<Map<String, String>> =
            dataStore.data.map { preferences ->
                val serializedModes = preferences[PreferencesKeys.PLAYLIST_SONG_ORDER_MODES]
                if (serializedModes.isNullOrBlank()) {
                    emptyMap()
                } else {
                    runCatching { json.decodeFromString<Map<String, String>>(serializedModes) }
                            .getOrDefault(emptyMap())
                }
            }

    // Legacy DataStore playlist payload kept only for one-time migration and old backup compatibility.
    val legacyUserPlaylistsFlow: Flow<List<Playlist>> =
            dataStore.data.map { preferences ->
                val jsonString = preferences[PreferencesKeys.USER_PLAYLISTS]
                if (jsonString != null) {
                    runCatching { json.decodeFromString<List<Playlist>>(jsonString) }
                        .getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }

    suspend fun getLegacyUserPlaylistsOnce(): List<Playlist> = legacyUserPlaylistsFlow.first()

    suspend fun clearLegacyUserPlaylists() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.USER_PLAYLISTS)
        }
    }

    suspend fun setPlaylistSongOrderMode(playlistId: String, modeValue: String) {
        dataStore.edit { preferences ->
            val existingModes =
                    preferences[PreferencesKeys.PLAYLIST_SONG_ORDER_MODES]?.let { raw ->
                        runCatching { json.decodeFromString<Map<String, String>>(raw) }
                                .getOrDefault(emptyMap())
                    }
                            ?: emptyMap()

            val updated = existingModes.toMutableMap()
            updated[playlistId] = modeValue

            preferences[PreferencesKeys.PLAYLIST_SONG_ORDER_MODES] = json.encodeToString(updated)
        }
    }

    suspend fun setPlaylistSongOrderModes(modes: Map<String, String>) {
        dataStore.edit { preferences ->
            if (modes.isEmpty()) {
                preferences.remove(PreferencesKeys.PLAYLIST_SONG_ORDER_MODES)
            } else {
                preferences[PreferencesKeys.PLAYLIST_SONG_ORDER_MODES] = json.encodeToString(modes)
            }
        }
    }

    suspend fun clearPlaylistSongOrderMode(playlistId: String) {
        dataStore.edit { preferences ->
            val existingModes =
                    preferences[PreferencesKeys.PLAYLIST_SONG_ORDER_MODES]?.let { raw ->
                        runCatching { json.decodeFromString<Map<String, String>>(raw) }
                                .getOrDefault(emptyMap())
                    }
                            ?: emptyMap()

            if (!existingModes.containsKey(playlistId)) return@edit

            val updated = existingModes.toMutableMap()
            updated.remove(playlistId)

            preferences[PreferencesKeys.PLAYLIST_SONG_ORDER_MODES] = json.encodeToString(updated)
        }
    }

    suspend fun updateAllowedDirectories(allowedPaths: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALLOWED_DIRECTORIES] = allowedPaths
            // Directory rules changed: force next sync to fetch full library again.
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = 0L
            val currentVersion = preferences[PreferencesKeys.DIRECTORY_RULES_VERSION] ?: 0
            preferences[PreferencesKeys.DIRECTORY_RULES_VERSION] =
                if (currentVersion == Int.MAX_VALUE) 0 else currentVersion + 1
        }
    }

    suspend fun updateDirectorySelections(allowedPaths: Set<String>, blockedPaths: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALLOWED_DIRECTORIES] = allowedPaths
            preferences[PreferencesKeys.BLOCKED_DIRECTORIES] = blockedPaths
            // Directory rules changed: force next sync to fetch full library again.
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = 0L
            val currentVersion = preferences[PreferencesKeys.DIRECTORY_RULES_VERSION] ?: 0
            preferences[PreferencesKeys.DIRECTORY_RULES_VERSION] =
                if (currentVersion == Int.MAX_VALUE) 0 else currentVersion + 1
        }
    }

    suspend fun toggleFavoriteSong(
            songId: String,
            removing: Boolean = false
    ) { // Nueva función para favoritos
        dataStore.edit { preferences ->
            val currentFavorites = preferences[PreferencesKeys.FAVORITE_SONG_IDS] ?: emptySet()
            val contains = currentFavorites.contains(songId)

            if (contains) preferences[PreferencesKeys.FAVORITE_SONG_IDS] = currentFavorites - songId
            else {
                if (removing)
                        preferences[PreferencesKeys.FAVORITE_SONG_IDS] = currentFavorites - songId
                else preferences[PreferencesKeys.FAVORITE_SONG_IDS] = currentFavorites + songId
            }
        }
    }

    suspend fun setFavoriteSong(songId: String, isFavorite: Boolean) {
        dataStore.edit { preferences ->
            val currentFavorites = preferences[PreferencesKeys.FAVORITE_SONG_IDS] ?: emptySet()
            preferences[PreferencesKeys.FAVORITE_SONG_IDS] = if (isFavorite) {
                currentFavorites + songId
            } else {
                currentFavorites - songId
            }
        }
    }

    suspend fun clearFavoriteSongIds() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FAVORITE_SONG_IDS] = emptySet()
        }
    }

    suspend fun setLikedAlbum(albumId: String, isLiked: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.LIKED_ALBUM_IDS] ?: emptySet()
            preferences[PreferencesKeys.LIKED_ALBUM_IDS] = if (isLiked) {
                current + albumId
            } else {
                current - albumId
            }
        }
    }

    suspend fun setLikedAlbumIds(albumIds: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LIKED_ALBUM_IDS] = albumIds
        }
    }


    suspend fun setInitialSetupDone(isDone: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.INITIAL_SETUP_DONE] = isDone }
    }

    // Flows for Sort Options
    val songsSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[PreferencesKeys.SONGS_SORT_OPTION],
                                SortOption.SONGS,
                                SortOption.SongTitleAZ
                        )
                        .storageKey
            }

    val albumsSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[PreferencesKeys.ALBUMS_SORT_OPTION],
                                SortOption.ALBUMS,
                                SortOption.AlbumTitleAZ
                        )
                        .storageKey
            }

    val artistsSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[PreferencesKeys.ARTISTS_SORT_OPTION],
                                SortOption.ARTISTS,
                                SortOption.ArtistNameAZ
                        )
                        .storageKey
            }

    val playlistsSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[PreferencesKeys.PLAYLISTS_SORT_OPTION],
                                SortOption.PLAYLISTS,
                                SortOption.PlaylistNameAZ
                        )
                        .storageKey
            }

    val foldersSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[PreferencesKeys.FOLDERS_SORT_OPTION],
                                SortOption.FOLDERS,
                                SortOption.FolderNameAZ
                        )
                        .storageKey
            }

    val likedSongsSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[PreferencesKeys.LIKED_SONGS_SORT_OPTION],
                                SortOption.LIKED,
                                SortOption.LikedSongDateLiked
                        )
                        .storageKey
            }

    // Functions to update Sort Options
    suspend fun setSongsSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SONGS_SORT_OPTION] = optionKey
            preferences[PreferencesKeys.SONGS_SORT_OPTION_MIGRATED] = true
        }
    }

    suspend fun setAlbumsSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALBUMS_SORT_OPTION] = optionKey
        }
    }

    suspend fun setArtistsSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ARTISTS_SORT_OPTION] = optionKey
        }
    }

    suspend fun setPlaylistsSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PLAYLISTS_SORT_OPTION] = optionKey
        }
    }

    suspend fun setFoldersSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FOLDERS_SORT_OPTION] = optionKey
        }
    }

    suspend fun setLikedSongsSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LIKED_SONGS_SORT_OPTION] = optionKey
        }
    }

    suspend fun ensureLibrarySortDefaults() {
        dataStore.edit { preferences ->
            val songsMigrated = preferences[PreferencesKeys.SONGS_SORT_OPTION_MIGRATED] ?: false
            val rawSongSort = preferences[PreferencesKeys.SONGS_SORT_OPTION]
            val resolvedSongSort =
                    SortOption.fromStorageKey(rawSongSort, SortOption.SONGS, SortOption.SongTitleAZ)
            val shouldForceSongDefault =
                    !songsMigrated &&
                            (rawSongSort.isNullOrBlank() ||
                                    rawSongSort == SortOption.SongTitleZA.storageKey ||
                                    rawSongSort == SortOption.SongTitleZA.displayName)

            preferences[PreferencesKeys.SONGS_SORT_OPTION] =
                    if (shouldForceSongDefault) {
                        SortOption.SongTitleAZ.storageKey
                    } else {
                        resolvedSongSort.storageKey
                    }
            if (!songsMigrated) {
                preferences[PreferencesKeys.SONGS_SORT_OPTION_MIGRATED] = true
            }

            migrateSortPreference(
                    preferences,
                    PreferencesKeys.SONGS_SORT_OPTION,
                    SortOption.SONGS,
                    SortOption.SongTitleAZ
            )
            migrateSortPreference(
                    preferences,
                    PreferencesKeys.ALBUMS_SORT_OPTION,
                    SortOption.ALBUMS,
                    SortOption.AlbumTitleAZ
            )
            migrateSortPreference(
                    preferences,
                    PreferencesKeys.ARTISTS_SORT_OPTION,
                    SortOption.ARTISTS,
                    SortOption.ArtistNameAZ
            )
            migrateSortPreference(
                    preferences,
                    PreferencesKeys.PLAYLISTS_SORT_OPTION,
                    SortOption.PLAYLISTS,
                    SortOption.PlaylistNameAZ
            )
            migrateSortPreference(
                    preferences,
                    PreferencesKeys.FOLDERS_SORT_OPTION,
                    SortOption.FOLDERS,
                    SortOption.FolderNameAZ
            )
            migrateSortPreference(
                    preferences,
                    PreferencesKeys.LIKED_SONGS_SORT_OPTION,
                    SortOption.LIKED,
                    SortOption.LikedSongDateLiked
            )
        }
    }

    private fun migrateSortPreference(
            preferences: MutablePreferences,
            key: Preferences.Key<String>,
            allowed: Collection<SortOption>,
            fallback: SortOption
    ) {
        val resolved = SortOption.fromStorageKey(preferences[key], allowed, fallback)
        if (preferences[key] != resolved.storageKey) {
            preferences[key] = resolved.storageKey
        }
    }

    // --- Library UI State ---
    val lastLibraryTabIndexFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LAST_LIBRARY_TAB_INDEX] ?: 0 // Default to 0 (Songs tab)
            }

    suspend fun saveLastLibraryTabIndex(tabIndex: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_LIBRARY_TAB_INDEX] = tabIndex
        }
    }

    val lastStorageFilterFlow: Flow<StorageFilter> =
        dataStore.data.map { preferences ->
            when (preferences[PreferencesKeys.LAST_STORAGE_FILTER]) {
                "LOCAL", "OFFLINE" -> StorageFilter.LOCAL
                "TELEGRAM"         -> StorageFilter.TELEGRAM
                "YOUTUBE", "ONLINE" -> StorageFilter.YOUTUBE
                else               -> StorageFilter.ALL
            }
        }

    suspend fun saveLastStorageFilter(filter: StorageFilter) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_STORAGE_FILTER] = filter.name
        }
    }

    val mockGenresEnabledFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.MOCK_GENRES_ENABLED] ?: false // Default to false
            }

    suspend fun setMockGenresEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.MOCK_GENRES_ENABLED] = enabled }
    }

    companion object {
        /** Default character delimiters for splitting multi-artist tags */
        val DEFAULT_ARTIST_DELIMITERS = listOf("/", ";", ",", "+", "&")
        /** Default word-based delimiters (matched case-insensitively with whitespace boundaries) */
        val DEFAULT_ARTIST_WORD_DELIMITERS = listOf("featuring", "feat.", "feat", "ft.", "ft", "vs.", "vs", "versus", "with", "prod.", "prod")
        /** Default keywords for filtering out cover songs and lo-fi tracks */
        val DEFAULT_FILTER_KEYWORDS = listOf("cover", "covers", "lofi", "lo-fi", "chillhop", "slowed", "reverb")
        const val DEFAULT_ALBUM_ART_CACHE_LIMIT_MB = 200
    }

    val navBarCornerRadiusFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                sanitizeNavBarCornerRadius(preferences[PreferencesKeys.NAV_BAR_CORNER_RADIUS] ?: 32)
            }

    suspend fun setNavBarCornerRadius(radius: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NAV_BAR_CORNER_RADIUS] = sanitizeNavBarCornerRadius(radius)
        }
    }

    val navBarStyleFlow: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.NAV_BAR_STYLE] ?: NavBarStyle.DEFAULT
            }

    suspend fun setNavBarStyle(style: String) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.NAV_BAR_STYLE] = style }
    }

    val navBarCompactModeFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.NAV_BAR_COMPACT_MODE] ?: false
            }

    suspend fun setNavBarCompactMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NAV_BAR_COMPACT_MODE] = enabled
        }
    }

    val navBarHeightOffsetFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.NAV_BAR_HEIGHT_OFFSET] ?: 0
            }

    suspend fun setNavBarHeightOffset(offset: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NAV_BAR_HEIGHT_OFFSET] = offset.coerceIn(-20, 20)
        }
    }

    val libraryNavigationModeFlow: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LIBRARY_NAVIGATION_MODE]
                        ?: LibraryNavigationMode.TAB_ROW
            }

    suspend fun setLibraryNavigationMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LIBRARY_NAVIGATION_MODE] = mode
        }
    }

    val carouselStyleFlow: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.CAROUSEL_STYLE] ?: CarouselStyle.NO_PEEK
            }

    suspend fun setCarouselStyle(style: String) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.CAROUSEL_STYLE] = style }
    }

    val launchTabFlow: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LAUNCH_TAB] ?: LaunchTab.HOME
            }

    suspend fun setLaunchTab(tab: String) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.LAUNCH_TAB] = tab }
    }

    suspend fun setKeepPlayingInBackground(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_PLAYING_IN_BACKGROUND] = enabled
        }
    }

    suspend fun setDisableCastAutoplay(disabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISABLE_CAST_AUTOPLAY] = disabled
        }
    }

    suspend fun setResumeOnHeadsetReconnect(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RESUME_ON_HEADSET_RECONNECT] = enabled
        }
    }

    suspend fun setDynamicIslandEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_ISLAND_ENABLED] = enabled
        }
    }

    suspend fun setDelayAllFullPlayerContent(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_ALL] = enabled

            preferences[PreferencesKeys.FULL_PLAYER_DELAY_ALBUM] = enabled
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_METADATA] = enabled
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_PROGRESS] = enabled
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_CONTROLS] = enabled
        }
    }

    suspend fun setDelayAlbumCarousel(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_ALBUM] = enabled
        }
    }

    suspend fun setDelaySongMetadata(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_METADATA] = enabled
        }
    }

    suspend fun setDelayProgressBar(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_PROGRESS] = enabled
        }
    }

    suspend fun setDelayControls(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_CONTROLS] = enabled
        }
    }

    suspend fun setFullPlayerPlaceholders(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDERS] = enabled
            if (!enabled) {
                preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDER_TRANSPARENT] = false
            }
        }
    }

    suspend fun setTransparentPlaceholders(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDER_TRANSPARENT] = enabled
        }
    }

    suspend fun setFullPlayerPlaceholdersOnClose(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDERS_ON_CLOSE] = enabled
        }
    }

    suspend fun setFullPlayerSwitchOnDragRelease(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_SWITCH_ON_DRAG_RELEASE] = enabled
        }
    }

    suspend fun setFullPlayerAppearThreshold(thresholdPercent: Int) {
        val coercedValue = thresholdPercent.coerceIn(0, 100)
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_THRESHOLD] = coercedValue
        }
    }

    suspend fun setFullPlayerCloseThreshold(thresholdPercent: Int) {
        val coercedValue = thresholdPercent.coerceIn(0, 100)
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_CLOSE_THRESHOLD] = coercedValue
        }
    }

    suspend fun clearDeprecatedPlayerSheetPreference() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.USE_PLAYER_SHEET_V2)
        }
    }

    val useAnimatedLyricsFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USE_ANIMATED_LYRICS] ?: false
        }

    suspend fun setUseAnimatedLyrics(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_ANIMATED_LYRICS] = enabled
        }
    }

    val animatedLyricsBlurEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ANIMATED_LYRICS_BLUR_ENABLED] ?: true
        }

    suspend fun setAnimatedLyricsBlurEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ANIMATED_LYRICS_BLUR_ENABLED] = enabled
        }
    }

    // Range should ideally be 0.0f to 5.0f (or similar)
    val animatedLyricsBlurStrengthFlow: Flow<Float> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ANIMATED_LYRICS_BLUR_STRENGTH] ?: 2.5f
        }

    suspend fun setAnimatedLyricsBlurStrength(strength: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ANIMATED_LYRICS_BLUR_STRENGTH] = strength
        }
    }

    val advancedExplorePageFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ADVANCED_EXPLORE_PAGE] ?: true
        }.distinctUntilChanged()

    suspend fun setAdvancedExplorePage(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ADVANCED_EXPLORE_PAGE] = enabled
        }
    }

    val libraryTabsOrderFlow: Flow<String?> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LIBRARY_TABS_ORDER]
        }

    suspend fun saveLibraryTabsOrder(order: String) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.LIBRARY_TABS_ORDER] = order }
    }

    suspend fun resetLibraryTabsOrder() {
        dataStore.edit { preferences -> preferences.remove(PreferencesKeys.LIBRARY_TABS_ORDER) }
    }

    suspend fun migrateTabOrder() {
        dataStore.edit { preferences ->
            val orderJson = preferences[PreferencesKeys.LIBRARY_TABS_ORDER]
            if (orderJson != null) {
                try {
                    val order = json.decodeFromString<MutableList<String>>(orderJson)
                    if (!order.contains("FOLDERS")) {
                        val likedIndex = order.indexOf("LIKED")
                        if (likedIndex != -1) {
                            order.add(likedIndex + 1, "FOLDERS")
                        } else {
                            order.add("FOLDERS") // Fallback
                        }
                        preferences[PreferencesKeys.LIBRARY_TABS_ORDER] = json.encodeToString(order)
                    }
                } catch (e: Exception) {
                    // Si la deserialización falla, no hacemos nada para evitar sobrescribir los
                    // datos del usuario.
                }
            }
            // Si orderJson es nulo, significa que el usuario nunca ha reordenado,
            // por lo que se utilizará el orden predeterminado que ya incluye FOLDERS.
        }
    }

    val isFolderFilterActiveFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.IS_FOLDER_FILTER_ACTIVE] ?: false
            }

    suspend fun setFolderFilterActive(isActive: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_FOLDER_FILTER_ACTIVE] = isActive
        }
    }

    val isFoldersPlaylistViewFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.IS_FOLDERS_PLAYLIST_VIEW] ?: false
        }

    val showTelegramCloudPlaylistsFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SHOW_TELEGRAM_CLOUD_PLAYLISTS] ?: true
        }

    val hideLocalMediaFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.HIDE_LOCAL_MEDIA] ?: false
        }.distinctUntilChanged()

    val telegramTopicDisplayModeFlow: Flow<TelegramTopicDisplayMode> = dataStore.data
        .map { preferences ->
            TelegramTopicDisplayMode.fromStorageKey(preferences[PreferencesKeys.TELEGRAM_TOPIC_DISPLAY_MODE])
        }

    val foldersSourceFlow: Flow<FolderSource> = dataStore.data
        .map { preferences ->
            FolderSource.fromStorageKey(preferences[PreferencesKeys.FOLDERS_SOURCE])
        }

    val folderBackGestureNavigationFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.FOLDER_BACK_GESTURE_NAVIGATION] ?: true
        }

    val useSmoothCornersFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USE_SMOOTH_CORNERS] ?: false
        }

    suspend fun setUseSmoothCorners(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_SMOOTH_CORNERS] = enabled
        }
    }

    suspend fun setFoldersPlaylistView(isPlaylistView: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_FOLDERS_PLAYLIST_VIEW] = isPlaylistView
        }
    }

    suspend fun setShowTelegramCloudPlaylists(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_TELEGRAM_CLOUD_PLAYLISTS] = show
        }
    }

    suspend fun setHideLocalMedia(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIDE_LOCAL_MEDIA] = hide
        }
    }

    suspend fun setTelegramTopicDisplayMode(mode: TelegramTopicDisplayMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TELEGRAM_TOPIC_DISPLAY_MODE] = mode.storageKey
        }
    }

    suspend fun setFoldersSource(source: FolderSource) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FOLDERS_SOURCE] = source.storageKey
        }
    }

    suspend fun setFolderBackGestureNavigation(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FOLDER_BACK_GESTURE_NAVIGATION] = enabled
        }
    }

    // ===== Developer Options =====

    /**
     * Album art quality for player view.
     * Controls the maximum resolution for album artwork displayed in the full player.
     * Thumbnails in lists always use low resolution (256px) for optimal performance.
     */
    val albumArtQualityFlow: Flow<AlbumArtQuality> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.ALBUM_ART_QUALITY]
                ?.let {
                    try { AlbumArtQuality.valueOf(it) }
                    catch (e: Exception) { AlbumArtQuality.ORIGINAL }
                }
                ?: AlbumArtQuality.ORIGINAL
        }

    suspend fun setAlbumArtQuality(quality: AlbumArtQuality) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALBUM_ART_QUALITY] = quality.name
        }
    }

    val albumArtCacheLimitMbFlow: Flow<Int> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.ALBUM_ART_CACHE_LIMIT_MB] ?: DEFAULT_ALBUM_ART_CACHE_LIMIT_MB
        }

    suspend fun setAlbumArtCacheLimitMb(limitMb: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALBUM_ART_CACHE_LIMIT_MB] = limitMb.coerceIn(50, 1500)
        }
    }

    /**
     * Whether tapping the background area of the player sheet closes it.
     * Default is false to avoid accidental dismissals while interacting with the full player.
     */
    val tapBackgroundClosesPlayerFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.TAP_BACKGROUND_CLOSES_PLAYER] ?: false
        }

    suspend fun setTapBackgroundClosesPlayer(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TAP_BACKGROUND_CLOSES_PLAYER] = enabled
        }
    }

    val hapticsEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.HAPTICS_ENABLED] ?: true
        }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun clearPreferencesByKeys(keyNames: Set<String>) {
        if (keyNames.isEmpty()) return
        dataStore.edit { preferences ->
            preferences.asMap().keys
                .filter { key -> key.name in keyNames && key.name !in backupExcludedKeyNames }
                .forEach { key ->
                    @Suppress("UNCHECKED_CAST")
                    preferences.remove(key as Preferences.Key<Any>)
                }
        }
    }

    suspend fun clearPreferencesExceptKeys(excludedKeyNames: Set<String>) {
        val protectedKeyNames = excludedKeyNames + backupExcludedKeyNames
        dataStore.edit { preferences ->
            preferences.asMap().keys
                .filterNot { key -> key.name in protectedKeyNames }
                .forEach { key ->
                    @Suppress("UNCHECKED_CAST")
                    preferences.remove(key as Preferences.Key<Any>)
                }
        }
    }

    suspend fun exportPreferencesForBackup(): List<PreferenceBackupEntry> {
        val snapshot = dataStore.data.first().asMap()
        return snapshot.mapNotNull { (key, value) ->
            val keyName = key.name
            if (keyName in backupExcludedKeyNames) {
                return@mapNotNull null
            }
            when (value) {
                is String -> PreferenceBackupEntry(
                    key = keyName,
                    type = "string",
                    stringValue = value
                )
                is Int -> PreferenceBackupEntry(
                    key = keyName,
                    type = "int",
                    intValue = value
                )
                is Long -> PreferenceBackupEntry(
                    key = keyName,
                    type = "long",
                    longValue = value
                )
                is Boolean -> PreferenceBackupEntry(
                    key = keyName,
                    type = "boolean",
                    booleanValue = value
                )
                is Float -> PreferenceBackupEntry(
                    key = keyName,
                    type = "float",
                    floatValue = value
                )
                is Double -> PreferenceBackupEntry(
                    key = keyName,
                    type = "double",
                    doubleValue = value
                )
                is Set<*> -> {
                    val stringSet = value.filterIsInstance<String>().toSet()
                    PreferenceBackupEntry(
                        key = keyName,
                        type = "string_set",
                        stringSetValue = stringSet
                    )
                }
                else -> null
            }
        }
    }

    suspend fun importPreferencesFromBackup(
        entries: List<PreferenceBackupEntry>,
        clearExisting: Boolean = true
    ) {
        dataStore.edit { preferences ->
            if (clearExisting) {
                preferences.asMap().keys
                    .filterNot { key -> key.name in backupExcludedKeyNames }
                    .forEach { key ->
                        @Suppress("UNCHECKED_CAST")
                        preferences.remove(key as Preferences.Key<Any>)
                    }
            }

            entries.forEach { entry ->
                if (entry.key in backupExcludedKeyNames) {
                    return@forEach
                }
                when (entry.type) {
                    "string" -> {
                        val value = entry.stringValue ?: return@forEach
                        preferences[stringPreferencesKey(entry.key)] = value
                    }
                    "int" -> {
                        val value = entry.intValue
                            ?: entry.doubleValue?.toInt()
                            ?: entry.longValue?.toInt()
                            ?: return@forEach
                        preferences[intPreferencesKey(entry.key)] = value
                    }
                    "long" -> {
                        val value = entry.longValue
                            ?: entry.doubleValue?.toLong()
                            ?: entry.intValue?.toLong()
                            ?: return@forEach
                        preferences[longPreferencesKey(entry.key)] = value
                    }
                    "boolean" -> {
                        val value = entry.booleanValue ?: return@forEach
                        preferences[booleanPreferencesKey(entry.key)] = value
                    }
                    "float" -> {
                        val value = entry.floatValue
                            ?: entry.doubleValue?.toFloat()
                            ?: return@forEach
                        preferences[androidx.datastore.preferences.core.floatPreferencesKey(entry.key)] = value
                    }
                    "double" -> {
                        val value = entry.doubleValue
                            ?: entry.floatValue?.toDouble()
                            ?: return@forEach
                        preferences[androidx.datastore.preferences.core.doublePreferencesKey(entry.key)] = value
                    }
                    "string_set" -> {
                        val value = entry.stringSetValue ?: return@forEach
                        preferences[stringSetPreferencesKey(entry.key)] = value
                    }
                }
            }
        }
    }

    // --- Collage Pattern ---

    val collagePatternFlow: Flow<CollagePattern> =
        dataStore.data.map { preferences ->
            CollagePattern.fromStorageKey(preferences[PreferencesKeys.COLLAGE_PATTERN])
        }

    suspend fun setCollagePattern(pattern: CollagePattern) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COLLAGE_PATTERN] = pattern.storageKey
        }
    }

    val collageAutoRotateFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.COLLAGE_AUTO_ROTATE] ?: false
        }

    suspend fun setCollageAutoRotate(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COLLAGE_AUTO_ROTATE] = enabled
        }
    }

    val collageEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.COLLAGE_ENABLED] ?: true
        }

    suspend fun setCollageEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COLLAGE_ENABLED] = enabled
        }
    }

    // --- Quick Settings: Last Playlist ---

    val lastPlaylistIdFlow: Flow<String?> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_PLAYLIST_ID]?.takeIf { it.isNotBlank() }
        }

    val lastPlaylistNameFlow: Flow<String?> =
        dataStore.data.map { it[PreferencesKeys.LAST_PLAYLIST_NAME] }

    suspend fun setLastPlaylist(playlistId: String, playlistName: String) {
        dataStore.edit {
            it[PreferencesKeys.LAST_PLAYLIST_ID] = playlistId
            it[PreferencesKeys.LAST_PLAYLIST_NAME] = playlistName
        }
    }

    suspend fun clearLastPlaylist() {
        dataStore.edit {
            it.remove(PreferencesKeys.LAST_PLAYLIST_ID)
            it.remove(PreferencesKeys.LAST_PLAYLIST_NAME)
        }
    }

    val subscribedArtistIdsFlow: Flow<Set<String>> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SUBSCRIBED_ARTIST_IDS] ?: emptySet()
        }

    suspend fun subscribeArtist(artistId: String, subscribe: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.SUBSCRIBED_ARTIST_IDS] ?: emptySet()
            if (subscribe) {
                preferences[PreferencesKeys.SUBSCRIBED_ARTIST_IDS] = current + artistId
            } else {
                preferences[PreferencesKeys.SUBSCRIBED_ARTIST_IDS] = current - artistId
            }
        }
    }

    suspend fun setSubscribedArtistIds(artistIds: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SUBSCRIBED_ARTIST_IDS] = artistIds
        }
    }

    val contentLanguageFlow: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.CONTENT_LANGUAGE] ?: if (java.util.Locale.getDefault().country == "IN") "en-IN" else java.util.Locale.getDefault().toLanguageTag().ifBlank { "en" }
        }.distinctUntilChanged()

    suspend fun setContentLanguage(language: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CONTENT_LANGUAGE] = language
        }
    }

    val contentCountryFlow: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.CONTENT_COUNTRY] ?: java.util.Locale.getDefault().country.ifBlank { "IN" }
        }.distinctUntilChanged()

    suspend fun setContentCountry(country: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CONTENT_COUNTRY] = country
        }
    }

    val playlistSuggestionSourceFlow: Flow<PlaylistSuggestionSource> =
        dataStore.data.map { preferences ->
            val stored = preferences[PreferencesKeys.PLAYLIST_SUGGESTION_SOURCE]
            try {
                if (stored != null) PlaylistSuggestionSource.valueOf(stored) else PlaylistSuggestionSource.BOTH
            } catch (e: Exception) {
                PlaylistSuggestionSource.BOTH
            }
        }.distinctUntilChanged()

    suspend fun setPlaylistSuggestionSource(source: PlaylistSuggestionSource) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PLAYLIST_SUGGESTION_SOURCE] = source.name
        }
    }

    val hideExplicitFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.HIDE_EXPLICIT] ?: false
        }.distinctUntilChanged()

    suspend fun setHideExplicit(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIDE_EXPLICIT] = hide
        }
    }

    val hideVideoFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.HIDE_VIDEO] ?: false
        }.distinctUntilChanged()

    suspend fun setHideVideo(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIDE_VIDEO] = hide
        }
    }

    val topSizeFlow: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.TOP_SIZE] ?: "50"
        }.distinctUntilChanged()

    suspend fun setTopSize(size: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TOP_SIZE] = size
        }
    }

    val discoverFlow: Flow<QuickPicks> =
        dataStore.data.map { preferences ->
            val stored = preferences[PreferencesKeys.DISCOVER]
            try {
                if (stored != null) QuickPicks.valueOf(stored) else QuickPicks.QUICK_PICKS
            } catch (e: Exception) {
                QuickPicks.QUICK_PICKS
            }
        }.distinctUntilChanged()

    suspend fun setDiscover(discover: QuickPicks) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISCOVER] = discover.name
        }
    }

    val quickPicksDisplayModeFlow: Flow<QuickPicksDisplayMode> =
        dataStore.data.map { preferences ->
            val stored = preferences[PreferencesKeys.QUICK_PICKS_DISPLAY_MODE]
            try {
                if (stored != null) QuickPicksDisplayMode.valueOf(stored) else QuickPicksDisplayMode.UNCONTAINED
            } catch (e: Exception) {
                QuickPicksDisplayMode.UNCONTAINED
            }
        }.distinctUntilChanged()

    suspend fun setQuickPicksDisplayMode(mode: QuickPicksDisplayMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.QUICK_PICKS_DISPLAY_MODE] = mode.name
        }
    }

    val searchSourceFlow: Flow<SearchSource> =
        dataStore.data.map { preferences ->
            val stored = preferences[PreferencesKeys.SEARCH_SOURCE]
            try {
                if (stored != null) SearchSource.valueOf(stored) else SearchSource.ONLINE
            } catch (e: Exception) {
                SearchSource.ONLINE
            }
        }.distinctUntilChanged()

    suspend fun setSearchSource(source: SearchSource) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SEARCH_SOURCE] = source.name
        }
    }

    val lastfmSessionFlow: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LASTFM_SESSION] ?: ""
        }.distinctUntilChanged()

    suspend fun setLastfmSession(session: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LASTFM_SESSION] = session
        }
    }

    val lastfmUsernameFlow: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LASTFM_USERNAME] ?: ""
        }.distinctUntilChanged()

    suspend fun setLastfmUsername(username: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LASTFM_USERNAME] = username
        }
    }

    val lastfmApiKeyFlow: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LASTFM_API_KEY] ?: ""
        }.distinctUntilChanged()

    suspend fun setLastfmApiKey(apiKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LASTFM_API_KEY] = apiKey
        }
    }

    val lastfmApiSecretFlow: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LASTFM_API_SECRET] ?: ""
        }.distinctUntilChanged()

    suspend fun setLastfmApiSecret(apiSecret: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LASTFM_API_SECRET] = apiSecret
        }
    }

    val lastfmScrobblingEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LASTFM_SCROBBLING_ENABLED] ?: false
        }.distinctUntilChanged()

    suspend fun setLastfmScrobblingEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LASTFM_SCROBBLING_ENABLED] = enabled
        }
    }

    val lastfmUseNowPlayingFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LASTFM_USE_NOW_PLAYING] ?: false
        }.distinctUntilChanged()

    suspend fun setLastfmUseNowPlaying(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LASTFM_USE_NOW_PLAYING] = enabled
        }
    }

    val lastfmSmartMixEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LASTFM_SMART_MIX_ENABLED] ?: true
        }.distinctUntilChanged()

    suspend fun setLastfmSmartMixEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LASTFM_SMART_MIX_ENABLED] = enabled
        }
    }

    val exploreLastfmEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.EXPLORE_LASTFM_ENABLED] ?: true
        }.distinctUntilChanged()

    suspend fun setExploreLastfmEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.EXPLORE_LASTFM_ENABLED] = enabled
        }
    }

    suspend fun setExploreLastFmEnabled(enabled: Boolean) = setExploreLastfmEnabled(enabled)

    val filterCoverAndLofiFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.FILTER_COVER_AND_LOFI] ?: true
        }.distinctUntilChanged()

    suspend fun setFilterCoverAndLofi(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FILTER_COVER_AND_LOFI] = enabled
        }
    }

    val filterKeywordsFlow: Flow<List<String>> =
        dataStore.data.map { preferences ->
            val stored = preferences[PreferencesKeys.FILTER_KEYWORDS]
            if (stored != null) {
                try {
                    json.decodeFromString<List<String>>(stored)
                } catch (e: Exception) {
                    DEFAULT_FILTER_KEYWORDS
                }
            } else {
                DEFAULT_FILTER_KEYWORDS
            }
        }.distinctUntilChanged()

    suspend fun setFilterKeywords(keywords: List<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FILTER_KEYWORDS] = json.encodeToString(keywords)
        }
    }

    suspend fun resetFilterKeywordsToDefault() {
        setFilterKeywords(DEFAULT_FILTER_KEYWORDS)
    }

    val notInterestedSongIdsFlow: Flow<Set<String>> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.NOT_INTERESTED_SONG_IDS] ?: emptySet()
        }.distinctUntilChanged()

    suspend fun setSongNotInterested(ids: Collection<String>, notInterested: Boolean) {
        if (ids.isEmpty()) return
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.NOT_INTERESTED_SONG_IDS] ?: emptySet()
            preferences[PreferencesKeys.NOT_INTERESTED_SONG_IDS] = if (notInterested) {
                current + ids
            } else {
                current - ids.toSet()
            }
        }
    }

    val blockedReleaseIdsFlow: Flow<Set<String>> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.BLOCKED_RELEASE_IDS] ?: emptySet()
        }.distinctUntilChanged()

    suspend fun blockRelease(id: String, blocked: Boolean = true) {
        if (id.isBlank()) return
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.BLOCKED_RELEASE_IDS] ?: emptySet()
            preferences[PreferencesKeys.BLOCKED_RELEASE_IDS] = if (blocked) {
                current + id
            } else {
                current - id
            }
            // Also synchronize with not-interested songs if applicable
            val currentNotInterested = preferences[PreferencesKeys.NOT_INTERESTED_SONG_IDS] ?: emptySet()
            preferences[PreferencesKeys.NOT_INTERESTED_SONG_IDS] = if (blocked) {
                currentNotInterested + id
            } else {
                currentNotInterested - id
            }
        }
    }

    val scrobbleDelayPercentFlow: Flow<Float> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SCROBBLE_DELAY_PERCENT] ?: 0.5f
        }.distinctUntilChanged()

    suspend fun setScrobbleDelayPercent(percent: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SCROBBLE_DELAY_PERCENT] = percent
        }
    }

    val scrobbleMinSongDurationFlow: Flow<Int> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SCROBBLE_MIN_SONG_DURATION] ?: 30
        }.distinctUntilChanged()

    suspend fun setScrobbleMinSongDuration(duration: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SCROBBLE_MIN_SONG_DURATION] = duration
        }
    }

    val scrobbleDelaySecondsFlow: Flow<Int> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SCROBBLE_DELAY_SECONDS] ?: 180
        }.distinctUntilChanged()

    suspend fun setScrobbleDelaySeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SCROBBLE_DELAY_SECONDS] = seconds
        }
    }

    val generatedPlaylistsRetentionPeriodFlow: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.GENERATED_PLAYLISTS_RETENTION_PERIOD] ?: "permanent"
        }.distinctUntilChanged()

    suspend fun setGeneratedPlaylistsRetentionPeriod(period: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.GENERATED_PLAYLISTS_RETENTION_PERIOD] = period
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // YouTube Granular Sync & Personalization Options
    // ─────────────────────────────────────────────────────────────────────────────

    val youtubeSyncPlaylistsAndLikesFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.YOUTUBE_SYNC_PLAYLISTS_AND_LIKES] ?: true
        }.distinctUntilChanged()

    suspend fun setYoutubeSyncPlaylistsAndLikes(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.YOUTUBE_SYNC_PLAYLISTS_AND_LIKES] = enabled
        }
    }

    val youtubeSyncListeningActivityFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.YOUTUBE_SYNC_LISTENING_ACTIVITY] ?: true
        }.distinctUntilChanged()

    suspend fun setYoutubeSyncListeningActivity(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.YOUTUBE_SYNC_LISTENING_ACTIVITY] = enabled
        }
    }

    val youtubePersonalizedExploreFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.YOUTUBE_PERSONALIZED_EXPLORE] ?: true
        }.distinctUntilChanged()

    suspend fun setYoutubePersonalizedExplore(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.YOUTUBE_PERSONALIZED_EXPLORE] = enabled
        }
    }

    val youtubePersonalizedQueueFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.YOUTUBE_PERSONALIZED_QUEUE] ?: true
        }.distinctUntilChanged()

    suspend fun setYoutubePersonalizedQueue(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.YOUTUBE_PERSONALIZED_QUEUE] = enabled
        }
    }

    val exoCacheSizeBytesFlow: Flow<Long> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.EXO_CACHE_SIZE_BYTES] ?: (2L * 1024L * 1024L * 1024L) // 2GB default
        }.distinctUntilChanged()

    suspend fun setExoCacheSizeBytes(sizeBytes: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.EXO_CACHE_SIZE_BYTES] = sizeBytes.coerceAtLeast(100L * 1024L * 1024L)
        }
    }

    val lastSuccessfulYoutubeClientKeyFlow: Flow<String?> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_SUCCESSFUL_YOUTUBE_CLIENT_KEY]
        }.distinctUntilChanged()

    suspend fun setLastSuccessfulYoutubeClientKey(key: String?) {
        dataStore.edit { preferences ->
            if (key != null) {
                preferences[PreferencesKeys.LAST_SUCCESSFUL_YOUTUBE_CLIENT_KEY] = key
            } else {
                preferences.remove(PreferencesKeys.LAST_SUCCESSFUL_YOUTUBE_CLIENT_KEY)
            }
        }
    }

    val advancedPerformanceDiagnosticsSettingsFlow: Flow<AdvancedPerformanceDiagnosticsSettings> =
        dataStore.data.map { preferences ->
            AdvancedPerformanceDiagnosticsSettings(
                enabled = preferences[PreferencesKeys.ADVANCED_PERFORMANCE_DIAGNOSTICS_ENABLED] ?: false,
                sessionStartedEpochMs =
                    preferences[PreferencesKeys.ADVANCED_PERFORMANCE_DIAGNOSTICS_STARTED_AT],
                expiresAtEpochMs =
                    preferences[PreferencesKeys.ADVANCED_PERFORMANCE_DIAGNOSTICS_EXPIRES_AT]
            )
        }.distinctUntilChanged()

    suspend fun setAdvancedPerformanceDiagnosticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            if (enabled) {
                val now = System.currentTimeMillis()
                preferences[PreferencesKeys.ADVANCED_PERFORMANCE_DIAGNOSTICS_ENABLED] = true
                preferences[PreferencesKeys.ADVANCED_PERFORMANCE_DIAGNOSTICS_STARTED_AT] = now
                preferences[PreferencesKeys.ADVANCED_PERFORMANCE_DIAGNOSTICS_EXPIRES_AT] =
                    now + AdvancedPerformanceDiagnostics.DEFAULT_SESSION_DURATION_MS
            } else {
                preferences[PreferencesKeys.ADVANCED_PERFORMANCE_DIAGNOSTICS_ENABLED] = false
                preferences.remove(PreferencesKeys.ADVANCED_PERFORMANCE_DIAGNOSTICS_STARTED_AT)
                preferences.remove(PreferencesKeys.ADVANCED_PERFORMANCE_DIAGNOSTICS_EXPIRES_AT)
            }
        }
    }

    suspend fun disableExpiredAdvancedPerformanceDiagnostics(nowEpochMs: Long = System.currentTimeMillis()) {
        dataStore.edit { preferences ->
            val enabled = preferences[PreferencesKeys.ADVANCED_PERFORMANCE_DIAGNOSTICS_ENABLED] ?: false
            val expiresAt = preferences[PreferencesKeys.ADVANCED_PERFORMANCE_DIAGNOSTICS_EXPIRES_AT]
            if (enabled && (expiresAt == null || nowEpochMs >= expiresAt)) {
                preferences[PreferencesKeys.ADVANCED_PERFORMANCE_DIAGNOSTICS_ENABLED] = false
                preferences.remove(PreferencesKeys.ADVANCED_PERFORMANCE_DIAGNOSTICS_STARTED_AT)
                preferences.remove(PreferencesKeys.ADVANCED_PERFORMANCE_DIAGNOSTICS_EXPIRES_AT)
            }
        }
    }

    // ────────────────────────── In-App Updates ──────────────────────────

    val autoUpdateCheckEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.AUTO_UPDATE_CHECK_ENABLED] ?: true
        }.distinctUntilChanged()

    suspend fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_UPDATE_CHECK_ENABLED] = enabled
        }
    }

    val lastUpdateCheckTimeFlow: Flow<Long> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_UPDATE_CHECK_TIME] ?: 0L
        }.distinctUntilChanged()

    suspend fun setLastUpdateCheckTime(timestampMs: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_UPDATE_CHECK_TIME] = timestampMs
        }
    }

    val lastUpdateEtagFlow: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_UPDATE_ETAG] ?: ""
        }.distinctUntilChanged()

    suspend fun setLastUpdateEtag(etag: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_UPDATE_ETAG] = etag
        }
    }

    val ignoredUpdateVersionFlow: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.IGNORED_UPDATE_VERSION] ?: ""
        }.distinctUntilChanged()

    suspend fun setIgnoredUpdateVersion(version: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IGNORED_UPDATE_VERSION] = version
        }
    }

    val cachedReleaseInfoFlow: Flow<String?> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.CACHED_RELEASE_INFO]
        }.distinctUntilChanged()

    suspend fun setCachedReleaseInfo(json: String?) {
        dataStore.edit { preferences ->
            if (json != null) {
                preferences[PreferencesKeys.CACHED_RELEASE_INFO] = json
            } else {
                preferences.remove(PreferencesKeys.CACHED_RELEASE_INFO)
            }
        }
    }
}
