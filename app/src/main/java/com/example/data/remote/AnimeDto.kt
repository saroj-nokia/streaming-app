package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ConsumetSearchResult(
    @Json(name = "currentPage") val currentPage: Int? = 1,
    @Json(name = "hasNextPage") val hasNextPage: Boolean? = false,
    @Json(name = "results") val results: List<AnimeSearchResultItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class AnimeSearchResultItem(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: Any?, // Can be string or Map
    @Json(name = "image") val image: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "releaseDate") val releaseDate: String? = null,
    @Json(name = "subOrDub") val subOrDub: String? = null,
    @Json(name = "episodeNumber") val episodeNumber: Int? = null,
    @Json(name = "episodeId") val episodeId: String? = null,
    @Json(name = "genres") val genres: List<String>? = emptyList()
) {
    val displayTitle: String
        get() = when (title) {
            is String -> title
            is Map<*, *> -> (title["english"] ?: title["romaji"] ?: title["userPreferred"] ?: "Anime").toString()
            else -> id.replace("-", " ").replaceFirstChar { it.uppercase() }
        }
}

@JsonClass(generateAdapter = true)
data class AnimeInfoResponse(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: Any?,
    @Json(name = "image") val image: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "releaseDate") val releaseDate: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "totalEpisodes") val totalEpisodes: Int? = null,
    @Json(name = "genres") val genres: List<String>? = emptyList(),
    @Json(name = "episodes") val episodes: List<AnimeEpisodeItem>? = emptyList()
) {
    val displayTitle: String
        get() = when (title) {
            is String -> title
            is Map<*, *> -> (title["english"] ?: title["romaji"] ?: title["userPreferred"] ?: id).toString()
            else -> id
        }
}

@JsonClass(generateAdapter = true)
data class AnimeEpisodeItem(
    @Json(name = "id") val id: String,
    @Json(name = "number") val number: Int? = 1,
    @Json(name = "title") val title: String? = null,
    @Json(name = "url") val url: String? = null
) {
    val displayEpisodeName: String
        get() = if (!title.isNullOrBlank()) "Ep $number: $title" else "Episode $number"
}

@JsonClass(generateAdapter = true)
data class AnimeStreamingSourcesResponse(
    @Json(name = "headers") val headers: Map<String, String>? = null,
    @Json(name = "sources") val sources: List<StreamingSourceItem>? = emptyList(),
    @Json(name = "download") val download: String? = null
)

@JsonClass(generateAdapter = true)
data class StreamingSourceItem(
    @Json(name = "url") val url: String,
    @Json(name = "isM3U8") val isM3U8: Boolean? = true,
    @Json(name = "quality") val quality: String? = "default"
)
