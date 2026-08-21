package com.example.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.data.local.VideoDao
import com.example.data.model.StreamFormat
import com.example.data.model.VideoCategory
import com.example.data.model.VideoItem
import com.example.data.remote.AnimeApiClient
import com.example.data.remote.AnimeInfoResponse
import com.example.data.remote.AnimeSearchResultItem
import com.example.data.remote.StreamingSourceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class VideoRepository(
    private val videoDao: VideoDao,
    private val context: Context
) {

    val allVideos: Flow<List<VideoItem>> = videoDao.getAllVideos()
    val continueWatching: Flow<List<VideoItem>> = videoDao.getContinueWatching()
    val favoriteVideos: Flow<List<VideoItem>> = videoDao.getFavoriteVideos()

    fun getVideosByCategory(category: String): Flow<List<VideoItem>> {
        return videoDao.getVideosByCategory(category)
    }

    fun getVideoByIdFlow(id: Long): Flow<VideoItem?> {
        return videoDao.getVideoByIdFlow(id)
    }

    suspend fun getVideoById(id: Long): VideoItem? {
        return withContext(Dispatchers.IO) {
            videoDao.getVideoById(id)
        }
    }

    suspend fun insertVideo(video: VideoItem): Long {
        return withContext(Dispatchers.IO) {
            videoDao.insertVideo(video)
        }
    }

    suspend fun updateWatchProgress(id: Long, positionMs: Long, durationMs: Long) {
        withContext(Dispatchers.IO) {
            videoDao.updateWatchProgress(
                id = id,
                positionMs = positionMs,
                durationMs = durationMs,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    suspend fun resetWatchProgress(id: Long) {
        withContext(Dispatchers.IO) {
            videoDao.resetWatchProgress(id)
        }
    }

    suspend fun toggleFavorite(id: Long, currentFav: Boolean) {
        withContext(Dispatchers.IO) {
            videoDao.updateFavorite(id, !currentFav)
        }
    }

    suspend fun deleteVideo(video: VideoItem) {
        withContext(Dispatchers.IO) {
            videoDao.deleteVideo(video)
        }
    }

    suspend fun deleteVideoById(id: Long) {
        withContext(Dispatchers.IO) {
            videoDao.deleteVideoById(id)
        }
    }

    // Import a local video picked via SAF (Storage Access Framework)
    suspend fun importLocalVideo(uri: Uri, fileName: String? = null): VideoItem = withContext(Dispatchers.IO) {
        var durationMs = 0L
        var resolvedTitle = fileName ?: "Local Video"
        
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val extractedDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (extractedDuration != null) {
                durationMs = extractedDuration.toLongOrNull() ?: 0L
            }
            val extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!extractedTitle.isNullOrBlank()) {
                resolvedTitle = extractedTitle
            }
            retriever.release()
        } catch (e: Exception) {
            // Fallback to default
        }

        val videoItem = VideoItem(
            title = resolvedTitle,
            description = "Local media storage file",
            videoUrl = uri.toString(),
            thumbnailUrl = "",
            category = VideoCategory.LOCAL.name,
            streamFormat = StreamFormat.MP4.name,
            durationMs = durationMs,
            watchPositionMs = 0L,
            lastPlayedTimestamp = System.currentTimeMillis(),
            isLocal = true
        )

        val newId = videoDao.insertVideo(videoItem)
        videoItem.copy(id = newId)
    }

    // Add a custom stream from user URL input
    suspend fun addCustomStream(
        title: String,
        url: String,
        description: String = "",
        category: VideoCategory = VideoCategory.STREAM,
        format: StreamFormat = StreamFormat.AUTO,
        thumbnailUrl: String = ""
    ): Long = withContext(Dispatchers.IO) {
        val detectedFormat = when {
            format != StreamFormat.AUTO -> format
            url.contains(".m3u8", ignoreCase = true) -> StreamFormat.HLS
            url.contains(".mpd", ignoreCase = true) -> StreamFormat.DASH
            else -> StreamFormat.MP4
        }

        val video = VideoItem(
            title = title.ifBlank { "Custom Stream" },
            description = description.ifBlank { "Network stream: $url" },
            videoUrl = url.trim(),
            thumbnailUrl = thumbnailUrl,
            category = category.name,
            streamFormat = detectedFormat.name,
            durationMs = 0L,
            watchPositionMs = 0L,
            lastPlayedTimestamp = System.currentTimeMillis(),
            isLocal = false
        )
        videoDao.insertVideo(video)
    }

    // Anime Search & Fetching via Consumet API
    suspend fun searchAnime(query: String): List<AnimeSearchResultItem> = withContext(Dispatchers.IO) {
        try {
            val res = AnimeApiClient.apiService.searchAnime(query)
            res.results ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTopAiringAnime(): List<AnimeSearchResultItem> = withContext(Dispatchers.IO) {
        try {
            val res = AnimeApiClient.apiService.getTopAiring()
            res.results ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getRecentAnimeEpisodes(): List<AnimeSearchResultItem> = withContext(Dispatchers.IO) {
        try {
            val res = AnimeApiClient.apiService.getRecentEpisodes()
            res.results ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAnimeDetails(animeId: String): AnimeInfoResponse? = withContext(Dispatchers.IO) {
        try {
            AnimeApiClient.apiService.getAnimeInfo(animeId)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAnimeEpisodeStreams(episodeId: String): List<StreamingSourceItem> = withContext(Dispatchers.IO) {
        try {
            AnimeApiClient.fetchEpisodeSources(episodeId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Add an anime episode stream directly into the Room library
    suspend fun addAnimeEpisodeToLibrary(
        animeTitle: String,
        episodeTitle: String,
        streamUrl: String,
        thumbnailUrl: String,
        episodeId: String
    ): Long = withContext(Dispatchers.IO) {
        val existing = videoDao.getVideoByUrl(streamUrl)
        if (existing != null) {
            return@withContext existing.id
        }

        val video = VideoItem(
            title = "$animeTitle - $episodeTitle",
            description = "Anime Stream ($episodeId)",
            videoUrl = streamUrl,
            thumbnailUrl = thumbnailUrl,
            category = VideoCategory.ANIME.name,
            streamFormat = if (streamUrl.contains(".m3u8")) StreamFormat.HLS.name else StreamFormat.AUTO.name,
            episodeInfo = episodeTitle,
            lastPlayedTimestamp = System.currentTimeMillis()
        )
        videoDao.insertVideo(video)
    }

    // Pre-populate sample videos if database is empty
    suspend fun prepopulateSamplesIfEmpty() = withContext(Dispatchers.IO) {
        val count = videoDao.getVideoCount()
        if (count == 0) {
            val samples = getPredefinedSampleVideos()
            videoDao.insertVideos(samples)
        }
    }

    suspend fun reloadSampleCatalog() = withContext(Dispatchers.IO) {
        val samples = getPredefinedSampleVideos()
        videoDao.insertVideos(samples)
    }

    companion object {
        fun getPredefinedSampleVideos(): List<VideoItem> {
            return listOf(
                VideoItem(
                    title = "Suzume no Tojimari - Official Teaser",
                    description = "Makoto Shinkai masterpiece anime official trailer stream (1080p HLS)",
                    videoUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    thumbnailUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80",
                    category = VideoCategory.ANIME.name,
                    streamFormat = StreamFormat.HLS.name,
                    durationMs = 634000L,
                    watchPositionMs = 0L,
                    lastPlayedTimestamp = System.currentTimeMillis() - 10000
                ),
                VideoItem(
                    title = "Demon Slayer: Mugen Train - Stream Sample",
                    description = "Anime action sequence showcase (Multi-bitrate Adaptive HLS)",
                    videoUrl = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
                    thumbnailUrl = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=600&auto=format&fit=crop&q=80",
                    category = VideoCategory.ANIME.name,
                    streamFormat = StreamFormat.HLS.name,
                    durationMs = 0L,
                    watchPositionMs = 0L,
                    lastPlayedTimestamp = System.currentTimeMillis() - 20000
                ),
                VideoItem(
                    title = "Big Buck Bunny (HLS Stream)",
                    description = "Blender Open Movie Project 1080p 60fps standard HLS live master playlist",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
                    category = VideoCategory.SAMPLES.name,
                    streamFormat = StreamFormat.MP4.name,
                    durationMs = 596000L,
                    watchPositionMs = 45000L, // Sample progress to test resume functionality
                    lastPlayedTimestamp = System.currentTimeMillis() - 5000
                ),
                VideoItem(
                    title = "Tears of Steel (4K Sci-Fi DASH Stream)",
                    description = "Open source VFX sci-fi film in Amsterdam with multi-audio & subtitles",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
                    category = VideoCategory.STREAM.name,
                    streamFormat = StreamFormat.MP4.name,
                    durationMs = 734000L,
                    watchPositionMs = 120000L,
                    lastPlayedTimestamp = System.currentTimeMillis() - 15000
                ),
                VideoItem(
                    title = "Sintel Animated Odyssey (DASH Stream)",
                    description = "Dragon fantasy animation by the Blender Foundation (DASH stream test)",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                    thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
                    category = VideoCategory.SAMPLES.name,
                    streamFormat = StreamFormat.MP4.name,
                    durationMs = 888000L,
                    watchPositionMs = 0L,
                    lastPlayedTimestamp = System.currentTimeMillis() - 30000
                ),
                VideoItem(
                    title = "Elephants Dream (Classic Open Movie)",
                    description = "The world's first open-movie animation in pristine high definition",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg",
                    category = VideoCategory.SAMPLES.name,
                    streamFormat = StreamFormat.MP4.name,
                    durationMs = 653000L,
                    watchPositionMs = 0L,
                    lastPlayedTimestamp = System.currentTimeMillis() - 40000
                ),
                VideoItem(
                    title = "For Bigger Blazes (Chromecast Stream)",
                    description = "High definition 4K MP4 streaming test sample",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerBlazes.jpg",
                    category = VideoCategory.STREAM.name,
                    streamFormat = StreamFormat.MP4.name,
                    durationMs = 15000L,
                    watchPositionMs = 0L,
                    lastPlayedTimestamp = System.currentTimeMillis() - 50000
                )
            )
        }
    }
}
