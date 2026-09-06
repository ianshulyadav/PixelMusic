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
    private val allowedRoots = allowed.mapNotNull { normalize(it) }.toSet()
    private val blockedRoots = blocked.mapNotNull { normalize(it) }.toSet()

    private val hasRules = allowedRoots.isNotEmpty() || blockedRoots.isNotEmpty()

    fun isBlocked(path: String): Boolean {
        if (!hasRules) return false
        
        var deepestBlockLen = -1
        var deepestAllowLen = -1

        for (root in blockedRoots) {
            if (isParentOrSame(root, path)) {
                if (root.length > deepestBlockLen) {
                    deepestBlockLen = root.length
                }
            }
        }

        if (deepestBlockLen == -1) return false

        for (root in allowedRoots) {
            if (isParentOrSame(root, path)) {
                if (root.length > deepestAllowLen) {
                    deepestAllowLen = root.length
                }
            }
        }

        return deepestBlockLen > deepestAllowLen
    }

    private fun normalize(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return if (path.endsWith("/")) path.dropLast(1) else path
    }

    private fun isParentOrSame(root: String, path: String): Boolean {
        if (!path.startsWith(root, ignoreCase = true)) return false
        if (path.length == root.length) return true
        return path[root.length] == '/'
    }
}
