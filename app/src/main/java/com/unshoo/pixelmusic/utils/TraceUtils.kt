package com.unshoo.pixelmusic.utils

import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger

private val traceCookieGenerator = AtomicInteger()

/**
 * Runs [block] inside a synchronous trace section, guaranteeing the section is closed even
 * when [block] throws or returns non-locally. Only for blocks that cannot suspend — use
 * [traceAsyncSection] in suspend functions.
 */
inline fun <T> traceSection(label: String, block: () -> T): T {
    Trace.beginSection(label)
    try {
        return block()
    } finally {
        Trace.endSection()
    }
}

/**
 * Runs [block] inside an async trace section.
 *
 * `Trace.beginSection`/`endSection` are thread-local, so a section held open across a
 * suspension point can resume on a different thread and unbalance the caller's trace
 * stack. Async sections are matched by (label, cookie) instead and survive thread hops,
 * making them the only safe form for suspend functions.
 */
suspend fun <T> traceAsyncSection(label: String, block: suspend () -> T): T {
    val cookie = traceCookieGenerator.incrementAndGet()
    Trace.beginAsyncSection(label, cookie)
    try {
        return block()
    } finally {
        Trace.endAsyncSection(label, cookie)
    }
}
