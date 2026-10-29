package com.haseebflix.app.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate

enum class MediaType(val api: String) {
    MOVIE("movie"), TV("tv");
    companion object { fun of(s: String?) = if (s.equals("tv", true)) TV else MOVIE }
}

object Genres {
    private val names = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary",
        18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
        9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi", 10770 to "TV Movie", 53 to "Thriller",
        10752 to "War", 37 to "Western", 10759 to "Action & Adventure", 10762 to "Kids", 10763 to "News",
        10764 to "Reality", 10765 to "Sci-Fi & Fantasy", 10766 to "Soap", 10767 to "Talk", 10768 to "War & Politics",
    )
    fun name(id: Int): String? = names[id]

    /** Movie genre ids that have a differently-numbered TV equivalent. */
    private val tvAlias = mapOf(28 to 10759, 12 to 10759, 878 to 10765, 14 to 10765, 10752 to 10768, 27 to 9648)
    fun tvId(movieId: Int): Int = tvAlias[movieId] ?: movieId
    fun matches(ids: List<Int>, movieGenreId: Int) = movieGenreId in ids || tvId(movieGenreId) in ids

    data class Entry(val id: Int, val name: String)
    val browse = listOf(
        Entry(28, "Action"), Entry(878, "Sci-Fi"), Entry(27, "Horror"), Entry(35, "Comedy"),
        Entry(16, "Animation"), Entry(18, "Drama"), Entry(10749, "Romance"), Entry(99, "Documentary"),
    )
    val onboarding = listOf(
        Entry(28, "Action"), Entry(878, "Sci-Fi"), Entry(35, "Comedy"), Entry(27, "Horror"),
        Entry(53, "Thriller"), Entry(12, "Adventure"), Entry(18, "Drama"), Entry(16, "Animation"),
        Entry(80, "Crime"), Entry(14, "Fantasy"), Entry(99, "Documentary"), Entry(9648, "Mystery"),
    )
}

@Serializable
data class TitleSummary(
    val type: MediaType,
    val id: Int,
    val title: String,
    val overview: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val rating: Double = 0.0,
    val votes: Int = 0,
    val date: String = "",
    val genreIds: List<Int> = emptyList(),
    val adult: Boolean = false,
) {
    val key: String get() = "${type.name}:$id"
    val year: String get() = date.take(4)
    val hasRating: Boolean get() = votes > 0 && rating > 0
    val genres: List<String> get() = genreIds.mapNotNull { Genres.name(it) }
    val isUpcoming: Boolean get() = date.isNotBlank() && runCatching { LocalDate.parse(date).isAfter(LocalDate.now()) }.getOrDefault(false)
}

@Serializable data class GenrePair(val id: Int, val name: String)
@Serializable data class CastMember(val id: Int, val name: String, val role: String, val photo: String?)
@Serializable data class SeasonInfo(val number: Int, val name: String, val episodes: Int, val airDate: String?)
@Serializable data class EpisodeRef(val season: Int, val number: Int, val name: String, val airDate: String?)
@Serializable data class Episode(
    val season: Int, val number: Int, val name: String, val overview: String,
    val still: String?, val runtime: Int?, val airDate: String?,
)

@Serializable
data class TitleDetail(
    val type: MediaType,
    val id: Int,
    val title: String,
    val tagline: String = "",
    val overview: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val rating: Double = 0.0,
    val votes: Int = 0,
    val date: String = "",
    val lastDate: String? = null,
    val runtime: Int? = null,
    val seasons: List<SeasonInfo> = emptyList(),
    val episodeCount: Int = 0,
    val genres: List<GenrePair> = emptyList(),
    val status: String = "",
    val certification: String? = null,
    val director: String? = null,
    val studios: List<String> = emptyList(),
    val networks: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val originalLanguage: String = "",
    val cast: List<CastMember> = emptyList(),
    val trailerKey: String? = null,
    val similar: List<TitleSummary> = emptyList(),
    val lastEpisode: EpisodeRef? = null,
    val nextEpisode: EpisodeRef? = null,
) {
    val year: String get() = date.take(4)
    val hasRating: Boolean get() = votes > 0 && rating > 0
    fun toSummary() = TitleSummary(type, id, title, overview, posterPath, backdropPath, rating, votes, date, genres.map { it.id })
}

data class PageResult(val results: List<TitleSummary>, val totalPages: Int, val totalResults: Int)

data class ContinueItem(
    val title: TitleSummary, val season: Int, val episode: Int,
    val positionMs: Long, val durationMs: Long, val updatedAt: Long,
) {
    val fraction: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val remainingMin: Int get() = ((durationMs - positionMs) / 60_000).toInt().coerceAtLeast(0)
}

enum class Feed(val id: String, val type: MediaType?, val list: String?, val trending: String? = null) {
    TRENDING_WEEK("trending_week", null, null, "all"),
    TRENDING_MOVIE("trending_movie", MediaType.MOVIE, null, "movie"),
    TRENDING_TV("trending_tv", MediaType.TV, null, "tv"),
    POPULAR_MOVIE("popular_movie", MediaType.MOVIE, "popular"),
    NOW_PLAYING("now_playing", MediaType.MOVIE, "now_playing"),
    UPCOMING("upcoming", MediaType.MOVIE, "upcoming"),
    TOP_MOVIE("top_movie", MediaType.MOVIE, "top_rated"),
    POPULAR_TV("popular_tv", MediaType.TV, "popular"),
    TOP_TV("top_tv", MediaType.TV, "top_rated"),
    ON_AIR("on_air", MediaType.TV, "on_the_air");

    companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: TRENDING_WEEK }
}

enum class SortOption(val label: String) { POPULAR("Popularity"), RATING("Top rated"), NEWEST("Newest") }

class AppError(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { OFFLINE, TIMEOUT, NO_TOKEN, HTTP, UNKNOWN }
}
