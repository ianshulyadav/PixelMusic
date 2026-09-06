package com.unshoo.pixelmusic.data.network.lyrics

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for interacting with the LRCLIB API.
 */
interface LrcLibApiService {

    /**
     * Searches for a song's lyrics using its metadata.
     * @param trackName The name of the song.
     * @param artistName The name of the artist.
     * @param albumName The name of the album.
     * @param duration The duration of the song in seconds.
     * @return An LrcLibResponse instance if found, or null.
     */
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String,
        @Query("duration") duration: Int
    ): LrcLibResponse?

    /**
     * Search for lyrics using flexible query parameters.
     * At least one of q or trackName should be provided.
     * @param query General search query (can include title, artist, or lyrics fragment).
     * @param trackName The name of the track.
     * @param artistName The name of the artist (optional filter).
     * @param albumName The name of the album (optional filter).
     * @return An array of LrcLibResponse objects.
     */
    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String? = null,
        @Query("track_name") trackName: String? = null,
        @Query("artist_name") artistName: String? = null,
        @Query("album_name") albumName: String? = null
    ): Array<LrcLibResponse>?
}