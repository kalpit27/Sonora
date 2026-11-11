package com.sonora.music.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sonora.music.data.local.database.entity.HistoryEntity
import com.sonora.music.data.local.database.entity.ListeningSessionEntity
import com.sonora.music.data.local.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListeningSession(session: ListeningSessionEntity): Long

    @Query("""
        UPDATE listening_sessions
        SET durationListenedMs = :durationListenedMs,
            wasCompleted = :wasCompleted,
            wasSkipped = :wasSkipped
        WHERE id = :sessionId
    """)
    suspend fun updateListeningSession(
        sessionId: Long,
        durationListenedMs: Long,
        wasCompleted: Boolean,
        wasSkipped: Boolean
    )

    @Query("SELECT * FROM listening_sessions ORDER BY timestampMs DESC")
    fun getAllListeningSessionsFlow(): Flow<List<ListeningSessionEntity>>

    @Query("SELECT * FROM listening_sessions ORDER BY timestampMs DESC")
    suspend fun getAllListeningSessions(): List<ListeningSessionEntity>

    @Query("DELETE FROM listening_sessions WHERE songId = :songId")
    suspend fun deleteSessionsForSong(songId: Long)

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

    @Query("DELETE FROM listening_sessions")
    suspend fun clearListeningSessions()

    @Query("SELECT COUNT(*) FROM playback_history")
    fun getHistoryCount(): Flow<Int>
}
