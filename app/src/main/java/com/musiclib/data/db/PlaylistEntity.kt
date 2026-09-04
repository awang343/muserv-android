package com.musiclib.data.db

import androidx.room.Entity
import com.musiclib.data.Playlist

@Entity(tableName = "playlists", primaryKeys = ["libraryId", "id"])
data class PlaylistEntity(
    val libraryId: Long,
    val id: Long,
    val name: String,
    val description: String?,
    val track_count: Long,
    val created_at: Long,
    val updated_at: Long,
) {
    fun toPlaylist(): Playlist = Playlist(
        id = id,
        name = name,
        description = description,
        track_count = track_count,
        created_at = created_at,
        updated_at = updated_at,
    )

    companion object {
        fun from(libraryId: Long, p: Playlist): PlaylistEntity = PlaylistEntity(
            libraryId = libraryId,
            id = p.id,
            name = p.name,
            description = p.description,
            track_count = p.track_count,
            created_at = p.created_at,
            updated_at = p.updated_at,
        )
    }
}
