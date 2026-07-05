package com.nexradwx.core.decode

import com.nexradwx.core.model.MomentData
import com.nexradwx.core.model.Radial
import com.nexradwx.core.model.RadialConstants
import com.nexradwx.core.model.RadialStatus
import com.nexradwx.core.model.RadarVolume
import com.nexradwx.core.model.Sweep
import com.nexradwx.core.model.VolumeHeader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/**
 * Decodes NOAA NEXRAD Level II Archive/real-time files (ICD 2620002 / 2620010).
 *
 * File layout:
 *  - optional 24-byte volume header ("AR2V0006." style)
 *  - a sequence of "LDM compressed records": a 4-byte big-endian signed byte count
 *    (magnitude only matters) followed by that many bytes of bzip2-compressed data.
 *    Concatenating the decompressed payloads yields the message stream.
 *  - the message stream is a sequence of [12-byte legacy CTM header][16-byte message
 *    header][message body]. We only decode Message Type 31 (Digital Radar Data Generic
 *    Format), which is what carries REF/VEL/SW/ZDR/PHI/RHO moments.
 */
object Level2Decoder {

    private const val CTM_HEADER_SIZE = 12
    private const val MESSAGE_HEADER_SIZE = 16
    private const val LEGACY_MESSAGE_SIZE = 2432 // CTM(12) + header+data(2416) + FCS(4)
    private const val DAY_MILLIS = 86_400_000L
    private const val MSG_TYPE_DIGITAL_RADAR_DATA = 31

    fun decode(raw: ByteArray): RadarVolume {
        val fileReader = RadarByteReader(raw)
        val header = tryReadVolumeHeader(fileReader)
        val messageStreamStart = fileReader.position
        val messageBytes = decompressLdmRecords(raw, messageStreamStart)

        val sweeps = LinkedHashMap<Int, MutableList<Radial>>()
        val reader = RadarByteReader(messageBytes)

        while (reader.remaining() >= CTM_HEADER_SIZE + MESSAGE_HEADER_SIZE) {
            val msgStart = reader.position
            reader.skip(CTM_HEADER_SIZE)

            val sizeHw = reader.readU16()
            reader.readU8() // RDA redundant-channel bitfield, unused
            val msgType = reader.readU8()
            reader.readU16() // sequence number, unused
            val julianDate = reader.readU16()
            val msOfDay = reader.readU32()
            val numSegments = reader.readU16()
            val segmentNum = reader.readU16()

            var msgBytesTotal = LEGACY_MESSAGE_SIZE
            if (sizeHw != 0) {
                msgBytesTotal = when {
                    sizeHw == 0xFFFF -> (numSegments shl 16) or (segmentNum + CTM_HEADER_SIZE)
                    msgType == MSG_TYPE_DIGITAL_RADAR_DATA || msgType == 29 ->
                        CTM_HEADER_SIZE + 2 * sizeHw
                    else -> LEGACY_MESSAGE_SIZE
                }
                if (msgType == MSG_TYPE_DIGITAL_RADAR_DATA) {
                    try {
                        decodeMessage31(reader, julianDate, msOfDay, sweeps)
                    } catch (e: Exception) {
                        // A malformed/truncated radial shouldn't take down the whole volume.
                    }
                }
            }

            val nextPos = msgStart + msgBytesTotal
            if (nextPos <= msgStart || nextPos > messageBytes.size) break
            reader.position = nextPos
        }

        val sweepList = sweeps.entries
            .sortedBy { it.key }
            .map { (elevationNumber, radials) ->
                Sweep(
                    elevationNumber = elevationNumber,
                    elevationDegrees = radials.firstOrNull()?.elevationDegrees ?: 0f,
                    radials = radials,
                )
            }
        return RadarVolume(header = header, sweeps = sweepList)
    }

    private fun tryReadVolumeHeader(reader: RadarByteReader): VolumeHeader? {
        if (reader.remaining() < 24) return null
        val startPos = reader.position
        val version = reader.readAscii(9)
        if (!version.startsWith("AR2V") && !version.startsWith("ARCHIVE2")) {
            reader.position = startPos
            return null
        }
        val extensionNumber = reader.readAscii(3)
        val julianDate = reader.readU32()
        val msOfDay = reader.readU32()
        val stationId = reader.readAscii(4)
        return VolumeHeader(
            version = version,
            extensionNumber = extensionNumber,
            timestampMillis = nexradToEpochMillis(julianDate, msOfDay),
            stationId = stationId,
        )
    }

    /** Concatenates the decompressed payloads of every LDM-framed bzip2 block. */
    private fun decompressLdmRecords(data: ByteArray, start: Int): ByteArray {
        val out = ByteArrayOutputStream(data.size * 3)
        var offset = start
        var decodedAny = false
        while (offset + 4 <= data.size) {
            val rawLen = readSignedBe32(data, offset)
            val blockLen = if (rawLen < 0) -rawLen else rawLen
            offset += 4
            if (blockLen <= 0 || offset + blockLen > data.size) break
            try {
                BZip2CompressorInputStream(ByteArrayInputStream(data, offset, blockLen)).use {
                    it.copyTo(out)
                }
                decodedAny = true
            } catch (e: IOException) {
                if (decodedAny) break
                return data.copyOfRange(start, data.size)
            }
            offset += blockLen
        }
        return if (decodedAny) out.toByteArray() else data.copyOfRange(start, data.size)
    }

