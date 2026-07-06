package com.nexradwx.app.data.radar

import com.nexradwx.app.network.HttpClientProvider
import com.nexradwx.core.decode.Level2Decoder
import com.nexradwx.core.model.RadarVolume
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

sealed class RadarFetchResult {
    data class Success(val volume: RadarVolume, val objectKey: String) : RadarFetchResult()
    data class Failure(val message: String, val cause: Throwable? = null) : RadarFetchResult()
}

/**
 * Fetches Level II volumes from Unidata's public mirror of the NOAA archive on S3.
 */
class RadarArchiveRepository {
    companion object {
        private const val BUCKET_HOST = "unidata-nexrad-level2.s3.amazonaws.com"
        private val VOLUME_KEY_PATTERN = Regex(""".*_V0\d(\.gz)?$""")
        private const val MAX_DAYS_BACK = 3
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
                downloadAndDecode(latestKey)
            } catch (e: Exception) {
                RadarFetchResult.Failure("Could not download or decode $latestKey: ${e.message}", e)
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

    private fun downloadAndDecode(key: String): RadarFetchResult {
        val url = "https://$BUCKET_HOST/$key"
        val request = Request.Builder().url(url).build()
        val response = HttpClientProvider.client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code} fetching $key")
        }

        val body = response.body ?: throw IOException("Empty body for $key")
        
        return try {
            val rawInputStream = body.byteStream()
            val inputStream = if (key.endsWith(".gz")) {
                GZIPInputStream(rawInputStream)
            } else {
                rawInputStream
            }
            
            inputStream.use { stream ->
                val volume = Level2Decoder.decode(stream)
                RadarFetchResult.Success(volume, key)
            }
        } finally {
            response.close()
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
