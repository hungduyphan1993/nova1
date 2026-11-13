package com.example.data.network

import android.util.Xml
import com.example.data.local.CategoryEntity
import com.example.data.local.EpgProgramEntity
import com.example.data.local.StreamEntity
import com.example.data.local.StreamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/**
 * Streaming line-by-line M3U / M3U8 parser capable of ingesting 50,000+ line playlists
 * without loading the full payload into memory, emitting 500-item chunks to Room.
 */
class M3uStreamingParser(private val okHttpClient: OkHttpClient) {

    private val attrRegex = Regex("""([a-zA-Z0-9_-]+)\s*=\s*"([^"]*)"""")

    suspend fun parseFromUrl(
        url: String,
        userAgent: String,
        playlistId: Long,
        favoriteKeys: Set<String>,
        onBatchReady: suspend (List<CategoryEntity>, List<StreamEntity>) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url.trim())
            .header("User-Agent", userAgent)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: Unable to fetch M3U playlist")
            }
            val bodyStream = response.body?.byteStream()
                ?: throw IllegalStateException("Empty M3U response body")
            parseFromInputStream(bodyStream, playlistId, favoriteKeys, onBatchReady)
        }
    }

    suspend fun parseFromInputStream(
        inputStream: InputStream,
        playlistId: Long,
        favoriteKeys: Set<String>,
        onBatchReady: suspend (List<CategoryEntity>, List<StreamEntity>) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val seenCategories = LinkedHashMap<String, CategoryEntity>()
        val batchStreams = ArrayList<StreamEntity>(500)
        var totalInserted = 0
        var channelCounter = 1

        var pendingAttrs: Map<String, String> = emptyMap()
        var pendingDisplayTitle = ""
        var hasExtInf = false

        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 32 * 1024).use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                    val attrs = HashMap<String, String>()
                    attrRegex.findAll(trimmed).forEach { match ->
                        attrs[match.groupValues[1].lowercase(Locale.US)] = match.groupValues[2]
                    }
                    val commaIdx = trimmed.lastIndexOf(',')
                    pendingDisplayTitle = if (commaIdx >= 0 && commaIdx + 1 < trimmed.length) {
                        trimmed.substring(commaIdx + 1).trim()
                    } else {
                        attrs["tvg-name"].orEmpty()
                    }
                    pendingAttrs = attrs
                    hasExtInf = true
                } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val streamUrl = trimmed
                    val rawTitle = pendingDisplayTitle.ifBlank {
                        pendingAttrs["tvg-name"].orEmpty().ifBlank { "Channel $channelCounter" }
                    }
                    val groupTitle = pendingAttrs["group-title"].orEmpty().ifBlank { "General Channels" }
                    val streamType = inferStreamType(streamUrl, groupTitle)
                    val categorySlug = groupTitle.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "general" }
                    val compositeCatId = "$playlistId:${streamType.name}:$categorySlug"
                    val isAdult = isAdultCategoryName(groupTitle)

                    if (!seenCategories.containsKey(compositeCatId)) {
                        seenCategories[compositeCatId] = CategoryEntity(
                            id = compositeCatId,
                            playlistId = playlistId,
                            categoryId = categorySlug,
                            name = groupTitle,
                            streamType = streamType,
                            isAdultLocked = isAdult,
                            sortOrder = seenCategories.size
                        )
                    }

                    val providerId = "m3u_${streamUrl.hashCode().toUInt()}"
                    val streamKey = "$playlistId:${streamType.name}:$providerId"
                    val tvgId = pendingAttrs["tvg-id"].orEmpty().ifBlank {
                        rawTitle.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), ".")
                    }
                    val tvgShift = pendingAttrs["tvg-shift"]?.toIntOrNull() ?: 0
                    val catchupDays = pendingAttrs["catchup-days"]?.toIntOrNull()
                        ?: pendingAttrs["tvg-rec"]?.toIntOrNull()
                        ?: if (pendingAttrs.containsKey("catchup")) 3 else 0

                    batchStreams.add(
                        StreamEntity(
                            streamKey = streamKey,
                            playlistId = playlistId,
                            providerStreamId = providerId,
                            channelNumber = if (streamType == StreamType.LIVE) channelCounter++ else 0,
                            name = rawTitle,
                            streamType = streamType,
                            categoryId = categorySlug,
                            categoryName = groupTitle,
                            streamUrl = streamUrl,
                            logoUrl = pendingAttrs["tvg-logo"].orEmpty(),
                            tvgId = tvgId,
                            tvgShift = tvgShift,
                            catchupDays = catchupDays,
                            catchupSource = pendingAttrs["catchup-source"].orEmpty(),
                            qualityBadge = inferQualityBadge(rawTitle, streamUrl),
                            isAdult = isAdult,
                            isFavorite = favoriteKeys.contains(streamKey)
                        )
                    )
                    totalInserted++
                    hasExtInf = false
                    pendingAttrs = emptyMap()
                    pendingDisplayTitle = ""

                    if (batchStreams.size >= 500) {
                        onBatchReady(seenCategories.values.toList(), batchStreams.toList())
                        batchStreams.clear()
                    }
                } else if (!hasExtInf) {
                    // Ignore comments like #EXTM3U or #EXTVLCOPT when idle
                }
                line = reader.readLine()
            }
        }

        if (batchStreams.isNotEmpty() || seenCategories.isNotEmpty()) {
            onBatchReady(seenCategories.values.toList(), batchStreams.toList())
        }
        totalInserted
    }

    private fun inferStreamType(url: String, group: String): StreamType {
        val lowerUrl = url.lowercase(Locale.US)
        val lowerGroup = group.lowercase(Locale.US)
        return when {
            lowerUrl.contains("/series/") || lowerGroup.contains("series") || Regex("s\\d{1,2}\\s*e\\d{1,2}", RegexOption.IGNORE_CASE).containsMatchIn(lowerGroup) -> StreamType.SERIES
            lowerUrl.contains("/movie/") || lowerGroup.contains("movie") || lowerGroup.contains("vod") ||
                lowerUrl.endsWith(".mkv") || lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".avi") -> StreamType.VOD
            else -> StreamType.LIVE
        }
    }

    companion object {
        fun isAdultCategoryName(name: String): Boolean {
            val lower = name.lowercase(Locale.US)
            return lower.contains("adult") || lower.contains("18+") || lower.contains("xxx") || lower.contains("mature")
        }

        fun inferQualityBadge(title: String, url: String): String {
            val combined = "$title $url".uppercase(Locale.US)
            return when {
                combined.contains("4K") || combined.contains("UHD") || combined.contains("2160") -> "4K UHD"
                combined.contains("FHD") || combined.contains("1080") -> "1080p FHD"
                combined.contains("HD") || combined.contains("720") -> "720p HD"
                else -> "1080p FHD"
            }
        }
    }
}

