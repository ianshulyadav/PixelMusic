package com.unshoo.pixelmusic.presentation.navigation

import androidx.compose.runtime.Immutable


@Immutable
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Library : Screen("library")
    object Explore : Screen("explore")
    object Settings : Screen("settings")
    object Accounts : Screen("settings_accounts")
    object SettingsCategory : Screen("settings_category/{categoryId}") {
        fun createRoute(categoryId: String) = "settings_category/$categoryId"
    }
    object PaletteStyle : Screen("palette_style_settings")
    object Experimental : Screen("experimental_settings")
    object NavBarCrRad : Screen("nav_bar_corner_radius")
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist_detail/$playlistId"
    }
    object FeedPlaylistDetail : Screen("feed_playlist_detail/{playlistId}") {
        fun createRoute(playlistId: String) = "feed_playlist_detail/${java.net.URLEncoder.encode(playlistId, "UTF-8")}"
    }

    object SmartMix : Screen("smart_mix")

    object  DailyMixScreen : Screen("daily_mix")
    object RecentlyPlayed : Screen("recently_played")
    object QuickPicksAll : Screen("quick_picks_all")
    object Stats : Screen("stats")
    object DJSpace : Screen("dj_space")
    // La ruta base es "album_detail". La ruta completa con el argumento se define en AppNavigation.
    object AlbumDetail : Screen("album_detail/{albumId}") {
        fun createRoute(albumId: Long) = "album_detail/$albumId"
        fun createRoute(albumId: String) = "album_detail/$albumId"
    }

    object ArtistDetail : Screen("artist_detail/{artistId}") {
        fun createRoute(artistId: Long) = "artist_detail/$artistId"
        fun createRoute(artistId: String) = "artist_detail/$artistId"
    }

    object ArtistAlbumsAll : Screen("artist_albums_all/{artistId}/{type}") {
        fun createRoute(artistId: String, type: String) = "artist_albums_all/$artistId/$type"
    }
    
    object ArtistSongsAll : Screen("artist_songs_all/{artistId}") {
        fun createRoute(artistId: String) = "artist_songs_all/$artistId"
    }

    object EditTransition : Screen("edit_transition?playlistId={playlistId}") {
        fun createRoute(playlistId: String?) =
            if (playlistId != null) "edit_transition?playlistId=$playlistId" else "edit_transition"
    }

    object About : Screen("about")
    object EasterEgg : Screen("easter_egg")
    object BlurSettings : Screen("blur_settings")

    object ArtistSettings : Screen("artist_settings")
    object DelimiterConfig : Screen("delimiter_config")
    object WordDelimiterConfig : Screen("word_delimiter_config")
    object FilterKeywordsConfig : Screen("filter_keywords_config")
    object Equalizer : Screen("equalizer")
    object DeviceCapabilities : Screen("device_capabilities")
    object YoutubeAuth : Screen("youtube_auth")

    // Lossless sources (ArchiveTune port) — provider sign-in WebViews
    object TidalLogin : Screen("lossless_tidal_login")
    object DeezerLogin : Screen("lossless_deezer_login")
    object QobuzLogin : Screen("lossless_qobuz_login")
    object AppleMusicLogin : Screen("lossless_apple_music_login")
    object CloudDownloads : Screen("cloud_downloads")

}
