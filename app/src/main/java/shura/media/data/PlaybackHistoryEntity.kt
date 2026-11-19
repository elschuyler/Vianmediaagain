package shura.media.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_history",
    indices = [
        Index(value = ["lastPlayedTime"]),
        Index(value = ["fileNameKey"])
    ]
)
data class PlaybackHistoryEntity(
    @PrimaryKey val mediaUri: String,
    val fileNameKey: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = -1L,
    val lastPlayedTime: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isPortrait: Boolean? = null,
    val audioTrackIndex: Int = -1,
    val subtitleTrackIndex: Int = -1
)
