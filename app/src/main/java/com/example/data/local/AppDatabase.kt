package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class IptvTypeConverters {
    @TypeConverter
    fun fromPlaylistType(type: PlaylistType): String = type.name

    @TypeConverter
    fun toPlaylistType(value: String): PlaylistType =
        runCatching { PlaylistType.valueOf(value) }.getOrDefault(PlaylistType.M3U)

    @TypeConverter
    fun fromStreamType(type: StreamType): String = type.name

    @TypeConverter
    fun toStreamType(value: String): StreamType =
        runCatching { StreamType.valueOf(value) }.getOrDefault(StreamType.LIVE)
}

@Database(
    entities = [
        PlaylistEntity::class,
        CategoryEntity::class,
        StreamEntity::class,
        StreamFtsEntity::class,
        EpisodeEntity::class,
        EpgProgramEntity::class,
        PlaybackProgressEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(IptvTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun iptvDao(): IptvDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "novastream_iptv.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
