package com.unshoo.pixelmusic.utils

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Snapshot of the device's current thermal and battery power states.
 */
data class ThermalSnapshot(
    val thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
    val isPowerSaveMode: Boolean = false,
    val thermalHeadroom: Float = 0f
)

/**
 * Pure, Android-free description of a [Display.Mode], so display-mode
 * selection can be unit-tested without a device.
 */
data class DisplayModeSpec(
    val modeId: Int,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val refreshRate: Float
)

/**
 * Pure policy engine that resolves optimal display refresh rates with
 * hysteresis to prevent oscillation around thermal/power thresholds.
 *
 * Why the extra guards (this is what made 120 Hz "shutter" before):
 * every change of the resolved rate is a physical display-mode switch.
 * SurfaceFlinger has to reconfigure the panel for it, which costs a
 * visible hitch / blank frame. The old policy re-resolved on every
 * thermal callback and switched instantly in both directions, so a
 * noisy predictive-headroom signal flapping around a threshold turned
 * into a mode-switch storm — the higher the refresh rate, the more
 * obvious every switch was. This policy therefore:
 *
 *  1. Requires the recovery hysteresis band to be satisfied before
 *     stepping back up (unchanged from before), AND
 *  2. Enforces a minimum dwell time ([minModeDwellMillis]) between
 *     display-mode switches. Safety downshifts (battery saver, severe
 *     thermal throttling) bypass the dwell so the device is never kept
 *     hot just to avoid a switch; everything else waits.
 *  3. Treats a NaN headroom reading as "unknown" (0f). Several OEM
 *     PowerManager implementations return NaN when no forecast is
 *     available; NaN fails every comparison, which previously wedged
 *     the policy in its stepped-down tier forever after one downshift.
 */
class AdaptiveRefreshRatePolicy(
    private val recoveryHeadroomThreshold: Float = 0.70f,
    private val downshiftHeadroomThreshold: Float = 0.85f,
    private val minModeDwellMillis: Long = 8_000L
) {
    // 0 = Default/Off, 1 = Throttled (60Hz), 2 = Moderate (90Hz), 3 = Normal/Peak
    private var currentTier: Int = 3

    /** Time (same clock as `nowMillis` args) of the last actual tier change. */
    private var lastTierChangeMillis: Long? = null

    /**
     * Resolves the target refresh rate preference.
     *
     * @param snapshot Current thermal and power conditions
     * @param supportedRates Display modes supported by the device
     * @param currentDisplayRate Active display refresh rate
     * @param highRefreshRateEnabled User preference toggle
     * @param nowMillis Current time; injectable so dwell behaviour is testable
     * @return Target rate in Hz (or 0f for system default adaptive scheduling)
     */
    fun resolveTargetRate(
        snapshot: ThermalSnapshot,
        supportedRates: List<Float>?,
        currentDisplayRate: Float,
        highRefreshRateEnabled: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): Float {
        if (!highRefreshRateEnabled) {
            currentTier = 0
            lastTierChangeMillis = null
            return 0f
        }

        // NaN-safe headroom: unknown forecast must not look like
        // "infinitely hot" (blocks recovery) or "perfectly cool".
        val headroom = snapshot.thermalHeadroom.takeIf { it.isFinite() } ?: 0f

        // 1. Battery Saver takes absolute priority: lock to 60Hz.
        //    Safety downshift — bypasses the dwell.
        if (snapshot.isPowerSaveMode) {
            moveToTier(1, nowMillis)
            return 60f
        }

        val allRates = supportedRates?.filter { it > 0f && it.isFinite() }?.distinct()?.sortedDescending()
            ?: listOf(currentDisplayRate)
        // Cap at 120Hz to prevent excessive dropped frames on 144Hz+ displays
        val peakRate = allRates.filter { it <= 120.5f }.maxOrNull()?.coerceAtMost(120f)
            ?: (allRates.firstOrNull { it > 60f }?.coerceAtMost(120f) ?: 60f)
        if (peakRate <= 60.5f) {
            moveToTier(1, nowMillis)
            return 60f
        }

        val rate90Hz = allRates.firstOrNull { it in 85f..95f }

        fun rateForTier(tier: Int): Float = when (tier) {
            2 -> rate90Hz ?: 60f
            1 -> 60f
            else -> peakRate
        }

        // 2. Severe thermal stress or shutdown (SEVERE, CRITICAL, EMERGENCY, SHUTDOWN).
        //    Safety downshift — bypasses the dwell.
        val isSevereOrHigher = when (snapshot.thermalStatus) {
            PowerManager.THERMAL_STATUS_SEVERE,
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> true
            else -> headroom >= 1.0f
        }

        if (isSevereOrHigher) {
            moveToTier(1, nowMillis)
            return 60f
        }

        // 3. Moderate thermal stress: MODERATE or predictive headroom >= 0.85
        val isModerate = snapshot.thermalStatus == PowerManager.THERMAL_STATUS_MODERATE ||
                headroom >= downshiftHeadroomThreshold

        val desiredTier = when {
            isModerate -> 2
            else -> {
                // 4. Hysteresis on Recovery:
                // When stepping back up from throttled (60Hz) or moderate (90Hz),
                // require thermal status to be NONE/LIGHT and headroom comfortably
                // below the recovery threshold.
                val isSafeForRecovery =
                    (snapshot.thermalStatus == PowerManager.THERMAL_STATUS_NONE ||
                            snapshot.thermalStatus == PowerManager.THERMAL_STATUS_LIGHT) &&
                            (headroom <= 0f || headroom < recoveryHeadroomThreshold)
                if (currentTier in 1..2 && !isSafeForRecovery) currentTier else 3
            }
        }

        if (desiredTier == currentTier) {
            return rateForTier(currentTier)
        }

        // 5. Minimum dwell between display-mode switches. A mode switch is
        //    itself a visible hitch, so non-urgent switches (stepping down
        //    for moderate thermals, or back up on recovery) must not happen
        //    more than once per dwell window — otherwise a signal hovering
        //    near a threshold produces a stutter storm at 120 Hz.
        val lastChange = lastTierChangeMillis
        if (lastChange != null && nowMillis - lastChange < minModeDwellMillis) {
            return rateForTier(currentTier)
        }

        // 6. Nominal (NONE) or Light thermal state: smooth peak rate (capped at 120Hz)
        moveToTier(desiredTier, nowMillis)
        return rateForTier(desiredTier)
    }

    private fun moveToTier(tier: Int, nowMillis: Long) {
        if (tier != currentTier) {
            currentTier = tier
            lastTierChangeMillis = nowMillis
        }
    }

    fun reset() {
        currentTier = 3
        lastTierChangeMillis = null
    }
}

