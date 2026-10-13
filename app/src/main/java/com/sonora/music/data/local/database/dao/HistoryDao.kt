package com.sonora.music.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sonora.music.data.local.database.entity.HistoryEntity
import com.sonora.music.data.local.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN (
            SELECT songId, MAX(playedAt) as maxPlayedAt 
            FROM playback_history 
            GROUP BY songId
        ) h ON s.id = h.songId
        ORDER BY h.maxPlayedAt DESC
        LIMIT :limit
    """)
    fun getDistinctRecentSongs(limit: Int = 50): Flow<List<SongEntity>>

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()

    @Query("SELECT COUNT(*) FROM playback_history")
    fun getHistoryCount(): Flow<Int>
}