/**
 * Streaming XMLTV & Gzipped XMLTV (.xml / .xml.gz) parser using XmlPullParser
 * emitting 500-item EPG batches into Room.
 */
class XmlTvStreamingParser(private val okHttpClient: OkHttpClient) {

    suspend fun syncXmlTvUrl(
        url: String,
        userAgent: String,
        playlistId: Long,
        onBatch: suspend (List<EpgProgramEntity>) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url.trim())
            .header("User-Agent", userAgent)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("XMLTV HTTP ${response.code}")
            }
            val raw = BufferedInputStream(
                response.body?.byteStream() ?: throw IllegalStateException("Empty XMLTV stream"),
                32 * 1024
            )
            raw.mark(4)
            val b0 = raw.read()
            val b1 = raw.read()
            raw.reset()

            val decodedStream: InputStream = if (b0 == 0x1f && b1 == 0x8b) {
                GZIPInputStream(raw)
            } else {
                raw
            }

            parseXmlTvStream(decodedStream, playlistId, onBatch)
        }
    }

    suspend fun parseXmlTvStream(
        inputStream: InputStream,
        playlistId: Long,
        onBatch: suspend (List<EpgProgramEntity>) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(InputStreamReader(inputStream, Charsets.UTF_8))

        val batch = ArrayList<EpgProgramEntity>(500)
        var total = 0

        var inProgramme = false
        var channelId = ""
        var startMs = 0L
        var stopMs = 0L
        var title = ""
        var desc = ""
        var category = "Broadcast"
        var currentTag = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name.orEmpty()
                    if (currentTag.equals("programme", ignoreCase = true)) {
                        inProgramme = true
                        channelId = parser.getAttributeValue(null, "channel").orEmpty()
                        startMs = parseXmlTvTimestamp(parser.getAttributeValue(null, "start"))
                        stopMs = parseXmlTvTimestamp(parser.getAttributeValue(null, "stop"))
                        title = ""
                        desc = ""
                        category = "Broadcast"
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inProgramme) {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            when (currentTag.lowercase(Locale.US)) {
                                "title" -> if (title.isEmpty()) title = text
                                "desc" -> if (desc.isEmpty()) desc = text
                                "category" -> category = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("programme", ignoreCase = true) && inProgramme) {
                        inProgramme = false
                        if (channelId.isNotBlank() && title.isNotBlank() && startMs > 0 && stopMs > startMs) {
                            batch.add(
                                EpgProgramEntity(
                                    playlistId = playlistId,
                                    tvgId = channelId,
                                    title = title,
                                    description = desc,
                                    category = category,
                                    startTimeMillis = startMs,
                                    endTimeMillis = stopMs
                                )
                            )
                            total++
                            if (batch.size >= 500) {
                                onBatch(batch.toList())
                                batch.clear()
                            }
                        }
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
        }
        total
    }

    private fun parseXmlTvTimestamp(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val cleaned = raw.trim()
        return runCatching {
            if (cleaned.length >= 19 && (cleaned.contains("+") || cleaned.contains("-"))) {
                val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
                fmt.parse(cleaned)?.time ?: 0L
            } else {
                val digits = cleaned.take(14)
                val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                fmt.parse(digits)?.time ?: 0L
            }
        }.getOrDefault(0L)
    }
}
