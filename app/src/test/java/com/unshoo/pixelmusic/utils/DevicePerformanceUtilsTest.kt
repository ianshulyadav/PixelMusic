package com.unshoo.pixelmusic.utils

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DevicePerformanceUtilsTest {

    @Test
    fun isLowEndDevice_withNullContext_returnsFalse() {
        val result = DevicePerformanceUtils.isLowEndDevice(null)
        assertFalse(result)
    }

    @Test
    fun getMaxSupportedRefreshRate_withNullContextAndWindow_returnsFallback60Hz() {
        val rate = DevicePerformanceUtils.getMaxSupportedRefreshRate(null, null)
        assertEquals(60f, rate, 0.01f)
    }

    @Test
    fun resolveTargetRate_whenDisabled_returnsZero() {
        val policy = AdaptiveRefreshRatePolicy()
        val rate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_NONE),
            supportedRates = listOf(60f, 90f, 120f),
            currentDisplayRate = 120f,
            highRefreshRateEnabled = false
        )
        assertEquals(0f, rate, 0.01f)
    }

    @Test
    fun resolveTargetRate_whenPowerSaveMode_returns60Hz() {
        val policy = AdaptiveRefreshRatePolicy()
        val rate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(isPowerSaveMode = true),
            supportedRates = listOf(60f, 90f, 120f),
            currentDisplayRate = 120f,
            highRefreshRateEnabled = true
        )
        assertEquals(60f, rate, 0.01f)
    }

    @Test
    fun resolveTargetRate_whenThermalStatusNone_returnsPeakRateCappedAt120Hz() {
        val policy = AdaptiveRefreshRatePolicy()
        val rate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_NONE),
            supportedRates = listOf(60f, 90f, 120f, 144f),
            currentDisplayRate = 60f,
            highRefreshRateEnabled = true
        )
        // 144Hz capped at 120Hz to preserve frame rendering budget and prevent frame drops
        assertEquals(120f, rate, 0.01f)
    }

    @Test
    fun resolveTargetRate_whenThermalStatusLight_returnsPeakRate() {
        val policy = AdaptiveRefreshRatePolicy()
        val rate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_LIGHT),
            supportedRates = listOf(60f, 90f, 120f),
            currentDisplayRate = 120f,
            highRefreshRateEnabled = true
        )
        // Normal operating temperature retains smooth 120Hz without stuttering
        assertEquals(120f, rate, 0.01f)
    }

    @Test
    fun resolveTargetRate_whenThermalStatusModerate_with90HzSupported_stepsDownTo90Hz() {
        val policy = AdaptiveRefreshRatePolicy()
        val rate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_MODERATE),
            supportedRates = listOf(60f, 90f, 120f, 144f),
            currentDisplayRate = 144f,
            highRefreshRateEnabled = true
        )
        assertEquals(90f, rate, 0.01f)
    }

    @Test
    fun resolveTargetRate_whenThermalStatusModerate_without90Hz_fallsBackTo60Hz() {
        val policy = AdaptiveRefreshRatePolicy()
        val rate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_MODERATE),
            supportedRates = listOf(60f, 120f),
            currentDisplayRate = 120f,
            highRefreshRateEnabled = true
        )
        assertEquals(60f, rate, 0.01f)
    }

    @Test
    fun resolveTargetRate_whenThermalStatusSevere_returns60Hz() {
        val policy = AdaptiveRefreshRatePolicy()
        val rate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_SEVERE),
            supportedRates = listOf(60f, 90f, 120f),
            currentDisplayRate = 120f,
            highRefreshRateEnabled = true
        )
        assertEquals(60f, rate, 0.01f)
    }

    @Test
    fun resolveTargetRate_whenThermalStatusCritical_returns60Hz() {
        val policy = AdaptiveRefreshRatePolicy()
        val rate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_CRITICAL),
            supportedRates = listOf(60f, 90f, 120f),
            currentDisplayRate = 120f,
            highRefreshRateEnabled = true
        )
        assertEquals(60f, rate, 0.01f)
    }

    @Test
    fun resolveTargetRate_whenThermalStatusEmergency_returns60Hz() {
        val policy = AdaptiveRefreshRatePolicy()
        val rate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_EMERGENCY),
            supportedRates = listOf(60f, 90f, 120f),
            currentDisplayRate = 120f,
            highRefreshRateEnabled = true
        )
        assertEquals(60f, rate, 0.01f)
    }

    @Test
    fun resolveTargetRate_whenThermalStatusShutdown_returns60Hz() {
        val policy = AdaptiveRefreshRatePolicy()
        val rate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_SHUTDOWN),
            supportedRates = listOf(60f, 90f, 120f),
            currentDisplayRate = 120f,
            highRefreshRateEnabled = true
        )
        assertEquals(60f, rate, 0.01f)
    }

    @Test
    fun resolveTargetRate_hysteresis_preventsOscillationAroundHeadroomThreshold() {
        val policy = AdaptiveRefreshRatePolicy(
            recoveryHeadroomThreshold = 0.70f,
            downshiftHeadroomThreshold = 0.85f
        )
        val supportedRates = listOf(60f, 90f, 120f)
        // Advance fake time past the mode-switch dwell between steps so this
        // test exercises the headroom hysteresis band, not the dwell guard.
        var now = 0L

        // 1. Initial nominal condition -> 120Hz
        val initialRate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_NONE, thermalHeadroom = 0.50f),
            supportedRates = supportedRates,
            currentDisplayRate = 120f,
            highRefreshRateEnabled = true,
            nowMillis = now
        )
        assertEquals(120f, initialRate, 0.01f)

        // 2. Predictive headroom spikes past 0.85 -> downshift to 90Hz
        now += 10_000L
        val downshiftedRate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_NONE, thermalHeadroom = 0.88f),
            supportedRates = supportedRates,
            currentDisplayRate = 120f,
            highRefreshRateEnabled = true,
            nowMillis = now
        )
        assertEquals(90f, downshiftedRate, 0.01f)

        // 3. Headroom drops slightly to 0.78f (still >= 0.70f) -> HYSTERESIS HOLDS AT 90Hz
        now += 10_000L
        val heldRate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_NONE, thermalHeadroom = 0.78f),
            supportedRates = supportedRates,
            currentDisplayRate = 90f,
            highRefreshRateEnabled = true,
            nowMillis = now
        )
        assertEquals(90f, heldRate, 0.01f)

        // 4. Headroom safely recovers below 0.70f (0.65f) -> RECOVERY TO 120Hz
        now += 10_000L
        val recoveredRate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_NONE, thermalHeadroom = 0.65f),
            supportedRates = supportedRates,
            currentDisplayRate = 90f,
            highRefreshRateEnabled = true,
            nowMillis = now
        )
        assertEquals(120f, recoveredRate, 0.01f)
    }

    @Test
    fun resolveTargetRate_dwellPreventsRapidModeSwitchFlapping() {
        // Regression test for 120Hz "shutter": a headroom signal hovering
        // around the thresholds must not flip the physical display mode
        // back and forth — every flip is a SurfaceFlinger reconfiguration
        // and a visible hitch.
        val policy = AdaptiveRefreshRatePolicy(minModeDwellMillis = 8_000L)
        val supportedRates = listOf(60f, 90f, 120f)

        // t=0: nominal -> 120Hz (no tier change yet, dwell not armed)
        assertEquals(
            120f,
            policy.resolveTargetRate(
                snapshot = ThermalSnapshot(thermalHeadroom = 0.50f),
                supportedRates = supportedRates,
                currentDisplayRate = 120f,
                highRefreshRateEnabled = true,
                nowMillis = 0L
            ),
            0.01f
        )

        // t=1s: headroom spike -> downshift to 90Hz (first change, allowed)
        assertEquals(
            90f,
            policy.resolveTargetRate(
                snapshot = ThermalSnapshot(thermalHeadroom = 0.90f),
                supportedRates = supportedRates,
                currentDisplayRate = 120f,
                highRefreshRateEnabled = true,
                nowMillis = 1_000L
            ),
            0.01f
        )

        // t=2s: headroom already recovered, but only 1s since the switch ->
        // dwell holds 90Hz instead of flapping straight back to 120Hz
        assertEquals(
            90f,
            policy.resolveTargetRate(
                snapshot = ThermalSnapshot(thermalHeadroom = 0.50f),
                supportedRates = supportedRates,
                currentDisplayRate = 90f,
                highRefreshRateEnabled = true,
                nowMillis = 2_000L
            ),
            0.01f
        )

        // t=10s: dwell elapsed and conditions are cool -> back to 120Hz
        assertEquals(
            120f,
            policy.resolveTargetRate(
                snapshot = ThermalSnapshot(thermalHeadroom = 0.50f),
                supportedRates = supportedRates,
                currentDisplayRate = 90f,
                highRefreshRateEnabled = true,
                nowMillis = 10_000L
            ),
            0.01f
        )
    }

    @Test
    fun resolveTargetRate_severeThermalBypassesDwell() {
        val policy = AdaptiveRefreshRatePolicy(minModeDwellMillis = 8_000L)
        val supportedRates = listOf(60f, 90f, 120f)

        // Downshift to 90Hz at t=0 (arms the dwell)
        assertEquals(
            90f,
            policy.resolveTargetRate(
                snapshot = ThermalSnapshot(thermalHeadroom = 0.90f),
                supportedRates = supportedRates,
                currentDisplayRate = 120f,
                highRefreshRateEnabled = true,
                nowMillis = 0L
            ),
            0.01f
        )

        // 100ms later the device hits SEVERE: safety wins over the dwell,
        // the rate must drop to 60Hz immediately.
        assertEquals(
            60f,
            policy.resolveTargetRate(
                snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_SEVERE),
                supportedRates = supportedRates,
                currentDisplayRate = 90f,
                highRefreshRateEnabled = true,
                nowMillis = 100L
            ),
            0.01f
        )
    }

    @Test
    fun resolveTargetRate_nanHeadroomDoesNotBlockRecovery() {
        // Some OEM PowerManager builds return NaN headroom. NaN fails every
        // comparison, which used to wedge the policy at 90Hz forever.
        val policy = AdaptiveRefreshRatePolicy(minModeDwellMillis = 8_000L)
        val supportedRates = listOf(60f, 90f, 120f)

        assertEquals(
            90f,
            policy.resolveTargetRate(
                snapshot = ThermalSnapshot(thermalHeadroom = 0.90f),
                supportedRates = supportedRates,
                currentDisplayRate = 120f,
                highRefreshRateEnabled = true,
                nowMillis = 0L
            ),
            0.01f
        )

        assertEquals(
            120f,
            policy.resolveTargetRate(
                snapshot = ThermalSnapshot(thermalHeadroom = Float.NaN),
                supportedRates = supportedRates,
                currentDisplayRate = 90f,
                highRefreshRateEnabled = true,
                nowMillis = 10_000L
            ),
            0.01f
        )
    }

    @Test
    fun selectDisplayModeId_picksExactSameResolutionMode() {
        val modes = listOf(
            DisplayModeSpec(modeId = 1, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 60f),
            DisplayModeSpec(modeId = 2, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 90f),
            DisplayModeSpec(modeId = 3, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 120f),
            // A higher-rate mode at a lower resolution must never be chosen:
            // switching resolution causes a full panel reconfiguration.
            DisplayModeSpec(modeId = 4, physicalWidth = 720, physicalHeight = 1600, refreshRate = 120f)
        )
        assertEquals(3, DevicePerformanceUtils.selectDisplayModeId(modes, currentModeId = 1, targetRate = 120f))
        assertEquals(2, DevicePerformanceUtils.selectDisplayModeId(modes, currentModeId = 1, targetRate = 90f))
        assertEquals(1, DevicePerformanceUtils.selectDisplayModeId(modes, currentModeId = 1, targetRate = 60f))
    }

    @Test
    fun selectDisplayModeId_fallsBackToFastestSameResolutionAndHandlesDefaults() {
        val modes = listOf(
            DisplayModeSpec(modeId = 1, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 60f),
            DisplayModeSpec(modeId = 2, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 90f)
        )
        // No 120Hz mode at this resolution: fastest available wins.
        assertEquals(2, DevicePerformanceUtils.selectDisplayModeId(modes, currentModeId = 1, targetRate = 120f))
        // System-default request and empty mode lists mean "no preference".
        assertEquals(0, DevicePerformanceUtils.selectDisplayModeId(modes, currentModeId = 1, targetRate = 0f))
        assertEquals(0, DevicePerformanceUtils.selectDisplayModeId(emptyList(), currentModeId = 1, targetRate = 120f))
    }

    @Test
    fun resolveTargetRate_togglingOffAndOnQuickly_doesNotGetTrappedInDwell() {
        val policy = AdaptiveRefreshRatePolicy(minModeDwellMillis = 8_000L)
        val supportedRates = listOf(60f, 90f, 120f)

        // User turns off at t=0
        val offRate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalHeadroom = 0.50f),
            supportedRates = supportedRates,
            currentDisplayRate = 120f,
            highRefreshRateEnabled = false,
            nowMillis = 0L
        )
        assertEquals(0f, offRate, 0.01f)

        // User immediately turns back on at t=1s: must take effect immediately
        // and not be delayed by the 8s dwell
        val onRate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalHeadroom = 0.50f),
            supportedRates = supportedRates,
            currentDisplayRate = 120f,
            highRefreshRateEnabled = true,
            nowMillis = 1_000L
        )
        assertEquals(120f, onRate, 0.01f)
    }

    @Test
    fun resolveTargetRate_enablingWhileMildlyWarm_transitionsCleanlyToPeakRate() {
        val policy = AdaptiveRefreshRatePolicy(
            recoveryHeadroomThreshold = 0.70f,
            downshiftHeadroomThreshold = 0.85f,
            minModeDwellMillis = 8_000L
        )
        val supportedRates = listOf(60f, 90f, 120f)

        // Starts disabled
        policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalHeadroom = 0.75f),
            supportedRates = supportedRates,
            currentDisplayRate = 60f,
            highRefreshRateEnabled = false,
            nowMillis = 0L
        )

        // Enabled while headroom is 0.75 (below downshift 0.85, but above recovery 0.70)
        // Must not get trapped in tier 0
        val enabledRate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalHeadroom = 0.75f),
            supportedRates = supportedRates,
            currentDisplayRate = 60f,
            highRefreshRateEnabled = true,
            nowMillis = 1_000L
        )
        assertEquals(120f, enabledRate, 0.01f)
    }
}
