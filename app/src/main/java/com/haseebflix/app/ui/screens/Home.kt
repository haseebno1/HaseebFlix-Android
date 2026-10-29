package com.haseebflix.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.haseebflix.app.data.repo.LibraryRepository
import com.haseebflix.app.data.repo.MediaRepository
import com.haseebflix.app.data.settings.SettingsRepository
import com.haseebflix.app.domain.*
import com.haseebflix.app.ui.components.*
import com.haseebflix.app.ui.theme.LocalDataSaver
import com.haseebflix.app.ui.theme.extras
import com.haseebflix.app.util.TmdbImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RefreshState(val loading: Boolean = false, val error: AppError? = null)

data class HomeUi(
    val hero: List<TitleSummary> = emptyList(),
    val trending: List<TitleSummary> = emptyList(),
    val nowPlaying: List<TitleSummary> = emptyList(),
    val tv: List<TitleSummary> = emptyList(),
    val topRated: List<TitleSummary> = emptyList(),
    val cont: List<ContinueItem> = emptyList(),
    val genre: Int? = null,
    val watchKeys: Set<String> = emptySet(),
    val chips: List<Genres.Entry> = Genres.onboarding,
    val refresh: RefreshState = RefreshState(loading = true),
) {
    val hasContent get() = trending.isNotEmpty() || nowPlaying.isNotEmpty() || tv.isNotEmpty() || topRated.isNotEmpty()
}

private class RawFeeds(val lists: List<List<TitleSummary>>, val favGenres: Set<Int>)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val media: MediaRepository, private val library: LibraryRepository, private val settings: SettingsRepository,
) : ViewModel() {
    private val genre = MutableStateFlow<Int?>(null)
    private val refreshState = MutableStateFlow(RefreshState(loading = true))
    private val feeds = listOf(Feed.TRENDING_WEEK, Feed.NOW_PLAYING, Feed.POPULAR_TV, Feed.TOP_MOVIE)

    private val raw = combine(
        media.feed(feeds[0]), media.feed(feeds[1]), media.feed(feeds[2]), media.feed(feeds[3]), settings.flow.map { it.genres }.distinctUntilChanged(),
    ) { a, b, c, d, g -> RawFeeds(listOf(a, b, c, d), g) }

    val state: StateFlow<HomeUi> = combine(raw, library.continueWatching, library.watchlistKeys, genre, refreshState) { r, cw, wk, g, rs ->
        fun flt(l: List<TitleSummary>) = if (g == null) l else l.filter { Genres.matches(it.genreIds, g) }
        HomeUi(
            hero = r.lists[0].filter { it.backdropPath != null }.take(5),
            trending = flt(r.lists[0]), nowPlaying = flt(r.lists[1]), tv = flt(r.lists[2]), topRated = flt(r.lists[3]),
            cont = cw, genre = g, watchKeys = wk, refresh = rs,
            chips = Genres.onboarding.sortedByDescending { it.id in r.favGenres },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    init {
        viewModelScope.launch {
            settings.flow.map { Triple(it.language, it.region, it.hideAdult) }.distinctUntilChanged().collect { refresh(false) }
        }
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            refreshState.value = RefreshState(loading = true)
            val results = feeds.map { f -> async { media.refreshFeed(f, force) } }.awaitAll()
            refreshState.value = RefreshState(false, results.firstOrNull { it.isFailure }?.exceptionOrNull() as? AppError)
        }
    }

    fun selectGenre(id: Int?) { genre.value = id }
    fun toggleList(t: TitleSummary) { viewModelScope.launch { library.toggleWatchlist(t) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    unread: Boolean,
    onOpen: (TitleSummary) -> Unit,
    onPlay: (TitleSummary, Int, Int) -> Unit,
    onSeeAll: (String, String) -> Unit,
    onSearch: () -> Unit, onBell: () -> Unit, onAvatar: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopBrandBar(unread, onSearch, onBell, onAvatar)
        PullToRefreshBox(isRefreshing = ui.refresh.loading && ui.hasContent, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
            when {
                !ui.hasContent && ui.refresh.loading -> HomeSkeleton()
                !ui.hasContent && ui.refresh.error != null -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), Alignment.Center) {
                    ErrorState(ui.refresh.error, { vm.refresh() })
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            HeroPager(ui.hero, ui.watchKeys, onOpen, onPlay, vm::toggleList)
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                item { HfChip("All", ui.genre == null, { vm.selectGenre(null) }) }
                                items(ui.chips, key = { it.id }) { g -> HfChip(g.name, ui.genre == g.id, { vm.selectGenre(g.id) }) }
                            }
                        }
                    }
                    if (ui.cont.isNotEmpty() && ui.genre == null) item { ContinueRow(ui.cont) { onOpen(it.title) } }
                    item { TitleRow("Trending This Week", ui.trending, onOpen, onSeeAll = { onSeeAll("Trending This Week", Feed.TRENDING_WEEK.id) }) }
                    item { TitleRow("Now Playing & Popular", ui.nowPlaying, onOpen, onSeeAll = { onSeeAll("Now Playing", Feed.NOW_PLAYING.id) }) }
                    item { TitleRow("Popular TV Shows", ui.tv, onOpen, onSeeAll = { onSeeAll("Popular TV Shows", Feed.POPULAR_TV.id) }) }
                    item { TitleRow("Top Rated", ui.topRated, onOpen, onSeeAll = { onSeeAll("Top Rated", Feed.TOP_MOVIE.id) }) }
                    if (ui.genre != null && !ui.hasContent) item { EmptyState(Icons.Outlined.Movie, "Nothing here yet", "No titles in this genre right now. Try another one.") }
                }
            }
        }
    }
}

