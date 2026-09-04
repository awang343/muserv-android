package com.musiclib.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.musiclib.data.SettingsRepository
import com.musiclib.data.Track
import com.musiclib.data.db.DownloadDao
import com.musiclib.data.db.DownloadEntity
import com.musiclib.data.db.DownloadStatus
import com.musiclib.data.db.DownloadWithTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

/** Sole entry point for the UI to trigger/observe/remove downloads. */
class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val settings: SettingsRepository,
) {
    fun statusFlow(libraryId: Long, trackId: Long): Flow<DownloadEntity?> =
        downloadDao.statusFlow(libraryId, trackId)

    fun downloadsForLibrary(libraryId: Long): Flow<List<DownloadEntity>> =
        downloadDao.downloadsForLibrary(libraryId)

    fun downloadsWithTrackInfo(libraryId: Long): Flow<List<DownloadWithTrack>> =
        downloadDao.downloadsWithTrackInfo(libraryId)

    fun storageUsedForLibrary(libraryId: Long): Flow<Long> =
        downloadDao.storageUsedForLibrary(libraryId)

    /** Single-track download, run as an expedited job for immediacy. */
    suspend fun downloadTrack(libraryId: Long, track: Track) {
        enqueue(libraryId, listOf(track), expedited = true)
    }

    /** Bulk (playlist) download — regular priority, one job per track. */
    suspend fun downloadTracks(libraryId: Long, tracks: List<Track>) {
        enqueue(libraryId, tracks, expedited = false)
    }

    suspend fun removeDownload(libraryId: Long, trackId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(libraryId, trackId))
        val entity = downloadDao.get(libraryId, trackId)
        entity?.localPath?.let { File(it).delete() }
        downloadDao.delete(libraryId, trackId)
    }

    suspend fun removeAllDownloads(libraryId: Long) {
        val entities = downloadDao.downloadsForLibrary(libraryId).first()
        for (entity in entities) {
            removeDownload(libraryId, entity.trackId)
        }
    }

    private suspend fun enqueue(libraryId: Long, tracks: List<Track>, expedited: Boolean) {
        val wifiOnly = settings.flow.first().wifiOnlyDownload
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val wm = WorkManager.getInstance(context)
        for (track in tracks) {
            val hash = track.hash ?: continue
            downloadDao.upsert(
                DownloadEntity(
                    libraryId = libraryId,
                    trackId = track.id,
                    hash = hash,
                    status = DownloadStatus.QUEUED,
                )
            )
            val data = workDataOf(
                KEY_LIBRARY_ID to libraryId,
                KEY_TRACK_ID to track.id,
                KEY_HASH to hash,
            )
            var builder = OneTimeWorkRequestBuilder<TrackDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(data)
            if (expedited) {
                builder = builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            wm.enqueueUniqueWork(workName(libraryId, track.id), ExistingWorkPolicy.KEEP, builder.build())
        }
    }

    companion object {
        fun workName(libraryId: Long, trackId: Long) = "download-$libraryId-$trackId"
        const val KEY_LIBRARY_ID = "libraryId"
        const val KEY_TRACK_ID = "trackId"
        const val KEY_HASH = "hash"
    }
}
