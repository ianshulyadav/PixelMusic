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
    fun resolveTargetRate_whenThermalStatusNone_returnsPeakRate() {
        val policy = AdaptiveRefreshRatePolicy()
        val rate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_NONE),
            supportedRates = listOf(60f, 90f, 120f, 144f),
            currentDisplayRate = 60f,
            highRefreshRateEnabled = true
        )
        assertEquals(144f, rate, 0.01f)
    }

    @Test
    fun resolveTargetRate_whenThermalStatusLight_returnsZeroForSystemAdaptive() {
        val policy = AdaptiveRefreshRatePolicy()
        val rate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_LIGHT),
            supportedRates = listOf(60f, 90f, 120f),
            currentDisplayRate = 120f,
            highRefreshRateEnabled = true
        )
        // System-adaptive behavior: 0f allows OS LTPO scheduler to balance power
        assertEquals(0f, rate, 0.01f)
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

        // 1. Initial nominal condition -> 120Hz
        val initialRate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_NONE, thermalHeadroom = 0.50f),
            supportedRates = supportedRates,
            currentDisplayRate = 120f,
            highRefreshRateEnabled = true
        )
        assertEquals(120f, initialRate, 0.01f)

        // 2. Predictive headroom spikes past 0.85 -> downshift to 90Hz
        val downshiftedRate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_NONE, thermalHeadroom = 0.88f),
            supportedRates = supportedRates,
            currentDisplayRate = 120f,
            highRefreshRateEnabled = true
        )
        assertEquals(90f, downshiftedRate, 0.01f)

        // 3. Headroom drops slightly to 0.78f (still >= 0.70f) -> HYSTERESIS HOLDS AT 90Hz
        val heldRate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_NONE, thermalHeadroom = 0.78f),
            supportedRates = supportedRates,
            currentDisplayRate = 90f,
            highRefreshRateEnabled = true
        )
        assertEquals(90f, heldRate, 0.01f)

        // 4. Headroom safely recovers below 0.70f (0.65f) -> RECOVERY TO 120Hz
        val recoveredRate = policy.resolveTargetRate(
            snapshot = ThermalSnapshot(thermalStatus = PowerManager.THERMAL_STATUS_NONE, thermalHeadroom = 0.65f),
            supportedRates = supportedRates,
            currentDisplayRate = 90f,
            highRefreshRateEnabled = true
        )
        assertEquals(120f, recoveredRate, 0.01f)
    }
}
