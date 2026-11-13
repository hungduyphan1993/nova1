package com.example.data.network

import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import com.example.data.local.CategoryEntity
import com.example.data.local.EpgProgramEntity
import com.example.data.local.EpisodeEntity
import com.example.data.local.StreamEntity
import com.example.data.local.StreamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

data class XtreamAccountInfo(
    val status: String,
    val expDateSec: Long?,
    val activeConnections: Int,
    val maxConnections: Int,
    val serverProtocol: String = "http"
)

data class SpeedTestResult(
    val latencyMs: Long,
    val jitterMs: Long,
    val downloadMbps: Double,
    val bytesTransferred: Long,
    val qualityVerdict: String
)

class XtreamAndStalkerClients(private val okHttpClient: OkHttpClient) {

    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    suspend fun authenticateXtream(
        serverUrl: String,
        username: String,
        password: String,
        userAgent: String
    ): XtreamAccountInfo = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(serverUrl)
        val url = "$base/player_api.php?username=${enc(username)}&password=${enc(password)}"
        val body = executeGetString(url, userAgent)
        val root = JSONObject(body)
        val userInfo = root.optJSONObject("user_info")
            ?: throw IllegalStateException("Invalid Xtream Codes response (missing user_info)")
        val auth = userInfo.optInt("auth", 0)
        val status = userInfo.optString("status", "Unknown")
        if (auth == 0 && !status.equals("Active", ignoreCase = true)) {
            throw IllegalStateException("Xtream Authentication failed (status: $status)")
        }
        val expDate = userInfo.optString("exp_date", "").toLongOrNull()
        val activeCons = userInfo.optString("active_cons", "0").toIntOrNull() ?: 0
        val maxCons = userInfo.optString("max_connections", "1").toIntOrNull() ?: 1
        val protocol = root.optJSONObject("server_info")?.optString("server_protocol", "http") ?: "http"
        XtreamAccountInfo(
            status = status,
            expDateSec = expDate,
            activeConnections = activeCons,
            maxConnections = maxCons,
            serverProtocol = protocol
        )
    }

    suspend fun fetchXtreamCategories(
        serverUrl: String,
        username: String,
        password: String,
        userAgent: String,
        playlistId: Long,
        streamType: StreamType
    ): List<CategoryEntity> = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(serverUrl)
        val action = when (streamType) {
            StreamType.LIVE -> "get_live_categories"
            StreamType.VOD -> "get_vod_categories"
            StreamType.SERIES -> "get_series_categories"
        }
        val url = "$base/player_api.php?username=${enc(username)}&password=${enc(password)}&action=$action"
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
        val list = ArrayList<CategoryEntity>()
        okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use
            val stream = resp.body?.byteStream() ?: return@use
            JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                    reader.beginArray()
                    var index = 0
                    while (reader.hasNext()) {
                        var catId = ""
                        var catName = ""
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "category_id" -> catId = readStringSafe(reader)
                                "category_name" -> catName = readStringSafe(reader)
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        if (catId.isNotBlank() && catName.isNotBlank()) {
                            list.add(
                                CategoryEntity(
                                    id = "$playlistId:${streamType.name}:$catId",
                                    playlistId = playlistId,
                                    categoryId = catId,
                                    name = catName,
                                    streamType = streamType,
                                    isAdultLocked = M3uStreamingParser.isAdultCategoryName(catName),
                                    sortOrder = index++
                                )
                            )
                        }
                    }
                    reader.endArray()
                }
            }
        }
        list
    }

    suspend fun streamXtreamCatalog(
        serverUrl: String,
        username: String,
        password: String,
        userAgent: String,
        playlistId: Long,
        streamType: StreamType,
        categoryNamesById: Map<String, String>,
        favoriteKeys: Set<String>,
        onBatch: suspend (List<StreamEntity>) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(serverUrl)
        val action = when (streamType) {
            StreamType.LIVE -> "get_live_streams"
            StreamType.VOD -> "get_vod_streams"
            StreamType.SERIES -> "get_series"
        }
        val url = "$base/player_api.php?username=${enc(username)}&password=${enc(password)}&action=$action"
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()

        var total = 0
        val batch = ArrayList<StreamEntity>(500)

        okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use
            val stream = resp.body?.byteStream() ?: return@use
            JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                if (reader.peek() != JsonToken.BEGIN_ARRAY) return@use
                reader.beginArray()
                var index = 1
                while (reader.hasNext()) {
                    var streamId = ""
                    var num = index
                    var name = ""
                    var icon = ""
                    var backdrop = ""
                    var epgChannelId = ""
                    var catId = "general"
                    var ext = if (streamType == StreamType.LIVE) "ts" else "mp4"
                    var rating = 0.0
                    var releaseDate = ""
                    var plot = ""
                    var cast = ""
                    var director = ""
                    var youtube = ""
                    var tvArchive = 0
                    var tvArchiveDuration = 0
                    var isAdult = 0

                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "stream_id", "series_id" -> streamId = readStringSafe(reader)
                            "num" -> num = readStringSafe(reader).toIntOrNull() ?: index
                            "name", "title" -> if (name.isEmpty()) name = readStringSafe(reader) else reader.skipValue()
                            "stream_icon", "cover" -> icon = readStringSafe(reader)
                            "backdrop_path" -> backdrop = readFirstArrayOrString(reader)
                            "epg_channel_id" -> epgChannelId = readStringSafe(reader)
                            "category_id" -> catId = readStringSafe(reader).ifBlank { "general" }
                            "container_extension" -> ext = readStringSafe(reader).ifBlank { "mp4" }
                            "rating", "rating_5based" -> {
                                val r = readStringSafe(reader).toDoubleOrNull() ?: 0.0
                                if (r > rating) rating = if (r <= 5.0 && r > 0.0) r * 2.0 else r
                            }
                            "releaseDate", "year" -> releaseDate = readStringSafe(reader).take(4)
                            "plot" -> plot = readStringSafe(reader)
                            "cast" -> cast = readStringSafe(reader)
                            "director" -> director = readStringSafe(reader)
                            "youtube_trailer" -> youtube = readStringSafe(reader)
                            "tv_archive" -> tvArchive = readStringSafe(reader).toIntOrNull() ?: 0
                            "tv_archive_duration" -> tvArchiveDuration = readStringSafe(reader).toIntOrNull() ?: 0
                            "is_adult" -> isAdult = readStringSafe(reader).toIntOrNull() ?: 0
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()

                    if (streamId.isNotBlank() && name.isNotBlank()) {
                        val catName = categoryNamesById[catId] ?: "General"
                        val streamUrl = when (streamType) {
                            StreamType.LIVE -> "$base/live/${enc(username)}/${enc(password)}/$streamId.m3u8"
                            StreamType.VOD -> "$base/movie/${enc(username)}/${enc(password)}/$streamId.$ext"
                            StreamType.SERIES -> "$base/series/${enc(username)}/${enc(password)}/$streamId"
                        }
                        val streamKey = "$playlistId:${streamType.name}:$streamId"
                        batch.add(
                            StreamEntity(
                                streamKey = streamKey,
                                playlistId = playlistId,
                                providerStreamId = streamId,
                                channelNumber = if (streamType == StreamType.LIVE) num else 0,
                                name = name,
                                streamType = streamType,
                                categoryId = catId,
                                categoryName = catName,
                                streamUrl = streamUrl,
                                logoUrl = icon,
                                backdropUrl = backdrop.ifBlank { icon },
                                tvgId = epgChannelId.ifBlank { streamId },
                                catchupDays = if (tvArchive == 1) tvArchiveDuration.coerceAtLeast(1) else 0,
                                rating = rating,
                                releaseYear = releaseDate,
                                qualityBadge = M3uStreamingParser.inferQualityBadge(name, ext),
                                synopsis = plot,
                                director = director,
                                cast = cast,
                                youtubeTrailerId = youtube,
                                containerExtension = ext,
                                isAdult = isAdult == 1 || M3uStreamingParser.isAdultCategoryName(catName),
                                isFavorite = favoriteKeys.contains(streamKey)
                            )
                        )
                        total++
                        index++
                        if (batch.size >= 500) {
                            onBatch(batch.toList())
                            batch.clear()
                        }
                    }
                }
                reader.endArray()
            }
        }

        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
        }
        total
    }

    suspend fun fetchXtreamSeriesEpisodes(
        serverUrl: String,
        username: String,
        password: String,
        userAgent: String,
        playlistId: Long,
        seriesStreamKey: String,
        providerSeriesId: String
    ): List<EpisodeEntity> = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(serverUrl)
        val url = "$base/player_api.php?username=${enc(username)}&password=${enc(password)}&action=get_series_info&series_id=${enc(providerSeriesId)}"
        val raw = runCatching { executeGetString(url, userAgent) }.getOrNull() ?: return@withContext emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return@withContext emptyList()
        val episodesObj = root.optJSONObject("episodes") ?: return@withContext emptyList()
        val result = ArrayList<EpisodeEntity>()

        val seasonKeys = episodesObj.keys()
        while (seasonKeys.hasNext()) {
            val seasonKey = seasonKeys.next()
            val seasonNum = seasonKey.toIntOrNull() ?: 1
            val epArray = episodesObj.optJSONArray(seasonKey) ?: continue
            for (i in 0 until epArray.length()) {
                val ep = epArray.optJSONObject(i) ?: continue
                val epId = ep.optString("id", "")
                val epNum = ep.optInt("episode_num", i + 1)
                val title = ep.optString("title", "Episode $epNum")
                val ext = ep.optString("container_extension", "mp4").ifBlank { "mp4" }
                val info = ep.optJSONObject("info")
                val thumb = info?.optString("movie_image", "").orEmpty()
                val plot = info?.optString("plot", "").orEmpty()
                val duration = info?.optString("duration", "45m").orEmpty().ifBlank { "45m" }
                val rating = info?.optDouble("rating", 8.4)?.takeIf { !it.isNaN() } ?: 8.4

                if (epId.isNotBlank()) {
                    val epUrl = "$base/series/${enc(username)}/${enc(password)}/$epId.$ext"
                    result.add(
                        EpisodeEntity(
                            id = "$seriesStreamKey:S$seasonNum:E$epNum",
                            playlistId = playlistId,
                            seriesStreamKey = seriesStreamKey,
                            providerSeriesId = providerSeriesId,
                            seasonNumber = seasonNum,
                            episodeNumber = epNum,
                            title = title,
                            streamUrl = epUrl,
                            thumbnailUrl = thumb,
                            durationLabel = duration,
                            synopsis = plot,
                            rating = rating
                        )
                    )
                }
            }
        }
        result.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
    }

    suspend fun fetchXtreamShortEpg(
        serverUrl: String,
        username: String,
        password: String,
        userAgent: String,
        playlistId: Long,
        providerStreamId: String,
        tvgId: String
    ): List<EpgProgramEntity> = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(serverUrl)
        val url = "$base/player_api.php?username=${enc(username)}&password=${enc(password)}&action=get_short_epg&stream_id=${enc(providerStreamId)}&limit=6"
        val raw = runCatching { executeGetString(url, userAgent) }.getOrNull() ?: return@withContext emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return@withContext emptyList()
        val listings = root.optJSONArray("epg_listings") ?: return@withContext emptyList()
        val out = ArrayList<EpgProgramEntity>()
        for (i in 0 until listings.length()) {
            val obj = listings.optJSONObject(i) ?: continue
            val rawTitle = obj.optString("title", "")
            val rawDesc = obj.optString("description", "")
            val decodedTitle = decodeBase64IfLikely(rawTitle)
            val decodedDesc = decodeBase64IfLikely(rawDesc)
            val startSec = obj.optString("start_timestamp", "0").toLongOrNull() ?: 0L
            val stopSec = obj.optString("stop_timestamp", "0").toLongOrNull() ?: 0L
            if (decodedTitle.isNotBlank() && startSec > 0 && stopSec > startSec) {
                out.add(
                    EpgProgramEntity(
                        playlistId = playlistId,
                        tvgId = tvgId,
                        title = decodedTitle,
                        description = decodedDesc,
                        startTimeMillis = startSec * 1000L,
                        endTimeMillis = stopSec * 1000L,
                        hasArchive = obj.optInt("has_archive", 0) == 1
                    )
                )
            }
        }
        out
    }

    fun buildXtreamTimeshiftUrl(
        serverUrl: String,
        username: String,
        password: String,
        providerStreamId: String,
        startMillis: Long,
        durationMinutes: Int = 60
    ): String {
        val base = normalizeBaseUrl(serverUrl)
        val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startFormatted = fmt.format(Date(startMillis))
        return "$base/timeshift/${enc(username)}/${enc(password)}/$durationMinutes/$startFormatted/$providerStreamId.ts"
    }

    // Stalker / Ministra MAC Portal STB Handshake
    fun generateVirtualMac(): String {
        val bytes = ByteArray(3)
        Random.Default.nextBytes(bytes)
        return "00:1A:79:%02X:%02X:%02X".format(
            Locale.US,
            bytes[0].toInt() and 0xFF,
            bytes[1].toInt() and 0xFF,
            bytes[2].toInt() and 0xFF
        )
    }

    suspend fun handshakeAndFetchStalkerPortal(
        portalUrl: String,
        macAddress: String,
        userAgent: String,
        playlistId: Long,
        favoriteKeys: Set<String>
    ): Pair<List<CategoryEntity>, List<StreamEntity>> = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(portalUrl).removeSuffix("/c")
        val encodedMac = enc(macAddress.uppercase(Locale.US))
        val cookieHeader = "mac=$encodedMac; stb_lang=en; timezone=UTC"
        val portalEndpoints = listOf("$base/portal.php", "$base/server/load.php")

        var token = ""
        var activeEndpoint = portalEndpoints.first()
        var lastError: Exception? = null

        for (endpoint in portalEndpoints) {
            try {
                val req = Request.Builder()
                    .url("$endpoint?type=stb&action=handshake&token=&JsHttpRequest=1-xml")
                    .header("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3 ($userAgent)")
                    .header("Cookie", cookieHeader)
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        token = json.optJSONObject("js")?.optString("token").orEmpty()
                        if (token.isNotBlank()) {
                            activeEndpoint = endpoint
                        }
                    }
                }
                if (token.isNotBlank()) break
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (token.isBlank()) {
            throw IllegalStateException(
                "Stalker STB Handshake failed at $base (${lastError?.message ?: "no token returned"})"
            )
        }

        // Trigger get_profile to activate STB session
        val profileReq = Request.Builder()
            .url("$activeEndpoint?type=stb&action=get_profile&JsHttpRequest=1-xml")
            .header("Cookie", cookieHeader)
            .header("Authorization", "Bearer $token")
            .build()
        runCatching { okHttpClient.newCall(profileReq).execute().close() }

        // Fetch all channels
        val chReq = Request.Builder()
            .url("$activeEndpoint?type=itv&action=get_all_channels&JsHttpRequest=1-xml")
            .header("Cookie", cookieHeader)
            .header("Authorization", "Bearer $token")
            .build()

        val categories = LinkedHashMap<String, CategoryEntity>()
        val streams = ArrayList<StreamEntity>()

        okHttpClient.newCall(chReq).execute().use { resp ->
            val root = JSONObject(resp.body?.string().orEmpty())
            val dataArr = root.optJSONObject("js")?.optJSONArray("data")
                ?: throw IllegalStateException("Stalker portal returned empty channel table")

            for (i in 0 until dataArr.length()) {
                val item = dataArr.optJSONObject(i) ?: continue
                val id = item.optString("id", "${i + 1}")
                val name = item.optString("name", "Channel ${i + 1}")
                val cmd = item.optString("cmd", "").replace("ffmpeg ", "").replace("auto ", "").trim()
                val genreId = item.optString("tv_genre_id", "stalker_tv")
                val logo = item.optString("logo", "")
                val number = item.optInt("number", i + 1)

                if (cmd.startsWith("http")) {
                    val catCompId = "$playlistId:LIVE:$genreId"
                    if (!categories.containsKey(catCompId)) {
                        categories[catCompId] = CategoryEntity(
                            id = catCompId,
                            playlistId = playlistId,
                            categoryId = genreId,
                            name = "Portal Group $genreId",
                            streamType = StreamType.LIVE,
                            sortOrder = categories.size
                        )
                    }
                    val streamKey = "$playlistId:LIVE:stk_$id"
                    streams.add(
                        StreamEntity(
                            streamKey = streamKey,
                            playlistId = playlistId,
                            providerStreamId = "stk_$id",
                            channelNumber = number,
                            name = name,
                            streamType = StreamType.LIVE,
                            categoryId = genreId,
                            categoryName = "Portal Group $genreId",
                            streamUrl = cmd,
                            logoUrl = logo,
                            tvgId = item.optString("xmltv_id", id),
                            isFavorite = favoriteKeys.contains(streamKey)
                        )
                    )
                }
            }
        }
        categories.values.toList() to streams
    }

    // Built-in Network Speed & Latency Throughput Tester
    suspend fun runNetworkSpeedTest(
        userAgent: String,
        onProgress: (Float, Double) -> Unit
    ): SpeedTestResult = withContext(Dispatchers.IO) {
        val pingSamples = ArrayList<Long>()
        val probeUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg"

        repeat(3) {
            val start = System.nanoTime()
            val req = Request.Builder().url(probeUrl).head().header("User-Agent", userAgent).build()
            runCatching {
                okHttpClient.newCall(req).execute().close()
                val elapsed = (System.nanoTime() - start) / 1_000_000L
                pingSamples.add(elapsed.coerceAtLeast(1L))
            }
        }

        val avgLatency = if (pingSamples.isNotEmpty()) pingSamples.average().toLong() else 45L
        val jitter = if (pingSamples.size >= 2) {
            (pingSamples.max() - pingSamples.min()).coerceAtLeast(1L)
        } else 4L

        val payloadUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
        val startNs = System.nanoTime()
        var totalBytes = 0L
        val maxTargetBytes = 6L * 1024L * 1024L // Up to 6 MB burst sample

        val dlReq = Request.Builder().url(payloadUrl).header("User-Agent", userAgent).build()
        okHttpClient.newCall(dlReq).execute().use { resp ->
            val stream = resp.body?.byteStream() ?: throw IllegalStateException("Speed test stream unavailable")
            val buffer = ByteArray(32 * 1024)
            var read = stream.read(buffer)
            while (read != -1 && totalBytes < maxTargetBytes) {
                totalBytes += read
                val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                val currentMbps = (totalBytes * 8.0) / (elapsedSec * 1_000_000.0)
                val progress = (totalBytes.toFloat() / maxTargetBytes.toFloat()).coerceIn(0.05f, 1f)
                onProgress(progress, currentMbps)
                if (elapsedSec > 4.5) break
                read = stream.read(buffer)
            }
        }

        val finalElapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.05)
        val mbps = (totalBytes * 8.0) / (finalElapsedSec * 1_000_000.0)
        val verdict = when {
            mbps >= 35.0 -> "4K UHD + 4-Way Multi-Screen Ready"
            mbps >= 15.0 -> "4K UHD Single Stream & 1080p Multi-Screen Ready"
            mbps >= 6.0 -> "1080p FHD Live Broadcast Ready"
            else -> "720p HD Recommended (Increase Stream Buffer to 25s+)"
        }
        SpeedTestResult(
            latencyMs = avgLatency,
            jitterMs = jitter,
            downloadMbps = mbps,
            bytesTransferred = totalBytes,
            qualityVerdict = verdict
        )
    }

    private fun executeGetString(url: String, userAgent: String): String {
        val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code} from server")
            }
            return resp.body?.string() ?: throw IllegalStateException("Empty server response")
        }
    }

    private fun readStringSafe(reader: JsonReader): String {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                ""
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean().toString()
            else -> {
                reader.skipValue()
                ""
            }
        }
    }

    private fun readFirstArrayOrString(reader: JsonReader): String {
        return when (reader.peek()) {
            JsonToken.STRING -> reader.nextString()
            JsonToken.BEGIN_ARRAY -> {
                var first = ""
                reader.beginArray()
                while (reader.hasNext()) {
                    val v = readStringSafe(reader)
                    if (first.isEmpty() && v.isNotBlank()) first = v
                }
                reader.endArray()
                first
            }
            else -> {
                reader.skipValue()
                ""
            }
        }
    }

    private fun decodeBase64IfLikely(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching {
            val bytes = Base64.decode(raw, Base64.DEFAULT)
            val str = String(bytes, Charsets.UTF_8)
            if (str.any { it.isLetterOrDigit() }) str else raw
        }.getOrDefault(raw)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
