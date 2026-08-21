package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VideoCategory(val displayName: String) {
    ALL("All"),
    ANIME("Anime"),
    STREAM("Live / Streams"),
    LOCAL("Local Files"),
    SAMPLES("Samples")
}

enum class StreamFormat {
    AUTO,
    HLS,
    DASH,
    MP4
}

@Entity(tableName = "videos")
data class VideoItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val videoUrl: String,
    val thumbnailUrl: String = "",
    val category: String = VideoCategory.STREAM.name,
    val streamFormat: String = StreamFormat.AUTO.name,
    val durationMs: Long = 0L,
    val watchPositionMs: Long = 0L,
    val lastPlayedTimestamp: Long = 0L,
    val isFavorite: Boolean = false,
    val isLocal: Boolean = false,
    val episodeInfo: String? = null,
    val customHeadersJson: String? = null
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (watchPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val isCompleted: Boolean
        get() = durationMs > 0L && (watchPositionMs >= (durationMs * 0.95f))
}
