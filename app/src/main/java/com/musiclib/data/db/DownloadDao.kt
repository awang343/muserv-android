package com.musiclib.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class DownloadWithTrack(
    val trackId: Long,
    val hash: String,
    val status: DownloadStatus,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val updatedAt: Long,
    val error: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
)

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE libraryId = :libraryId AND trackId = :trackId")
    suspend fun delete(libraryId: Long, trackId: Long)

    @Query("SELECT * FROM downloads WHERE libraryId = :libraryId AND trackId = :trackId")
    suspend fun get(libraryId: Long, trackId: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE libraryId = :libraryId AND trackId = :trackId")
    fun statusFlow(libraryId: Long, trackId: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE libraryId = :libraryId")
    fun downloadsForLibrary(libraryId: Long): Flow<List<DownloadEntity>>

    @Query(
        """
        SELECT d.trackId AS trackId, d.hash AS hash, d.status AS status,
               d.bytesDownloaded AS bytesDownloaded, d.totalBytes AS totalBytes,
               d.updatedAt AS updatedAt, d.error AS error,
               t.title AS title, t.artist AS artist, t.album AS album
        FROM downloads d
        LEFT JOIN tracks t ON t.libraryId = d.libraryId AND t.id = d.trackId
        WHERE d.libraryId = :libraryId
        ORDER BY d.updatedAt DESC
        """
    )
    fun downloadsWithTrackInfo(libraryId: Long): Flow<List<DownloadWithTrack>>

    @Query("SELECT COALESCE(SUM(totalBytes), 0) FROM downloads WHERE libraryId = :libraryId AND status = 'DOWNLOADED'")
    fun storageUsedForLibrary(libraryId: Long): Flow<Long>

    @Query("UPDATE downloads SET status = 'FAILED', error = 'Interrupted' WHERE status = 'DOWNLOADING'")
    suspend fun resetStuckDownloading()
}
