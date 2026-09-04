package com.musiclib.data.db

import androidx.room.Entity

@Entity(tableName = "playlist_tracks", primaryKeys = ["libraryId", "playlistId", "trackId"])
data class PlaylistTrackCrossRef(
    val libraryId: Long,
    val playlistId: Long,
    val trackId: Long,
    val position: Long,
    val added_at: Long,
)