@Composable
private fun HeroPager(
    items: List<TitleSummary>, watchKeys: Set<String>, onOpen: (TitleSummary) -> Unit,
    onPlay: (TitleSummary, Int, Int) -> Unit, onToggle: (TitleSummary) -> Unit,
) {
    if (items.isEmpty()) { SkeletonBox(Modifier.fillMaxWidth().height(440.dp), RectangleShape); return }
    val pager = rememberPagerState { items.size }
    LaunchedEffect(items.size) {
        while (true) {
            delay(6_000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }
    val cs = MaterialTheme.colorScheme
    val saver = LocalDataSaver.current
    Box(Modifier.fillMaxWidth().height(460.dp)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
            val t = items[i]
            Box(Modifier.fillMaxSize().clickable { onOpen(t) }) {
                RemoteImage(TmdbImage.backdrop(t.backdropPath, 1280, saver), Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to MaterialTheme.extras.canvas)))
                Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RatingBadge(t)
                        Text(
                            (t.genres.take(2) + listOfNotNull(t.year.ifBlank { null })).joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE4E1E7), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(t.title, style = MaterialTheme.typography.displayMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryButton(
                            "Watch Now", { if (t.type == MediaType.MOVIE) onPlay(t, 0, 0) else onOpen(t) },
                            Modifier.weight(1.5f), Icons.Filled.PlayArrow, height = 54.dp,
                        )
                        val inList = t.key in watchKeys
                        SecondaryButton(if (inList) "In List" else "My List", { onToggle(t) }, Modifier.weight(1f), if (inList) Icons.Filled.Check else Icons.Filled.Add, height = 54.dp)
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(items.size) { i ->
                Box(Modifier.size(if (pager.currentPage == i) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (pager.currentPage == i) cs.primary else cs.outline))
            }
        }
    }
}

@Composable
private fun ContinueRow(items: List<ContinueItem>, onOpen: (ContinueItem) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val saver = LocalDataSaver.current
    Column {
        SectionHeader("Continue Watching")
        Spacer(Modifier.height(14.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.title.key }) { c ->
                Column(Modifier.width(270.dp).clip(RoundedCornerShape(16.dp)).clickable { onOpen(c) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))) {
                        RemoteImage(TmdbImage.backdrop(c.title.backdropPath ?: c.title.posterPath, 780, saver), Modifier.fillMaxSize())
                        Box(Modifier.align(Alignment.Center).size(44.dp).background(Color(0x99000000), CircleShape), Alignment.Center) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White) }
                        LinearProgressIndicator({ c.fraction }, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp), color = cs.primary, trackColor = Color(0x40FFFFFF), gapSize = 0.dp, drawStopIndicator = {})
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(c.title.title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(if (c.title.type == MediaType.TV) "S${c.season} • E${c.episode}" else null, "${c.remainingMin}m left").joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = false), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SkeletonBox(Modifier.fillMaxWidth().height(440.dp), RectangleShape)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(4) { SkeletonBox(Modifier.size(78.dp, 36.dp), CircleShape) } }
        PosterRowSkeleton()
    }
}
