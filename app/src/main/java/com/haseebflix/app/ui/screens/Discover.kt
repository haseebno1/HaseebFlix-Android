package com.haseebflix.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.haseebflix.app.data.repo.LibraryRepository
import com.haseebflix.app.data.repo.MediaRepository
import com.haseebflix.app.domain.*
import com.haseebflix.app.ui.components.*
import com.haseebflix.app.ui.nav.SearchBus
import com.haseebflix.app.ui.theme.extras
import com.haseebflix.app.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

enum class SearchSort(val label: String) { RELEVANCE("Relevance"), RATING("Highest rated"), NEWEST("Newest") }
enum class DiscoverChip(val label: String, val feed: Feed, val icon: ImageVector) {
    ALL("All", Feed.TRENDING_WEEK, Icons.Filled.AutoAwesome), TOP("Top 100", Feed.TOP_MOVIE, Icons.Filled.EmojiEvents),
    NEW("New releases", Feed.NOW_PLAYING, Icons.Filled.NewReleases), SERIES("TV series", Feed.POPULAR_TV, Icons.Filled.Tv),
    TOP_TV("Top rated TV", Feed.TOP_TV, Icons.Filled.Star),
}

data class ResultsState(
    val items: List<TitleSummary> = emptyList(), val total: Int = 0, val page: Int = 0, val totalPages: Int = 1,
    val loading: Boolean = false, val error: AppError? = null,
)

data class DiscoverUi(
    val query: String = "", val recent: List<String> = emptyList(), val tab: Int = 0, val sort: SearchSort = SearchSort.RELEVANCE,
    val minRating: Float = 0f, val chip: DiscoverChip = DiscoverChip.ALL,
    val movies: ResultsState = ResultsState(), val tv: ResultsState = ResultsState(), val counts: Map<Int, Int> = emptyMap(),
    val feed: List<TitleSummary> = emptyList(), val upcoming: List<TitleSummary> = emptyList(), val watchKeys: Set<String> = emptySet(),
)

private class Inputs(val q: String, val tab: Int, val sort: SearchSort, val min: Float, val chip: DiscoverChip)
private class HomeBits(val feed: List<TitleSummary>, val upcoming: List<TitleSummary>, val keys: Set<String>, val recent: List<String>)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiscoverViewModel @Inject constructor(private val media: MediaRepository, private val library: LibraryRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val tab = MutableStateFlow(0)
    private val sort = MutableStateFlow(SearchSort.RELEVANCE)
    private val minRating = MutableStateFlow(0f)
    private val chip = MutableStateFlow(DiscoverChip.ALL)
    private val movies = MutableStateFlow(ResultsState())
    private val tv = MutableStateFlow(ResultsState())
    private val counts = MutableStateFlow<Map<Int, Int>>(emptyMap())

    private val inputs = combine(query, tab, sort, minRating, chip) { q, t, s, m, c -> Inputs(q, t, s, m, c) }
    private val results = combine(movies, tv, counts) { a, b, c -> Triple(a, b, c) }
    private val bits = combine(
        chip.flatMapLatest { media.feed(it.feed) }, media.feed(Feed.UPCOMING), library.watchlistKeys, media.recentSearches(),
    ) { f, u, k, r -> HomeBits(f, u, k, r) }

    val state: StateFlow<DiscoverUi> = combine(inputs, results, bits) { i, r, b ->
        DiscoverUi(i.q, b.recent, i.tab, i.sort, i.min, i.chip, r.first, r.second, r.third, b.feed, b.upcoming.filter { it.isUpcoming }.sortedBy { it.date }.take(12), b.keys)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUi())

    init {
        viewModelScope.launch {
            query.debounce(350).map { it.trim() }.distinctUntilChanged().collectLatest { q ->
                if (q.length < 2) { movies.value = ResultsState(); tv.value = ResultsState() }
                else {
                    movies.value = ResultsState(loading = true); tv.value = ResultsState(loading = true)
                    coroutineScope { launch { load(MediaType.MOVIE, q, 1) }; launch { load(MediaType.TV, q, 1) } }
                }
            }
        }
        viewModelScope.launch { chip.collectLatest { media.refreshFeed(it.feed) } }
        viewModelScope.launch { media.refreshFeed(Feed.UPCOMING) }
        Genres.browse.forEach { g -> viewModelScope.launch { media.genreCount(g.id)?.let { c -> counts.update { it + (g.id to c) } } } }
    }

    private suspend fun load(type: MediaType, q: String, page: Int) {
        val st = if (type == MediaType.MOVIE) movies else tv
        media.search(q, type, page).fold(
            { p ->
                st.update { cur ->
                    cur.copy(items = if (page == 1) p.results else cur.items + p.results, total = p.totalResults, page = page, totalPages = p.totalPages, loading = false, error = null)
                }
            },
            { e -> st.update { it.copy(loading = false, error = e as? AppError) } },
        )
    }

    fun loadMore(type: MediaType) {
        val st = if (type == MediaType.MOVIE) movies else tv
        val cur = st.value
        if (cur.loading || cur.page >= cur.totalPages || cur.page == 0) return
        st.update { it.copy(loading = true) }
        viewModelScope.launch { load(type, query.value.trim(), cur.page + 1) }
    }

    fun retry() { val q = query.value; query.value = ""; query.value = q }
    fun setQuery(q: String) { query.value = q }
    fun setTab(i: Int) { tab.value = i }
    fun setSort(s: SearchSort) { sort.value = s }
    fun setMinRating(v: Float) { minRating.value = v }
    fun setChip(c: DiscoverChip) { chip.value = c }
    fun submit() { viewModelScope.launch { media.addRecent(query.value) } }
    fun removeRecent(q: String) { viewModelScope.launch { media.removeRecent(q) } }
    fun clearRecents() { viewModelScope.launch { media.clearRecents() } }
    fun toggleList(t: TitleSummary) { viewModelScope.launch { library.toggleWatchlist(t) } }
    suspend fun trailerKey(t: TitleSummary): String? = media.trailerKey(t.type, t.id)
}

