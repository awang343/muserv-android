package com.musiclib.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.musiclib.MusicLibApp
import com.musiclib.data.db.DownloadEntity
import com.musiclib.data.db.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

private const val PROGRESS_STEP_BYTES = 131_072L
private const val MAX_ATTEMPTS = 3

class TrackDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val libraryId = inputData.getLong(DownloadRepository.KEY_LIBRARY_ID, -1L)
        val trackId = inputData.getLong(DownloadRepository.KEY_TRACK_ID, -1L)
        val hash = inputData.getString(DownloadRepository.KEY_HASH)
        if (libraryId < 0 || trackId < 0 || hash == null) return@withContext Result.failure()

        val app = applicationContext as MusicLibApp
        val container = app.container
        val dao = container.database.downloadDao()

        suspend fun update(
            status: DownloadStatus,
            bytesDownloaded: Long = 0,
            totalBytes: Long = 0,
            localPath: String? = null,
            error: String? = null,
        ) {
            dao.upsert(
                DownloadEntity(
                    libraryId = libraryId,
                    trackId = trackId,
                    hash = hash,
                    status = status,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = totalBytes,
                    localPath = localPath,
                    error = error,
                )
            )
        }

        update(DownloadStatus.DOWNLOADING)

        val downloadsDir = File(applicationContext.filesDir, "downloads")
        val tmpDir = File(downloadsDir, "tmp")
        tmpDir.mkdirs()
        val tmpFile = File(tmpDir, "$trackId-$hash.part")
        val finalFile = File(downloadsDir, hash)

        try {
            val url = container.api.streamUrlFor(libraryId, trackId)
            val authHeader = container.api.authHeader()

            val requestBuilder = Request.Builder().url(url)
            if (authHeader != null) requestBuilder.header("Authorization", authHeader)

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    update(DownloadStatus.FAILED, error = "HTTP ${response.code}")
                    return@withContext Result.failure()
                }
                val body = response.body ?: run {
                    update(DownloadStatus.FAILED, error = "Empty response body")
                    return@withContext Result.failure()
                }

                val totalBytes = body.contentLength().coerceAtLeast(0)
                val digest = MessageDigest.getInstance("SHA-256")
                var bytesDownloaded = 0L
                var lastReported = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            bytesDownloaded += read
                            if (bytesDownloaded - lastReported >= PROGRESS_STEP_BYTES) {
                                lastReported = bytesDownloaded
                                setProgress(
                                    workDataOf(
                                        "bytesDownloaded" to bytesDownloaded,
                                        "totalBytes" to totalBytes,
                                    )
                                )
                                update(DownloadStatus.DOWNLOADING, bytesDownloaded, totalBytes)
                            }
                        }
                    }
                }

                val computedHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (!computedHash.equals(hash, ignoreCase = true)) {
                    tmpFile.delete()
                    update(DownloadStatus.FAILED, error = "Checksum mismatch")
                    return@withContext Result.failure()
                }

                tmpFile.renameTo(finalFile)
                update(
                    DownloadStatus.DOWNLOADED,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = bytesDownloaded,
                    localPath = finalFile.absolutePath,
                )
                Result.success()
            }
        } catch (e: Exception) {
            tmpFile.delete()
            if (runAttemptCount < MAX_ATTEMPTS) {
                update(DownloadStatus.QUEUED, error = e.message)
                Result.retry()
            } else {
                update(DownloadStatus.FAILED, error = e.message ?: e.javaClass.simpleName)
                Result.failure()
            }
        }
    }

    companion object {
        private val httpClient = OkHttpClient()
    }
}
