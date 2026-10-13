package com.sonora.music.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sonora.music.data.local.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSongById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<Long>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE lastPlayedTimestamp > 0 ORDER BY lastPlayedTimestamp DESC LIMIT :limit")
    fun getRecentlyPlayedSongs(limit: Int = 30): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC LIMIT :limit")
    fun getRecentlyAddedSongs(limit: Int = 30): Flow<List<SongEntity>>

    @Query("""
        SELECT * FROM songs 
        WHERE title LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%' 
           OR album LIKE '%' || :query || '%' 
        ORDER BY title COLLATE NOCASE ASC
    """)
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("""
        UPDATE songs 
        SET playCount = playCount + 1, lastPlayedTimestamp = :timestamp 
        WHERE id = :id
    """)
    suspend fun recordPlayback(id: Long, timestamp: Long)

    @Query("DELETE FROM songs WHERE id NOT IN (:validIds)")
    suspend fun deleteSongsNotIn(validIds: List<Long>)

    @Query("SELECT id, isFavorite, playCount, lastPlayedTimestamp FROM songs")
    suspend fun getAllUserData(): List<SongUserDataSummary>

    @Transaction
    suspend fun syncMediaStoreSongs(scannedSongs: List<SongEntity>) {
        if (scannedSongs.isEmpty()) {
            return
        }
        val existingUserData = getAllUserData().associateBy { it.id }
        val merged = scannedSongs.map { scanned ->
            val existing = existingUserData[scanned.id]
            if (existing != null) {
                scanned.copy(
                    isFavorite = existing.isFavorite,
                    playCount = existing.playCount,
                    lastPlayedTimestamp = existing.lastPlayedTimestamp
                )
            } else {
                scanned
            }
        }
        insertSongs(merged)
        val validIds = scannedSongs.map { it.id }
        deleteSongsNotIn(validIds)
    }
}

data class SongUserDataSummary(
    val id: Long,
    val isFavorite: Boolean,
    val playCount: Int,
    val lastPlayedTimestamp: Long
)
