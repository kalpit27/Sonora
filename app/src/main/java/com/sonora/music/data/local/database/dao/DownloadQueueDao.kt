package com.sonora.music.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sonora.music.data.local.database.entity.DownloadQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadQueueDao {

    @Query("SELECT * FROM download_queue ORDER BY orderIndex ASC, timestamp ASC")
    fun getAllQueue(): Flow<List<DownloadQueueEntity>>

    @Query("SELECT * FROM download_queue ORDER BY orderIndex ASC, timestamp ASC")
    suspend fun getQueueList(): List<DownloadQueueEntity>

    @Query("SELECT * FROM download_queue WHERE status = 'PENDING' OR status = 'DOWNLOADING' ORDER BY orderIndex ASC")
    suspend fun getPendingItems(): List<DownloadQueueEntity>

    @Query("SELECT * FROM download_queue WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): DownloadQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DownloadQueueEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: DownloadQueueEntity)

    @Update
    suspend fun updateItem(item: DownloadQueueEntity)

    @Query("""
        UPDATE download_queue 
        SET status = :status, 
            progress = :progress, 
            downloadedBytes = :downloadedBytes, 
            totalBytes = :totalBytes 
        WHERE id = :id
    """)
    suspend fun updateProgress(id: String, status: String, progress: Int, downloadedBytes: Long, totalBytes: Long)

    @Query("""
        UPDATE download_queue 
        SET status = :status, 
            progress = :progress, 
            localUri = :localUri, 
            errorMessage = :errorMessage 
        WHERE id = :id
    """)
    suspend fun updateStatus(id: String, status: String, progress: Int, localUri: String?, errorMessage: String?)

    @Query("DELETE FROM download_queue WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM download_queue")
    suspend fun clearQueue()
}

