package com.musiclib.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus =
        runCatching { DownloadStatus.valueOf(value) }.getOrDefault(DownloadStatus.QUEUED)
}
