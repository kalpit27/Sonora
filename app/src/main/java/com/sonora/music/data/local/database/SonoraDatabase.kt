package com.sonora.music.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sonora.music.data.local.database.dao.DownloadHistoryDao
import com.sonora.music.data.local.database.dao.DownloadQueueDao
import com.sonora.music.data.local.database.dao.HistoryDao
import com.sonora.music.data.local.database.dao.PlaylistDao
import com.sonora.music.data.local.database.dao.SongDao
import com.sonora.music.data.local.database.entity.DownloadHistoryEntity
import com.sonora.music.data.local.database.entity.DownloadQueueEntity
import com.sonora.music.data.local.database.entity.HistoryEntity
import com.sonora.music.data.local.database.entity.ListeningSessionEntity
import com.sonora.music.data.local.database.entity.PlaylistEntity
import com.sonora.music.data.local.database.entity.PlaylistSongCrossRef
import com.sonora.music.data.local.database.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        HistoryEntity::class,
        ListeningSessionEntity::class,
        DownloadHistoryEntity::class,
        DownloadQueueEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class SonoraDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadHistoryDao(): DownloadHistoryDao
    abstract fun downloadQueueDao(): DownloadQueueDao

    companion object {
        @Volatile
        private var INSTANCE: SonoraDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN skipCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN totalListeningTimeMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN lastCompletedTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN customTitle TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN customArtist TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN customAlbum TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN customYear INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN customArtworkUriString TEXT DEFAULT NULL")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS listening_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        songId INTEGER NOT NULL,
                        songTitle TEXT NOT NULL,
                        artistName TEXT NOT NULL,
                        albumName TEXT NOT NULL,
                        timestampMs INTEGER NOT NULL,
                        durationListenedMs INTEGER NOT NULL,
                        wasCompleted INTEGER NOT NULL,
                        wasSkipped INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_listening_sessions_songId ON listening_sessions(songId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_listening_sessions_timestampMs ON listening_sessions(timestampMs)"
                )
            }
        }

        fun getInstance(context: Context): SonoraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SonoraDatabase::class.java,
                    "sonora_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
