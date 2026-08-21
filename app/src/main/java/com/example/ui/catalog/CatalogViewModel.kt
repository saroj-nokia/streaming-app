package com.example.ui.catalog

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.StreamFormat
import com.example.data.model.VideoCategory
import com.example.data.model.VideoItem
import com.example.data.remote.AnimeEpisodeItem
import com.example.data.remote.AnimeInfoResponse
import com.example.data.remote.AnimeSearchResultItem
import com.example.data.remote.StreamingSourceItem
import com.example.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CatalogViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    val repository = VideoRepository(database.videoDao(), application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow(VideoCategory.ALL)
    val selectedCategory = _selectedCategory.asStateFlow()

    // Anime Search & Remote Explorer state
    private val _animeSearchResults = MutableStateFlow<List<AnimeSearchResultItem>>(emptyList())
    val animeSearchResults = _animeSearchResults.asStateFlow()

    private val _topAiringAnime = MutableStateFlow<List<AnimeSearchResultItem>>(emptyList())
    val topAiringAnime = _topAiringAnime.asStateFlow()

    private val _isAnimeLoading = MutableStateFlow(false)
    val isAnimeLoading = _isAnimeLoading.asStateFlow()

    private val _animeError = MutableStateFlow<String?>(null)
    val animeError = _animeError.asStateFlow()

    private val _selectedAnime = MutableStateFlow<AnimeInfoResponse?>(null)
    val selectedAnime = _selectedAnime.asStateFlow()

    private val _isFetchingEpisodes = MutableStateFlow(false)
    val isFetchingEpisodes = _isFetchingEpisodes.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    val continueWatching: StateFlow<List<VideoItem>> = repository.continueWatching
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVideos: StateFlow<List<VideoItem>> = combine(
        repository.allVideos,
        _selectedCategory,
        _searchQuery
    ) { videos, category, query ->
        videos.filter { video ->
            val matchesCategory = when (category) {
                VideoCategory.ALL -> true
                else -> video.category.equals(category.name, ignoreCase = true)
            }
            val matchesQuery = query.isBlank() ||
                    video.title.contains(query, ignoreCase = true) ||
                    video.description.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.prepopulateSamplesIfEmpty()
            fetchTopAiringAnime()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: VideoCategory) {
        _selectedCategory.value = category
    }

    fun addCustomStream(
        title: String,
        url: String,
        description: String = "",
        category: VideoCategory = VideoCategory.STREAM,
        format: StreamFormat = StreamFormat.AUTO
    ) {
        viewModelScope.launch {
            try {
                val newId = repository.addCustomStream(
                    title = title,
                    url = url,
                    description = description,
                    category = category,
                    format = format
                )
                _statusMessage.value = "Stream added to library"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to add stream: ${e.message}"
            }
        }
    }

    fun importLocalVideo(uri: Uri, fileName: String? = null, onImported: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val item = repository.importLocalVideo(uri, fileName)
                _statusMessage.value = "Imported: ${item.title}"
                onImported(item.id)
            } catch (e: Exception) {
                _statusMessage.value = "Failed to import video: ${e.message}"
            }
        }
    }

    fun deleteVideo(video: VideoItem) {
        viewModelScope.launch {
            repository.deleteVideo(video)
            _statusMessage.value = "Deleted ${video.title}"
        }
    }

    fun resetWatchProgress(id: Long) {
        viewModelScope.launch {
            repository.resetWatchProgress(id)
            _statusMessage.value = "Progress reset"
        }
    }

    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch {
            repository.toggleFavorite(video.id, video.isFavorite)
        }
    }

    fun reloadSampleCatalog() {
        viewModelScope.launch {
            repository.reloadSampleCatalog()
            _statusMessage.value = "Samples reloaded"
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // Anime Remote Search via Consumet
    fun searchAnimeOnline(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isAnimeLoading.value = true
            _animeError.value = null
            try {
                val results = repository.searchAnime(query)
                _animeSearchResults.value = results
                if (results.isEmpty()) {
                    _animeError.value = "No anime found for \"$query\""
                }
            } catch (e: Exception) {
                _animeError.value = "Anime API error: ${e.message}"
            } finally {
                _isAnimeLoading.value = false
            }
        }
    }

    fun fetchTopAiringAnime() {
        viewModelScope.launch {
            _isAnimeLoading.value = true
            try {
                val results = repository.getTopAiringAnime()
                _topAiringAnime.value = results
            } catch (e: Exception) {
                // Ignore silent background fetch error
            } finally {
                _isAnimeLoading.value = false
            }
        }
    }

    fun selectAnime(animeId: String) {
        viewModelScope.launch {
            _isFetchingEpisodes.value = true
            _selectedAnime.value = null
            try {
                val info = repository.getAnimeDetails(animeId)
                _selectedAnime.value = info
            } catch (e: Exception) {
                _statusMessage.value = "Failed to load anime details"
            } finally {
                _isFetchingEpisodes.value = false
            }
        }
    }

    fun clearSelectedAnime() {
        _selectedAnime.value = null
    }

    fun playOrSaveAnimeEpisode(
        anime: AnimeInfoResponse,
        episode: AnimeEpisodeItem,
        onReadyToPlay: (Long) -> Unit
    ) {
        viewModelScope.launch {
            _isFetchingEpisodes.value = true
            try {
                val sources = repository.getAnimeEpisodeStreams(episode.id)
                val bestSource = sources.firstOrNull { it.quality == "default" || it.quality == "1080p" || it.quality == "700p" }
                    ?: sources.firstOrNull()
                
                val streamUrl = bestSource?.url ?: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                
                val videoId = repository.addAnimeEpisodeToLibrary(
                    animeTitle = anime.displayTitle,
                    episodeTitle = episode.displayEpisodeName,
                    streamUrl = streamUrl,
                    thumbnailUrl = anime.image ?: "",
                    episodeId = episode.id
                )
                onReadyToPlay(videoId)
            } catch (e: Exception) {
                _statusMessage.value = "Could not fetch stream URL: ${e.message}"
            } finally {
                _isFetchingEpisodes.value = false
            }
        }
    }
}
