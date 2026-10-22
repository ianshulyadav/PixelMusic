package com.unshoo.pixelmusic.utils.perf

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import com.unshoo.pixelmusic.BuildConfig
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Centralized, zero-overhead-in-release performance instrumentation and tracing utility.
 * Emits structured tags for logcat parsing and native Android Trace sections for Perfetto.
 */
object PerfTracker {

    const val TAG_STARTUP = "PM_PERF_STARTUP"
    const val TAG_NETWORK = "PM_PERF_NETWORK"
    const val TAG_DB = "PM_PERF_DB"
    const val TAG_CPU = "PM_PERF_CPU"
    const val TAG_COMPOSE = "PM_PERF_COMPOSE"
    const val TAG_IMAGE = "PM_PERF_IMAGE"
    const val TAG_MEMORY = "PM_PERF_MEMORY"
    const val TAG_PLAYER = "PM_PERF_PLAYER"
    const val TAG_CACHE = "PM_PERF_CACHE"
    const val TAG_ERROR = "PM_PERF_ERROR"

    val isDebug = BuildConfig.DEBUG
    private val nextRequestId = AtomicLong(1L)
    private val activeNetworkCount = AtomicInteger(0)

    @Volatile
    var appStartTimeMs: Long = 0L
        private set

    @Volatile
    var firstFrameTimeMs: Long = 0L

    @Volatile
    var firstMeaningfulUiTimeMs: Long = 0L

    @Volatile
    var homeClickableTimeMs: Long = 0L

    @Volatile
    var firstAlbumArtTimeMs: Long = 0L

    @Volatile
    var firstYouTubeDataTimeMs: Long = 0L

    @Volatile
    var quickPicksAppearedTimeMs: Long = 0L

    @Volatile
    var startupNetworkSettledTimeMs: Long = 0L

    fun recordAppStart() {
        if (appStartTimeMs == 0L) {
            appStartTimeMs = SystemClock.elapsedRealtime()
            if (isDebug) {
                Log.i(TAG_STARTUP, "[STARTUP_BEGIN] Timestamp: ${System.currentTimeMillis()}")
            }
        }
    }

    inline fun <T> traceSection(sectionName: String, block: () -> T): T {
        if (!isDebug) return block()
        val truncatedName = if (sectionName.length > 120) sectionName.take(120) else sectionName
        Trace.beginSection(truncatedName)
        val start = SystemClock.elapsedRealtime()
        return try {
            block()
        } finally {
            val duration = SystemClock.elapsedRealtime() - start
            Trace.endSection()
            if (duration >= 16) {
                Log.w(TAG_CPU, "[SLOW_SECTION] $sectionName took ${duration}ms")
            }
        }
    }

    fun markStartupPhase(phase: String, detail: String = "") {
        if (!isDebug) return
        val now = SystemClock.elapsedRealtime()
        val elapsed = if (appStartTimeMs > 0L) now - appStartTimeMs else 0L
        Log.i(TAG_STARTUP, "[PHASE] $phase | elapsed: ${elapsed}ms | $detail")
    }

    fun markFirstFrame() {
        if (firstFrameTimeMs == 0L && appStartTimeMs > 0L) {
            firstFrameTimeMs = SystemClock.elapsedRealtime()
            val elapsed = firstFrameTimeMs - appStartTimeMs
            Log.i(TAG_STARTUP, "[METRIC] Time to first frame: ${elapsed}ms")
        }
    }

    fun markFirstMeaningfulUi() {
        if (firstMeaningfulUiTimeMs == 0L && appStartTimeMs > 0L) {
            firstMeaningfulUiTimeMs = SystemClock.elapsedRealtime()
            val elapsed = firstMeaningfulUiTimeMs - appStartTimeMs
            Log.i(TAG_STARTUP, "[METRIC] Time to first meaningful UI: ${elapsed}ms")
        }
    }

    fun markHomeClickable() {
        if (homeClickableTimeMs == 0L && appStartTimeMs > 0L) {
            homeClickableTimeMs = SystemClock.elapsedRealtime()
            val elapsed = homeClickableTimeMs - appStartTimeMs
            Log.i(TAG_STARTUP, "[METRIC] Time until Home becomes clickable: ${elapsed}ms")
        }
    }

