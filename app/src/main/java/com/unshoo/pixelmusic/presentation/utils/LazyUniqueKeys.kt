package com.unshoo.pixelmusic.presentation.utils

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable

/**
 * Keys stay identical to [keyOf] for unique entries; only genuine repeats get a
 * disambiguating suffix. Item identity (and therefore scroll/animation state) is
 * preserved for the normal case while a duplicated remote id can no longer throw
 * IllegalArgumentException out of a lazy layout.
 */
fun <T> uniqueLazyKeys(items: List<T>, keyOf: (T) -> Any?): List<Any> {
    if (items.isEmpty()) return emptyList()
    val seen = HashMap<Any, Int>(items.size)
    return items.mapIndexed { index, item ->
        val raw = keyOf(item) ?: return@mapIndexed "pm_null_key_$index"
        val seenCount = seen[raw] ?: 0
        seen[raw] = seenCount + 1
        if (seenCount == 0) raw else "$raw#$seenCount"
    }
}

fun <T> LazyListScope.itemsUnique(
    items: List<T>,
    key: (T) -> Any?,
    contentType: (T) -> Any? = { null },
    itemContent: @Composable LazyItemScope.(T) -> Unit
) {
    val keys = uniqueLazyKeys(items, key)
    items(
        count = items.size,
        key = { keys[it] },
        contentType = { contentType(items[it]) }
    ) { itemContent(items[it]) }
}

fun <T> LazyGridScope.itemsUnique(
    items: List<T>,
    key: (T) -> Any?,
    contentType: (T) -> Any? = { null },
    itemContent: @Composable LazyGridItemScope.(T) -> Unit
) {
    val keys = uniqueLazyKeys(items, key)
    items(
        count = items.size,
        key = { keys[it] },
        contentType = { contentType(items[it]) }
    ) { itemContent(items[it]) }
}

/**
 * Key for a Paging list. Indices are stable while placeholders are enabled, so
 * qualifying by index keeps identity correct and survives a paging source that
 * returns the same row on two pages (non-unique ORDER BY, concurrent writes).
 */
fun pagedKey(prefix: String, id: Any?, index: Int): Any =
    if (id == null) "${prefix}_placeholder_$index" else "${prefix}_$id"
