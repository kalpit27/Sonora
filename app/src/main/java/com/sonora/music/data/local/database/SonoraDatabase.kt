package com.sonora.music.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sonora.music.data.local.database.dao.DownloadHistoryDao
import com.sonora.music.data.local.database.dao.HistoryDao
import com.sonora.music.data.local.database.dao.PlaylistDao
import com.sonora.music.data.local.database.dao.SongDao
import com.sonora.music.data.local.database.entity.DownloadHistoryEntity
import com.sonora.music.data.local.database.entity.HistoryEntity
import com.sonora.music.data.local.database.entity.PlaylistEntity
import com.sonora.music.data.local.database.entity.PlaylistSongCrossRef
import com.sonora.music.data.local.database.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        HistoryEntity::class,
        DownloadHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SonoraDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadHistoryDao(): DownloadHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: SonoraDatabase? = null

        fun getInstance(context: Context): SonoraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SonoraDatabase::class.java,
                    "sonora_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
