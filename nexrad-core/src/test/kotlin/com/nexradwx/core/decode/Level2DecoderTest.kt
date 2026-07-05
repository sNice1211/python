package com.nexradwx.core.decode

import com.nexradwx.core.model.MomentCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Level2DecoderTest {

    private fun loadFixture(): ByteArray {
        val stream = requireNotNull(
            javaClass.classLoader.getResourceAsStream("level2_sample.ar2")
        ) { "level2_sample.ar2 fixture missing; regenerate with build_fixture.py" }
        return stream.readBytes()
    }

    @Test
    fun `decodes volume header`() {
        val volume = Level2Decoder.decode(loadFixture())
        val header = assertNotNull(volume.header)
        assertEquals("KTLX", header.stationId)
        assertTrue(header.version.startsWith("AR2V"))
    }

    @Test
    fun `decodes both radials into a single sweep`() {
        val volume = Level2Decoder.decode(loadFixture())
        assertEquals(1, volume.sweeps.size)
        val sweep = volume.sweeps.single()
        assertEquals(1, sweep.elevationNumber)
        assertEquals(0.5f, sweep.elevationDegrees)
        assertEquals(2, sweep.radials.size)
        assertEquals(0.5f, sweep.radials[0].azimuthDegrees)
        assertEquals(1.5f, sweep.radials[1].azimuthDegrees)
    }

    @Test
    fun `decodes reflectivity gates with below-threshold and range-folded sentinels`() {
        val volume = Level2Decoder.decode(loadFixture())
        val ref = volume.sweeps.single().radials[0].moment(MomentCode.REFLECTIVITY)
        val values = assertNotNull(ref).values
        assertEquals(4, values.size)
        assertTrue(values[0].isNaN(), "raw=0 should be below-threshold (NaN)")
        assertTrue(values[1] == Float.POSITIVE_INFINITY, "raw=1 should be range-folded")
        assertEquals(17.0f, values[2], 1e-4f) // (100 - 66) / 2
        assertEquals(33.0f, values[3], 1e-4f) // (132 - 66) / 2
        assertEquals(2125, ref.firstGateMeters)
        assertEquals(250, ref.gateSpacingMeters)
    }

    @Test
    fun `decodes velocity gates`() {
        val volume = Level2Decoder.decode(loadFixture())
        val vel = assertNotNull(volume.sweeps.single().radials[0].moment(MomentCode.VELOCITY))
        assertEquals(-1.0f, vel.values[2], 1e-4f) // (127 - 129) / 2
        assertEquals(1.0f, vel.values[3], 1e-4f) // (131 - 129) / 2
    }

    @Test
    fun `decodes correlation coefficient gates`() {
        val volume = Level2Decoder.decode(loadFixture())
        val rho = assertNotNull(volume.sweeps.single().radials[0].moment(MomentCode.CORRELATION_COEFFICIENT))
        assertEquals(1.0f, rho.values[2], 1e-4f) // (100 - (-100)) / 200
        assertEquals(1.5f, rho.values[3], 1e-4f) // (200 - (-100)) / 200
    }

    @Test
    fun `decodes radial constants nyquist velocity`() {
        val volume = Level2Decoder.decode(loadFixture())
        val radial = volume.sweeps.single().radials[0]
        assertEquals(30.0f, assertNotNull(radial.nyquistVelocityMs), 1e-4f)
    }

    @Test
    fun `radial status flags mark start and end of elevation-volume`() {
        val volume = Level2Decoder.decode(loadFixture())
        val radials = volume.sweeps.single().radials
        assertEquals(
            com.nexradwx.core.model.RadialStatus.START_ELEVATION or com.nexradwx.core.model.RadialStatus.START_VOLUME,
            radials[0].radialStatusFlags,
        )
        assertEquals(
            com.nexradwx.core.model.RadialStatus.END_ELEVATION or com.nexradwx.core.model.RadialStatus.END_VOLUME,
            radials[1].radialStatusFlags,
        )
    }

    @Test
    fun `truncated garbage input decodes to an empty volume instead of throwing`() {
        val volume = Level2Decoder.decode(ByteArray(10) { 0x42 })
        assertTrue(volume.sweeps.isEmpty())
    }
}
