package com.nexradwx.core.render

import com.nexradwx.core.model.MomentCode
import com.nexradwx.core.model.MomentData
import com.nexradwx.core.model.Radial
import com.nexradwx.core.model.Sweep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PpiRasterizerTest {

    private fun radial(azimuth: Float, gateValue: Float): Radial {
        val moment = MomentData(
            code = "REF",
            gateCount = 10,
            firstGateMeters = 0,
            gateSpacingMeters = 1000, // 1 km per gate, 10 gates -> 10 km max range
            values = FloatArray(10) { gateValue },
        )
        return Radial(
            azimuthDegrees = azimuth,
            elevationDegrees = 0.5f,
            azimuthNumber = 0,
            elevationNumber = 1,
            radialStatusFlags = 0,
            timestampMillis = 0L,
            moments = mapOf("REF" to moment),
        )
    }

    private fun uniformSweep(value: Float): Sweep {
        // One radial every degree, all reporting the same value everywhere.
        val radials = (0 until 360).map { radial(it.toFloat(), value) }
        return Sweep(elevationNumber = 1, elevationDegrees = 0.5f, radials = radials)
    }

    @Test
    fun `pixels beyond max range are transparent`() {
        val sweep = uniformSweep(30f)
        val pixels = PpiRasterizer.rasterize(sweep, MomentCode.REFLECTIVITY, 100, 100, 10f) { 0xFFFF0000.toInt() }
        // Corner pixels are always outside the inscribed circle of radius maxRange.
        assertEquals(0, pixels[0])
    }

    @Test
    fun `pixel at radar center gets a color from the nearest radial's innermost gate`() {
        val sweep = uniformSweep(30f)
        val pixels = PpiRasterizer.rasterize(sweep, MomentCode.REFLECTIVITY, 101, 101, 10f) { v ->
            if (v == 30f) 0xFFFF0000.toInt() else 0x00000000
        }
        val centerIdx = 50 * 101 + 50
        assertEquals(0xFFFF0000.toInt(), pixels[centerIdx])
    }

    @Test
    fun `distinct azimuth halves render distinct colors`() {
        // North half of the sweep reports 10, south half reports 50.
        val radials = (0 until 360).map { az ->
            radial(az.toFloat(), if (az in 271..359 || az in 0..89) 10f else 50f)
        }
        val sweep = Sweep(1, 0.5f, radials)
        val colorFor: (Float) -> Int = { v -> if (v == 10f) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }

        val pixels = PpiRasterizer.rasterize(sweep, MomentCode.REFLECTIVITY, 200, 200, 10f, colorFor)
        val north = pixels[10 * 200 + 100] // near top-center: north
        val south = pixels[190 * 200 + 100] // near bottom-center: south
        assertEquals(0xFFFFFFFF.toInt(), north)
        assertEquals(0xFF000000.toInt(), south)
    }

    @Test
    fun `missing moment on a radial leaves those pixels transparent`() {
        val radials = (0 until 360).map {
            Radial(
                azimuthDegrees = it.toFloat(),
                elevationDegrees = 0.5f,
                azimuthNumber = 0,
                elevationNumber = 1,
                radialStatusFlags = 0,
                timestampMillis = 0L,
                moments = emptyMap(),
            )
        }
        val sweep = Sweep(1, 0.5f, radials)
        val pixels = PpiRasterizer.rasterize(sweep, MomentCode.REFLECTIVITY, 50, 50, 10f) { 0xFFFF0000.toInt() }
        assertTrue(pixels.all { it == 0 })
    }

    @Test
    fun `nearestAzimuthIndex wraps around the 0-360 boundary`() {
        val azimuths = floatArrayOf(1f, 90f, 180f, 270f, 359f)
        // 0.5 is closer to the 359-wraparound-to-1 boundary; both 359 and 1 are 1 degree away.
        val idx = PpiRasterizer.nearestAzimuthIndex(azimuths, 0.5f)
        assertTrue(azimuths[idx] == 1f || azimuths[idx] == 359f)
    }

    @Test
    fun `empty sweep produces an all-transparent buffer without crashing`() {
        val sweep = Sweep(1, 0.5f, emptyList())
        val pixels = PpiRasterizer.rasterize(sweep, MomentCode.REFLECTIVITY, 20, 20, 10f) { 0xFFFF0000.toInt() }
        assertTrue(pixels.all { it == 0 })
    }
}
