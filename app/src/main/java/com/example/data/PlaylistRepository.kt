package com.example.data

import kotlinx.coroutines.flow.Flow

class PlaylistRepository(private val playlistDao: PlaylistDao) {
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()
    fun getPlaylistById(id: Int): Flow<Playlist?> = playlistDao.getPlaylistById(id)
    suspend fun insertPlaylist(playlist: Playlist): Long = playlistDao.insertPlaylist(playlist)
    suspend fun deletePlaylistById(id: Int) = playlistDao.deletePlaylistById(id)
    fun getItemsForPlaylist(playlistId: Int): Flow<List<PlaylistItem>> = playlistDao.getItemsForPlaylist(playlistId)
    suspend fun insertPlaylistItem(item: PlaylistItem) = playlistDao.insertPlaylistItem(item)
    suspend fun deletePlaylistItemById(id: Int) = playlistDao.deletePlaylistItemById(id)
    suspend fun deletePlaylistItemByUri(playlistId: Int, mediaUri: String) = playlistDao.deletePlaylistItemByUri(playlistId, mediaUri)
    suspend fun updatePlaylistItem(item: PlaylistItem) = playlistDao.updatePlaylistItem(item)
    suspend fun updatePlaylistItems(items: List<PlaylistItem>) = playlistDao.updatePlaylistItems(items)
    suspend fun updatePlaylists(playlists: List<Playlist>) = playlistDao.updatePlaylists(playlists)

    suspend fun getTemporaryPlaylist(): Playlist? = playlistDao.getTemporaryPlaylist()

    suspend fun saveOrUpdateTemporaryPlaylist(uris: List<String>, append: Boolean = false): Long {
        val existing = playlistDao.getTemporaryPlaylist()
        val playlistId = if (existing != null) {
            if (!append) {
                playlistDao.clearItemsForPlaylist(existing.id)
            }
            playlistDao.insertPlaylist(existing.copy(timestamp = System.currentTimeMillis()))
            existing.id
        } else {
            playlistDao.insertPlaylist(
                Playlist(
                    name = "Quick Play (Temporary)",
                    timestamp = System.currentTimeMillis(),
                    isTemporary = true
                )
            ).toInt()
        }

        uris.forEachIndexed { index, uriStr ->
            playlistDao.insertPlaylistItem(
                PlaylistItem(
                    playlistId = playlistId,
                    mediaUri = uriStr,
                    timestamp = System.currentTimeMillis() + index
                )
            )
        }
        return playlistId.toLong()
    }

    suspend fun makePlaylistPermanent(playlistId: Int, newName: String) {
        playlistDao.makePlaylistPermanent(playlistId, newName)
    }

    suspend fun cleanExpiredTemporaryPlaylists(thresholdHours: Int = 24): Int {
        val cutoff = System.currentTimeMillis() - (thresholdHours * 60 * 60 * 1000L)
        return playlistDao.deleteExpiredTemporaryPlaylists(cutoff)
    }
}