    private fun readSignedBe32(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    private fun decodeMessage31(
        reader: RadarByteReader,
        outerJulianDate: Int,
        outerMsOfDay: Long,
        sweeps: MutableMap<Int, MutableList<Radial>>,
    ) {
        val dataHeaderStart = reader.position
        reader.readAscii(4) // station id, redundant with volume header
        val timeMs = reader.readU32()
        val julianDate = reader.readU16()
        val azNum = reader.readU16()
        val azAngle = reader.readF32()
        val compression = reader.readU8()
        reader.skip(1) // spare
        reader.readU16() // radial length in bytes, informational only
        reader.skip(1) // azimuth resolution spacing code
        val radialStatusRaw = reader.readU8()
        val elevNum = reader.readU8()
        reader.skip(1) // cut sector number
        val elevAngle = reader.readF32()
        reader.skip(1) // spot blanking status
        reader.skip(1) // azimuth indexing mode
        val numDataBlocks = reader.readU16()

        if (compression != 0) return // compressed Msg31 payloads are not produced in practice

        val blockPointers = IntArray(numDataBlocks) { reader.readI32() }

        var radialConsts: RadialConstants? = null
        val moments = LinkedHashMap<String, MomentData>()

        for (ptr in blockPointers) {
            if (ptr <= 0) continue
            val blockPos = dataHeaderStart + ptr
            if (blockPos < 0 || blockPos + 4 > reader.size) continue
            reader.position = blockPos
            val blockType = reader.readAscii(1)
            val blockName = reader.readAscii(3)
            when (blockName) {
                "RAD" -> {
                    reader.skip(2) // block size
                    val unambRangeRaw = reader.readU16()
                    reader.skip(8) // noise_h, noise_v
                    val nyquistRaw = reader.readU16()
                    radialConsts = RadialConstants(
                        unambiguousRangeKm = unambRangeRaw * 0.1f,
                        nyquistVelocityMs = nyquistRaw * 0.01f,
                    )
                }
                "VOL", "ELV" -> {
                    // Calibration/site metadata not needed to render REF/VEL/RHO.
                }
                else -> if (blockType == "D") {
                    decodeMomentBlock(reader, blockName)?.let { moments[blockName] = it }
                }
            }
        }

        val timestamp = nexradToEpochMillis(
            if (julianDate != 0) julianDate.toLong() else outerJulianDate.toLong(),
            if (timeMs != 0L) timeMs else outerMsOfDay,
        )

        val radial = Radial(
            azimuthDegrees = azAngle,
            elevationDegrees = elevAngle,
            azimuthNumber = azNum,
            elevationNumber = elevNum,
            radialStatusFlags = remapRadialStatus(radialStatusRaw),
            timestampMillis = timestamp,
            moments = moments,
            nyquistVelocityMs = radialConsts?.nyquistVelocityMs,
        )

        sweeps.getOrPut(elevNum) { mutableListOf() }.add(radial)
    }

    /** Generic Data Moment block (ICD Table XVII): 28-byte header + packed gate data. */
    private fun decodeMomentBlock(reader: RadarByteReader, name: String): MomentData? {
        reader.skip(4) // reserved
        val numGates = reader.readU16()
        val firstGateRaw = reader.readU16()
        val gateWidthRaw = reader.readU16()
        reader.skip(2) // tover
        reader.skip(2) // snr threshold
        reader.skip(1) // recombined-azimuths/gates flags
        val dataSizeBits = reader.readU8()
        val scale = reader.readF32()
        val offset = reader.readF32()

        if (dataSizeBits != 8 && dataSizeBits != 16) return null
        if (reader.remaining() < numGates * (dataSizeBits / 8)) return null

        val values = FloatArray(numGates)
        for (i in 0 until numGates) {
            val raw = if (dataSizeBits == 8) reader.readU8() else reader.readU16()
            values[i] = when {
                raw == 0 -> MomentData.BELOW_THRESHOLD
                raw == 1 -> MomentData.RANGE_FOLDED
                scale == 0f -> MomentData.BELOW_THRESHOLD
                else -> (raw - offset) / scale
            }
        }
        return MomentData(
            code = name,
            gateCount = numGates,
            firstGateMeters = firstGateRaw,
            gateSpacingMeters = gateWidthRaw,
            values = values,
        )
    }

    private fun remapRadialStatus(raw: Int): Int = when (raw and 0x0F) {
        0 -> RadialStatus.START_ELEVATION
        2 -> RadialStatus.END_ELEVATION
        3 -> RadialStatus.START_ELEVATION or RadialStatus.START_VOLUME
        4 -> RadialStatus.END_ELEVATION or RadialStatus.END_VOLUME
        5 -> RadialStatus.START_ELEVATION or RadialStatus.LAST_ELEVATION
        else -> 0
    }

    private fun nexradToEpochMillis(julianDate: Long, msOfDay: Long): Long =
        (julianDate - 1) * DAY_MILLIS + msOfDay
}
