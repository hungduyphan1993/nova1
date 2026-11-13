package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.data.local.CategoryEntity
import com.example.data.local.EpgProgramEntity
import com.example.data.local.EpisodeEntity
import com.example.data.local.IptvDao
import com.example.data.local.PlaybackProgressEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistType
import com.example.data.local.StreamEntity
import com.example.data.local.StreamType
import com.example.data.network.M3uStreamingParser
import com.example.data.network.SpeedTestResult
import com.example.data.network.XmlTvStreamingParser
import com.example.data.network.XtreamAndStalkerClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class IptvRepository(
    private val appContext: Context,
    private val dao: IptvDao
) {
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val m3uParser = M3uStreamingParser(okHttpClient)
    private val xmlTvParser = XmlTvStreamingParser(okHttpClient)
    val networkClients = XtreamAndStalkerClients(okHttpClient)

    val playlistsFlow: Flow<List<PlaylistEntity>> = dao.observePlaylists()
    val activePlaylistFlow: Flow<PlaylistEntity?> = dao.observeActivePlaylist()

    fun observeCategories(playlistId: Long, type: StreamType): Flow<List<CategoryEntity>> =
        dao.observeCategories(playlistId, type)

    fun observeStreams(playlistId: Long, type: StreamType): Flow<List<StreamEntity>> =
        dao.observeStreamsByType(playlistId, type)

    fun pagingStreams(playlistId: Long, type: StreamType, categoryId: String?): Flow<PagingData<StreamEntity>> {
        return Pager(
            config = PagingConfig(pageSize = 60, prefetchDistance = 20, enablePlaceholders = false),
            pagingSourceFactory = {
                if (categoryId.isNullOrBlank() || categoryId == "ALL") {
                    dao.pagingStreamsByType(playlistId, type)
                } else {
                    dao.pagingStreamsByCategory(playlistId, type, categoryId)
                }
            }
        ).flow
    }

    fun observeFavorites(playlistId: Long): Flow<List<StreamEntity>> = dao.observeFavorites(playlistId)

    fun observeContinueWatching(playlistId: Long): Flow<List<PlaybackProgressEntity>> =
        dao.observeContinueWatching(playlistId)

    fun observeStreamByKey(streamKey: String): Flow<StreamEntity?> = dao.observeStreamByKey(streamKey)

    suspend fun getStreamByKeyOnce(streamKey: String): StreamEntity? = dao.getStreamByKeyOnce(streamKey)

    fun observeCountByType(playlistId: Long, type: StreamType): Flow<Int> =
        dao.observeCountByType(playlistId, type)

    fun observeTotalEpgCount(playlistId: Long): Flow<Int> = dao.observeTotalEpgCount(playlistId)

    fun observeEpgWindow(playlistId: Long, fromMillis: Long, toMillis: Long): Flow<List<EpgProgramEntity>> =
        dao.observeEpgWindow(playlistId, fromMillis, toMillis)

    fun observeChannelShortEpg(playlistId: Long, tvgId: String, nowMillis: Long): Flow<List<EpgProgramEntity>> =
        dao.observeChannelShortEpg(playlistId, tvgId, nowMillis, limit = 6)

    fun observeEpisodes(seriesStreamKey: String): Flow<List<EpisodeEntity>> =
        dao.observeEpisodesForSeries(seriesStreamKey)

    fun searchInstantFts(playlistId: Long, rawQuery: String): Flow<List<StreamEntity>> {
        val tokens = rawQuery.trim()
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}_]"), "") }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyFlow()
        val ftsQuery = tokens.joinToString(" ") { "$it*" }
        return dao.searchStreamsFts(playlistId, ftsQuery)
    }

    /**
     * Self-Healing Startup Verification:
     * Ensures the active playlist (or initial Open-Source Broadcast Showcase) has populated
     * Live TV, VOD Movies, Multi-Season Series, and fresh 24-hour EPG listings.
     */
    suspend fun ensureSelfHealingCatalog() = withContext(Dispatchers.IO) {
        val active = dao.getActivePlaylistOnce()
        if (active == null) {
            seedOrRefreshShowcasePlaylist(makeActive = true)
            return@withContext
        }
        if (active.type == PlaylistType.SHOWCASE) {
            val liveCount = dao.countStreamsByType(active.id, StreamType.LIVE)
            val vodCount = dao.countStreamsByType(active.id, StreamType.VOD)
            val seriesCount = dao.countStreamsByType(active.id, StreamType.SERIES)
            val activeEpg = dao.countActiveEpgPrograms(active.id, System.currentTimeMillis())
            if (liveCount < 10 || vodCount < 10 || seriesCount < 6 || activeEpg < 24) {
                seedOrRefreshShowcasePlaylist(makeActive = true)
            }
        }
    }

    suspend fun seedOrRefreshShowcasePlaylist(makeActive: Boolean = true): Long = withContext(Dispatchers.IO) {
        val existing = dao.getPlaylistByTypeOnce(PlaylistType.SHOWCASE)
        val playlistId = if (existing != null) {
            val updated = ShowcaseCatalogSeeder.createShowcasePlaylist(existing.id)
                .copy(isActive = if (makeActive) true else existing.isActive)
            dao.insertPlaylist(updated)
            existing.id
        } else {
            dao.insertPlaylist(ShowcaseCatalogSeeder.createShowcasePlaylist())
        }

        if (makeActive) {
            dao.setActivePlaylist(playlistId)
        }

        val favKeys = dao.getFavoriteStreamKeys(playlistId).toSet()
        val categories = ShowcaseCatalogSeeder.buildCategories(playlistId)
        val liveChannels = ShowcaseCatalogSeeder.buildLiveChannels(playlistId, favKeys)
        val vodMovies = ShowcaseCatalogSeeder.buildVodMovies(playlistId, favKeys)
        val (seriesList, episodes) = ShowcaseCatalogSeeder.buildSeriesAndEpisodes(playlistId, favKeys)
        val epgPrograms = ShowcaseCatalogSeeder.build24HourEpg(playlistId, liveChannels)

        dao.clearAllCategoriesForPlaylist(playlistId)
        dao.insertCategories(categories)

        dao.clearAllStreamsForPlaylist(playlistId)
        dao.insertStreamsInBatches(liveChannels + vodMovies + seriesList, batchSize = 500)

        dao.clearEpisodesForPlaylist(playlistId)
        dao.insertEpisodes(episodes)

        dao.replaceEpgInBatches(playlistId, epgPrograms, batchSize = 500)
        playlistId
    }

    suspend fun addAndSyncXtreamPlaylist(
        profileName: String,
        serverUrl: String,
        username: String,
        password: String,
        userAgent: String,
        onStatus: (String) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        onStatus("Authenticating with Xtream /player_api.php…")
        val account = networkClients.authenticateXtream(serverUrl, username, password, userAgent)
        val baseUrl = networkClients.normalizeBaseUrl(serverUrl)
        val xmlTvUrl = "$baseUrl/xmltv.php?username=$username&password=$password"

        val playlistId = dao.insertPlaylist(
            PlaylistEntity(
                name = profileName.ifBlank { "Xtream • $username" },
                type = PlaylistType.XTREAM,
                serverUrl = baseUrl,
                username = username,
                password = password,
                epgUrl = xmlTvUrl,
                accountStatus = account.status,
                expirationTimestampSec = account.expDateSec,
                activeConnections = account.activeConnections,
                maxConnections = account.maxConnections,
                isActive = true
            )
        )
        dao.setActivePlaylist(playlistId)
        syncPlaylistContent(playlistId, userAgent, onStatus)
        playlistId
    }

    suspend fun addAndSyncM3uUrlPlaylist(
        profileName: String,
        m3uUrl: String,
        epgUrl: String,
        userAgent: String,
        onStatus: (String) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        val playlistId = dao.insertPlaylist(
            PlaylistEntity(
                name = profileName.ifBlank { "M3U Playlist" },
                type = PlaylistType.M3U,
                m3uUrl = m3uUrl.trim(),
                epgUrl = epgUrl.trim(),
                accountStatus = "Active • M3U",
                isActive = true
            )
        )
        dao.setActivePlaylist(playlistId)
        syncPlaylistContent(playlistId, userAgent, onStatus)
        playlistId
    }

    suspend fun addAndSyncLocalM3uFile(
        profileName: String,
        fileUri: Uri,
        epgUrl: String,
        userAgent: String,
        onStatus: (String) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        onStatus("Streaming local M3U file in 500-item batches…")
        val playlistId = dao.insertPlaylist(
            PlaylistEntity(
                name = profileName.ifBlank { "Local M3U File" },
                type = PlaylistType.M3U,
                m3uUrl = fileUri.toString(),
                epgUrl = epgUrl.trim(),
                accountStatus = "Active • Local File",
                isActive = true
            )
        )
        dao.setActivePlaylist(playlistId)
        val favKeys = dao.getFavoriteStreamKeys(playlistId).toSet()
        dao.clearAllCategoriesForPlaylist(playlistId)
        dao.clearAllStreamsForPlaylist(playlistId)

        appContext.contentResolver.openInputStream(fileUri)?.use { input ->
            m3uParser.parseFromInputStream(input, playlistId, favKeys) { cats, batch ->
                dao.insertCategories(cats)
                dao.insertStreamsInBatches(batch, batchSize = 500)
            }
        } ?: throw IllegalStateException("Cannot open selected M3U document")

        if (epgUrl.isNotBlank()) {
            onStatus("Syncing XMLTV EPG guide…")
            syncXmlTvForPlaylist(playlistId, epgUrl, userAgent)
        }
        playlistId
    }

    suspend fun addAndSyncStalkerPlaylist(
        profileName: String,
        portalUrl: String,
        macAddress: String,
        userAgent: String,
        onStatus: (String) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        onStatus("Performing Stalker STB MAC Handshake ($macAddress)…")
        val playlistId = dao.insertPlaylist(
            PlaylistEntity(
                name = profileName.ifBlank { "Stalker • $macAddress" },
                type = PlaylistType.STALKER,
                serverUrl = networkClients.normalizeBaseUrl(portalUrl),
                macAddress = macAddress.uppercase(),
                accountStatus = "Connected • MAG STB",
                isActive = true
            )
        )
        dao.setActivePlaylist(playlistId)
        syncPlaylistContent(playlistId, userAgent, onStatus)
        playlistId
    }

    suspend fun syncPlaylistContent(
        playlistId: Long,
        userAgent: String,
        onStatus: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val playlist = dao.getActivePlaylistOnce()?.takeIf { it.id == playlistId }
            ?: return@withContext
        val favKeys = dao.getFavoriteStreamKeys(playlistId).toSet()

        when (playlist.type) {
            PlaylistType.SHOWCASE -> {
                onStatus("Refreshing Open-Source Broadcast Showcase & 24h EPG…")
                seedOrRefreshShowcasePlaylist(makeActive = true)
            }
            PlaylistType.XTREAM -> {
                onStatus("Verifying Xtream account & fetching categories…")
                val account = networkClients.authenticateXtream(
                    playlist.serverUrl, playlist.username, playlist.password, userAgent
                )
                val liveCats = networkClients.fetchXtreamCategories(
                    playlist.serverUrl, playlist.username, playlist.password, userAgent, playlistId, StreamType.LIVE
                )
                val vodCats = networkClients.fetchXtreamCategories(
                    playlist.serverUrl, playlist.username, playlist.password, userAgent, playlistId, StreamType.VOD
                )
                val seriesCats = networkClients.fetchXtreamCategories(
                    playlist.serverUrl, playlist.username, playlist.password, userAgent, playlistId, StreamType.SERIES
                )
                dao.clearAllCategoriesForPlaylist(playlistId)
                dao.insertCategories(liveCats + vodCats + seriesCats)
                dao.clearAllStreamsForPlaylist(playlistId)

                onStatus("Streaming Live TV channels (500-item Room batches)…")
                networkClients.streamXtreamCatalog(
                    playlist.serverUrl, playlist.username, playlist.password, userAgent,
                    playlistId, StreamType.LIVE, liveCats.associate { it.categoryId to it.name }, favKeys
                ) { batch -> dao.insertStreamsInBatches(batch, 500) }

                onStatus("Streaming VOD Movies…")
                networkClients.streamXtreamCatalog(
                    playlist.serverUrl, playlist.username, playlist.password, userAgent,
                    playlistId, StreamType.VOD, vodCats.associate { it.categoryId to it.name }, favKeys
                ) { batch -> dao.insertStreamsInBatches(batch, 500) }

                onStatus("Streaming TV Series…")
                networkClients.streamXtreamCatalog(
                    playlist.serverUrl, playlist.username, playlist.password, userAgent,
                    playlistId, StreamType.SERIES, seriesCats.associate { it.categoryId to it.name }, favKeys
                ) { batch -> dao.insertStreamsInBatches(batch, 500) }

                if (playlist.epgUrl.isNotBlank()) {
                    onStatus("Syncing XMLTV Electronic Program Guide…")
                    runCatching { syncXmlTvForPlaylist(playlistId, playlist.epgUrl, userAgent) }
                }

                dao.updatePlaylistSyncMeta(
                    playlistId = playlistId,
                    timestamp = System.currentTimeMillis(),
                    status = account.status,
                    activeCons = account.activeConnections,
                    maxCons = account.maxConnections,
                    expSec = account.expDateSec
                )
            }
            PlaylistType.M3U -> {
                onStatus("Streaming M3U playlist line-by-line…")
                dao.clearAllCategoriesForPlaylist(playlistId)
                dao.clearAllStreamsForPlaylist(playlistId)
                if (playlist.m3uUrl.startsWith("content://")) {
                    appContext.contentResolver.openInputStream(Uri.parse(playlist.m3uUrl))?.use { stream ->
                        m3uParser.parseFromInputStream(stream, playlistId, favKeys) { cats, batch ->
                            dao.insertCategories(cats)
                            dao.insertStreamsInBatches(batch, 500)
                        }
                    }
                } else {
                    m3uParser.parseFromUrl(playlist.m3uUrl, userAgent, playlistId, favKeys) { cats, batch ->
                        dao.insertCategories(cats)
                        dao.insertStreamsInBatches(batch, 500)
                    }
                }
                if (playlist.epgUrl.isNotBlank()) {
                    onStatus("Syncing XMLTV guide…")
                    runCatching { syncXmlTvForPlaylist(playlistId, playlist.epgUrl, userAgent) }
                }
            }
            PlaylistType.STALKER -> {
                onStatus("Handshaking with Stalker MAC portal…")
                val (cats, streams) = networkClients.handshakeAndFetchStalkerPortal(
                    playlist.serverUrl, playlist.macAddress, userAgent, playlistId, favKeys
                )
                dao.clearAllCategoriesForPlaylist(playlistId)
                dao.insertCategories(cats)
                dao.clearAllStreamsForPlaylist(playlistId)
                dao.insertStreamsInBatches(streams, 500)
            }
        }
    }

    suspend fun syncXmlTvForPlaylist(playlistId: Long, xmlTvUrl: String, userAgent: String) {
        dao.clearEpgForPlaylist(playlistId)
        xmlTvParser.syncXmlTvUrl(xmlTvUrl, userAgent, playlistId) { batch ->
            dao.insertEpgChunk(batch)
        }
    }

    /**
     * Ensures opening ANY Series displays playable seasons & episodes:
     * Queries live Xtream get_series_info if playlist is XTREAM, or synthesizes playable fallback
     * episode entries for M3U playlists when missing.
     */
    suspend fun ensureSeriesEpisodesLoaded(series: StreamEntity, userAgent: String) = withContext(Dispatchers.IO) {
        val existingCount = dao.countEpisodesForSeries(series.streamKey)
        if (existingCount > 0) return@withContext

        val active = dao.getActivePlaylistOnce()
        if (active != null && active.type == PlaylistType.XTREAM) {
            val remote = networkClients.fetchXtreamSeriesEpisodes(
                serverUrl = active.serverUrl,
                username = active.username,
                password = active.password,
                userAgent = userAgent,
                playlistId = series.playlistId,
                seriesStreamKey = series.streamKey,
                providerSeriesId = series.providerStreamId
            )
            if (remote.isNotEmpty()) {
                dao.insertEpisodes(remote)
                return@withContext
            }
        }

        // Automatic playable episode fallback for M3U / direct series streams
        val fallbackEpisodes = (1..2).flatMap { season ->
            (1..3).map { ep ->
                EpisodeEntity(
                    id = "${series.streamKey}:S$season:E$ep",
                    playlistId = series.playlistId,
                    seriesStreamKey = series.streamKey,
                    providerSeriesId = series.providerStreamId,
                    seasonNumber = season,
                    episodeNumber = ep,
                    title = "S${season}E${ep} • ${series.name} Episode $ep",
                    streamUrl = series.streamUrl,
                    thumbnailUrl = series.backdropUrl.ifBlank { series.logoUrl },
                    durationLabel = "45m",
                    synopsis = series.synopsis.ifBlank { "Playable stream from ${series.categoryName}." },
                    rating = series.rating.takeIf { it > 0.0 } ?: 8.5
                )
            }
        }
        dao.insertEpisodes(fallbackEpisodes)
    }

    suspend fun refreshShortEpgIfNeeded(stream: StreamEntity, userAgent: String) = withContext(Dispatchers.IO) {
        val active = dao.getActivePlaylistOnce() ?: return@withContext
        if (active.type == PlaylistType.XTREAM) {
            val shortList = networkClients.fetchXtreamShortEpg(
                serverUrl = active.serverUrl,
                username = active.username,
                password = active.password,
                userAgent = userAgent,
                playlistId = stream.playlistId,
                providerStreamId = stream.providerStreamId,
                tvgId = stream.tvgId
            )
            if (shortList.isNotEmpty()) {
                dao.insertEpgChunk(shortList)
            }
        }
    }

    suspend fun switchActivePlaylist(playlistId: Long) {
        dao.setActivePlaylist(playlistId)
        ensureSelfHealingCatalog()
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.clearAllStreamsForPlaylist(playlistId)
        dao.clearAllCategoriesForPlaylist(playlistId)
        dao.clearEpgForPlaylist(playlistId)
        dao.clearEpisodesForPlaylist(playlistId)
        dao.clearProgressForPlaylist(playlistId)
        dao.deletePlaylist(playlistId)
        if (dao.getActivePlaylistOnce() == null) {
            val remaining = dao.getPlaylistByTypeOnce(PlaylistType.SHOWCASE)
            if (remaining != null) dao.setActivePlaylist(remaining.id)
        }
    }

    suspend fun toggleFavorite(streamKey: String, currentFavorite: Boolean) {
        dao.setFavorite(streamKey, !currentFavorite)
    }

    suspend fun setCategoryAdultLock(compositeId: String, locked: Boolean) {
        dao.setCategoryLocked(compositeId, locked)
    }

    suspend fun savePlaybackProgress(
        streamKey: String,
        playlistId: Long,
        streamType: StreamType,
        title: String,
        subtitle: String,
        posterUrl: String,
        streamUrl: String,
        positionMs: Long,
        durationMs: Long,
        engineName: String
    ) {
        dao.upsertPlaybackProgress(
            PlaybackProgressEntity(
                streamKey = streamKey,
                playlistId = playlistId,
                streamType = streamType,
                title = title,
                subtitle = subtitle,
                posterUrl = posterUrl,
                streamUrl = streamUrl,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                lastEngine = engineName,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun runSpeedTest(userAgent: String, onProgress: (Float, Double) -> Unit): SpeedTestResult =
        networkClients.runNetworkSpeedTest(userAgent, onProgress)
}
