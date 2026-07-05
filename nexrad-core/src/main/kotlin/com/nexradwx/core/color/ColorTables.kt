package com.nexradwx.core.color

import com.nexradwx.core.model.isRangeFolded
import com.nexradwx.core.model.isValidGate

/** Packed 0xAARRGGBB color, matching Android's `android.graphics.Color` int encoding. */
typealias Argb = Int

private const val TRANSPARENT: Argb = 0x00000000
private const val RANGE_FOLD_COLOR: Argb = 0xFFC0C0C0.toInt() // "purple haze" convention, kept neutral gray

private fun argb(a: Int, r: Int, g: Int, b: Int): Argb =
    (a shl 24) or (r shl 16) or (g shl 8) or b

private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt().coerceIn(0, 255)

/** A value->color stop. [value] marks the lower bound of the band starting here. */
data class ColorStop(val value: Float, val color: Argb)

/** Linear interpolation across an ascending list of [stops]; clamps outside the range. */
private fun rampColor(value: Float, stops: List<ColorStop>): Argb {
    if (value <= stops.first().value) return stops.first().color
    if (value >= stops.last().value) return stops.last().color
    for (i in 0 until stops.size - 1) {
        val lo = stops[i]
        val hi = stops[i + 1]
        if (value in lo.value..hi.value) {
            val t = if (hi.value == lo.value) 0f else (value - lo.value) / (hi.value - lo.value)
            val a = lerp((lo.color ushr 24) and 0xFF, (hi.color ushr 24) and 0xFF, t)
            val r = lerp((lo.color ushr 16) and 0xFF, (hi.color ushr 16) and 0xFF, t)
            val g = lerp((lo.color ushr 8) and 0xFF, (hi.color ushr 8) and 0xFF, t)
            val b = lerp(lo.color and 0xFF, hi.color and 0xFF, t)
            return argb(a, r, g, b)
        }
    }
    return stops.last().color
}

object ReflectivityColorTable {
    // dBZ stops loosely following the common NWS-style reflectivity ramp: blues/greens for
    // light precip, yellow/orange/red through moderate-heavy, magenta/white for extreme.
    private val stops = listOf(
        ColorStop(5f, argb(255, 0x64, 0xB4, 0xFF)),
        ColorStop(10f, argb(255, 0x00, 0x90, 0xFF)),
        ColorStop(15f, argb(255, 0x00, 0xC8, 0x64)),
        ColorStop(20f, argb(255, 0x00, 0x96, 0x00)),
        ColorStop(25f, argb(255, 0xC8, 0xE6, 0x00)),
        ColorStop(30f, argb(255, 0xFF, 0xE6, 0x00)),
        ColorStop(35f, argb(255, 0xFF, 0xB4, 0x00)),
        ColorStop(40f, argb(255, 0xFF, 0x78, 0x00)),
        ColorStop(45f, argb(255, 0xFF, 0x00, 0x00)),
        ColorStop(50f, argb(255, 0xC8, 0x00, 0x00)),
        ColorStop(55f, argb(255, 0x96, 0x00, 0x00)),
        ColorStop(60f, argb(255, 0xFF, 0x00, 0xFF)),
        ColorStop(65f, argb(255, 0xC8, 0x64, 0xFF)),
        ColorStop(70f, argb(255, 0xFF, 0xFF, 0xFF)),
    )

    fun colorFor(dbz: Float): Argb = when {
        dbz.isRangeFolded() -> RANGE_FOLD_COLOR
        !dbz.isValidGate() -> TRANSPARENT
        dbz < stops.first().value -> TRANSPARENT
        else -> rampColor(dbz, stops)
    }
}

object VelocityColorTable {
    // Diverging: green = inbound (negative, toward radar), red = outbound (positive, away).
    // Scaled to the radial's Nyquist velocity so folding-limited scans still use full range.
    private val negativeStops = listOf(
        ColorStop(-1f, argb(255, 0x00, 0xFF, 0x00)),
        ColorStop(-0.5f, argb(255, 0x00, 0x96, 0x00)),
        ColorStop(-0.05f, argb(255, 0x00, 0x32, 0x00)),
    )
    private val positiveStops = listOf(
        ColorStop(0.05f, argb(255, 0x32, 0x00, 0x00)),
        ColorStop(0.5f, argb(255, 0x96, 0x00, 0x00)),
        ColorStop(1f, argb(255, 0xFF, 0x00, 0x00)),
    )

    fun colorFor(velocityMs: Float, nyquistMs: Float): Argb {
        if (velocityMs.isRangeFolded()) return RANGE_FOLD_COLOR
        if (!velocityMs.isValidGate()) return TRANSPARENT
        val nyquist = if (nyquistMs > 0f) nyquistMs else 1f
        val normalized = (velocityMs / nyquist).coerceIn(-1f, 1f)
        return when {
            normalized < -0.05f -> rampColor(normalized, negativeStops)
            normalized > 0.05f -> rampColor(normalized, positiveStops)
            else -> argb(255, 0x20, 0x20, 0x20) // near-zero velocity, ground clutter territory
        }
    }
}

object CorrelationCoefficientColorTable {
    // Low CC (< ~0.8) flags non-meteorological returns (debris, birds, chaff); high CC (~1.0)
    // is typical of uniform precipitation.
    private val stops = listOf(
        ColorStop(0.2f, argb(255, 0xFF, 0x00, 0x00)),
        ColorStop(0.5f, argb(255, 0xFF, 0x96, 0x00)),
        ColorStop(0.7f, argb(255, 0xFF, 0xFF, 0x00)),
        ColorStop(0.85f, argb(255, 0x64, 0xFF, 0x64)),
        ColorStop(0.95f, argb(255, 0x00, 0x96, 0xFF)),
        ColorStop(1.0f, argb(255, 0xFF, 0xFF, 0xFF)),
        ColorStop(1.05f, argb(255, 0x80, 0x80, 0x80)),
    )

    fun colorFor(cc: Float): Argb = when {
        cc.isRangeFolded() -> RANGE_FOLD_COLOR
        !cc.isValidGate() -> TRANSPARENT
        else -> rampColor(cc, stops)
    }
}
