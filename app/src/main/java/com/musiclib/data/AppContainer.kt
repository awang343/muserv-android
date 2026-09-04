package com.musiclib.data

import android.content.Context
import com.musiclib.data.db.MuservDatabase
import com.musiclib.data.download.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Manual DI — single source of truth for app-wide singletons. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settings: SettingsRepository = SettingsRepository(appContext)
    val api: MusicApi = MusicApi(settings)
    val database: MuservDatabase = MuservDatabase.getInstance(appContext)
    val downloadRepository: DownloadRepository =
        DownloadRepository(appContext, database.downloadDao(), settings)

    init {
        // Interrupted downloads (app killed mid-write) leave stale rows/tmp files behind.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            database.downloadDao().resetStuckDownloading()
            File(appContext.filesDir, "downloads/tmp").listFiles()?.forEach { it.delete() }
        }
    }
}