private data class GenreStyle(val color: Color, val icon: ImageVector)
private val genreStyles = mapOf(
    28 to GenreStyle(Color(0xFFE11D2E), Icons.Filled.LocalFireDepartment), 878 to GenreStyle(Color(0xFF4DA3FF), Icons.Filled.RocketLaunch),
    27 to GenreStyle(Color(0xFFFF5A5F), Icons.Filled.Dangerous), 35 to GenreStyle(Color(0xFFFFC107), Icons.Filled.TheaterComedy),
    16 to GenreStyle(Color(0xFFB794F6), Icons.Filled.AutoFixHigh), 18 to GenreStyle(Color(0xFFFF8A80), Icons.Filled.Theaters),
    10749 to GenreStyle(Color(0xFFFF4D9D), Icons.Outlined.FavoriteBorder), 99 to GenreStyle(Color(0xFF34D399), Icons.Filled.Public),
)

private fun List<TitleSummary>.shaped(sort: SearchSort, min: Float) = filter { min <= 0f || it.rating >= min }.let {
    when (sort) {
        SearchSort.RELEVANCE -> it
        SearchSort.RATING -> it.sortedByDescending { t -> t.rating }
        SearchSort.NEWEST -> it.sortedByDescending { t -> t.date }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    unread: Boolean, onOpen: (TitleSummary) -> Unit, onBell: () -> Unit, onAvatar: () -> Unit,
    onGenre: (Int, String) -> Unit, onSeeAll: (String, String) -> Unit,
    vm: DiscoverViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    val searching = ui.query.isNotBlank()
    val focusReq by SearchBus.focus.collectAsStateWithLifecycle()

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(vm::setQuery)
    }
    LaunchedEffect(focusReq) { if (focusReq) { delay(250); runCatching { focus.requestFocus() }; SearchBus.focus.value = false } }
    BackHandler(enabled = searching || focused) { vm.setQuery(""); focusManager.clearFocus() }

    Column(Modifier.fillMaxSize()) {
        TopBrandBar(unread, null, onBell, onAvatar, title = if (searching) "Discover" else null, filledAvatar = true)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                ui.query, vm::setQuery,
                Modifier.weight(1f).focusRequester(focus).onFocusChanged { focused = it.isFocused },
                placeholder = { Text("Search movies, shows…", maxLines = 1) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (ui.query.isNotEmpty()) IconButton({ vm.setQuery("") }) { Icon(Icons.Filled.Close, "Clear search") }
                    else IconButton({
                        runCatching {
                            speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
                        }
                    }) { Icon(Icons.Filled.Mic, "Voice search") }
                },
                singleLine = true, shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.submit(); focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = cs.primary, unfocusedBorderColor = cs.outline,
                    focusedContainerColor = cs.surfaceContainerLow, unfocusedContainerColor = cs.surfaceContainerLow,
                    focusedPlaceholderColor = cs.onSurfaceVariant, unfocusedPlaceholderColor = cs.onSurfaceVariant,
                ),
            )
            Box(Modifier.size(56.dp).clip(CircleShape).background(cs.surfaceContainerLow).border(1.dp, cs.outline, CircleShape).clickable { showFilter = true }, Alignment.Center) {
                Icon(Icons.Filled.Tune, "Filters", tint = cs.onSurface)
            }
        }

        if (focused && ui.recent.isNotEmpty() && !searching) RecentSearches(ui.recent, { vm.setQuery(it); vm.submit(); focusManager.clearFocus() }, vm::removeRecent, vm::clearRecents)

        if (searching) SearchBody(ui, vm, onOpen)
        else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(DiscoverChip.entries) { c -> HfChip(c.label, ui.chip == c, { vm.setChip(c) }, icon = c.icon.takeIf { ui.chip == c }) }
                }
            }
            item {
                Column {
                    SectionHeader("Browse by Genre", "Explore every category")
                    Spacer(Modifier.height(14.dp))
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Genres.browse.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { g -> GenreTile(g, ui.counts[g.id], Modifier.weight(1f)) { onGenre(g.id, g.name) } }
                            }
                        }
                    }
                }
            }
            item {
                TitleRow(
                    if (ui.chip == DiscoverChip.ALL) "Trending This Week" else ui.chip.label, ui.feed, onOpen,
                    subtitle = "Most-watched right now", onSeeAll = { onSeeAll(ui.chip.label, ui.chip.feed.id) },
                )
            }
            if (ui.upcoming.isNotEmpty()) item {
                Column {
                    SectionHeader("Coming Soon", "In theaters & premiering soon", "See all", { onSeeAll("Coming Soon", Feed.UPCOMING.id) })
                    Spacer(Modifier.height(14.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(ui.upcoming, key = { it.key }) { t ->
                            UpcomingCard(t, t.key in ui.watchKeys, { onOpen(t) }, { vm.toggleList(t) }) { scope.launch { ctx.openTrailer(vm.trailerKey(t)) } }
                        }
                    }
                }
            }
        }
    }

    if (showFilter) FilterSheet(ui.sort, ui.minRating, vm::setSort, vm::setMinRating) { showFilter = false }
}

