package com.example.data.local

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PlaylistType(val label: String) {
    XTREAM("Xtream Codes API"),
    M3U("M3U / M3U8 Playlist"),
    STALKER("Stalker MAC Portal"),
    SHOWCASE("Open-Source Broadcast")
}

enum class StreamType {
    LIVE, VOD, SERIES
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PlaylistType,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    val epgUrl: String = "",
    val macAddress: String = "",
    val accountStatus: String = "Active",
    val expirationTimestampSec: Long? = null,
    val activeConnections: Int = 1,
    val maxConnections: Int = 4,
    val isActive: Boolean = false,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["playlistId", "streamType"])]
)
data class CategoryEntity(
    @PrimaryKey val id: String, // "$playlistId:$streamType:$categoryId"
    val playlistId: Long,
    val categoryId: String,
    val name: String,
    val streamType: StreamType,
    val isAdultLocked: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "streams",
    indices = [
        Index(value = ["streamKey"], unique = true),
        Index(value = ["playlistId", "streamType", "categoryId"]),
        Index(value = ["playlistId", "isFavorite"]),
        Index(value = ["playlistId", "tvgId"])
    ]
)
data class StreamEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val streamKey: String, // "$playlistId:$streamType:$providerStreamId"
    val playlistId: Long,
    val providerStreamId: String,
    val channelNumber: Int = 0,
    val name: String,
    val streamType: StreamType,
    val categoryId: String,
    val categoryName: String,
    val streamUrl: String,
    val logoUrl: String = "",
    val backdropUrl: String = "",
    val tvgId: String = "",
    val tvgShift: Int = 0,
    val catchupDays: Int = 0,
    val catchupSource: String = "",
    val rating: Double = 0.0,
    val releaseYear: String = "",
    val qualityBadge: String = "1080p FHD",
    val synopsis: String = "",
    val director: String = "",
    val cast: String = "",
    val youtubeTrailerId: String = "",
    val containerExtension: String = "mp4",
    val isAdult: Boolean = false,
    val isFavorite: Boolean = false,
    val addedTimestamp: Long = System.currentTimeMillis()
)

@Fts4(contentEntity = StreamEntity::class)
@Entity(tableName = "streams_fts")
data class StreamFtsEntity(
    val name: String,
    val categoryName: String,
    val synopsis: String,
    val cast: String
)

@Entity(
    tableName = "episodes",
    indices = [Index(value = ["seriesStreamKey", "seasonNumber", "episodeNumber"])]
)
data class EpisodeEntity(
    @PrimaryKey val id: String, // "$seriesStreamKey:S$seasonNumber:E$episodeNumber"
    val playlistId: Long,
    val seriesStreamKey: String,
    val providerSeriesId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val streamUrl: String,
    val thumbnailUrl: String = "",
    val durationLabel: String = "42m",
    val synopsis: String = "",
    val rating: Double = 8.5
)

@Entity(
    tableName = "epg_programs",
    indices = [
        Index(value = ["playlistId", "tvgId", "startTimeMillis"]),
        Index(value = ["playlistId", "endTimeMillis"])
    ]
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val tvgId: String,
    val title: String,
    val description: String = "",
    val category: String = "Broadcast",
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val hasArchive: Boolean = false
)

@Entity(
    tableName = "playback_progress",
    indices = [Index(value = ["playlistId", "updatedAt"])]
)
data class PlaybackProgressEntity(
    @PrimaryKey val streamKey: String,
    val playlistId: Long,
    val streamType: StreamType,
    val title: String,
    val subtitle: String = "",
    val posterUrl: String = "",
    val streamUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastEngine: String = "EXOPLAYER",
    val updatedAt: Long = System.currentTimeMillis()
)
