package shura.media.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY lastPlayedTime DESC")
    suspend fun getAllHistory(): List<PlaybackHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PlaybackHistoryEntity>)

    @Query("DELETE FROM playback_history WHERE mediaUri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM playback_history WHERE fileNameKey != '' AND fileNameKey = :fileNameKey")
    suspend fun deleteByFileNameKey(fileNameKey: String)

    @Query(
        "DELETE FROM playback_history WHERE mediaUri NOT IN (" +
            "SELECT mediaUri FROM playback_history ORDER BY lastPlayedTime DESC LIMIT :limit" +
        ")"
    )
    suspend fun pruneBeyondLimit(limit: Int = 250)
}
