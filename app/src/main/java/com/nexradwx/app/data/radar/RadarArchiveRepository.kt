package com.nexradwx.app.data.radar

import com.nexradwx.app.network.HttpClientProvider
import com.nexradwx.core.decode.Level2Decoder
import com.nexradwx.core.model.RadarVolume
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.StringReader
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

sealed class RadarFetchResult {
    data class Success(val volume: RadarVolume, val objectKey: String) : RadarFetchResult()
    data class Failure(val message: String, val cause: Throwable? = null) : RadarFetchResult()
}

/**
 * Fetches Level II volumes from Unidata's public mirror of the NOAA archive on S3
 * (s3://unidata-nexrad-level2, anonymous/no-sign-request).
 *
 * NOAA's own `noaa-nexrad-level2` bucket -- long documented as the canonical public archive --
 * now returns AccessDenied for anonymous requests, including plain GetObject on known keys, not
 * just listing (confirmed directly against S3, not a local network artifact). Unidata's mirror is
 * still genuinely public and has the same key layout, but syncs with several hours of lag rather
 * than the ~4-10 minutes NOAA's bucket used to offer, so this is raw data but noticeably delayed.
 * Cutting that lag would mean reading Unidata's separate real-time chunk feed
 * (s3://unidata-nexrad-level2-chunks, key layout `<SITE>/<volume>/<yyyyMMdd>-<HHmmss>-<seq>-<S|I|E>`)
 * and reassembling the S/I/E sequence per volume -- verified reachable, not implemented here.
 *
 * Object keys look like `<yyyy>/<MM>/<dd>/<SITE>/<SITE><yyyyMMdd>_<HHmmss>_V06`, with older
 * entries additionally gzip-compressed (`..._V06.gz`).
 */
class RadarArchiveRepository {
    companion object {
        private const val BUCKET_HOST = "unidata-nexrad-level2.s3.amazonaws.com"
        private val VOLUME_KEY_PATTERN = Regex(""".*_V0\d(\.gz)?$""")
        private const val MAX_DAYS_BACK = 3 // covers the mirror's observed multi-hour sync lag
    }

    suspend fun fetchLatestVolume(siteId: String): RadarFetchResult = withContext(Dispatchers.IO) {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        var lastError: RadarFetchResult.Failure? = null

        for (daysBack in 0..MAX_DAYS_BACK) {
            val day = now.minusDays(daysBack.toLong())
            val prefix = "%04d/%02d/%02d/%s/".format(day.year, day.monthValue, day.dayOfMonth, siteId)

            val keys = try {
                listObjects(prefix).filter { VOLUME_KEY_PATTERN.matches(it) }
            } catch (e: IOException) {
                lastError = RadarFetchResult.Failure("Could not list $siteId volumes for $prefix: ${e.message}", e)
                continue
            }

            val latestKey = keys.maxOrNull() ?: continue
            return@withContext try {
                val bytes = downloadObject(latestKey)
                RadarFetchResult.Success(Level2Decoder.decode(bytes), latestKey)
            } catch (e: Exception) {
                RadarFetchResult.Failure("Could not download $latestKey: ${e.message}", e)
            }
        }
        lastError ?: RadarFetchResult.Failure(
            "No volumes found for $siteId in the last $MAX_DAYS_BACK UTC days",
        )
    }

    private fun listObjects(prefix: String): List<String> {
        val url = "https://$BUCKET_HOST/?list-type=2&prefix=$prefix"
        val request = Request.Builder().url(url).build()
        HttpClientProvider.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} listing $prefix")
            return parseObjectKeys(response.body?.string().orEmpty())
        }
    }

    private fun downloadObject(key: String): ByteArray {
        val url = "https://$BUCKET_HOST/$key"
        val request = Request.Builder().url(url).build()
        HttpClientProvider.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} fetching $key")
            val raw = response.body?.bytes() ?: throw IOException("Empty body for $key")
            return if (key.endsWith(".gz")) gunzip(raw) else raw
        }
    }

    private fun gunzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size * 3)
        GZIPInputStream(data.inputStream()).use { it.copyTo(out) }
        return out.toByteArray()
    }

    private fun parseObjectKeys(xml: String): List<String> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        val keys = mutableListOf<String>()
        var inKey = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> inKey = parser.name == "Key"
                XmlPullParser.TEXT -> if (inKey) keys.add(parser.text)
                XmlPullParser.END_TAG -> inKey = false
            }
            event = parser.next()
        }
        return keys
    }
}