    fun markFirstAlbumArt() {
        if (firstAlbumArtTimeMs == 0L && appStartTimeMs > 0L) {
            firstAlbumArtTimeMs = SystemClock.elapsedRealtime()
            val elapsed = firstAlbumArtTimeMs - appStartTimeMs
            Log.i(TAG_STARTUP, "[METRIC] Time until first album art appears: ${elapsed}ms")
        }
    }

    fun markFirstYouTubeData() {
        if (firstYouTubeDataTimeMs == 0L && appStartTimeMs > 0L) {
            firstYouTubeDataTimeMs = SystemClock.elapsedRealtime()
            val elapsed = firstYouTubeDataTimeMs - appStartTimeMs
            Log.i(TAG_STARTUP, "[METRIC] Time until first YouTube data appears: ${elapsed}ms")
        }
    }

    fun markQuickPicksAppeared() {
        if (quickPicksAppearedTimeMs == 0L && appStartTimeMs > 0L) {
            quickPicksAppearedTimeMs = SystemClock.elapsedRealtime()
            val elapsed = quickPicksAppearedTimeMs - appStartTimeMs
            Log.i(TAG_STARTUP, "[METRIC] Time until Quick Picks appears: ${elapsed}ms")
        }
    }

    fun markStartupNetworkSettled() {
        if (startupNetworkSettledTimeMs == 0L && appStartTimeMs > 0L) {
            startupNetworkSettledTimeMs = SystemClock.elapsedRealtime()
            val elapsed = startupNetworkSettledTimeMs - appStartTimeMs
            Log.i(TAG_STARTUP, "[METRIC] Time until startup network activity settles: ${elapsed}ms")
            Log.i(TAG_STARTUP, "[METRIC] Total startup duration: ${elapsed}ms")
        }
    }

    fun onNetworkRequestStart(url: String, method: String): Long {
        if (!isDebug) return 0L
        val reqId = nextRequestId.getAndIncrement()
        val concurrent = activeNetworkCount.incrementAndGet()
        val cleanEndpoint = sanitizeEndpoint(url)
        Log.i(TAG_NETWORK, "[REQ_START #$reqId] $method $cleanEndpoint | activeConcurrent=$concurrent")
        return reqId
    }

    fun onNetworkRequestEnd(
        reqId: Long,
        url: String,
        method: String,
        durationMs: Long,
        statusCode: Int,
        responseBytes: Long,
        isSuccess: Boolean,
        cacheHit: Boolean = false,
        errorMsg: String? = null
    ) {
        if (!isDebug) return
        val concurrent = activeNetworkCount.decrementAndGet().coerceAtLeast(0)
        val cleanEndpoint = sanitizeEndpoint(url)
        val statusText = if (isSuccess) "SUCCESS($statusCode)" else "FAIL($statusCode): ${errorMsg.orEmpty()}"
        val cacheText = if (cacheHit) "CACHE_HIT" else "CACHE_MISS"
        Log.i(
            TAG_NETWORK,
            "[REQ_END #$reqId] $method $cleanEndpoint | duration=${durationMs}ms | status=$statusText | size=${responseBytes}B | $cacheText | activeConcurrent=$concurrent"
        )
    }

    fun logDbQuery(queryName: String, durationMs: Long, rowCount: Int = -1) {
        if (!isDebug) return
        Log.i(TAG_DB, "[QUERY] $queryName | duration=${durationMs}ms | rows=$rowCount")
    }

    fun logComposeRecomposition(composableName: String, reason: String = "") {
        if (!isDebug) return
        Log.d(TAG_COMPOSE, "[RECOMPOSE] $composableName ${if (reason.isNotBlank()) "| $reason" else ""}")
    }

    private fun sanitizeEndpoint(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val path = uri.path.orEmpty()
            val browseId = uri.getQueryParameter("browseId") ?: uri.getQueryParameter("id")
            if (!browseId.isNullOrBlank()) "$path?id=$browseId" else path.ifBlank { url.take(60) }
        } catch (_: Exception) {
            url.take(60)
        }
    }
}
