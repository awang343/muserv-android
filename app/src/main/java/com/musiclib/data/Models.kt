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
    val path: String,
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
    val displayTitle: String get() = title ?: path.substringAfterLast('/')
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
data class ScanState(
    val running: Boolean,
    val started_at: Long? = null,
    val finished_at: Long? = null,
    val last_stats: ScanStats? = null,
    val last_error: String? = null,
)

@Serializable
data class ScanStats(
    val seen: Long,
    val inserted: Long,
    val updated: Long,
    val unchanged: Long,
    val failed: Long,
)
