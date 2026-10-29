package com.haseebflix.app.data.repo

import com.haseebflix.app.BuildConfig
import com.haseebflix.app.data.local.*
import com.haseebflix.app.data.remote.*
import com.haseebflix.app.data.settings.AppSettings
import com.haseebflix.app.data.settings.SettingsRepository
import com.haseebflix.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is HttpException -> if (code() == 401) AppError(AppError.Kind.NO_TOKEN, "TMDB rejected the access token")
    else AppError(AppError.Kind.HTTP, "Server error ${code()}")
    is SocketTimeoutException -> AppError(AppError.Kind.TIMEOUT, "The request timed out")
    is IOException -> AppError(AppError.Kind.OFFLINE, "No internet connection")
    else -> AppError(AppError.Kind.UNKNOWN, message ?: "Something went wrong")
}

@Singleton
class MediaRepository @Inject constructor(
    private val api: TmdbApi,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val json: Json,
) {
    private val titles get() = db.titleDao()
    private val details get() = db.detailDao()

    private companion object {
        const val FEED_TTL = 60L * 60_000
        const val DETAIL_TTL = 24L * 60 * 60_000
        const val EPISODE_TTL = 6L * 60 * 60_000
        const val GENRE_TTL = 7L * 24 * 60 * 60_000
    }

    private fun now() = System.currentTimeMillis()
    private fun feedKey(f: Feed, s: AppSettings) = "${f.id}|${s.language}|${s.region}|${s.hideAdult}"

    private fun requireToken() {
        if (BuildConfig.TMDB_TOKEN.isBlank()) throw AppError(AppError.Kind.NO_TOKEN, "Add TMDB_READ_TOKEN to local.properties")
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e.toAppError())
    }

    // ---------------- feeds ----------------
    @OptIn(ExperimentalCoroutinesApi::class)
    fun feed(f: Feed): Flow<List<TitleSummary>> = settings.flow
        .map { feedKey(f, it) }.distinctUntilChanged()
        .flatMapLatest { key -> titles.observeFeed(key).map { rows -> rows.map { it.toDomain() } } }

    private suspend fun fetchFeed(f: Feed, page: Int, s: AppSettings): PageResult {
        requireToken()
        val dto = if (f.trending != null) api.trending(f.trending, "week", s.language, page)
        else api.list(f.type!!.api, f.list!!, s.language, s.region, page)
        return dto.toPage(f.type ?: MediaType.MOVIE, s.hideAdult)
    }

    suspend fun refreshFeed(f: Feed, force: Boolean = false): Result<Unit> = call {
        val s = settings.current()
        val key = feedKey(f, s)
        val t = titles.feedTime(key)
        if (!force && t != null && now() - t < FEED_TTL) return@call
        val page = fetchFeed(f, 1, s)
        val n = now()
        titles.replaceFeed(
            key,
            page.results.map { it.toEntity(n) },
            page.results.mapIndexed { i, r -> FeedEntity(key, i, r.type, r.id, n) },
        )
    }

    suspend fun feedPage(f: Feed, page: Int): Result<PageResult> = call { fetchFeed(f, page, settings.current()) }

    suspend fun discoverPage(type: MediaType, genreId: Int?, sort: SortOption, page: Int): Result<PageResult> = call {
        requireToken()
        val s = settings.current()
        val gid = genreId?.let { if (type == MediaType.TV) Genres.tvId(it) else it }?.toString()
        val sortBy = when (sort) {
            SortOption.POPULAR -> "popularity.desc"
            SortOption.RATING -> "vote_average.desc"
            SortOption.NEWEST -> if (type == MediaType.MOVIE) "primary_release_date.desc" else "first_air_date.desc"
        }
        val result = api.discover(type.api, gid, sortBy, s.language, !s.hideAdult, if (sort == SortOption.POPULAR) null else 200, page)
            .toPage(type, s.hideAdult)
        if (sort == SortOption.NEWEST) result.copy(results = result.results.filter { !it.isUpcoming }) else result
    }

    suspend fun search(query: String, type: MediaType, page: Int = 1): Result<PageResult> = call {
        requireToken()
        val s = settings.current()
        api.search(type.api, query, s.language, !s.hideAdult, page).toPage(type, s.hideAdult)
    }

    // ---------------- details ----------------
    fun detail(type: MediaType, id: Int): Flow<TitleDetail?> = details.observe(type, id).map { row ->
        row?.let { runCatching { json.decodeFromString<TitleDetail>(it.json) }.getOrNull() }
    }

    suspend fun refreshDetail(type: MediaType, id: Int, force: Boolean = false): Result<Unit> = call {
        val cached = details.get(type, id)
        if (!force && cached != null && now() - cached.cachedAt < DETAIL_TTL) return@call
        requireToken()
        val s = settings.current()
        val vl = "${s.language.take(2)},en,null"
        val dto = if (type == MediaType.MOVIE) api.movie(id, s.language, videoLang = vl) else api.tv(id, s.language, videoLang = vl)
        val d = dto.toDetail(type, s.region)
        details.upsert(DetailEntity(type, id, json.encodeToString(d), now()))
        titles.upsertOne(d.toSummary().toEntity())
    }

    suspend fun episodes(showId: Int, season: Int): Result<List<Episode>> {
        val cached = details.episodes(showId, season)
        val list = cached?.let { runCatching { json.decodeFromString<List<Episode>>(it.json) }.getOrNull() }
        if (cached != null && list != null && now() - cached.cachedAt < EPISODE_TTL) return Result.success(list)
        val fresh = call {
            requireToken()
            val eps = api.season(showId, season, settings.current().language).episodes.map { it.toDomain() }
            details.upsertEpisodes(EpisodeCacheEntity(showId, season, json.encodeToString(eps), now()))
            eps
        }
        return if (fresh.isFailure && list != null) Result.success(list) else fresh
    }

    suspend fun trailerKey(type: MediaType, id: Int): String? {
        refreshDetail(type, id)
        return detail(type, id).first()?.trailerKey
    }

    suspend fun genreCount(genreId: Int): Int? {
        val cached = details.genreCount(genreId)
        if (cached != null && now() - cached.cachedAt < GENRE_TTL) return cached.count
        val total = call {
            requireToken()
            val s = settings.current()
            val m = api.discover("movie", genreId.toString(), "popularity.desc", s.language, false, null, 1).totalResults
            val t = api.discover("tv", Genres.tvId(genreId).toString(), "popularity.desc", s.language, false, null, 1).totalResults
            m + t
        }.getOrNull()
        if (total != null) details.upsertGenreCount(GenreCountEntity(genreId, total, now()))
        return total ?: cached?.count
    }

    // ---------------- search history ----------------
    fun recentSearches(): Flow<List<String>> = db.searchDao().recent()
    suspend fun addRecent(q: String) { if (q.isNotBlank()) db.searchDao().add(SearchEntity(q.trim(), now())) }
    suspend fun removeRecent(q: String) = db.searchDao().remove(q)
    suspend fun clearRecents() = db.searchDao().clear()

    // ---------------- cache ----------------
    suspend fun clearContentCache() {
        titles.clearAllFeeds(); details.clearDetails(); details.clearEpisodes(); details.clearGenreCounts()
        titles.purgeUnreferenced()
    }
}
