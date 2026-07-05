package com.nexradwx.app.data.radar

import com.nexradwx.app.network.HttpClientProvider
import com.nexradwx.core.decode.Level2Decoder
import com.nexradwx.core.model.RadarVolume
import java.io.IOException
import java.io.StringReader
import java.time.ZoneOffset
import java.time.ZonedDateTime
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
 * Fetches Level II volumes from NOAA's public "Big Data Program" archive on S3
 * (s3://noaa-nexrad-level2, anonymous/no-sign-request, see registry.opendata.aws/noaa-nexrad).
 *
 * Object keys look like `<yyyy>/<MM>/<dd>/<SITE>/<SITE><yyyyMMdd>_<HHmmss>_V06`. A full volume
 * (all elevation sweeps) typically lands every 4-10 minutes depending on the site's VCP, so this
 * is "raw data" but not sub-minute real-time; streaming the individual real-time LDM chunks from
 * Unidata's separate bucket would cut that latency but isn't implemented here (see README).
 */
class RadarArchiveRepository {
    companion object {
        private const val BUCKET_HOST = "noaa-nexrad-level2.s3.amazonaws.com"
        private val VOLUME_KEY_PATTERN = Regex(""".*_V0\d$""")
    }

    suspend fun fetchLatestVolume(siteId: String): RadarFetchResult = withContext(Dispatchers.IO) {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        for (daysBack in 0..1) { // near UTC midnight the latest volume may be "yesterday"
            val day = now.minusDays(daysBack.toLong())
            val prefix = "%04d/%02d/%02d/%s/".format(day.year, day.monthValue, day.dayOfMonth, siteId)

            val keys = try {
                listObjects(prefix).filter { VOLUME_KEY_PATTERN.matches(it) }
            } catch (e: IOException) {
                return@withContext RadarFetchResult.Failure("Could not list $siteId volumes: ${e.message}", e)
            }

            val latestKey = keys.maxOrNull() ?: continue
            return@withContext try {
                val bytes = downloadObject(latestKey)
                RadarFetchResult.Success(Level2Decoder.decode(bytes), latestKey)
            } catch (e: Exception) {
                RadarFetchResult.Failure("Could not download $latestKey: ${e.message}", e)
            }
        }
        RadarFetchResult.Failure("No volumes found for $siteId in the last two UTC days")
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
            return response.body?.bytes() ?: throw IOException("Empty body for $key")
        }
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
