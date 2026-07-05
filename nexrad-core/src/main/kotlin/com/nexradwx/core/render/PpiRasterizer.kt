package com.nexradwx.core.render

import com.nexradwx.core.color.Argb
import com.nexradwx.core.color.CorrelationCoefficientColorTable
import com.nexradwx.core.color.ReflectivityColorTable
import com.nexradwx.core.color.VelocityColorTable
import com.nexradwx.core.model.MomentCode
import com.nexradwx.core.model.Sweep
import kotlin.math.atan2
import kotlin.math.sqrt

/** Maps a moment's physical value to a color, closing over anything product-specific (e.g. Nyquist). */
fun colorFunctionFor(moment: MomentCode, nyquistVelocityMs: Float): (Float) -> Argb = when (moment) {
    MomentCode.REFLECTIVITY -> ReflectivityColorTable::colorFor
    MomentCode.CORRELATION_COEFFICIENT -> CorrelationCoefficientColorTable::colorFor
    MomentCode.VELOCITY -> { v -> VelocityColorTable.colorFor(v, nyquistVelocityMs) }
    else -> { _ -> 0x00000000 }
}

/**
 * Rasterizes one sweep's worth of a single moment into a square ARGB pixel buffer (row-major,
 * top-left origin), as a plan position indicator (PPI): radar at image center, north up.
 *
 * This does a per-pixel polar lookup (nearest radial by azimuth, nearest gate by range) rather
 * than drawing per-gate polygons, which stays fast regardless of gate/radial count and keeps the
 * rendering logic independent of any UI toolkit.
 */
object PpiRasterizer {

    private const val TRANSPARENT: Argb = 0x00000000

    fun rasterize(
        sweep: Sweep,
        moment: MomentCode,
        widthPx: Int,
        heightPx: Int,
        maxRangeKm: Float,
        colorFor: (Float) -> Argb,
    ): IntArray {
        val pixels = IntArray(widthPx * heightPx) { TRANSPARENT }
        if (sweep.radials.isEmpty() || maxRangeKm <= 0f) return pixels

        val sorted = sweep.radials.sortedBy { it.azimuthDegrees }
        val azimuths = FloatArray(sorted.size) { sorted[it].azimuthDegrees }

        val centerX = widthPx / 2f
        val centerY = heightPx / 2f
        val pxPerKm = minOf(widthPx, heightPx) / (2f * maxRangeKm)
        if (pxPerKm <= 0f) return pixels

        for (y in 0 until heightPx) {
            val dy = y - centerY
            for (x in 0 until widthPx) {
                val dx = x - centerX
                val rangeKm = sqrt(dx * dx + dy * dy) / pxPerKm
                if (rangeKm > maxRangeKm) continue

                // Screen y grows downward; compass azimuth is clockwise from north (up).
                var azimuthDeg = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
                if (azimuthDeg < 0f) azimuthDeg += 360f

                val radial = sorted[nearestAzimuthIndex(azimuths, azimuthDeg)]
                val gateData = radial.moment(moment) ?: continue
                if (gateData.gateSpacingMeters <= 0) continue

                val rangeMeters = rangeKm * 1000f
                val gateIndex =
                    ((rangeMeters - gateData.firstGateMeters) / gateData.gateSpacingMeters).toInt()
                if (gateIndex < 0 || gateIndex >= gateData.gateCount) continue

                pixels[y * widthPx + x] = colorFor(gateData.values[gateIndex])
            }
        }
        return pixels
    }

    /** Index of the azimuth in the ascending, 0..360 [azimuths] array closest to [target], with wraparound. */
    internal fun nearestAzimuthIndex(azimuths: FloatArray, target: Float): Int {
        if (azimuths.size == 1) return 0

        var lo = 0
        var hi = azimuths.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (azimuths[mid] < target) lo = mid + 1 else hi = mid
        }
        // lo is the insertion point; compare against its neighbors (with wraparound at the ends).
        val candidates = intArrayOf(
            (lo - 1 + azimuths.size) % azimuths.size,
            lo % azimuths.size,
        )
        return candidates.minBy { circularDelta(azimuths[it], target) }
    }

    private fun circularDelta(a: Float, b: Float): Float {
        val diff = kotlin.math.abs(a - b) % 360f
        return minOf(diff, 360f - diff)
    }
}
