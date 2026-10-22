package com.unshoo.pixelmusic.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/**
 * App-wide readiness signal that decouples "UI is visible and interactive"
 * from ViewModels that want to defer network work until after first frame.
 *
 * **Why this instead of a hard delay:**
 * A fixed timer (e.g. delay(1_500L)) is arbitrary — it may fire too early on
 * low-end devices and wastes time on high-end ones. This signal fires exactly
 * when [MainActivity] marks the UI as ready (after contentVisible = true and
 * the fade-in has begun), ensuring deferred work aligns with actual UI readiness
 * regardless of device speed.
 *
 * **Usage:**
 * - `AppReadinessSignal.markReady()` — called by MainActivity once content is visible.
 * - `AppReadinessSignal.awaitReady()` — suspend until ready; returns immediately if
 *   already ready (safe to call multiple times).
 */
object AppReadinessSignal {

    private val _isReady = MutableStateFlow(false)

    /** Emits true once [markReady] has been called. Never resets. */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isInitialFetchFinished = MutableStateFlow(false)

    /** Emits true once the initial fresh-start network fetch has completed or timed out. */
    val isInitialFetchFinished: StateFlow<Boolean> = _isInitialFetchFinished.asStateFlow()

    /**
     * Mark the app UI as ready. Should be called once from [MainActivity]
     * after the content fade-in animation has been triggered.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    fun markReady() {
        _isReady.compareAndSet(expect = false, update = true)
    }

    /**
     * Suspends until the app UI is ready, then returns.
     * If already ready, returns immediately without suspending.
     */
    suspend fun awaitReady() {
        isReady.filter { it }.first()
    }

    /**
     * Mark the initial fresh-start network fetch as complete.
     */
    fun markInitialFetchFinished() {
        _isInitialFetchFinished.compareAndSet(expect = false, update = true)
    }

    /**
     * Suspends until the initial network fetch is finished, then returns.
     */
    suspend fun awaitInitialFetchFinished() {
        isInitialFetchFinished.filter { it }.first()
    }
}
