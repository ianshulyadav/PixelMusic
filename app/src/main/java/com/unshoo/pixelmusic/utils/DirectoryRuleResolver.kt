package com.unshoo.pixelmusic.utils

/**
 * Resolves directory allow/deny rules using the nearest ancestor match strategy.
 * A more specific rule (longer path) always overrides a parent rule. Allowed rules
 * take precedence over blocked rules at the same depth to enable explicit overrides
 * inside excluded trees.
 */
class DirectoryRuleResolver(
    allowed: Set<String>,
    blocked: Set<String>
) {
    // Normalize paths: remove trailing slashes to ensure consistent matching
    // We assume paths are absolute and start with /
    private val allowedRoots = allowed.mapNotNull { normalize(it) }.toSet()
    private val blockedRoots = blocked.mapNotNull { normalize(it) }.toSet()

    private val hasRules = allowedRoots.isNotEmpty() || blockedRoots.isNotEmpty()

    fun isBlocked(path: String): Boolean {
        // Logic: Find the most specific (longest) rule that matches the path.
        // If an explicit allow rule is equal to or deeper than a block rule, it's allowed.
        // If an explicit block rule is deeper, it's blocked.
        // If no explicit rule matches, recording directories are blocked by default, others allowed.

        var deepestBlockLen = -1
        var deepestAllowLen = -1

        for (root in blockedRoots) {
            if (isParentOrSame(root, path)) {
                if (root.length > deepestBlockLen) {
                    deepestBlockLen = root.length
                }
            }
        }

        for (root in allowedRoots) {
            if (isParentOrSame(root, path)) {
                if (root.length > deepestAllowLen) {
                    deepestAllowLen = root.length
                }
            }
        }

        if (deepestAllowLen >= 0 && deepestAllowLen >= deepestBlockLen) {
            return false
        }

        if (deepestBlockLen >= 0) {
            return true
        }

        // Default exclusion: exclude recordings and android directories out-of-the-box
        return isRecordingDirectory(path) || isAndroidDirectory(path)
    }

    private fun normalize(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val trimmed = path.trim().replace('\\', '/')
        return if (trimmed.endsWith("/")) trimmed.dropLast(1) else trimmed
    }

    private fun isParentOrSame(root: String, path: String): Boolean {
        val normPath = path.replace('\\', '/')
        if (!normPath.startsWith(root, ignoreCase = true)) return false
        if (normPath.length == root.length) return true
        return normPath[root.length] == '/'
    }

    companion object {
        private val RECORDING_DIR_NAMES = setOf(
            "recordings",
            "recording",
            "voice recorder",
            "voicerecorder",
            "sound_recorder",
            "sound recorder",
            "call_recordings",
            "call recordings",
            "callrecordings"
        )

        fun isRecordingDirectory(path: String): Boolean {
            val normalized = path.replace('\\', '/').trimEnd('/')
            val segments = normalized.split('/')
            return segments.any { it.lowercase() in RECORDING_DIR_NAMES }
        }

        fun isAndroidDirectory(path: String): Boolean {
            val normalized = path.replace('\\', '/').trimEnd('/')
            val segments = normalized.split('/')
            return segments.any { it.equals("Android", ignoreCase = true) }
        }
    }
}
