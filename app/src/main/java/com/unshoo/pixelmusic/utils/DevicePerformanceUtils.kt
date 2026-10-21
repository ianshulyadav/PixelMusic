package com.unshoo.pixelmusic.utils

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
 * Pure policy engine that resolves optimal display refresh rates with hysteresis
 * to prevent oscillation around thermal/power thresholds.
 */
class AdaptiveRefreshRatePolicy(
    private val recoveryHeadroomThreshold: Float = 0.70f,
    private val downshiftHeadroomThreshold: Float = 0.85f
) {
    // 0 = Default/Off, 1 = Throttled (60Hz), 2 = Moderate (90Hz), 3 = Normal/Peak
    private var currentTier: Int = 3

    /**
     * Resolves the target refresh rate preference.
     *
     * @param snapshot Current thermal and power conditions
     * @param supportedRates Display modes supported by the device
     * @param currentDisplayRate Active display refresh rate
     * @param highRefreshRateEnabled User preference toggle
     * @return Target rate in Hz (or 0f for system default adaptive scheduling)
     */
    fun resolveTargetRate(
        snapshot: ThermalSnapshot,
        supportedRates: List<Float>?,
        currentDisplayRate: Float,
        highRefreshRateEnabled: Boolean
    ): Float {
        if (!highRefreshRateEnabled) {
            currentTier = 0
            return 0f
        }

        // 1. Battery Saver takes absolute priority: lock to 60Hz
        if (snapshot.isPowerSaveMode) {
            currentTier = 1
            return 60f
        }

        val allRates = supportedRates?.filter { it > 0f }?.distinct()?.sortedDescending()
            ?: listOf(currentDisplayRate)
        val peakRate = allRates.maxOrNull() ?: currentDisplayRate
        if (peakRate <= 60.5f) {
            currentTier = 1
            return 60f
        }

        val rate90Hz = allRates.firstOrNull { it in 85f..95f }

        // 2. Severe thermal stress or shutdown (SEVERE, CRITICAL, EMERGENCY, SHUTDOWN)
        val isSevereOrHigher = when (snapshot.thermalStatus) {
            PowerManager.THERMAL_STATUS_SEVERE,
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> true
            else -> snapshot.thermalHeadroom >= 1.0f
        }

        if (isSevereOrHigher) {
            currentTier = 1
            return 60f
        }

        // 3. Moderate thermal stress: MODERATE or predictive headroom >= 0.85
        val isModerate = snapshot.thermalStatus == PowerManager.THERMAL_STATUS_MODERATE ||
                snapshot.thermalHeadroom >= downshiftHeadroomThreshold

        if (isModerate) {
            currentTier = 2
            return rate90Hz ?: 60f
        }

        // 4. Hysteresis on Recovery:
        // When stepping back up from throttled (60Hz) or moderate (90Hz),
        // require thermal status to be NONE/LIGHT and headroom comfortably below recovery threshold.
        val isSafeForRecovery = (snapshot.thermalStatus == PowerManager.THERMAL_STATUS_NONE ||
                snapshot.thermalStatus == PowerManager.THERMAL_STATUS_LIGHT) &&
                (snapshot.thermalHeadroom <= 0f || snapshot.thermalHeadroom < recoveryHeadroomThreshold)

        if (currentTier < 3 && !isSafeForRecovery) {
            // Retain stepped-down tier to prevent oscillation
            return when (currentTier) {
                2 -> rate90Hz ?: 60f
                else -> 60f
            }
        }

        // 5. Light thermal state:
        // Use system-adaptive behavior (0f) to preserve battery and allow the OS scheduler to manage rate
        if (snapshot.thermalStatus == PowerManager.THERMAL_STATUS_LIGHT) {
            currentTier = 3
            return 0f
        }

        // 6. Nominal (NONE): Full peak rate preference
        currentTier = 3
        return peakRate
    }

    fun reset() {
        currentTier = 3
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
     * Applies the target refresh rate to the window using standard WindowManager APIs.
     * Per Android official guidance: prefers preferredRefreshRate and leaves
     * preferredDisplayModeId as 0 so the system display manager selects the optimal mode
     * without altering physical resolution.
     */
    fun applyAdaptiveRefreshRate(window: Window, targetRate: Float) {
        try {
            val params = window.attributes
            params.preferredDisplayModeId = 0
            params.preferredRefreshRate = targetRate
            window.attributes = params
            LogUtils.d(this, "Window preferredRefreshRate applied: ${targetRate}Hz")
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

    private fun reevaluateRefreshRate() {
        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val headroom = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                powerManager?.getThermalHeadroom(10) ?: 0f
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
            reevaluateRefreshRate()
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
                reevaluateRefreshRate()
            }
        }
        powerSaveReceiver = receiver
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        try {
            activity.registerReceiver(receiver, filter)
        } catch (e: Throwable) {
            LogUtils.w(this, "Failed to register power save receiver: ${e.message}")
        }

        // 3. User preference reactive observation
        preferenceJob = activity.lifecycleScope.launch {
            userPreferencesRepository.highRefreshRateFlow.collect { enabled ->
                highRefreshRateEnabled = enabled
                reevaluateRefreshRate()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
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

        policy.reset()

        // Reset to system default when in background to preserve battery
        DevicePerformanceUtils.applyAdaptiveRefreshRate(
            window = activity.window,
            targetRate = 0f
        )
    }
}
