package com.musiclib.data.db

import androidx.room.Entity
import androidx.room.Index

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
}

@Entity(
    tableName = "downloads",
    primaryKeys = ["libraryId", "trackId"],
    indices = [Index(value = ["hash"], unique = true)],
)
data class DownloadEntity(
    val libraryId: Long,
    val trackId: Long,
    val hash: String,
    val status: DownloadStatus,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val localPath: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val error: String? = null,
)
