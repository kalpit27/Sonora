package com.sonora.music.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sonora.music.data.local.database.entity.DownloadHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadHistoryDao {

    @Query("SELECT * FROM download_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<DownloadHistoryEntity>>

    @Query("SELECT * FROM download_history WHERE videoId = :videoId LIMIT 1")
    suspend fun getByVideoId(videoId: String): DownloadHistoryEntity?

    @Query("SELECT * FROM download_history WHERE title = :title LIMIT 1")
    suspend fun getByTitle(title: String): DownloadHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entity: DownloadHistoryEntity): Long

    @Update
    suspend fun updateHistory(entity: DownloadHistoryEntity)

    @Query("""
        UPDATE download_history 
        SET status = :status, 
            localFilePath = :localPath, 
            mediaStoreUri = :mediaStoreUri, 
            fileSizeBytes = :fileSize, 
            errorMessage = :errorMessage 
        WHERE id = :id
    """)
    suspend fun updateStatus(
        id: Long,
        status: String,
        localPath: String?,
        mediaStoreUri: String?,
        fileSize: Long,
        errorMessage: String?
    )

    @Query("DELETE FROM download_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE download_history SET thumbnailUrl = :thumbnailUrl WHERE id = :id")
    suspend fun updateThumbnail(id: Long, thumbnailUrl: String)

    @Query("DELETE FROM download_history")
    suspend fun clearAll()
}