/**
 * Utility for evaluating device hardware capabilities and managing
 * display refresh rates for optimal UI performance, thermal friendliness,
 * and battery conservation.
 */
object DevicePerformanceUtils {

    /**
     * Determines if the current device is considered "low-end" based on:
     * - Android's official [ActivityManager.isLowRamDevice] flag
     * - Physical RAM capacity (under ~3.8 GB)
     * - Processor core count (4 or fewer cores)
     * - Display hardware capability (maximum supported refresh rate <= 60Hz)
     *
     * @param context Application or UI context
     * @return true if device should default to conservative performance settings
     */
    fun isLowEndDevice(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager?.isLowRamDevice == true) {
                return true
            }

            val memInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memInfo)
            // Devices with less than ~3.8 GB total RAM
            if (memInfo.totalMem in 1 until 3_800_000_000L) {
                return true
            }

            // Budget quad-core or dual-core chipsets
            if (Runtime.getRuntime().availableProcessors() <= 4) {
                return true
            }

            // Display cannot exceed standard 60Hz anyway
            val maxRate = getMaxSupportedRefreshRate(context)
            if (maxRate <= 60.5f) {
                return true
            }

            false
        } catch (e: Throwable) {
            LogUtils.w(this, "Failed to evaluate device performance tier: ${e.message}")
            false
        }
    }

    /**
     * Resolves the active [Display] associated with the given window or context.
     */
    fun getTargetDisplay(context: Context?, window: Window? = null): Display? {
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    return window.context.display
                } catch (_: Throwable) {}
            }
            try {
                @Suppress("DEPRECATION")
                window.decorView.display?.let { return it }
            } catch (_: Throwable) {}
        }
        if (context != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    return context.display
                } catch (_: Throwable) {}
            }
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                @Suppress("DEPRECATION")
                return wm?.defaultDisplay
            } catch (_: Throwable) {}
        }
        return null
    }

    /**
     * Finds the maximum refresh rate supported by the device display.
     */
    fun getMaxSupportedRefreshRate(context: Context?, window: Window? = null): Float {
        return try {
            val targetDisplay = getTargetDisplay(context, window)
            val modes = targetDisplay?.supportedModes
            val candidateRate = modes?.map { it.refreshRate }?.maxOrNull()
            candidateRate ?: targetDisplay?.refreshRate ?: 60f
        } catch (e: Throwable) {
            LogUtils.w(this, "Failed to inspect display refresh rate: ${e.message}")
            60f
        }
    }

    /**
     * Picks the [Display.Mode] id to request for [targetRate].
     *
     * Pure/testable core of [applyAdaptiveRefreshRate]. Rules:
     * - `targetRate <= 0` (system default) or no modes -> 0 (no preference).
     * - Never change physical resolution: only modes with the same
     *   physical size as the currently active mode are considered.
     *   Switching resolution as a side effect of a refresh-rate toggle
     *   causes a full display reconfiguration (and on some panels a
     *   brief black frame) — a classic "120 Hz makes it stutter" cause.
     * - Among those, pick the lowest mode that still reaches the target
     *   (e.g. exactly 120 Hz for a 120 Hz target); if none reaches it,
     *   pick the fastest same-resolution mode available.
     */
    fun selectDisplayModeId(
        modes: List<DisplayModeSpec>,
        currentModeId: Int,
        targetRate: Float
    ): Int {
        if (targetRate <= 0f || modes.isEmpty()) return 0
        val current = modes.firstOrNull { it.modeId == currentModeId }
        val sameResolution = if (current != null) {
            modes.filter {
                it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight
            }
        } else {
            modes
        }
        val candidates = sameResolution.ifEmpty { modes }
        // Lowest mode that still reaches the target, else the fastest one.
        return candidates
            .filter { it.refreshRate >= targetRate - 0.5f }
            .minByOrNull { it.refreshRate }
            ?.modeId
            ?: candidates.maxByOrNull { it.refreshRate }?.modeId
            ?: 0
    }

    /**
     * Applies the target refresh rate to the window.
     *
     * Requests BOTH [WindowManager.LayoutParams.preferredDisplayModeId]
     * and [WindowManager.LayoutParams.preferredRefreshRate]. Setting only
     * the rate (the previous behaviour, with mode id left at 0) is treated
     * as a soft hint by several OEM display stacks and by SurfaceFlinger
     * when multiple modes share a resolution: the panel then either never
     * leaves its adaptive 60 Hz mode or keeps renegotiating modes while
     * the app animates — visible as shutters/stutter with the toggle on.
     * Pinning the concrete same-resolution mode makes the 90/120 Hz mode
     * actually engage and stay engaged. On single-mode LTPO panels
     * (1–120 Hz variable) the resolved mode is the current one and the
     * rate preference is what drives the panel, so this is still correct.
     *
     * Idempotent: re-applying the already-active preference is a no-op,
     * because assigning `window.attributes` unconditionally re-triggers
     * a SurfaceFlinger mode evaluation.
     */
    fun applyAdaptiveRefreshRate(window: Window, targetRate: Float) {
        try {
            val display = getTargetDisplay(null, window)
            val desiredModeId = if (targetRate > 0f && display != null) {
                val modes = display.supportedModes?.map { mode ->
                    DisplayModeSpec(
                        modeId = mode.modeId,
                        physicalWidth = mode.physicalWidth,
                        physicalHeight = mode.physicalHeight,
                        refreshRate = mode.refreshRate
                    )
                }.orEmpty()
                selectDisplayModeId(
                    modes = modes,
                    currentModeId = display.mode?.modeId ?: 0,
                    targetRate = targetRate
                )
            } else {
                0
            }
            val desiredRate = if (targetRate > 0f) targetRate else 0f

            val params = window.attributes
            if (params.preferredRefreshRate == desiredRate &&
                params.preferredDisplayModeId == desiredModeId
            ) {
                return
            }
            params.preferredDisplayModeId = desiredModeId
            params.preferredRefreshRate = desiredRate
            window.attributes = params
            LogUtils.d(
                this,
                "Window refresh preference applied: ${desiredRate}Hz (modeId=$desiredModeId)"
            )
        } catch (e: Throwable) {
            LogUtils.w(this, "Failed to apply preferred refresh rate: ${e.message}")
        }
    }

    /**
     * Backward-compatible helper method.
     */
    fun applyHighRefreshRate(window: Window, enabled: Boolean) {
        applyAdaptiveRefreshRate(window, if (enabled) 120f else 0f)
    }
}

