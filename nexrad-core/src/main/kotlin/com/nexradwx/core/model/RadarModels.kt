package com.nexradwx.core.model

/** Identifies a Level II data moment and how to present it. Units are the ICD-native units. */
enum class MomentCode(val code: String, val displayName: String, val units: String) {
    REFLECTIVITY("REF", "Reflectivity", "dBZ"),
    VELOCITY("VEL", "Velocity", "m/s"),
    SPECTRUM_WIDTH("SW", "Spectrum Width", "m/s"),
    DIFFERENTIAL_REFLECTIVITY("ZDR", "Differential Reflectivity", "dB"),
    DIFFERENTIAL_PHASE("PHI", "Differential Phase", "deg"),
    CORRELATION_COEFFICIENT("RHO", "Correlation Coefficient", "");

    companion object {
        fun fromCode(code: String): MomentCode? = entries.find { it.code == code }
    }
}

/**
 * One radial's worth of gate values for a single moment (e.g. REF), already converted from
 * raw digital counts to physical units via the per-radial scale/offset.
 *
 * A gate value of [BELOW_THRESHOLD] (NaN) means no valid return; [RANGE_FOLDED]
 * (+Infinity) means the RDA flagged the gate as range-folded ("purple haze").
 */
data class MomentData(
    val code: String,
    val gateCount: Int,
    val firstGateMeters: Int,
    val gateSpacingMeters: Int,
    val values: FloatArray,
) {
    companion object {
        const val BELOW_THRESHOLD = Float.NaN
        const val RANGE_FOLDED = Float.POSITIVE_INFINITY
    }
}

fun Float.isBelowThreshold(): Boolean = this.isNaN()
fun Float.isRangeFolded(): Boolean = this == Float.POSITIVE_INFINITY
fun Float.isValidGate(): Boolean = !this.isNaN() && this != Float.POSITIVE_INFINITY

data class RadialConstants(
    val unambiguousRangeKm: Float,
    val nyquistVelocityMs: Float,
)

object RadialStatus {
    const val START_ELEVATION = 0x1
    const val END_ELEVATION = 0x2
    const val START_VOLUME = 0x4
    const val END_VOLUME = 0x8
    const val LAST_ELEVATION = 0x10
}

data class Radial(
    val azimuthDegrees: Float,
    val elevationDegrees: Float,
    val azimuthNumber: Int,
    val elevationNumber: Int,
    val radialStatusFlags: Int,
    val timestampMillis: Long,
    val moments: Map<String, MomentData>,
    val nyquistVelocityMs: Float? = null,
) {
    fun moment(code: MomentCode): MomentData? = moments[code.code]
}

data class Sweep(
    val elevationNumber: Int,
    val elevationDegrees: Float,
    val radials: List<Radial>,
) {
    fun availableMoments(): Set<String> = radials.flatMap { it.moments.keys }.toSet()
}

data class VolumeHeader(
    val version: String,
    val extensionNumber: String,
    val timestampMillis: Long,
    val stationId: String,
)

data class RadarVolume(
    val header: VolumeHeader?,
    val sweeps: List<Sweep>,
) {
    /** Lowest-elevation sweep that actually carries the requested moment, or null. */
    fun lowestSweepWith(moment: MomentCode): Sweep? =
        sweeps.filter { it.availableMoments().contains(moment.code) }
            .minByOrNull { it.elevationDegrees }
}
