package com.unshoo.pixelmusic.data.navidrome.model

/**
 * How credentials are sent to a Subsonic-compatible server.
 *
 * Token auth is the modern default, but some servers cannot support it because they
 * never store a recoverable copy of the password (Nextcloud/ownCloud Music rejects
 * `t`/`s` outright with Subsonic error 41). Those servers accept only the older
 * password parameter, so we fall back to it when the server says token auth is
 * unsupported.
 */
enum class NavidromeAuthMethod {
    /** Salted MD5 token: `t=md5(password + salt)&s=salt`. */
    TOKEN,

    /** Hex-encoded password: `p=enc:<hex>`. */
    PASSWORD;

    companion object {
        /** Reads a persisted value, defaulting to [TOKEN] for unknown or missing input. */
        fun fromStorageKey(value: String?): NavidromeAuthMethod =
            entries.firstOrNull { it.name == value } ?: TOKEN
    }
}
