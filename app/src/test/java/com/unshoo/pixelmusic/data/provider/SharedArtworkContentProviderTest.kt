package com.unshoo.pixelmusic.data.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SharedArtworkContentProviderTest {

    @Test
    fun artworkReadAccess_allowsProviderProcess() {
        assertThat(
            SharedArtworkContentProvider.hasArtworkReadAccess(
                callingUid = 1001,
                providerUid = 1001,
                uriPermissionResult = android.content.pm.PackageManager.PERMISSION_DENIED,
            )
        ).isTrue()
    }

    @Test
    fun artworkReadAccess_allowsExplicitUriGrant() {
        assertThat(
            SharedArtworkContentProvider.hasArtworkReadAccess(
                callingUid = 2001,
                providerUid = 1001,
                uriPermissionResult = android.content.pm.PackageManager.PERMISSION_GRANTED,
            )
        ).isTrue()
    }

    @Test
    fun artworkReadAccess_rejectsUntrustedExternalCaller() {
        assertThat(
            SharedArtworkContentProvider.hasArtworkReadAccess(
                callingUid = 2001,
                providerUid = 1001,
                uriPermissionResult = android.content.pm.PackageManager.PERMISSION_DENIED,
            )
        ).isFalse()
    }

    @Test
    fun buildSongUri_usesDedicatedArtworkAuthority() {
        val uri = SharedArtworkContentProvider.buildSongUriString(
            packageName = "com.unshoo.pixelmusic",
            songId = 42L
        )

        assertThat(uri).isEqualTo("content://com.unshoo.pixelmusic.artwork/song/42")
    }

    @Test
    fun buildSongUri_preservesCacheBustToken() {
        val uri = SharedArtworkContentProvider.buildSongUriString(
            packageName = "com.unshoo.pixelmusic",
            songId = 42L,
            cacheBustToken = "1234"
        )

        assertThat(uri)
            .isEqualTo("content://com.unshoo.pixelmusic.artwork/song/42?t=1234")
    }

    @Test
    fun parseSongId_rejectsOtherAuthorities() {
        val songId = SharedArtworkContentProvider.parseSongId(
            uriString = "content://example.com.artwork/song/42",
            packageName = "com.unshoo.pixelmusic"
        )

        assertThat(songId).isNull()
    }

    @Test
    fun parseSongId_readsSharedArtworkSongUri() {
        val songId = SharedArtworkContentProvider.parseSongId(
            uriString = "content://com.unshoo.pixelmusic.artwork/song/42",
            packageName = "com.unshoo.pixelmusic"
        )

        assertThat(songId).isEqualTo(42L)
    }

    @Test
    fun cloudArtworkUri_roundTripsNavidromeArtwork() {
        val rawArtworkUri = "navidrome_cover://album-42"
        val sharedUri = SharedArtworkContentProvider.buildCloudUriString(
            packageName = "com.unshoo.pixelmusic",
            rawArtworkUri = rawArtworkUri,
        )

        assertThat(sharedUri).isNotNull()
        assertThat(
            SharedArtworkContentProvider.parseCloudArtworkUri(
                uriString = sharedUri!!,
                packageName = "com.unshoo.pixelmusic",
            )
        ).isEqualTo(rawArtworkUri)
    }

    @Test
    fun cloudArtworkUri_roundTripsJellyfinArtwork() {
        val rawArtworkUri = "jellyfin_cover://item-84"
        val sharedUri = SharedArtworkContentProvider.buildCloudUriString(
            packageName = "com.unshoo.pixelmusic",
            rawArtworkUri = rawArtworkUri,
        )

        assertThat(sharedUri).isNotNull()
        assertThat(
            SharedArtworkContentProvider.parseCloudArtworkUri(
                uriString = sharedUri!!,
                packageName = "com.unshoo.pixelmusic",
            )
        ).isEqualTo(rawArtworkUri)
    }

    @Test
    fun cloudArtworkUri_rejectsUnsupportedRemoteArtwork() {
        val sharedUri = SharedArtworkContentProvider.buildCloudUriString(
            packageName = "com.unshoo.pixelmusic",
            rawArtworkUri = "https://example.com/cover.jpg",
        )

        assertThat(sharedUri).isNull()
    }
}
