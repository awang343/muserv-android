package com.musiclib.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MusicApi(private val settings: SettingsRepository) {

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 8_000
        }
    }

    private suspend fun current(): Settings = settings.flow.first()

    private fun urlOf(base: String, path: String): String {
        val b = base.trimEnd('/')
        return if (path.startsWith('/')) "$b$path" else "$b/$path"
    }

    suspend fun listLibraries(): List<Library> {
        val s = current()
        return httpClient.get(urlOf(s.serverUrl, "/api/libraries")) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
        }.body()
    }

    suspend fun listTracks(libraryId: Long): List<Track> {
        val s = current()
        return httpClient.get(urlOf(s.serverUrl, "/api/libraries/$libraryId/tracks?limit=1000")) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
        }.body()
    }

    suspend fun searchTracks(libraryId: Long, query: String): List<Track> {
        val s = current()
        return httpClient.get(urlOf(s.serverUrl, "/api/libraries/$libraryId/search")) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
            parameter("q", query)
        }.body()
    }

    suspend fun listPlaylists(libraryId: Long): List<Playlist> {
        val s = current()
        return httpClient.get(urlOf(s.serverUrl, "/api/libraries/$libraryId/playlists")) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
        }.body()
    }

    suspend fun createPlaylist(libraryId: Long, name: String): Playlist {
        val s = current()
        return httpClient.post(urlOf(s.serverUrl, "/api/libraries/$libraryId/playlists")) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
            contentType(ContentType.Application.Json)
            setBody(NameBody(name))
        }.body()
    }

    suspend fun renamePlaylist(libraryId: Long, id: Long, name: String): Playlist {
        val s = current()
        return httpClient.patch(urlOf(s.serverUrl, "/api/libraries/$libraryId/playlists/$id")) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
            contentType(ContentType.Application.Json)
            setBody(NameBody(name))
        }.body()
    }

    suspend fun deletePlaylist(libraryId: Long, id: Long) {
        val s = current()
        httpClient.delete(urlOf(s.serverUrl, "/api/libraries/$libraryId/playlists/$id")) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
        }
    }

    suspend fun getPlaylistTracks(libraryId: Long, id: Long): List<PlaylistTrack> {
        val s = current()
        return httpClient.get(urlOf(s.serverUrl, "/api/libraries/$libraryId/playlists/$id/tracks")) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
        }.body()
    }

    suspend fun playlistsContainingTrack(libraryId: Long, trackId: Long): List<Long> {
        val s = current()
        return httpClient.get(
            urlOf(s.serverUrl, "/api/libraries/$libraryId/tracks/$trackId/playlists")
        ) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
        }.body()
    }

    suspend fun addToPlaylist(libraryId: Long, playlistId: Long, trackId: Long) {
        val s = current()
        httpClient.post(urlOf(s.serverUrl, "/api/libraries/$libraryId/playlists/$playlistId/tracks")) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
            contentType(ContentType.Application.Json)
            setBody(TrackIdBody(trackId))
        }
    }

    suspend fun removeFromPlaylist(libraryId: Long, playlistId: Long, trackId: Long) {
        val s = current()
        httpClient.delete(
            urlOf(s.serverUrl, "/api/libraries/$libraryId/playlists/$playlistId/tracks/$trackId")
        ) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
        }
    }

    suspend fun setPlaylistTracks(libraryId: Long, playlistId: Long, trackIds: List<Long>) {
        val s = current()
        httpClient.put(urlOf(s.serverUrl, "/api/libraries/$libraryId/playlists/$playlistId/tracks")) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
            contentType(ContentType.Application.Json)
            setBody(TrackIdsBody(trackIds))
        }
    }

    suspend fun listTrackTags(libraryId: Long, trackId: Long): List<TrackTag> {
        val s = current()
        return httpClient.get(urlOf(s.serverUrl, "/api/libraries/$libraryId/tracks/$trackId/tags")) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
        }.body()
    }

    suspend fun addUserTag(libraryId: Long, trackId: Long, namespace: String, value: String) {
        val s = current()
        httpClient.post(urlOf(s.serverUrl, "/api/libraries/$libraryId/tracks/$trackId/tags")) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
            contentType(ContentType.Application.Json)
            setBody(TagBody(namespace, value))
        }
    }

    suspend fun removeUserTag(libraryId: Long, trackId: Long, tagId: Long) {
        val s = current()
        httpClient.delete(urlOf(s.serverUrl, "/api/libraries/$libraryId/tracks/$trackId/tags/$tagId")) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
        }
    }

    /** Stream URL doesn't require a library id — track ids are globally unique. */
    suspend fun streamUrlFor(trackId: Long): String {
        val s = current()
        return urlOf(s.serverUrl, "/api/tracks/$trackId/stream")
    }

    suspend fun authHeader(): String? {
        val s = current()
        return if (s.authToken.isBlank()) null else "Bearer ${s.authToken}"
    }

    suspend fun triggerImport(libraryId: Long): ImportState {
        val s = current()
        return httpClient.post(urlOf(s.serverUrl, "/api/libraries/$libraryId/import")) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
        }.body()
    }

    suspend fun getImportStatus(libraryId: Long): ImportState {
        val s = current()
        return httpClient.get(urlOf(s.serverUrl, "/api/libraries/$libraryId/import")) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
        }.body()
    }

    suspend fun listDownloaders(libraryId: Long): List<DownloaderInfo> {
        val s = current()
        return httpClient.get(urlOf(s.serverUrl, "/api/libraries/$libraryId/downloaders")) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
        }.body()
    }

    suspend fun runDownloader(libraryId: Long, name: String, urls: List<String>): String {
        val s = current()
        val resp: JobIdResponse = httpClient.post(
            urlOf(s.serverUrl, "/api/libraries/$libraryId/downloaders/$name/run")
        ) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
            contentType(ContentType.Application.Json)
            setBody(UrlsBody(urls))
        }.body()
        return resp.job_id
    }

    suspend fun getDownloaderJob(libraryId: Long, jobId: String): DownloaderJob {
        val s = current()
        return httpClient.get(
            urlOf(s.serverUrl, "/api/libraries/$libraryId/downloaders/jobs/$jobId")
        ) {
            if (s.authToken.isNotBlank()) bearerAuth(s.authToken)
        }.body()
    }
}

@Serializable
private data class NameBody(val name: String)

@Serializable
private data class TrackIdBody(val track_id: Long)

@Serializable
private data class TrackIdsBody(val track_ids: List<Long>)

@Serializable
private data class TagBody(val namespace: String, val value: String)

@Serializable
private data class UrlsBody(val urls: List<String>)

@Serializable
private data class JobIdResponse(val job_id: String)
