package com.haseebflix.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.haseebflix.app.data.local.ProgressEntity
import com.haseebflix.app.data.repo.LibraryRepository
import com.haseebflix.app.data.repo.MediaRepository
import com.haseebflix.app.domain.*
import com.haseebflix.app.ui.components.*
import com.haseebflix.app.ui.nav.DetailsRoute
import com.haseebflix.app.ui.theme.LocalDataSaver
import com.haseebflix.app.ui.theme.extras
import com.haseebflix.app.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailsUi(
    val detail: TitleDetail? = null, val loading: Boolean = true, val error: AppError? = null,
    val inList: Boolean = false, val liked: Boolean = false, val following: Boolean = false,
    val progress: List<ProgressEntity> = emptyList(),
    val season: Int = 1, val episodes: List<Episode> = emptyList(), val epLoading: Boolean = false, val epError: AppError? = null,
    val newest: Boolean = false, val unwatched: Boolean = false,
) {
    val latest: ProgressEntity? get() = progress.maxByOrNull { it.updatedAt }
}

private class Flags(val inList: Boolean, val liked: Boolean, val following: Boolean, val progress: List<ProgressEntity>)
private data class DLocal(
    val season: Int = 1, val episodes: List<Episode> = emptyList(), val epLoading: Boolean = false, val epError: AppError? = null,
    val newest: Boolean = false, val unwatched: Boolean = false, val loading: Boolean = true, val error: AppError? = null,
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    saved: SavedStateHandle, private val media: MediaRepository, private val library: LibraryRepository,
) : ViewModel() {
    private val route = saved.toRoute<DetailsRoute>()
    val type = MediaType.of(route.type)
    val id = route.id
    private val key = "${type.name}:$id"
    private val local = MutableStateFlow(DLocal())
    private val detail = media.detail(type, id)
    private val flags = combine(library.watchlistKeys, library.favoriteKeys, library.followIds, library.progressFor(type, id)) { w, f, fo, p ->
        Flags(key in w, key in f, id in fo, p)
    }

    val state: StateFlow<DetailsUi> = combine(detail, flags, local) { d, f, l ->
        DetailsUi(d, l.loading && d == null, if (d == null) l.error else null, f.inList, f.liked, f.following && type == MediaType.TV, f.progress, l.season, l.episodes, l.epLoading, l.epError, l.newest, l.unwatched)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailsUi())

    init { load() }

    fun load() {
        viewModelScope.launch {
            local.update { it.copy(loading = true, error = null) }
            val r = media.refreshDetail(type, id)
            local.update { it.copy(loading = false, error = r.exceptionOrNull() as? AppError) }
            if (type == MediaType.TV) {
                val d = detail.first() ?: return@launch
                val last = library.progressFor(type, id).first().maxByOrNull { it.updatedAt }
                val first = d.seasons.firstOrNull { it.number >= 1 }?.number ?: 1
                selectSeason(last?.season?.takeIf { it >= 1 } ?: first)
            }
        }
    }

    fun selectSeason(n: Int) {
        local.update { it.copy(season = n, episodes = emptyList(), epLoading = true, epError = null) }
        viewModelScope.launch {
            media.episodes(id, n).fold(
                { eps -> local.update { if (it.season == n) it.copy(episodes = eps, epLoading = false) else it } },
                { e -> local.update { if (it.season == n) it.copy(epLoading = false, epError = e as? AppError) else it } },
            )
        }
    }

    fun setNewest(v: Boolean) { local.update { it.copy(newest = v) } }
    fun setUnwatched(v: Boolean) { local.update { it.copy(unwatched = v) } }
    private fun withSummary(block: suspend (TitleSummary) -> Unit) { viewModelScope.launch { detail.first()?.let { block(it.toSummary()) } } }
    fun toggleList() = withSummary(library::toggleWatchlist)
    fun toggleLike() = withSummary(library::toggleFavorite)
    fun toggleFollow() = withSummary(library::toggleFollow)
}

