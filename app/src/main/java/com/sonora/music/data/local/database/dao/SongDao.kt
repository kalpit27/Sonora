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
        WHERE COALESCE(customTitle, title) LIKE '%' || :query || '%' 
           OR COALESCE(customArtist, artist) LIKE '%' || :query || '%' 
           OR COALESCE(customAlbum, album) LIKE '%' || :query || '%'
           OR title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY COALESCE(customTitle, title) COLLATE NOCASE ASC
    """)
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Transaction
    suspend fun toggleFavoriteAtomic(id: Long): Boolean {
        val current = getSongById(id) ?: return false
        val newState = !current.isFavorite
        setFavorite(id, newState)
        return newState
    }

    @Query("""
        UPDATE songs 
        SET playCount = playCount + 1, lastPlayedTimestamp = :timestamp 
        WHERE id = :id
    """)
    suspend fun recordPlayback(id: Long, timestamp: Long)

    @Query("""
        UPDATE songs 
        SET totalListeningTimeMs = totalListeningTimeMs + :addedMs 
        WHERE id = :id
    """)
    suspend fun addListeningTime(id: Long, addedMs: Long)

    @Query("""
        UPDATE songs 
        SET lastCompletedTimestamp = :timestamp 
        WHERE id = :id
    """)
    suspend fun recordSongCompleted(id: Long, timestamp: Long)

    @Query("""
        UPDATE songs 
        SET skipCount = skipCount + 1 
        WHERE id = :id
    """)
    suspend fun recordSongSkipped(id: Long)

    @Query("""
        UPDATE songs 
        SET customTitle = :customTitle,
            customArtist = :customArtist,
            customAlbum = :customAlbum,
            customYear = :customYear,
            customArtworkUriString = :customArtworkUriString
        WHERE id = :id
    """)
    suspend fun updateSongMetadata(
        id: Long,
        customTitle: String?,
        customArtist: String?,
        customAlbum: String?,
        customYear: Int?,
        customArtworkUriString: String?
    )

    @Query("UPDATE songs SET customArtworkUriString = :artworkUri WHERE id IN (:songIds)")
    suspend fun updateArtworkForSongs(songIds: List<Long>, artworkUri: String?)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSongById(id: Long)

    @Query("DELETE FROM songs WHERE id NOT IN (:validIds)")
    suspend fun deleteSongsNotIn(validIds: List<Long>)

    @Query("""
        SELECT id, durationMs, isFavorite, playCount, lastPlayedTimestamp, skipCount,
               totalListeningTimeMs, lastCompletedTimestamp,
               customTitle, customArtist, customAlbum, customYear, customArtworkUriString
        FROM songs
    """)
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
                    durationMs = scanned.durationMs.takeIf { it > 0L } ?: existing.durationMs,
                    isFavorite = existing.isFavorite,
                    playCount = existing.playCount,
                    lastPlayedTimestamp = existing.lastPlayedTimestamp,
                    skipCount = existing.skipCount,
                    totalListeningTimeMs = existing.totalListeningTimeMs,
                    lastCompletedTimestamp = existing.lastCompletedTimestamp,
                    customTitle = existing.customTitle,
                    customArtist = existing.customArtist,
                    customAlbum = existing.customAlbum,
                    customYear = existing.customYear,
                    customArtworkUriString = existing.customArtworkUriString
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
    val durationMs: Long,
    val isFavorite: Boolean,
    val playCount: Int,
    val lastPlayedTimestamp: Long,
    val skipCount: Int,
    val totalListeningTimeMs: Long,
    val lastCompletedTimestamp: Long,
    val customTitle: String?,
    val customArtist: String?,
    val customAlbum: String?,
    val customYear: Int?,
    val customArtworkUriString: String?
)