@Composable
private fun RecentSearches(recent: List<String>, onPick: (String) -> Unit, onRemove: (String) -> Unit, onClear: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.History, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text("RECENT SEARCHES", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClear) { Text("Clear all", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.extras.brandText) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(recent, key = { it }) { q ->
                Row(
                    Modifier.height(40.dp).clip(CircleShape).background(cs.surfaceContainerLow).border(1.dp, cs.outlineVariant, CircleShape).clickable { onPick(q) }.padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(q, style = MaterialTheme.typography.labelLarge, color = cs.onSurface, maxLines = 1)
                    IconButton({ onRemove(q) }, Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Remove $q", Modifier.size(16.dp), tint = cs.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun GenreTile(g: Genres.Entry, count: Int?, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val style = genreStyles[g.id] ?: GenreStyle(cs.primary, Icons.Filled.Movie)
    Box(modifier.height(96.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerLow).border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Icon(style.icon, null, tint = style.color.copy(alpha = 0.22f), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(64.dp))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(g.name, style = MaterialTheme.typography.headlineMedium, color = cs.onSurface, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(7.dp).background(style.color, CircleShape))
                Text(if (count != null) "${compactCount(count)} titles" else "…", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UpcomingCard(t: TitleSummary, inList: Boolean, onClick: () -> Unit, onBell: () -> Unit, onTrailer: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val soon = (daysUntil(t.date) ?: 99) <= 14
    Column(Modifier.width(156.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
            Poster(t, Modifier.fillMaxSize())
            RatingBadge(t, Modifier.align(Alignment.TopStart).padding(8.dp))
            shortMonthDay(t.date)?.let {
                Text(
                    it, Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape).background(if (soon) cs.primary else Color(0xCC000000)).padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White,
                )
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(36.dp).clip(CircleShape).background(Color(0xCC000000)).clickable(onClick = onBell), Alignment.Center,
            ) { Icon(if (inList) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications, if (inList) "Remove reminder" else "Remind me", tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        Text(t.title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.genres.firstOrNull() ?: "Movie", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1f))
            Text("Trailer", Modifier.clickable(onClick = onTrailer).padding(vertical = 4.dp, horizontal = 2.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.extras.brandText)
        }
    }
}

@Composable
private fun SearchBody(ui: DiscoverUi, vm: DiscoverViewModel, onOpen: (TitleSummary) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val type = if (ui.tab == 0) MediaType.MOVIE else MediaType.TV
    val st = if (ui.tab == 0) ui.movies else ui.tv
    val items = st.items.shaped(ui.sort, ui.minRating)
    val listState = rememberLazyListState()
    var sortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(listState, type, st.page) {
        snapshotFlow { Pair(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0, listState.layoutInfo.totalItemsCount) }
            .collect { (last, total) -> if (total > 0 && last >= total - 3) vm.loadMore(type) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().drawBottomLine(cs.outlineVariant)) {
            listOf(Triple("Movies", Icons.Outlined.Movie, ui.movies.total), Triple("TV Shows", Icons.Outlined.Tv, ui.tv.total)).forEachIndexed { i, (label, icon, n) ->
                val sel = ui.tab == i
                Column(Modifier.weight(1f).clickable { vm.setTab(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(icon, null, tint = if (sel) MaterialTheme.extras.brandText else cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = if (sel) cs.onSurface else cs.onSurfaceVariant)
                        Text("$n", Modifier.clip(CircleShape).background(if (sel) cs.primaryContainer else cs.surfaceContainer).padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, color = if (sel) cs.onPrimaryContainer else cs.onSurfaceVariant)
                    }
                    Box(Modifier.fillMaxWidth(0.7f).height(3.dp).clip(CircleShape).background(if (sel) cs.primary else Color.Transparent))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Top Results", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            Text("  •  ${st.total} found", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            Box {
                Row(
                    Modifier.clip(CircleShape).background(cs.surfaceContainerLow).border(1.dp, cs.outline, CircleShape).clickable { sortMenu = true }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ui.sort.label, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Icon(Icons.Filled.ExpandMore, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    SearchSort.entries.forEach { s -> DropdownMenuItem({ Text(s.label) }, { vm.setSort(s); sortMenu = false }) }
                }
            }
        }
        when {
            st.loading && st.items.isEmpty() -> ListSkeleton()
            st.error != null && st.items.isEmpty() -> ErrorState(st.error, vm::retry)
            items.isEmpty() -> EmptyState(Icons.Outlined.SearchOff, "No results for “${ui.query.trim()}”", "Check the spelling or try a different title.")
            else -> LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.key }) { t -> ResultCard(t, t.key in ui.watchKeys, { vm.submit(); onOpen(t) }, { vm.toggleList(t) }) }
                if (st.loading) item { Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
            }
        }
    }
}

private fun Modifier.drawBottomLine(color: Color) = this.drawBehind {
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
}

@Composable
fun ResultCard(t: TitleSummary, inList: Boolean, onClick: () -> Unit, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    SurfaceCard(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), onClick) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.width(104.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))) {
                Poster(t, Modifier.fillMaxSize()); RatingBadge(t, Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            Column(Modifier.weight(1f).heightIn(min = 156.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(t.title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onToggle, Modifier.size(36.dp)) {
                        Icon(if (inList) Icons.Filled.BookmarkAdded else Icons.Outlined.BookmarkAdd, if (inList) "Remove from list" else "Add to list", tint = if (inList) MaterialTheme.extras.brandText else cs.onSurface)
                    }
                }
                Text(t.overview.ifBlank { "No overview available yet." }, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (t.year.isNotBlank()) InfoPill(t.year, container = cs.primaryContainer, content = cs.onPrimaryContainer)
                    Text(if (t.isUpcoming) "Upcoming" else if (t.type == MediaType.TV) "Series" else "Movie", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Theaters, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Text(t.genres.take(3).joinToString(", ").ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(sort: SearchSort, minRating: Float, onSort: (SearchSort) -> Unit, onMin: (Float) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismiss, containerColor = cs.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Filters", style = MaterialTheme.typography.headlineMedium, color = cs.onSurface)
            Text("Sort results by", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SearchSort.entries.forEach { s -> HfChip(s.label, sort == s, { onSort(s) }) } }
            Text("Minimum rating: ${if (minRating <= 0f) "Any" else "%.1f+".format(minRating)}", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            Slider(minRating, onMin, valueRange = 0f..9f, steps = 8, colors = SliderDefaults.colors(thumbColor = cs.primary, activeTrackColor = cs.primary))
            PrimaryButton("Done", onDismiss, Modifier.fillMaxWidth())
        }
    }
}
