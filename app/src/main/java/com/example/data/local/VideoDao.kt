package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.VideoItem
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {

    @Query("SELECT * FROM videos ORDER BY lastPlayedTimestamp DESC, id DESC")
    fun getAllVideos(): Flow<List<VideoItem>>

    @Query("SELECT * FROM videos WHERE category = :category ORDER BY lastPlayedTimestamp DESC, id DESC")
    fun getVideosByCategory(category: String): Flow<List<VideoItem>>

    @Query("SELECT * FROM videos WHERE watchPositionMs > 0 AND watchPositionMs < (durationMs * 0.95) ORDER BY lastPlayedTimestamp DESC LIMIT 10")
    fun getContinueWatching(): Flow<List<VideoItem>>

    @Query("SELECT * FROM videos WHERE isFavorite = 1 ORDER BY id DESC")
    fun getFavoriteVideos(): Flow<List<VideoItem>>

    @Query("SELECT * FROM videos WHERE id = :id")
    fun getVideoByIdFlow(id: Long): Flow<VideoItem?>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getVideoById(id: Long): VideoItem?

    @Query("SELECT * FROM videos WHERE videoUrl = :url LIMIT 1")
    suspend fun getVideoByUrl(url: String): VideoItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoItem>)

    @Update
    suspend fun updateVideo(video: VideoItem)

    @Query("UPDATE videos SET watchPositionMs = :positionMs, durationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE durationMs END, lastPlayedTimestamp = :timestamp WHERE id = :id")
    suspend fun updateWatchProgress(id: Long, positionMs: Long, durationMs: Long, timestamp: Long)

    @Query("UPDATE videos SET watchPositionMs = 0 WHERE id = :id")
    suspend fun resetWatchProgress(id: Long)

    @Query("UPDATE videos SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Delete
    suspend fun deleteVideo(video: VideoItem)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteVideoById(id: Long)

    @Query("DELETE FROM videos")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM videos")
    suspend fun getVideoCount(): Int
}
