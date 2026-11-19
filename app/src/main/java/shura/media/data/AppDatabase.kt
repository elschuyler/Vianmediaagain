package shura.media.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Playlist::class, PlaylistItem::class, PlaybackHistoryEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE playlists ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
                    }
                }
                val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE playlist_items ADD COLUMN isNotFound INTEGER NOT NULL DEFAULT 0")
                    }
                }
                val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE playlists ADD COLUMN isTemporary INTEGER NOT NULL DEFAULT 0")
                    }
                }
                val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `playback_history` (" +
                                "`mediaUri` TEXT NOT NULL, " +
                                "`fileNameKey` TEXT NOT NULL, " +
                                "`positionMs` INTEGER NOT NULL, " +
                                "`durationMs` INTEGER NOT NULL, " +
                                "`lastPlayedTime` INTEGER NOT NULL, " +
                                "`playbackSpeed` REAL NOT NULL, " +
                                "`isPortrait` INTEGER, " +
                                "`audioTrackIndex` INTEGER NOT NULL, " +
                                "`subtitleTrackIndex` INTEGER NOT NULL, " +
                                "PRIMARY KEY(`mediaUri`)" +
                            ")"
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_playback_history_lastPlayedTime` " +
                                "ON `playback_history` (`lastPlayedTime`)"
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_playback_history_fileNameKey` " +
                                "ON `playback_history` (`fileNameKey`)"
                        )
                    }
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "media_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
