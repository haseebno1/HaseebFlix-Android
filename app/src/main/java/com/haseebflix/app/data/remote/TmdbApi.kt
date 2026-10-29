package com.haseebflix.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/{type}/{window}")
    suspend fun trending(
        @Path("type") type: String, @Path("window") window: String,
        @Query("language") language: String, @Query("page") page: Int = 1,
    ): PageDto

    /** type = movie|tv, list = popular|top_rated|now_playing|upcoming|on_the_air */
    @GET("{type}/{list}")
    suspend fun list(
        @Path("type") type: String, @Path("list") list: String,
        @Query("language") language: String, @Query("region") region: String?, @Query("page") page: Int = 1,
    ): PageDto

    @GET("discover/{type}")
    suspend fun discover(
        @Path("type") type: String,
        @Query("with_genres") genres: String?,
        @Query("sort_by") sort: String,
        @Query("language") language: String,
        @Query("include_adult") adult: Boolean,
        @Query("vote_count.gte") minVotes: Int?,
        @Query("page") page: Int = 1,
    ): PageDto

    @GET("search/{type}")
    suspend fun search(
        @Path("type") type: String, @Query("query") query: String, @Query("language") language: String,
        @Query("include_adult") adult: Boolean, @Query("page") page: Int = 1,
    ): PageDto

    @GET("movie/{id}")
    suspend fun movie(
        @Path("id") id: Int, @Query("language") language: String,
        @Query("append_to_response") append: String = "credits,videos,similar,release_dates",
        @Query("include_video_language") videoLang: String = "en,null",
    ): DetailDto

    @GET("tv/{id}")
    suspend fun tv(
        @Path("id") id: Int, @Query("language") language: String,
        @Query("append_to_response") append: String = "credits,videos,similar,content_ratings",
        @Query("include_video_language") videoLang: String = "en,null",
    ): DetailDto

    @GET("tv/{id}/season/{season}")
    suspend fun season(@Path("id") id: Int, @Path("season") season: Int, @Query("language") language: String): SeasonDto
}
