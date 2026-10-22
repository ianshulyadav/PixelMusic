package com.unshoo.pixelmusic.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Clase de prueba para medir el rendimiento del inicio de la aplicación.
 * Compara el inicio sin optimizaciones ([CompilationMode.None]) frente al
 * inicio optimizado con Baseline Profiles ([CompilationMode.Partial]).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCompilationNone() = startup(CompilationMode.None())

    @Test
    fun coldStartThenRootNavigationFrameTiming() {
        val packageName = benchmarkTargetPackageName()
        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.UseIfAvailable
            ),
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = {
                setupBenchmarkPermissions(packageName)
                pressHome()
            }
        ) {
            startActivityAndWait { intent -> intent.putExtra(BENCHMARK_EXTRA, true) }
            waitForAppForeground("Cold start root navigation benchmark", packageName)
            clickTabIfFound(packageName, "Explore")
            clickTabIfFound(packageName, "Library")
            clickTabIfFound(packageName, "Home")
        }
    }

    @Test
    fun startupCompilationBaselineProfiles() = startup(
        // Usamos UseIfAvailable en lugar de Require para evitar errores de
        // asunción fallida en dispositivos Samsung/OneUI.
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.UseIfAvailable
        )
    )

    private fun startup(compilationMode: CompilationMode) {
        val packageName = benchmarkTargetPackageName()

        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = 7,
            startupMode = StartupMode.COLD,
            setupBlock = {
                setupBenchmarkPermissions(packageName)
                pressHome()
            }
        ) {
            startActivityAndWait { intent ->
                intent.putExtra(BENCHMARK_EXTRA, true)
            }
            waitForAppForeground("Startup benchmark measure block", packageName)
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.clickTabIfFound(
        packageName: String,
        label: String
    ) {
        val selector = androidx.test.uiautomator.By.pkg(packageName)
        val candidate = device.findObjects(selector).firstOrNull { item ->
            val text = item.text.orEmpty()
            val description = item.contentDescription.orEmpty()
            text.equals(label, ignoreCase = true) || description.equals(label, ignoreCase = true)
        }
        candidate?.let {
            val center = it.visibleCenter
            device.click(center.x, center.y)
            device.waitForIdle(1_000L)
        }
    }
}
