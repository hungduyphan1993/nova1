package com.example.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvDao {

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY isActive DESC, lastSyncedAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    fun observeActivePlaylist(): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePlaylistOnce(): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE type = :type LIMIT 1")
    suspend fun getPlaylistByTypeOnce(type: PlaylistType): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET isActive = CASE WHEN id = :playlistId THEN 1 ELSE 0 END")
    suspend fun setActivePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET lastSyncedAt = :timestamp, accountStatus = :status, activeConnections = :activeCons, maxConnections = :maxCons, expirationTimestampSec = :expSec WHERE id = :playlistId")
    suspend fun updatePlaylistSyncMeta(
        playlistId: Long,
        timestamp: Long,
        status: String,
        activeCons: Int,
        maxCons: Int,
        expSec: Long?
    )

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    // Categories
    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND streamType = :streamType ORDER BY sortOrder ASC, name ASC")
    fun observeCategories(playlistId: Long, streamType: StreamType): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("UPDATE categories SET isAdultLocked = :locked WHERE id = :categoryCompositeId")
    suspend fun setCategoryLocked(categoryCompositeId: String, locked: Boolean)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId AND streamType = :streamType")
    suspend fun clearCategoriesForType(playlistId: Long, streamType: StreamType)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun clearAllCategoriesForPlaylist(playlistId: Long)

    // Streams
    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND streamType = :streamType ORDER BY channelNumber ASC, addedTimestamp DESC, name ASC")
    fun observeStreamsByType(playlistId: Long, streamType: StreamType): Flow<List<StreamEntity>>

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND streamType = :streamType ORDER BY channelNumber ASC, name ASC")
    fun pagingStreamsByType(playlistId: Long, streamType: StreamType): PagingSource<Int, StreamEntity>

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND streamType = :streamType AND categoryId = :categoryId ORDER BY channelNumber ASC, name ASC")
    fun pagingStreamsByCategory(playlistId: Long, streamType: StreamType, categoryId: String): PagingSource<Int, StreamEntity>

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND isFavorite = 1 ORDER BY name ASC")
    fun observeFavorites(playlistId: Long): Flow<List<StreamEntity>>

    @Query("SELECT streamKey FROM streams WHERE playlistId = :playlistId AND isFavorite = 1")
    suspend fun getFavoriteStreamKeys(playlistId: Long): List<String>

    @Query("SELECT * FROM streams WHERE streamKey = :streamKey LIMIT 1")
    fun observeStreamByKey(streamKey: String): Flow<StreamEntity?>

    @Query("SELECT * FROM streams WHERE streamKey = :streamKey LIMIT 1")
    suspend fun getStreamByKeyOnce(streamKey: String): StreamEntity?

    @Query("SELECT COUNT(*) FROM streams WHERE playlistId = :playlistId AND streamType = :streamType")
    suspend fun countStreamsByType(playlistId: Long, streamType: StreamType): Int

    @Query("SELECT COUNT(*) FROM streams WHERE playlistId = :playlistId AND streamType = :streamType")
    fun observeCountByType(playlistId: Long, streamType: StreamType): Flow<Int>

    @Query("UPDATE streams SET isFavorite = :favorite WHERE streamKey = :streamKey")
    suspend fun setFavorite(streamKey: String, favorite: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreamsChunk(streams: List<StreamEntity>)

    @Query("DELETE FROM streams WHERE playlistId = :playlistId AND streamType = :streamType")
    suspend fun clearStreamsForType(playlistId: Long, streamType: StreamType)

    @Query("DELETE FROM streams WHERE playlistId = :playlistId")
    suspend fun clearAllStreamsForPlaylist(playlistId: Long)

    @Query("INSERT INTO streams_fts(streams_fts) VALUES('rebuild')")
    suspend fun rebuildFtsIndex()

    @Transaction
    suspend fun insertStreamsInBatches(streams: List<StreamEntity>, batchSize: Int = 500) {
        streams.chunked(batchSize).forEach { batch ->
            insertStreamsChunk(batch)
        }
        rebuildFtsIndex()
    }

    // FTS4 Instant Full-Text Search across tens of thousands of channels, VODs, and Series
    @Query(
        """
        SELECT streams.* FROM streams
        JOIN streams_fts ON streams.id = streams_fts.rowid
        WHERE streams.playlistId = :playlistId AND streams_fts MATCH :ftsQuery
        ORDER BY streams.isFavorite DESC, streams.streamType ASC, streams.name ASC
        LIMIT 150
        """
    )
    fun searchStreamsFts(playlistId: Long, ftsQuery: String): Flow<List<StreamEntity>>

    // Episodes
    @Query("SELECT * FROM episodes WHERE seriesStreamKey = :seriesStreamKey ORDER BY seasonNumber ASC, episodeNumber ASC")
    fun observeEpisodesForSeries(seriesStreamKey: String): Flow<List<EpisodeEntity>>

    @Query("SELECT COUNT(*) FROM episodes WHERE seriesStreamKey = :seriesStreamKey")
    suspend fun countEpisodesForSeries(seriesStreamKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("DELETE FROM episodes WHERE playlistId = :playlistId")
    suspend fun clearEpisodesForPlaylist(playlistId: Long)

    // EPG Programs
    @Query("SELECT * FROM epg_programs WHERE playlistId = :playlistId AND endTimeMillis > :fromMillis AND startTimeMillis < :toMillis ORDER BY startTimeMillis ASC")
    fun observeEpgWindow(playlistId: Long, fromMillis: Long, toMillis: Long): Flow<List<EpgProgramEntity>>

    @Query("SELECT * FROM epg_programs WHERE playlistId = :playlistId AND tvgId = :tvgId AND endTimeMillis > :nowMillis ORDER BY startTimeMillis ASC LIMIT :limit")
    fun observeChannelShortEpg(playlistId: Long, tvgId: String, nowMillis: Long, limit: Int = 6): Flow<List<EpgProgramEntity>>

    @Query("SELECT COUNT(*) FROM epg_programs WHERE playlistId = :playlistId AND endTimeMillis > :nowMillis")
    suspend fun countActiveEpgPrograms(playlistId: Long, nowMillis: Long): Int

    @Query("SELECT COUNT(*) FROM epg_programs WHERE playlistId = :playlistId")
    fun observeTotalEpgCount(playlistId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpgChunk(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE playlistId = :playlistId")
    suspend fun clearEpgForPlaylist(playlistId: Long)

    @Transaction
    suspend fun replaceEpgInBatches(playlistId: Long, programs: List<EpgProgramEntity>, batchSize: Int = 500) {
        clearEpgForPlaylist(playlistId)
        programs.chunked(batchSize).forEach { chunk ->
            insertEpgChunk(chunk)
        }
    }

    // Continue Watching / Playback Progress
    @Query("SELECT * FROM playback_progress WHERE playlistId = :playlistId ORDER BY updatedAt DESC LIMIT 20")
    fun observeContinueWatching(playlistId: Long): Flow<List<PlaybackProgressEntity>>

    @Query("SELECT * FROM playback_progress WHERE streamKey = :streamKey LIMIT 1")
    suspend fun getProgressOnce(streamKey: String): PlaybackProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaybackProgress(progress: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress WHERE playlistId = :playlistId")
    suspend fun clearProgressForPlaylist(playlistId: Long)
}
