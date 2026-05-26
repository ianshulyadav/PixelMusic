package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.unshoo.pixelmusic.data.model.youtube.Cookies
import com.unshoo.pixelmusic.data.model.youtube.UmihiSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

val Context.youtubeDataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.Datastore.NAME)

open class DatastoreRepository(private val context: Context) {
    object PreferenceKeys {
        val COOKIES = stringPreferencesKey(Constants.Datastore.COOKIES_KEY)
        val DATA_SYNC_ID = stringPreferencesKey(Constants.Datastore.DATA_SYNC_ID)
        val UPDATE_CHANNEL = stringPreferencesKey(Constants.Datastore.UPDATE_CHANNEL_KEY)
        val SHOW_PODCAST_PLAYLIST = booleanPreferencesKey(Constants.Datastore.SHOW_PODCAST_PLAYLIST)
        val USE_SPECIAL_LANGUAGE = booleanPreferencesKey(Constants.Datastore.USE_SPECIAL_LANGUAGE)
        val USE_AUDIO_OFFLOAD = booleanPreferencesKey(Constants.Datastore.USE_AUDIO_OFFLOAD)
        val KEEP_SCREEN_ON = booleanPreferencesKey(Constants.Datastore.KEEP_SCREEN_ON)
        val USE_ANIMATED_LYRICS = booleanPreferencesKey(Constants.Datastore.USE_ANIMATED_LYRICS)
        val ANIMATED_LYRICS_BLUR_ENABLED = booleanPreferencesKey(Constants.Datastore.ANIMATED_LYRICS_BLUR_ENABLED)
        val USE_IMMERSIVE_LYRICS = booleanPreferencesKey("use_immersive_lyrics")
        val LYRICS_AUTOHIDE_DELAY = intPreferencesKey("lyrics_autohide_delay")
        val SHOW_PLAYER_FILE_INFO = booleanPreferencesKey("show_player_file_info")
        val PLAYER_THEME_PREFERENCE = stringPreferencesKey("player_theme_preference")
        val COLOR_PALETTE_PREFERENCE = stringPreferencesKey("color_palette_preference")
        val LYRICS_MINIPLAYER_POSITION = stringPreferencesKey("lyrics_miniplayer_position")
        val LYRICS_MINIPLAYER_ALIGNMENT = stringPreferencesKey("lyrics_miniplayer_alignment")
        val USE_IMMERSIVE_LYRICS_STATUS_BAR = booleanPreferencesKey("use_immersive_lyrics_status_bar")
        val AUTO_QUEUE_ENABLED = booleanPreferencesKey("auto_queue_enabled")
        val PRELOAD_QUEUE_ENABLED = booleanPreferencesKey("preload_queue_enabled")
        val PRELOAD_QUEUE_SIZE = intPreferencesKey("preload_queue_size")
        val PERSISTENT_QUEUE = stringPreferencesKey("persistent_queue")
    }

    suspend fun <T> save(key: Preferences.Key<T>, value: T) {
        context.youtubeDataStore.edit {
            it[key] = value
        }
    }

