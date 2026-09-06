package com.unshoo.pixelmusic.data.library

import com.unshoo.pixelmusic.data.model.Song

/**
 * Pure, side-effect-free duplicate-track detection.
 *
 * Two tracks are considered duplicates when their normalized title and artist match
 * AND their durations fall within [DEFAULT_DURATION_TOLERANCE_MS] of each other — so a
 * studio cut and a much longer live version of the same song are kept apart, while the
 * same track imported twice (or in two formats) is grouped.
 */
object DuplicateFinder {

    const val DEFAULT_DURATION_TOLERANCE_MS = 3_000L

    data class DuplicateGroup(
        val title: String,
        val artist: String,
        val songs: List<Song>,
    )

    private val whitespace = Regex("\\s+")

    private fun normalize(value: String): String =
        value.trim().lowercase().replace(whitespace, " ")

    fun findDuplicates(
        songs: List<Song>,
        durationToleranceMs: Long = DEFAULT_DURATION_TOLERANCE_MS,
    ): List<DuplicateGroup> {
        val result = mutableListOf<DuplicateGroup>()

        songs
            .groupBy { normalize(it.title) to normalize(it.artist) }
            .forEach { (_, sameNamed) ->
                if (sameNamed.size < 2) return@forEach

                val sorted = sameNamed.sortedBy { it.duration }
                var cluster = mutableListOf(sorted.first())
                for (i in 1 until sorted.size) {
                    val song = sorted[i]
                    if (song.duration - cluster.first().duration <= durationToleranceMs) {
                        cluster.add(song)
                    } else {
                        result.addClusterIfDuplicate(cluster)
                        cluster = mutableListOf(song)
                    }
                }
                result.addClusterIfDuplicate(cluster)
            }

        return result.sortedWith(
            compareByDescending<DuplicateGroup> { it.songs.size }
                .thenBy { it.title.lowercase() }
        )
    }

    private fun MutableList<DuplicateGroup>.addClusterIfDuplicate(cluster: List<Song>) {
        if (cluster.size > 1) {
            val head = cluster.first()
            add(DuplicateGroup(title = head.title, artist = head.artist, songs = cluster.toList()))
        }
    }
}
