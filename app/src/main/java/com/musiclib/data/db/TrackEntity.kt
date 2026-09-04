package com.musiclib.data.db

import androidx.room.Entity
import com.musiclib.data.Track

@Entity(tableName = "tracks", primaryKeys = ["libraryId", "id"])
data class TrackEntity(
    val libraryId: Long,
    val id: Long,
    val hash: String?,
    val original_filename: String?,
    val title: String?,
    val album: String?,
    val artist: String?,
    val album_artist: String?,
    val track_no: Long?,
    val disc_no: Long?,
    val duration_ms: Long?,
    val year: Long?,
    val bitrate: Long?,
    val sample_rate: Long?,
    val channels: Long?,
    val added_at: Long,
) {
    fun toTrack(): Track = Track(
        id = id,
        hash = hash,
        original_filename = original_filename,
        title = title,
        album = album,
        artist = artist,
        album_artist = album_artist,
        track_no = track_no,
        disc_no = disc_no,
        duration_ms = duration_ms,
        year = year,
        bitrate = bitrate,
        sample_rate = sample_rate,
        channels = channels,
        added_at = added_at,
    )

    companion object {
        fun from(libraryId: Long, t: Track): TrackEntity = TrackEntity(
            libraryId = libraryId,
            id = t.id,
            hash = t.hash,
            original_filename = t.original_filename,
            title = t.title,
            album = t.album,
            artist = t.artist,
            album_artist = t.album_artist,
            track_no = t.track_no,
            disc_no = t.disc_no,
            duration_ms = t.duration_ms,
            year = t.year,
            bitrate = t.bitrate,
            sample_rate = t.sample_rate,
            channels = t.channels,
            added_at = t.added_at,
        )
    }
}