    open val settings = context.youtubeDataStore.data.map {
        val updateChannel = it[PreferenceKeys.UPDATE_CHANNEL]?.let { value -> UmihiSettings.UpdateChannel.valueOf(value) }
            ?: UmihiSettings.UpdateChannel.Stable
        val showPodcastPlaylist = it[PreferenceKeys.SHOW_PODCAST_PLAYLIST] ?: true
        val useSpecialLanguage = it[PreferenceKeys.USE_SPECIAL_LANGUAGE] ?: false
        val useAudioOffload = it[PreferenceKeys.USE_AUDIO_OFFLOAD] ?: false
        val keepScreenOn = it[PreferenceKeys.KEEP_SCREEN_ON] ?: false
        val useAnimatedLyrics = it[PreferenceKeys.USE_ANIMATED_LYRICS] ?: true
        val animatedLyricsBlurEnabled = it[PreferenceKeys.ANIMATED_LYRICS_BLUR_ENABLED] ?: true
        val useImmersiveLyrics = it[PreferenceKeys.USE_IMMERSIVE_LYRICS] ?: true
        val lyricsAutoHideDelay = it[PreferenceKeys.LYRICS_AUTOHIDE_DELAY] ?: 4
        val showPlayerFileInfo = it[PreferenceKeys.SHOW_PLAYER_FILE_INFO] ?: false
        val playerThemePreference = it[PreferenceKeys.PLAYER_THEME_PREFERENCE] ?: "ALBUM_ART"
        val colorPalettePreference = it[PreferenceKeys.COLOR_PALETTE_PREFERENCE] ?: "SAGE"
        val lyricsMiniPlayerPosition = it[PreferenceKeys.LYRICS_MINIPLAYER_POSITION] ?: "TOP"
        val lyricsMiniPlayerAlignment = it[PreferenceKeys.LYRICS_MINIPLAYER_ALIGNMENT] ?: "LEFT"
        val useImmersiveLyricsStatusBar = it[PreferenceKeys.USE_IMMERSIVE_LYRICS_STATUS_BAR] ?: true
        val autoQueueEnabled = it[PreferenceKeys.AUTO_QUEUE_ENABLED] ?: true
        val preloadQueueEnabled = it[PreferenceKeys.PRELOAD_QUEUE_ENABLED] ?: true
        val preloadQueueSize = it[PreferenceKeys.PRELOAD_QUEUE_SIZE] ?: 5
        val cookies = cookies.first()
        val dataSyncId = dataSyncId.first()

        UmihiSettings(
            updateChannel = updateChannel,
            showPodcastPlaylist = showPodcastPlaylist,
            cookies = cookies,
            dataSyncId = dataSyncId,
            useSpecialLanguage = useSpecialLanguage,
            useAudioOffload = useAudioOffload,
            keepScreenOn = keepScreenOn,
            useAnimatedLyrics = useAnimatedLyrics,
            animatedLyricsBlurEnabled = animatedLyricsBlurEnabled,
            useImmersiveLyrics = useImmersiveLyrics,
            lyricsAutoHideDelay = lyricsAutoHideDelay,
            showPlayerFileInfo = showPlayerFileInfo,
            playerThemePreference = playerThemePreference,
            colorPalettePreference = colorPalettePreference,
            lyricsMiniPlayerPosition = lyricsMiniPlayerPosition,
            lyricsMiniPlayerAlignment = lyricsMiniPlayerAlignment,
            useImmersiveLyricsStatusBar = useImmersiveLyricsStatusBar,
            autoQueueEnabled = autoQueueEnabled,
            preloadQueueEnabled = preloadQueueEnabled,
            preloadQueueSize = preloadQueueSize
        )
    }



    val cookies = context.youtubeDataStore.data.map {
        Cookies(it[PreferenceKeys.COOKIES] ?: "")
    }

    val dataSyncId = context.youtubeDataStore.data.map {
        it[PreferenceKeys.DATA_SYNC_ID] ?: ""
    }

    suspend fun saveCookies(cookies: Cookies) {
        context.youtubeDataStore.edit {
            it[PreferenceKeys.COOKIES] = cookies.toRawCookie()
        }
    }

    suspend fun saveDataSyncId(newId: String) {
        context.youtubeDataStore.edit {
            it[PreferenceKeys.DATA_SYNC_ID] = newId
        }
    }

    suspend fun getPersistentQueue(): String {
        return context.youtubeDataStore.data.first()[PreferenceKeys.PERSISTENT_QUEUE] ?: ""
    }

    suspend fun savePersistentQueue(json: String) {
        context.youtubeDataStore.edit {
            it[PreferenceKeys.PERSISTENT_QUEUE] = json
        }
    }
}
