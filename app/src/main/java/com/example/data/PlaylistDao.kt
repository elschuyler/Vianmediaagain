package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY orderIndex ASC, timestamp DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getPlaylistById(id: Int): Flow<Playlist?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsSync(): List<Playlist>

    @Query("SELECT * FROM playlist_items")
    suspend fun getAllPlaylistItemsSync(): List<PlaylistItem>

    @Query("DELETE FROM playlists")
    suspend fun clearAllPlaylists()

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Int)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY timestamp ASC")
    fun getItemsForPlaylist(playlistId: Int): Flow<List<PlaylistItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItem)

    @Query("DELETE FROM playlist_items WHERE id = :id")
    suspend fun deletePlaylistItemById(id: Int)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaUri = :mediaUri")
    suspend fun deletePlaylistItemByUri(playlistId: Int, mediaUri: String)

    @Update
    suspend fun updatePlaylistItem(item: PlaylistItem)
    
    @Update
    suspend fun updatePlaylistItems(items: List<PlaylistItem>)

    @Update
    suspend fun updatePlaylists(playlists: List<Playlist>)

    @Query("SELECT * FROM playlists WHERE isTemporary = 1 LIMIT 1")
    suspend fun getTemporaryPlaylist(): Playlist?

    @Query("DELETE FROM playlists WHERE isTemporary = 1 AND timestamp < :thresholdTimestamp")
    suspend fun deleteExpiredTemporaryPlaylists(thresholdTimestamp: Long): Int

    @Query("UPDATE playlists SET isTemporary = 0, name = :newName WHERE id = :id")
    suspend fun makePlaylistPermanent(id: Int, newName: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearItemsForPlaylist(playlistId: Int)
}
