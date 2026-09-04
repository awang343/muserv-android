package com.musiclib.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        DownloadEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MuservDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var instance: MuservDatabase? = null

        fun getInstance(context: Context): MuservDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MuservDatabase::class.java,
                    "muserv.db",
                ).build().also { instance = it }
            }
    }
}
