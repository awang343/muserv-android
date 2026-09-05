package com.musiclib.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musiclib.MusicLibApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.OkHttpClient

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as MusicLibApp

        // Read credentials fresh per-request so changes (Settings save) take
        // effect without restarting the service.
        val ok = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val settings = runBlocking {
                    app.container.settings.flow.first()
                }
                val authed = if (settings.username.isBlank() || settings.token.isBlank()) {
                    req
                } else {
                    req.newBuilder()
                        .header("Authorization", Credentials.basic(settings.username, settings.token))
                        .build()
                }
                chain.proceed(authed)
            }
            .build()

        val httpFactory = OkHttpDataSource.Factory(ok)
        // Routes file:// to FileDataSource and http(s):// to the OkHttp factory,
        // so downloaded and streamed tracks play through the same player.
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply { playWhenReady = true }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
