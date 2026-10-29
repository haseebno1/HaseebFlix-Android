package com.haseebflix.app.data.remote

import com.haseebflix.app.domain.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PageDto(
    val page: Int = 1,
    val results: List<TitleDto> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0,
)

@Serializable
data class TitleDto(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    val adult: Boolean = false,
    @SerialName("media_type") val mediaType: String? = null,
)

@Serializable data class GenreDto(val id: Int, val name: String = "")
@Serializable data class NameDto(val name: String = "")
@Serializable data class CountryDto(val name: String = "")
@Serializable data class LangDto(@SerialName("english_name") val name: String = "")

@Serializable
data class SeasonDto(
    @SerialName("season_number") val number: Int = 0,
    val name: String? = null,
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("air_date") val airDate: String? = null,
    val episodes: List<EpisodeDto> = emptyList(),
)

@Serializable
data class EpisodeDto(
    val name: String? = null,
    val overview: String? = null,
    @SerialName("episode_number") val number: Int = 0,
    @SerialName("season_number") val season: Int = 0,
    @SerialName("still_path") val still: String? = null,
    val runtime: Int? = null,
    @SerialName("air_date") val airDate: String? = null,
)

@Serializable
data class EpDto(
    @SerialName("season_number") val season: Int = 0,
    @SerialName("episode_number") val number: Int = 0,
    val name: String? = null,
    @SerialName("air_date") val airDate: String? = null,
)

@Serializable data class CastDto(val id: Int, val name: String = "", val character: String? = null, @SerialName("profile_path") val profile: String? = null)
@Serializable data class CrewDto(val id: Int, val name: String = "", val job: String? = null)
@Serializable data class CreditsDto(val cast: List<CastDto> = emptyList(), val crew: List<CrewDto> = emptyList())
@Serializable data class VideoDto(val key: String = "", val site: String = "", val type: String = "", val official: Boolean = false)
@Serializable data class VideosDto(val results: List<VideoDto> = emptyList())
@Serializable data class RdDto(val certification: String = "")
@Serializable data class RdCountryDto(@SerialName("iso_3166_1") val country: String = "", @SerialName("release_dates") val dates: List<RdDto> = emptyList())
@Serializable data class ReleaseDatesDto(val results: List<RdCountryDto> = emptyList())
@Serializable data class CrDto(@SerialName("iso_3166_1") val country: String = "", val rating: String = "")
@Serializable data class ContentRatingsDto(val results: List<CrDto> = emptyList())

@Serializable
data class DetailDto(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val tagline: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("last_air_date") val lastAirDate: String? = null,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    val seasons: List<SeasonDto> = emptyList(),
    val status: String? = null,
    val genres: List<GenreDto> = emptyList(),
    @SerialName("production_companies") val companies: List<NameDto> = emptyList(),
    val networks: List<NameDto> = emptyList(),
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("production_countries") val productionCountries: List<CountryDto> = emptyList(),
    @SerialName("spoken_languages") val spoken: List<LangDto> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
    val credits: CreditsDto? = null,
    val videos: VideosDto? = null,
    val similar: PageDto? = null,
    @SerialName("release_dates") val releaseDates: ReleaseDatesDto? = null,
    @SerialName("content_ratings") val contentRatings: ContentRatingsDto? = null,
    @SerialName("last_episode_to_air") val lastEpisode: EpDto? = null,
    @SerialName("next_episode_to_air") val nextEpisode: EpDto? = null,
)

// ---------- mappers ----------
fun TitleDto.toSummary(default: MediaType): TitleSummary {
    val t = if (mediaType != null) MediaType.of(mediaType) else default
    return TitleSummary(
        type = t, id = id, title = (title ?: name).orEmpty(), overview = overview.orEmpty(),
        posterPath = posterPath, backdropPath = backdropPath, rating = voteAverage, votes = voteCount,
        date = (releaseDate ?: firstAirDate).orEmpty(), genreIds = genreIds, adult = adult,
    )
}

fun PageDto.toPage(default: MediaType, hideAdult: Boolean) = PageResult(
    results = results.filter { it.mediaType != "person" && !(hideAdult && it.adult) }.map { it.toSummary(default) },
    totalPages = totalPages, totalResults = totalResults,
)

fun EpisodeDto.toDomain() = Episode(season, number, name.orEmpty().ifBlank { "Episode $number" }, overview.orEmpty(), still, runtime, airDate)
private fun EpDto.toRef() = EpisodeRef(season, number, name.orEmpty(), airDate)

fun DetailDto.toDetail(type: MediaType, region: String): TitleDetail {
    val trailer = videos?.results.orEmpty().filter { it.site.equals("YouTube", true) }
        .let { v -> v.firstOrNull { it.type == "Trailer" && it.official } ?: v.firstOrNull { it.type == "Trailer" } ?: v.firstOrNull { it.type == "Teaser" } }
    val cert = if (type == MediaType.MOVIE) {
        val all = releaseDates?.results.orEmpty()
        (all.firstOrNull { it.country == region } ?: all.firstOrNull { it.country == "US" })
            ?.dates?.firstOrNull { it.certification.isNotBlank() }?.certification
    } else {
        val all = contentRatings?.results.orEmpty()
        (all.firstOrNull { it.country == region } ?: all.firstOrNull { it.country == "US" })?.rating?.ifBlank { null }
    }
    return TitleDetail(
        type = type, id = id, title = (title ?: name).orEmpty(), tagline = tagline.orEmpty(), overview = overview.orEmpty(),
        posterPath = posterPath, backdropPath = backdropPath, rating = voteAverage, votes = voteCount,
        date = (releaseDate ?: firstAirDate).orEmpty(), lastDate = lastAirDate,
        runtime = runtime ?: episodeRunTime.firstOrNull(),
        seasons = seasons.map { SeasonInfo(it.number, it.name.orEmpty().ifBlank { "Season ${it.number}" }, it.episodeCount, it.airDate) },
        episodeCount = numberOfEpisodes ?: 0,
        genres = genres.map { GenrePair(it.id, it.name) },
        status = status.orEmpty(), certification = cert,
        director = credits?.crew.orEmpty().firstOrNull { it.job == "Director" }?.name,
        studios = companies.map { it.name }.take(3), networks = networks.map { it.name },
        countries = if (originCountry.isNotEmpty()) originCountry else productionCountries.map { it.name },
        languages = spoken.map { it.name }.filter { it.isNotBlank() }, originalLanguage = originalLanguage.orEmpty(),
        cast = credits?.cast.orEmpty().take(24).map { CastMember(it.id, it.name, it.character.orEmpty(), it.profile) },
        trailerKey = trailer?.key,
        similar = similar?.results.orEmpty().filter { !it.adult }.take(14).map { it.toSummary(type) },
        lastEpisode = lastEpisode?.toRef(), nextEpisode = nextEpisode?.toRef(),
    )
}
