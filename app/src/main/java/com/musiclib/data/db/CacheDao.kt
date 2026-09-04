package com.musiclib.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.musiclib.data.PlaylistTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {

    @Query("SELECT * FROM tracks WHERE libraryId = :libraryId")
    fun tracksForLibrary(libraryId: Long): Flow<List<TrackEntity>>

    @Query("DELETE FROM tracks WHERE libraryId = :libraryId")
    suspend fun deleteTracks(libraryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Transaction
    suspend fun replaceTracks(libraryId: Long, tracks: List<TrackEntity>) {
        deleteTracks(libraryId)
        insertTracks(tracks)
    }

    @Query("SELECT * FROM playlists WHERE libraryId = :libraryId")
    fun playlistsForLibrary(libraryId: Long): Flow<List<PlaylistEntity>>

    @Query("DELETE FROM playlists WHERE libraryId = :libraryId")
    suspend fun deletePlaylists(libraryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Transaction
    suspend fun replacePlaylists(libraryId: Long, playlists: List<PlaylistEntity>) {
        deletePlaylists(libraryId)
        insertPlaylists(playlists)
    }

    @Query(
        """
        SELECT pt.trackId AS track_id, pt.position AS position, pt.added_at AS added_at,
               t.title AS title, t.album AS album, t.artist AS artist,
               t.album_artist AS album_artist, t.duration_ms AS duration_ms
        FROM playlist_tracks pt
        LEFT JOIN tracks t ON t.libraryId = pt.libraryId AND t.id = pt.trackId
        WHERE pt.libraryId = :libraryId AND pt.playlistId = :playlistId
        ORDER BY pt.position
        """
    )
    fun playlistTracksFor(libraryId: Long, playlistId: Long): Flow<List<PlaylistTrack>>

    @Query("DELETE FROM playlist_tracks WHERE libraryId = :libraryId AND playlistId = :playlistId")
    suspend fun deletePlaylistTracks(libraryId: Long, playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(tracks: List<PlaylistTrackCrossRef>)

    @Transaction
    suspend fun replacePlaylistTracks(libraryId: Long, playlistId: Long, tracks: List<PlaylistTrackCrossRef>) {
        deletePlaylistTracks(libraryId, playlistId)
        insertPlaylistTracks(tracks)
    }
}