/**
 * Lifecycle-aware manager that coordinates OS thermal and power-save signals,
 * applies policy evaluations with hysteresis, and manages window attributes cleanly.
 *
 * Anti-stutter behaviour:
 * - Thermal callbacks are coalesced ([THERMAL_COALESCE_MS]): PowerManager can
 *   deliver them in bursts, and each one used to re-run the policy (including
 *   a predictive-headroom IPC) and potentially flip the display mode.
 *   Preference changes and lifecycle events still apply immediately, so the
 *   settings toggle feels instant.
 * - Nothing is reset in `onStop`. A background/stopped window does not drive
 *   SurfaceFlinger's mode choice, so clearing the preference there bought no
 *   battery saving — it only guaranteed two extra mode switches (down on
 *   every transient stop such as a permission dialog or file picker, back
 *   up on return), each one a visible hitch. The policy's tier/dwell state
 *   is likewise preserved across stops so hysteresis keeps working.
 */
class AdaptiveRefreshRateManager(
    private val activity: ComponentActivity,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val policy: AdaptiveRefreshRatePolicy = AdaptiveRefreshRatePolicy()
) : DefaultLifecycleObserver {

    private var highRefreshRateEnabled: Boolean = false
    private var currentThermalStatus: Int = PowerManager.THERMAL_STATUS_NONE
    private var isPowerSaveMode: Boolean = false
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var powerSaveReceiver: BroadcastReceiver? = null
    private var preferenceJob: Job? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val coalescedReevaluate = Runnable { reevaluateRefreshRate() }

    /** Coalesce bursty thermal/power callbacks into one trailing evaluation. */
    private fun scheduleReevaluate() {
        mainHandler.removeCallbacks(coalescedReevaluate)
        mainHandler.postDelayed(coalescedReevaluate, THERMAL_COALESCE_MS)
    }

    private fun reevaluateRefreshRate() {
        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val headroom = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // getThermalHeadroom() is a forecast and returns NaN on
                // devices without a thermal HAL forecast; normalize it
                // here as well as in the policy so a NaN can never wedge
                // the state machine.
                powerManager?.getThermalHeadroom(THERMAL_HEADROOM_FORECAST_SECONDS)
                    ?.takeIf { it.isFinite() } ?: 0f
            } else 0f
        } catch (_: Throwable) {
            0f
        }

        val snapshot = ThermalSnapshot(
            thermalStatus = currentThermalStatus,
            isPowerSaveMode = isPowerSaveMode,
            thermalHeadroom = headroom
        )

        val targetDisplay = DevicePerformanceUtils.getTargetDisplay(activity, activity.window)
        val supportedRates = targetDisplay?.supportedModes?.map { it.refreshRate }
        val currentRate = targetDisplay?.refreshRate ?: 60f

        val targetRate = policy.resolveTargetRate(
            snapshot = snapshot,
            supportedRates = supportedRates,
            currentDisplayRate = currentRate,
            highRefreshRateEnabled = highRefreshRateEnabled
        )

        DevicePerformanceUtils.applyAdaptiveRefreshRate(
            window = activity.window,
            targetRate = targetRate
        )
    }

    override fun onStart(owner: LifecycleOwner) {
        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
        isPowerSaveMode = powerManager?.isPowerSaveMode == true
        currentThermalStatus = try {
            powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
        } catch (_: Throwable) {
            PowerManager.THERMAL_STATUS_NONE
        }

        // 1. Thermal status listener (PowerManager)
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            currentThermalStatus = status
            scheduleReevaluate()
        }
        thermalListener = listener
        try {
            powerManager?.addThermalStatusListener(activity.mainExecutor, listener)
        } catch (e: Throwable) {
            LogUtils.w(this, "Failed to register thermal status listener: ${e.message}")
        }

        // 2. Battery saver broadcast receiver
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                isPowerSaveMode = powerManager?.isPowerSaveMode == true
                scheduleReevaluate()
            }
        }
        powerSaveReceiver = receiver
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        try {
            activity.registerReceiver(receiver, filter)
        } catch (e: Throwable) {
            LogUtils.w(this, "Failed to register power save receiver: ${e.message}")
        }

        // 3. User preference reactive observation — applies immediately so
        //    toggling the setting takes effect without waiting for the
        //    thermal coalescing window.
        preferenceJob = activity.lifecycleScope.launch {
            userPreferencesRepository.highRefreshRateFlow.collect { enabled ->
                highRefreshRateEnabled = enabled
                mainHandler.removeCallbacks(coalescedReevaluate)
                reevaluateRefreshRate()
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        // The display may have changed while paused (fold/unfold, external
        // display, resolution change in system settings): re-resolve the
        // concrete mode against the current display.
        if (preferenceJob != null) {
            reevaluateRefreshRate()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        mainHandler.removeCallbacks(coalescedReevaluate)
        preferenceJob?.cancel()
        preferenceJob = null

        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
        thermalListener?.let {
            try {
                powerManager?.removeThermalStatusListener(it)
            } catch (_: Throwable) {}
            thermalListener = null
        }

        powerSaveReceiver?.let {
            try {
                activity.unregisterReceiver(it)
            } catch (_: Throwable) {}
            powerSaveReceiver = null
        }

        // Deliberately NOT resetting the window preference or the policy
        // here — see the class doc. A stopped window does not influence
        // the display mode, and keeping both avoids a 120 -> default ->
        // 120 mode-switch round trip (two visible hitches) on every
        // transient stop, and keeps the thermal hysteresis state intact.
    }

    private companion object {
        /** Trailing-edge coalescing window for thermal/power callbacks. */
        const val THERMAL_COALESCE_MS = 2_000L

        /** Forecast horizon for PowerManager.getThermalHeadroom(). */
        const val THERMAL_HEADROOM_FORECAST_SECONDS = 10
    }
}
