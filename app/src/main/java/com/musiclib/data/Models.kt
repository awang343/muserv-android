package com.musiclib.data

import kotlinx.serialization.Serializable

@Serializable
data class Library(
    val id: Long,
    val name: String,
    val root_path: String,
)

@Serializable
data class Track(
    val id: Long,
    val library_id: Long = 0,
    val hash: String? = null,
    val original_filename: String? = null,
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val album_artist: String? = null,
    val track_no: Long? = null,
    val disc_no: Long? = null,
    val duration_ms: Long? = null,
    val year: Long? = null,
    val bitrate: Long? = null,
    val sample_rate: Long? = null,
    val channels: Long? = null,
    val added_at: Long = 0,
) {
    val displayTitle: String get() = title ?: original_filename ?: "(untitled)"
    val displayArtist: String get() = artist ?: album_artist ?: "—"
    val displayAlbum: String get() = album ?: "—"
}

@Serializable
data class Playlist(
    val id: Long,
    val library_id: Long = 0,
    val name: String,
    val description: String? = null,
    val track_count: Long = 0,
    val created_at: Long,
    val updated_at: Long,
)

@Serializable
data class PlaylistTrack(
    val track_id: Long,
    val position: Long,
    val added_at: Long,
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val album_artist: String? = null,
    val duration_ms: Long? = null,
)

@Serializable
data class TrackTag(
    val tag_id: Long,
    val namespace: String,
    val value: String,
    val source: String,
    val added_at: Long,
)

@Serializable
data class ImportState(
    val running: Boolean,
    val started_at: Long? = null,
    val finished_at: Long? = null,
    val last_stats: ImportStats? = null,
    val last_error: String? = null,
)

@Serializable
data class ImportStats(
    val scanned: Long,
    val imported: Long,
    val duplicates: Long,
    val failed: Long,
)

@Serializable
data class DownloaderInfo(
    val name: String,
)

@Serializable
data class DownloaderJob(
    val id: String,
    val library_id: Long,
    val script: String,
    val urls: List<String>,
    val current_index: Long? = null,
    val status: String,
    val log: List<String> = emptyList(),
    val summary: DownloaderSummary? = null,
    val started_at: String,
    val finished_at: String? = null,
)

@Serializable
data class DownloaderSummary(
    val scanned: Long,
    val imported: Long,
    val duplicates: Long,
    val failed: Long,
)
