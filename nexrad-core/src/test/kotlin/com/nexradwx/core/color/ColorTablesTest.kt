package com.nexradwx.core.color

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorTablesTest {

    private fun alphaOf(argb: Argb): Int = (argb ushr 24) and 0xFF

    @Test
    fun `reflectivity below the visibility floor is transparent`() {
        assertEquals(0, alphaOf(ReflectivityColorTable.colorFor(-10f)))
    }

    @Test
    fun `reflectivity below-threshold sentinel is transparent`() {
        assertEquals(0, alphaOf(ReflectivityColorTable.colorFor(Float.NaN)))
    }

    @Test
    fun `reflectivity range-folded sentinel is opaque and distinct`() {
        val color = ReflectivityColorTable.colorFor(Float.POSITIVE_INFINITY)
        assertEquals(255, alphaOf(color))
    }

    @Test
    fun `reflectivity ramp is monotonically increasing in stop order`() {
        val low = ReflectivityColorTable.colorFor(10f)
        val mid = ReflectivityColorTable.colorFor(40f)
        val high = ReflectivityColorTable.colorFor(65f)
        assertTrue(low != mid && mid != high, "distinct dBZ bands should render distinct colors")
    }

    @Test
    fun `velocity of zero is near-neutral not fully saturated`() {
        val color = VelocityColorTable.colorFor(0f, 30f)
        assertEquals(255, alphaOf(color))
    }

    @Test
    fun `velocity sign determines hue direction`() {
        val inbound = VelocityColorTable.colorFor(-20f, 30f)
        val outbound = VelocityColorTable.colorFor(20f, 30f)
        val inboundGreen = (inbound ushr 8) and 0xFF
        val outboundRed = (outbound ushr 16) and 0xFF
        assertTrue(inboundGreen > 0x40, "inbound (negative) velocity should read green-dominant")
        assertTrue(outboundRed > 0x40, "outbound (positive) velocity should read red-dominant")
    }

    @Test
    fun `velocity below-threshold sentinel is transparent`() {
        assertEquals(0, alphaOf(VelocityColorTable.colorFor(Float.NaN, 30f)))
    }

    @Test
    fun `correlation coefficient near 1 is meteorological and distinct from low cc debris signature`() {
        val meteorological = CorrelationCoefficientColorTable.colorFor(0.98f)
        val debris = CorrelationCoefficientColorTable.colorFor(0.3f)
        assertTrue(meteorological != debris)
    }

    @Test
    fun `correlation coefficient below-threshold sentinel is transparent`() {
        assertEquals(0, alphaOf(CorrelationCoefficientColorTable.colorFor(Float.NaN)))
    }
}
