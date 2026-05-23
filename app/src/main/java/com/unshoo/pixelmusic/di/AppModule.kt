package com.unshoo.pixelmusic.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.WorkManager
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.unshoo.pixelmusic.BuildConfig
import com.unshoo.pixelmusic.PixelMusicApplication
import com.unshoo.pixelmusic.data.database.AlbumArtThemeDao
import com.unshoo.pixelmusic.data.database.EngagementDao
import com.unshoo.pixelmusic.data.database.FavoritesDao
import com.unshoo.pixelmusic.data.database.GDriveDao
import com.unshoo.pixelmusic.data.database.LyricsDao
import com.unshoo.pixelmusic.data.database.AiCacheDao
import com.unshoo.pixelmusic.data.database.AiUsageDao
import com.unshoo.pixelmusic.data.database.LocalPlaylistDao
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.database.PixelMusicDatabase
import com.unshoo.pixelmusic.data.database.SearchHistoryDao
import com.unshoo.pixelmusic.data.database.TransitionDao
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.preferences.PlaylistPreferencesRepository
import com.unshoo.pixelmusic.data.preferences.dataStore
import com.unshoo.pixelmusic.data.media.SongMetadataEditor
import com.unshoo.pixelmusic.data.network.deezer.DeezerApiService
import com.unshoo.pixelmusic.data.network.lyrics.LrcLibApiService
import com.unshoo.pixelmusic.data.repository.ArtistImageRepository
import com.unshoo.pixelmusic.data.repository.LyricsRepository
import com.unshoo.pixelmusic.data.repository.LyricsRepositoryImpl
import com.unshoo.pixelmusic.data.repository.MediaStoreSongRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.data.repository.MusicRepositoryImpl
import com.unshoo.pixelmusic.data.repository.SongRepository
import com.unshoo.pixelmusic.data.repository.TransitionRepository
import com.unshoo.pixelmusic.data.repository.TransitionRepositoryImpl
import com.unshoo.pixelmusic.data.repository.FolderTreeBuilder
import dagger.Module
import dagger.Provides
import dagger.Lazy
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideApplication(@ApplicationContext app: Context): PixelMusicApplication {
        return app as PixelMusicApplication
    }

    @Singleton
    @Provides
    fun provideGson(): com.google.gson.Gson {
        return com.google.gson.Gson()
    }

    @OptIn(UnstableApi::class)
    @Singleton
    @Provides
    fun provideSessionToken(@ApplicationContext context: Context): androidx.media3.session.SessionToken {
        return androidx.media3.session.SessionToken(
            context,
            android.content.ComponentName(context, com.unshoo.pixelmusic.data.service.MusicService::class.java)
        )
    }

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.dataStore

    @Singleton
    @Provides
    fun provideJson(): Json { // Proveer Json
        return Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    @Singleton
    @Provides
    @AppScope
    fun provideAppCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Singleton
    @Provides
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Singleton
    @Provides
    fun providePixelMusicDatabase(@ApplicationContext context: Context): PixelMusicDatabase {
        val builder = Room.databaseBuilder(
            context.applicationContext,
            PixelMusicDatabase::class.java,
            "pixelmusic_database"
        ).addMigrations(
            PixelMusicDatabase.MIGRATION_3_4,
            PixelMusicDatabase.MIGRATION_4_5,
            PixelMusicDatabase.MIGRATION_5_6,
            PixelMusicDatabase.MIGRATION_6_7,
            PixelMusicDatabase.MIGRATION_7_8,
            PixelMusicDatabase.MIGRATION_8_9,
            PixelMusicDatabase.MIGRATION_9_10,
            PixelMusicDatabase.MIGRATION_10_11,
            PixelMusicDatabase.MIGRATION_11_12,
            PixelMusicDatabase.MIGRATION_12_13,
            PixelMusicDatabase.MIGRATION_13_14,
            PixelMusicDatabase.MIGRATION_14_15,
            PixelMusicDatabase.MIGRATION_15_16,
            PixelMusicDatabase.MIGRATION_16_17,
            PixelMusicDatabase.MIGRATION_17_18,
            PixelMusicDatabase.MIGRATION_18_19,
            PixelMusicDatabase.MIGRATION_19_20,
            PixelMusicDatabase.MIGRATION_20_21,
            PixelMusicDatabase.MIGRATION_21_22,
            PixelMusicDatabase.MIGRATION_22_23,
            PixelMusicDatabase.MIGRATION_23_24,
            PixelMusicDatabase.MIGRATION_24_25,
            PixelMusicDatabase.MIGRATION_25_26,
            PixelMusicDatabase.MIGRATION_26_27,
            PixelMusicDatabase.MIGRATION_27_28,
            PixelMusicDatabase.MIGRATION_28_29,
            PixelMusicDatabase.MIGRATION_29_30,
            PixelMusicDatabase.MIGRATION_30_31,
            PixelMusicDatabase.MIGRATION_31_32,
            PixelMusicDatabase.MIGRATION_32_33,
            PixelMusicDatabase.MIGRATION_33_34,
            PixelMusicDatabase.MIGRATION_34_35,
            PixelMusicDatabase.MIGRATION_35_36,
            PixelMusicDatabase.MIGRATION_36_37,
            PixelMusicDatabase.MIGRATION_37_38,
            PixelMusicDatabase.MIGRATION_38_39,
            PixelMusicDatabase.MIGRATION_39_40,
            PixelMusicDatabase.MIGRATION_40_41,
            PixelMusicDatabase.MIGRATION_41_42
        )
            .addCallback(PixelMusicDatabase.createRuntimeArtifactsCallback())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)

        // P2-4: Only allow destructive migration in debug builds.
        // In release, a migration bug will crash the app (revealing the problem)
        // rather than silently wiping user data (playlists, favorites, statistics).
        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration(dropAllTables = true)
        }

        return builder.build()
    }

    @Singleton
    @Provides
    fun provideAlbumArtThemeDao(database: PixelMusicDatabase): AlbumArtThemeDao {
        return database.albumArtThemeDao()
    }

    @Singleton
    @Provides
    fun provideSearchHistoryDao(database: PixelMusicDatabase): SearchHistoryDao { // NUEVO MÉTODO
        return database.searchHistoryDao()
    }

    @Singleton
    @Provides
    fun provideMusicDao(database: PixelMusicDatabase): MusicDao { // Proveer MusicDao
        return database.musicDao()
    }

    @Singleton
    @Provides
    fun provideTransitionDao(database: PixelMusicDatabase): TransitionDao {
        return database.transitionDao()
    }

    @Singleton
    @Provides
    fun provideEngagementDao(database: PixelMusicDatabase): EngagementDao {
        return database.engagementDao()
    }

    @Singleton
    @Provides
    fun provideFavoritesDao(database: PixelMusicDatabase): FavoritesDao {
        return database.favoritesDao()
    }

    @Singleton
    @Provides
    fun provideLyricsDao(database: PixelMusicDatabase): LyricsDao {
        return database.lyricsDao()
    }

    @Singleton
    @Provides
    fun provideGDriveDao(database: PixelMusicDatabase): GDriveDao {
        return database.gdriveDao()
    }

    @Singleton
    @Provides
    fun provideLocalPlaylistDao(database: PixelMusicDatabase): LocalPlaylistDao {
        return database.localPlaylistDao()
    }
    
    @Singleton
    @Provides
    fun provideAiCacheDao(database: PixelMusicDatabase): AiCacheDao {
        return database.aiCacheDao()
    }

    @Provides
    fun provideAiUsageDao(database: PixelMusicDatabase): AiUsageDao {
        return database.aiUsageDao()
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context
    ): ImageLoader {
        // Add interceptor for QQ Music images
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()

                // Add Referer header for QQ Music images
                val newRequest = if (url.contains("y.qq.com")) {
                    request.newBuilder()
                        .header("Referer", "https://y.qq.com/")
                        .build()
                } else {
                    request
                }

                chain.proceed(newRequest)
            }
            .build()

        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .dispatcher(Dispatchers.Default) // Use CPU-bound dispatcher for decoding
            .allowHardware(true) // Re-enable hardware bitmaps for better performance
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.20) // Use 20% of app memory for image cache
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100 MB disk cache
                    .build()
            }
            .respectCacheHeaders(false) // Ignore server cache headers, always cache
            .build()
    }

    @Provides
    @Singleton
    fun provideLyricsRepository(
        @ApplicationContext context: Context,
        lrcLibApiService: LrcLibApiService,
        lyricsDao: LyricsDao,
        okHttpClient: OkHttpClient
    ): LyricsRepository {
        return LyricsRepositoryImpl(
            context = context,
            lrcLibApiService = lrcLibApiService,
            lyricsDao = lyricsDao,
            okHttpClient = okHttpClient
        )
    }

    @Provides
    @Singleton
    fun provideSongRepository(
        @ApplicationContext context: Context,
        mediaStoreObserver: com.unshoo.pixelmusic.data.observer.MediaStoreObserver,
        favoritesDao: FavoritesDao,
        userPreferencesRepository: UserPreferencesRepository,
        musicDao: MusicDao
    ): SongRepository {
        return MediaStoreSongRepository(
            context = context,
            mediaStoreObserver = mediaStoreObserver,
            favoritesDao = favoritesDao,
            userPreferencesRepository = userPreferencesRepository,
            musicDao = musicDao
        )
    }

    @Singleton
    @Provides
    fun provideTelegramDao(database: PixelMusicDatabase): com.unshoo.pixelmusic.data.database.TelegramDao {
        return database.telegramDao()
    }

    @Provides
    @Singleton
    fun provideFolderTreeBuilder(): FolderTreeBuilder {
        return FolderTreeBuilder()
    }

    @Provides
    @Singleton
    fun provideMusicRepository(
        @ApplicationContext context: Context,
        userPreferencesRepository: UserPreferencesRepository,
        playlistPreferencesRepository: PlaylistPreferencesRepository,
        searchHistoryDao: SearchHistoryDao,
        musicDao: MusicDao,
        lyricsRepository: LyricsRepository,
        telegramDao: com.unshoo.pixelmusic.data.database.TelegramDao,
        telegramCacheManager: Lazy<com.unshoo.pixelmusic.data.telegram.TelegramCacheManager>,
        telegramRepository: Lazy<com.unshoo.pixelmusic.data.telegram.TelegramRepository>,
        songRepository: SongRepository,
        favoritesDao: FavoritesDao,
        artistImageRepository: ArtistImageRepository,
        folderTreeBuilder: FolderTreeBuilder,
        youtubeDatastoreRepository: com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository
    ): MusicRepository {
        return MusicRepositoryImpl(
            context = context,
            userPreferencesRepository = userPreferencesRepository,
            playlistPreferencesRepository = playlistPreferencesRepository,
            searchHistoryDao = searchHistoryDao,
            musicDao = musicDao,
            lyricsRepository = lyricsRepository,
            telegramDao = telegramDao,
            telegramCacheManagerProvider = telegramCacheManager,
            telegramRepositoryProvider = telegramRepository,
            songRepository = songRepository,
            favoritesDao = favoritesDao,
            artistImageRepository = artistImageRepository,
            folderTreeBuilder = folderTreeBuilder,
            youtubeDatastoreRepository = youtubeDatastoreRepository
        )
    }

    @Provides
    @Singleton
    fun provideTransitionRepository(
        transitionRepositoryImpl: TransitionRepositoryImpl
    ): TransitionRepository {
        return transitionRepositoryImpl
    }

    @Singleton
    @Provides
    fun provideSongMetadataEditor(
        @ApplicationContext context: Context,
        musicDao: MusicDao,
        telegramDao: com.unshoo.pixelmusic.data.database.TelegramDao,
        userPreferencesRepository: UserPreferencesRepository
    ): SongMetadataEditor {
        return SongMetadataEditor(context, musicDao, telegramDao, userPreferencesRepository)
    }

    /**
     * Provee una instancia singleton de OkHttpClient con logging e interceptor de User-Agent.
     * Retry logic with backoff is handled in coroutine-based callers.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // HEADERS (not BODY) so we never print response bodies that may contain
            // cookies, tokens, or third-party API payloads. Headers are still useful
            // for debugging request paths and status codes.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            // Redact every header that can carry a credential or session token.
            redactHeader("Authorization")
            redactHeader("Proxy-Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            redactHeader("x-goog-api-key")
            redactHeader("X-Emby-Token")
            redactHeader("X-Emby-Authorization")
            redactHeader("X-MediaBrowser-Token")
        }
        
        // Connection pool with optimized connections for better performance
        val connectionPool = okhttp3.ConnectionPool(
            maxIdleConnections = 5,
            keepAliveDuration = 30,
            timeUnit = java.util.concurrent.TimeUnit.SECONDS
        )
        
        return OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Add User-Agent header (required by some APIs)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", "PixelMusic/1.0 (Android; Music Player)")
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Provee una instancia de OkHttpClient con timeouts para búsquedas de lyrics.
     * Includes DNS resolver, modern TLS, connection pool, and connection retry.
     */
    @Provides
    @Singleton
    @FastOkHttpClient
    fun provideFastOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS)
        
        // Connection pool to reuse connections for better performance
        val connectionPool = okhttp3.ConnectionPool(
            maxIdleConnections = 5,
            keepAliveDuration = 30,
            timeUnit = java.util.concurrent.TimeUnit.SECONDS
        )
        
        // Use Cloudflare and Google DNS to avoid potential DNS issues
        val dns = okhttp3.Dns { hostname ->
            try {
                // First try system DNS
                okhttp3.Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                // Fallback to manual resolution if system DNS fails
                java.net.InetAddress.getAllByName(hostname).toList()
            }
        }

        return OkHttpClient.Builder()
            .dns(dns)
            .connectionPool(connectionPool)
            // Use HTTP/1.1 to avoid HTTP/2 stream issues with some servers
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            // Use modern TLS connection spec
            .connectionSpecs(listOf(
                okhttp3.ConnectionSpec.MODERN_TLS,
                okhttp3.ConnectionSpec.COMPATIBLE_TLS
            ))
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            // Enable built-in retry on connection failure
            .retryOnConnectionFailure(true)
            // Add headers
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithHeaders = originalRequest.newBuilder()
                    .header("User-Agent", "PixelMusic/1.0 (Android; Music Player)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(requestWithHeaders)
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Provee una instancia singleton de Retrofit para la API de LRCLIB.
     */
    @Provides
    @Singleton
    fun provideRetrofit(@FastOkHttpClient okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provee una instancia singleton del servicio de la API de LRCLIB.
     */
    @Provides
    @Singleton
    fun provideLrcLibApiService(retrofit: Retrofit): LrcLibApiService {
        return retrofit.create(LrcLibApiService::class.java)
    }

    /**
     * Provee una instancia de Retrofit para la API de Deezer.
     */
    @Provides
    @Singleton
    @DeezerRetrofit
    fun provideDeezerRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provee el servicio de la API de Deezer.
     */
    @Provides
    @Singleton
    fun provideDeezerApiService(@DeezerRetrofit retrofit: Retrofit): DeezerApiService {
        return retrofit.create(DeezerApiService::class.java)
    }

    /**
     * Provee el repositorio de imágenes de artistas.
     */
    @Provides
    @Singleton
    fun provideArtistImageRepository(
        deezerApiService: DeezerApiService,
        musicDao: MusicDao
    ): ArtistImageRepository {
        return ArtistImageRepository(deezerApiService, musicDao)
    }
}
