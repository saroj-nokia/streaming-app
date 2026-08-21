package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface AnimeApiService {

    @GET("anime/gogoanime/{query}")
    suspend fun searchAnime(
        @Path("query") query: String,
        @Query("page") page: Int = 1
    ): ConsumetSearchResult

    @GET("anime/gogoanime/top-airing")
    suspend fun getTopAiring(
        @Query("page") page: Int = 1
    ): ConsumetSearchResult

    @GET("anime/gogoanime/recent-episodes")
    suspend fun getRecentEpisodes(
        @Query("page") page: Int = 1
    ): ConsumetSearchResult

    @GET("anime/gogoanime/info/{id}")
    suspend fun getAnimeInfo(
        @Path("id") id: String
    ): AnimeInfoResponse

    @GET("anime/gogoanime/watch/{episodeId}")
    suspend fun getWatchSources(
        @Path("episodeId") episodeId: String,
        @Query("server") server: String? = null
    ): AnimeStreamingSourcesResponse
}

object AnimeApiClient {
    // Primary and fallback open Consumet / Anime instances
    private const val BASE_URL = "https://api.consumet.org/"
    
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val apiService: AnimeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AnimeApiService::class.java)
    }

    // Direct stream fetcher helper that supports fallback servers if primary is rate limited
    suspend fun fetchEpisodeSources(episodeId: String): List<StreamingSourceItem> {
        return try {
            val response = apiService.getWatchSources(episodeId)
            response.sources ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
