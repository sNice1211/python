package com.nexradwx.core.decode

import com.nexradwx.core.model.MomentData
import com.nexradwx.core.model.Radial
import com.nexradwx.core.model.RadialConstants
import com.nexradwx.core.model.RadialStatus
import com.nexradwx.core.model.RadarVolume
import com.nexradwx.core.model.Sweep
import com.nexradwx.core.model.VolumeHeader
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/**
 * Decodes NOAA NEXRAD Level II Archive/real-time files (ICD 2620002 / 2620010).
 */
object Level2Decoder {

    private const val CTM_HEADER_SIZE = 12
    private const val MESSAGE_HEADER_SIZE = 16
    private const val LEGACY_MESSAGE_SIZE = 2432
    private const val DAY_MILLIS = 86_400_000L
    private const val MSG_TYPE_DIGITAL_RADAR_DATA = 31

    /** Moments required by the app's UI. Others are skipped to save memory. */
    private val REQUIRED_MOMENTS = setOf("REF", "VEL", "RHO")

    fun decode(inputStream: InputStream): RadarVolume {
        val bufferedIn = if (inputStream is BufferedInputStream) inputStream else BufferedInputStream(inputStream)
        val dataIn = DataInputStream(bufferedIn)
        val header = tryReadVolumeHeader(bufferedIn)
        
        // Decompress the LDM-framed bzip2 records into a single message stream.
        val messageBytes = decompressLdmRecords(dataIn)

        val sweeps = LinkedHashMap<Int, MutableList<Radial>>()
        val reader = RadarByteReader(messageBytes)

        while (reader.remaining() >= CTM_HEADER_SIZE + MESSAGE_HEADER_SIZE) {
            val msgStart = reader.position
            reader.skip(CTM_HEADER_SIZE)

            val sizeHw = reader.readU16()
            reader.readU8() // RDA redundant-channel bitfield
            val msgType = reader.readU8()
            reader.readU16() // sequence number
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
                        // Skip malformed radials
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

    private fun tryReadVolumeHeader(bufferedIn: BufferedInputStream): VolumeHeader? {
        val buffer = ByteArray(24)
        bufferedIn.mark(24)
        val read = try {
            var total = 0
            while (total < 24) {
                val n = bufferedIn.read(buffer, total, 24 - total)
                if (n == -1) break
                total += n
            }
            total
        } catch (e: IOException) {
            0
        }
        
        if (read < 24) {
            bufferedIn.reset()
            return null
        }

        val reader = RadarByteReader(buffer)
        val version = reader.readAscii(9)
        if (!version.startsWith("AR2V") && !version.startsWith("ARCHIVE2")) {
            bufferedIn.reset()
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

    private fun decompressLdmRecords(dataIn: DataInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        
        try {
            while (true) {
                val rawLen = try { dataIn.readInt() } catch (e: Exception) { break }
                val blockLen = if (rawLen < 0) -rawLen else rawLen
                if (blockLen <= 0) break
                
                val block = ByteArray(blockLen)
                dataIn.readFully(block)
                
                BZip2CompressorInputStream(ByteArrayInputStream(block)).use {
                    it.copyTo(out)
                }
            }
        } catch (e: IOException) {
            // End of stream or malformed block
        }
        return out.toByteArray()
    }

    private fun decodeMessage31(
        reader: RadarByteReader,
        outerJulianDate: Int,
        outerMsOfDay: Long,
        sweeps: MutableMap<Int, MutableList<Radial>>,
    ) {
        val dataHeaderStart = reader.position
        reader.readAscii(4) // station id
        val timeMs = reader.readU32()
        val julianDate = reader.readU16()
        val azNum = reader.readU16()
        val azAngle = reader.readF32()
        val compression = reader.readU8()
        reader.skip(1) // spare
        reader.readU16() // radial length
        reader.skip(1) // azimuth resolution
        val radialStatusRaw = reader.readU8()
        val elevNum = reader.readU8()
        reader.skip(1) // cut sector
        val elevAngle = reader.readF32()
        reader.skip(1) // spot blanking
        reader.skip(1) // azimuth indexing
        val numDataBlocks = reader.readU16()

        if (compression != 0) return

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
                    reader.skip(2)
                    val unambRangeRaw = reader.readU16()
                    reader.skip(8)
                    val nyquistRaw = reader.readU16()
                    radialConsts = RadialConstants(
                        unambiguousRangeKm = unambRangeRaw * 0.1f,
                        nyquistVelocityMs = nyquistRaw * 0.01f,
                    )
                }
                else -> if (blockType == "D" && REQUIRED_MOMENTS.contains(blockName)) {
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

    private fun decodeMomentBlock(reader: RadarByteReader, name: String): MomentData? {
        reader.skip(4) // reserved
        val numGates = reader.readU16()
        val firstGateRaw = reader.readU16()
        val gateWidthRaw = reader.readU16()
        reader.skip(2) // tover
        reader.skip(2) // snr threshold
        reader.skip(1) // flags
        val dataSizeBits = reader.readU8()
        val scale = reader.readF32()
        val offset = reader.readF32()

        if (dataSizeBits != 8 && dataSizeBits != 16) return null
        if (reader.remaining() < numGates * (dataSizeBits / 8)) return null

        val values = FloatArray(numGates)
        for (i in 0 until numGates) {
            val raw = if (dataSizeBits == 8) reader.readU8() else reader.readU16()
            values[i] = when {
                raw <= 1 -> if (raw == 1) MomentData.RANGE_FOLDED else MomentData.BELOW_THRESHOLD
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