// ───────────── row model (lets chips scroll to sections and wide mode split into panes) ─────────────
private sealed interface DRow { val key: String }
private data object RHero : DRow { override val key = "hero" }
private data object RTabs : DRow { override val key = "tabs" }
private data object RStory : DRow { override val key = "story" }
private data object RStats : DRow { override val key = "stats" }
private data object RInfo : DRow { override val key = "info" }
private data object REpHeader : DRow { override val key = "epheader" }
private data object REpState : DRow { override val key = "epstate" }
private data class REp(val ep: Episode, val frac: Float) : DRow { override val key = "ep${ep.season}_${ep.number}" }
private data object RCast : DRow { override val key = "cast" }
private data object RSimilar : DRow { override val key = "similar" }
private data object RFooter : DRow { override val key = "footer" }

private fun DRow.pane(tv: Boolean) = when (this) {
    RHero, RStory, RStats -> 0
    RInfo -> if (tv) 1 else 0
    else -> 1
}

private class DActions(
    val onBack: () -> Unit, val onOpen: (TitleSummary) -> Unit, val onPlayMain: () -> Unit, val onPlayEpisode: (Int, Int) -> Unit,
    val onTrailer: () -> Unit, val onDownload: () -> Unit, val onToggleList: () -> Unit, val onToggleLike: () -> Unit,
    val onToggleFollow: () -> Unit, val onShare: () -> Unit, val onSeason: (Int) -> Unit, val onNewest: (Boolean) -> Unit,
    val onUnwatched: (Boolean) -> Unit, val onCast: () -> Unit,
)

private class PlayTarget(val season: Int, val episode: Int, val label: String)

private fun playTarget(d: TitleDetail, ui: DetailsUi): PlayTarget {
    val latest = ui.latest
    val remaining = latest?.let { ((it.durationMs - it.positionMs) / 60_000).toInt().coerceAtLeast(0) } ?: 0
    val unfinished = latest != null && latest.durationMs > 0 && latest.positionMs.toFloat() / latest.durationMs < 0.95f
    return if (d.type == MediaType.MOVIE) PlayTarget(0, 0, if (unfinished) "Resume Movie (${remaining}m left)" else "Play Movie")
    else when {
        latest == null -> PlayTarget(d.seasons.firstOrNull { it.number >= 1 }?.number ?: 1, 1, "Play S${d.seasons.firstOrNull { it.number >= 1 }?.number ?: 1}:E1")
        unfinished -> PlayTarget(latest.season, latest.episode, "Resume S${latest.season}:E${latest.episode} (${remaining}m left)")
        else -> PlayTarget(latest.season, latest.episode + 1, "Play S${latest.season}:E${latest.episode + 1} (Next)")
    }
}

@Composable
fun DetailsScreen(
    wide: Boolean, onBack: () -> Unit, onOpen: (TitleSummary) -> Unit,
    onPlay: (String, Int, Int, Int) -> Unit, vm: DetailsViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val d = ui.detail
    var castSheet by remember { mutableStateOf(false) }

    if (d == null) {
        Box(Modifier.fillMaxSize()) {
            if (ui.loading) DetailsSkeleton() else ErrorState(ui.error, vm::load, Modifier.align(Alignment.Center))
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, Modifier.statusBarsPadding().padding(12.dp), Color(0x66000000), Color.White)
        }
        return
    }

    val target = playTarget(d, ui)
    val a = DActions(
        onBack = onBack, onOpen = onOpen,
        onPlayMain = { onPlay(d.type.api, d.id, target.season, target.episode) },
        onPlayEpisode = { s, e -> onPlay(d.type.api, d.id, s, e) },
        onTrailer = { ctx.openTrailer(d.trailerKey) },
        onDownload = { scope.launch { snack.showSnackbar("Downloads aren't available yet") } },
        onToggleList = vm::toggleList, onToggleLike = vm::toggleLike, onToggleFollow = {
            vm.toggleFollow(); scope.launch { snack.showSnackbar(if (ui.following) "You'll stop getting alerts for ${d.title}" else "Tracking ${d.title}. We'll alert you on new episodes") }
        },
        onShare = { ctx.shareTitle(d.title, d.type.api, d.id) },
        onSeason = vm::selectSeason, onNewest = vm::setNewest, onUnwatched = vm::setUnwatched, onCast = { castSheet = true },
    )
    val tv = d.type == MediaType.TV
    val epRows = remember(ui.episodes, ui.progress, ui.season, ui.newest, ui.unwatched) {
        ui.episodes.map { e ->
            val p = ui.progress.firstOrNull { it.season == e.season && it.episode == e.number }
            REp(e, if (p != null && p.durationMs > 0) (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) else 0f)
        }.filter { !ui.unwatched || it.frac < 0.9f }.let { l -> if (ui.newest) l.sortedByDescending { it.ep.number } else l.sortedBy { it.ep.number } }
    }
    val rows = buildList<DRow> {
        add(RHero); if (!wide) add(RTabs); add(RStory)
        if (tv) { add(RStats); add(REpHeader); if (epRows.isEmpty()) add(REpState) else addAll(epRows) } else add(RInfo)
        if (d.cast.isNotEmpty()) add(RCast)
        if (d.similar.isNotEmpty()) add(RSimilar)
        if (tv) add(RInfo)
        add(RFooter)
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.55f)) {
                DetailList(rows.filter { it.pane(tv) == 0 }, d, ui, a, target, Modifier.fillMaxSize())
                TopOverlay(ui, a)
            }
            DetailList(rows.filter { it.pane(tv) == 1 }, d, ui, a, target, Modifier.weight(0.45f).fillMaxHeight().statusBarsPadding())
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            DetailList(rows, d, ui, a, target, Modifier.fillMaxSize())
            TopOverlay(ui, a)
        }
    }
    if (castSheet) CastSheet(d.cast) { castSheet = false }
}

@Composable
private fun TopOverlay(ui: DetailsUi, a: DActions) {
    val dark = Color(0x66000000)
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", a.onBack, container = dark, tint = Color.White)
        Spacer(Modifier.weight(1f))
        CircleIconButton(if (ui.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (ui.liked) "Remove like" else "Like", a.onToggleLike, container = dark, tint = if (ui.liked) MaterialTheme.colorScheme.primary else Color.White)
        CircleIconButton(Icons.Outlined.Share, "Share", a.onShare, container = dark, tint = Color.White)
    }
}

@Composable
private fun DetailList(rows: List<DRow>, d: TitleDetail, ui: DetailsUi, a: DActions, target: PlayTarget, modifier: Modifier) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val anchors = listOfNotNull("story" to "Overview", if (d.type == MediaType.TV) "epheader" to "Episodes & Seasons" else null, "cast" to "Cast & Crew", "similar" to "More Like This")
        .filter { (k, _) -> rows.any { it.key == k } }
    val current by remember(rows) {
        derivedStateOf {
            val first = state.firstVisibleItemIndex
            anchors.lastOrNull { (k, _) -> rows.indexOfFirst { it.key == k } <= first }?.first ?: anchors.firstOrNull()?.first ?: ""
        }
    }
    LazyColumn(modifier, state = state, contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                RHero -> HeroBlock(d, ui, a, target)
                RTabs -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(anchors, key = { it.first }) { (k, label) ->
                        HfChip(label, current == k, { scope.launch { state.animateScrollToItem(rows.indexOfFirst { it.key == k }.coerceAtLeast(0)) } })
                    }
                }
                RStory -> Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (d.tagline.isNotBlank()) Text("“${d.tagline}”", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal), color = MaterialTheme.extras.brandText)
                    Text(if (d.type == MediaType.MOVIE) "Storyline" else "Overview", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(d.overview.ifBlank { "No overview available yet." }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                }
                RStats -> StatsCard(d)
                RInfo -> InfoCard(d)
                REpHeader -> EpisodeHeader(d, ui, a)
                REpState -> Box(Modifier.fillMaxWidth()) {
                    when {
                        ui.epLoading -> ListSkeleton(2)
                        ui.epError != null -> ErrorState(ui.epError, { a.onSeason(ui.season) })
                        else -> EmptyState(Icons.Outlined.Tv, "No episodes to show", "Nothing matches this season or filter yet.")
                    }
                }
                is REp -> EpisodeCard(row, { if (isFuture(row.ep.airDate)) {} else a.onPlayEpisode(row.ep.season, row.ep.number) }, a.onShare, a.onDownload)
                RCast -> CastSection(d, a)
                RSimilar -> TitleRow("More Like This", d.similar, a.onOpen, subtitle = null, cardWidth = 140.dp)
                RFooter -> Footer()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroBlock(d: TitleDetail, ui: DetailsUi, a: DActions, target: PlayTarget) {
    val cs = MaterialTheme.colorScheme
    val saver = LocalDataSaver.current
    val tv = d.type == MediaType.TV
    Column {
        Box(Modifier.fillMaxWidth().height(340.dp)) {
            RemoteImage(TmdbImage.backdrop(d.backdropPath ?: d.posterPath, 1280, saver), Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.25f to Color.Transparent, 0.95f to MaterialTheme.extras.canvas)))
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tv) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(7.dp).background(cs.primary, CircleShape))
                        Text((d.networks.firstOrNull() ?: "Series").uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), color = MaterialTheme.extras.brandText)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("FEATURE", Modifier.clip(RoundedCornerShape(6.dp)).background(cs.primary).padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = Color.White)
                        d.genres.firstOrNull()?.let { Text(it.name.uppercase(), Modifier.clip(RoundedCornerShape(6.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = cs.onSurfaceVariant) }
                    }
                }
                Text(d.title, style = MaterialTheme.typography.displayMedium.copy(fontSize = 32.sp, lineHeight = 38.sp), color = cs.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.clip(CircleShape).background(cs.surfaceContainerLow).border(1.dp, cs.outlineVariant, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Filled.Star, null, tint = MaterialTheme.extras.rating, modifier = Modifier.size(16.dp))
                    Text(if (d.hasRating) "%.1f".format(d.rating) else "—", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = cs.onSurface)
                    Text("/10", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                val years = if (tv) "${d.year}${if (d.status.equals("Ended", true) || d.status.equals("Canceled", true)) " – ${d.lastDate.orEmpty().take(4)}" else " – Present"}" else d.year
                if (years.isNotBlank()) Text(years, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                Text(if (tv) "${d.seasons.count { it.number >= 1 }} seasons" else runtimeText(d.runtime) ?: "Runtime TBA", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                d.certification?.let { InfoPill(it, bordered = true, container = Color.Transparent) }
            }
            PrimaryButton(target.label, a.onPlayMain, Modifier.fillMaxWidth(), Icons.Filled.PlayArrow, height = 58.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tv) {
                    ActionPill(if (ui.following) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone, if (ui.following) "Tracking" else "Track", a.onToggleFollow, Modifier.weight(1f), highlight = ui.following)
                    CircleIconButton(if (ui.inList) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, if (ui.inList) "Remove from watchlist" else "Add to watchlist", a.onToggleList, container = cs.surfaceContainerHigh, tint = if (ui.inList) MaterialTheme.extras.brandText else cs.onSurface, size = 52.dp)
                    CircleIconButton(Icons.Outlined.Download, "Download", a.onDownload, container = cs.surfaceContainerHigh, size = 52.dp)
                } else {
                    ActionPill(if (ui.inList) Icons.Filled.Check else Icons.Filled.Add, if (ui.inList) "In list" else "Watchlist", a.onToggleList, Modifier.weight(1f), highlight = ui.inList)
                    ActionPill(Icons.Outlined.PlayCircleOutline, "Trailer", a.onTrailer, Modifier.weight(1f))
                    ActionPill(Icons.Outlined.Download, "Download", a.onDownload, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ActionPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.height(52.dp).clip(CircleShape).background(if (highlight) cs.primaryContainer else cs.surfaceContainerHigh).clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (highlight) MaterialTheme.extras.brandText else cs.onSurface, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onSurface, maxLines = 1)
    }
}

@Composable
private fun StatsCard(d: TitleDetail) {
    val cs = MaterialTheme.colorScheme
    val seasons = d.seasons.count { it.number >= 1 }
    val status = when { d.status.equals("Returning Series", true) -> "Ongoing"; d.status.isBlank() -> "—"; else -> d.status }
    SurfaceCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(vertical = 14.dp)) {
            listOf(compactCount(d.episodeCount).removeSuffix("+") to "Total Episodes", "$seasons" to "Seasons", status to "Status").forEachIndexed { i, (v, l) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(v, style = MaterialTheme.typography.titleLarge, color = if (i == 2) MaterialTheme.extras.rating else cs.onSurface, maxLines = 1)
                    Text(l, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InfoCard(d: TitleDetail) {
    val cs = MaterialTheme.colorScheme
    val tv = d.type == MediaType.TV
    val cells = if (tv) listOf(
        "Network" to d.networks.joinToString(", ").ifBlank { "—" }, "Origin" to d.countries.joinToString(", ").ifBlank { "—" },
        "First Aired" to (longDate(d.date) ?: "—"), "Status" to d.status.ifBlank { "—" },
    ) else listOf(
        "Director" to (d.director ?: "—"), "Studio" to d.studios.joinToString(" / ").ifBlank { "—" },
        "Languages" to d.languages.take(3).joinToString(", ").ifBlank { d.originalLanguage.uppercase().ifBlank { "—" } }, "Release" to (longDate(d.date) ?: "—"),
    )
    SurfaceCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (tv) Text("About the Show", style = MaterialTheme.typography.headlineSmall, color = cs.onSurface)
            cells.chunked(2).forEach { pair ->
                Row {
                    pair.forEach { (k, v) ->
                        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(k, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeHeader(d: TitleDetail, ui: DetailsUi, a: DActions) {
    val cs = MaterialTheme.colorScheme
    var seasonMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    val info = d.seasons.firstOrNull { it.number == ui.season }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Row(
                Modifier.clip(CircleShape).background(cs.surfaceContainerHigh).border(1.dp, cs.outlineVariant, CircleShape).clickable { seasonMenu = true }.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(if (ui.season == 0) "Specials" else "Season ${ui.season}" + (info?.episodes?.takeIf { it > 0 }?.let { " ($it Ep)" } ?: ""), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Icon(Icons.Filled.ExpandMore, null, tint = cs.onSurface, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(seasonMenu, { seasonMenu = false }) {
                d.seasons.sortedBy { if (it.number == 0) 999 else it.number }.forEach { s ->
                    DropdownMenuItem({ Text(if (s.number == 0) "Specials" else "Season ${s.number}" + if (s.episodes > 0) " · ${s.episodes} episodes" else "") }, { seasonMenu = false; a.onSeason(s.number) })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box {
            Row(
                Modifier.clip(CircleShape).background(cs.surfaceContainerHigh).clickable { filterMenu = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) { Icon(Icons.Filled.FilterList, null, Modifier.size(18.dp), tint = cs.onSurface); Text("Filter", style = MaterialTheme.typography.labelLarge, color = cs.onSurface) }
            DropdownMenu(filterMenu, { filterMenu = false }) {
                DropdownMenuItem({ Text("Oldest first") }, { a.onNewest(false); filterMenu = false }, leadingIcon = { if (!ui.newest) Icon(Icons.Filled.Check, null) })
                DropdownMenuItem({ Text("Newest first") }, { a.onNewest(true); filterMenu = false }, leadingIcon = { if (ui.newest) Icon(Icons.Filled.Check, null) })
                HorizontalDivider()
                DropdownMenuItem({ Text("Unwatched only") }, { a.onUnwatched(!ui.unwatched); filterMenu = false }, leadingIcon = { if (ui.unwatched) Icon(Icons.Filled.Check, null) })
            }
        }
    }
}

@Composable
private fun EpisodeCard(r: REp, onPlay: () -> Unit, onShare: () -> Unit, onDownload: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val saver = LocalDataSaver.current
    val e = r.ep
    val future = isFuture(e.airDate)
    val inProgress = r.frac in 0.01f..0.9f
    SurfaceCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp).alpha(if (future) 0.65f else 1f), RoundedCornerShape(20.dp), onPlay) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            RemoteImage(TmdbImage.still(e.still, 300, saver), Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).size(52.dp).background(if (inProgress) cs.primary else Color(0x99000000), CircleShape), Alignment.Center) {
                Icon(if (future) Icons.Outlined.Schedule else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            e.runtime?.let { Text("${it}m", Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium, color = Color.White) }
            if (r.frac > 0f) LinearProgressIndicator({ r.frac }, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp), color = cs.primary, trackColor = Color(0x55FFFFFF), gapSize = 0.dp, drawStopIndicator = {})
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("E${e.number}" + if (inProgress) " • IN PROGRESS" else "", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = if (inProgress) MaterialTheme.extras.brandText else cs.onSurfaceVariant)
                Text(relativeDate(e.airDate), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Text(e.name, style = MaterialTheme.typography.titleLarge, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (e.overview.isNotBlank()) Text(e.overview, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (r.frac >= 0.9f) { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.extras.success, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Watched", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
                else if (r.frac > 0f) { Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.extras.warning, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Watched ${(r.frac * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
                else { Icon(Icons.Outlined.Schedule, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(e.runtime?.let { "$it mins" } ?: "Runtime TBA", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
                Spacer(Modifier.weight(1f))
                IconButton(onDownload, Modifier.size(40.dp)) { Icon(Icons.Outlined.Download, "Download", tint = cs.onSurfaceVariant) }
                IconButton(onShare, Modifier.size(40.dp)) { Icon(Icons.Outlined.Share, "Share", tint = cs.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun CastAvatar(c: CastMember, size: androidx.compose.ui.unit.Dp) {
    val saver = LocalDataSaver.current
    val url = TmdbImage.profile(c.photo, saver)
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape), Alignment.Center) {
        if (url != null) RemoteImage(url, Modifier.fillMaxSize(), description = c.name)
        else Text(c.name.split(" ").take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.extras.brandText)
    }
}

@Composable
private fun CastSection(d: TitleDetail, a: DActions) {
    val cs = MaterialTheme.colorScheme
    Column {
        SectionHeader(if (d.type == MediaType.TV) "Cast" else "Top Billed Cast", null, "View All (${d.cast.size})", a.onCast)
        Spacer(Modifier.height(14.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(d.cast.take(12), key = { it.id }) { c ->
                Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CastAvatar(c, 72.dp)
                    Text(c.name, style = MaterialTheme.typography.labelLarge, color = cs.onSurface, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                    if (c.role.isNotBlank()) Text(c.role, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CastSheet(cast: List<CastMember>, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismiss, containerColor = cs.surfaceContainerHigh) {
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Cast (${cast.size})", style = MaterialTheme.typography.headlineMedium, color = cs.onSurface) }
            items(cast, key = { it.id }) { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CastAvatar(c, 52.dp)
                    Column {
                        Text(c.name, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                        if (c.role.isNotBlank()) Text(c.role, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Footer() {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("TMDB", Modifier.clip(RoundedCornerShape(6.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color(0xFF01B4E4))
            Text("Data and assets provided by The Movie Database", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        Text("This product uses the TMDB API but is not endorsed or certified by TMDB.", style = MaterialTheme.typography.bodySmall, color = cs.outline)
    }
}

@Composable
private fun DetailsSkeleton() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SkeletonBox(Modifier.fillMaxWidth().height(340.dp), RectangleShape)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SkeletonBox(Modifier.width(220.dp).height(20.dp)); SkeletonBox(Modifier.fillMaxWidth().height(58.dp), CircleShape)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(3) { SkeletonBox(Modifier.weight(1f).height(52.dp), CircleShape) } }
            SkeletonBox(Modifier.fillMaxWidth().height(14.dp)); SkeletonBox(Modifier.fillMaxWidth(0.9f).height(14.dp)); SkeletonBox(Modifier.fillMaxWidth(0.7f).height(14.dp))
        }
    }
}
