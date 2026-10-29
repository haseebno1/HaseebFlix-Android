package com.haseebflix.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.haseebflix.app.data.repo.LibraryRepository
import com.haseebflix.app.domain.*
import com.haseebflix.app.ui.components.*
import com.haseebflix.app.ui.theme.LocalDataSaver
import com.haseebflix.app.ui.theme.extras
import com.haseebflix.app.util.TmdbImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibTab(val label: String) { WATCHLIST("Watchlist"), HISTORY("History"), FOLLOWING("Following") }
enum class LibFilter { ALL, MOVIES, TV, LIKED }
enum class LibSort(val label: String) { RECENT("Recently added"), TITLE("Title A–Z"), RATING("Highest rated") }

data class LibraryUi(
    val tab: LibTab = LibTab.WATCHLIST, val filter: LibFilter = LibFilter.ALL, val sort: LibSort = LibSort.RECENT,
    val edit: Boolean = false, val selected: Set<String> = emptySet(),
    val watchlist: List<TitleSummary> = emptyList(), val liked: List<TitleSummary> = emptyList(),
    val following: List<TitleSummary> = emptyList(), val history: List<ContinueItem> = emptyList(),
    val jump: ContinueItem? = null,
) {
    val movieCount get() = watchlist.count { it.type == MediaType.MOVIE }
    val tvCount get() = watchlist.count { it.type == MediaType.TV }
    private fun List<TitleSummary>.sorted() = when (sort) {
        LibSort.RECENT -> this
        LibSort.TITLE -> sortedBy { it.title.lowercase() }
        LibSort.RATING -> sortedByDescending { it.rating }
    }
    private fun List<TitleSummary>.typed() = when (filter) {
        LibFilter.MOVIES -> filter { it.type == MediaType.MOVIE }
        LibFilter.TV -> filter { it.type == MediaType.TV }
        else -> this
    }
    val saved: List<TitleSummary> get() = (if (filter == LibFilter.LIKED) liked else watchlist).typed().sorted()
    val historyShown: List<ContinueItem> get() = when (filter) {
        LibFilter.MOVIES -> history.filter { it.title.type == MediaType.MOVIE }
        LibFilter.TV -> history.filter { it.title.type == MediaType.TV }
        else -> history
    }
}

private class LibData(val watch: List<TitleSummary>, val liked: List<TitleSummary>, val follow: List<TitleSummary>, val history: List<ContinueItem>, val jump: ContinueItem?)
private class LibLocal(val tab: LibTab, val filter: LibFilter, val sort: LibSort, val edit: Boolean, val sel: Set<String>)

@HiltViewModel
class LibraryViewModel @Inject constructor(private val library: LibraryRepository) : ViewModel() {
    private val tab = MutableStateFlow(LibTab.WATCHLIST)
    private val filter = MutableStateFlow(LibFilter.ALL)
    private val sort = MutableStateFlow(LibSort.RECENT)
    private val edit = MutableStateFlow(false)
    private val selected = MutableStateFlow<Set<String>>(emptySet())

    private val data = combine(library.watchlist, library.favorites, library.following, library.history, library.continueWatching) { w, l, f, h, c ->
        LibData(w, l, f, h, c.firstOrNull())
    }
    private val local = combine(tab, filter, sort, edit, selected) { t, f, s, e, sel -> LibLocal(t, f, s, e, sel) }

    val state: StateFlow<LibraryUi> = combine(data, local) { d, l ->
        LibraryUi(l.tab, l.filter, l.sort, l.edit, l.sel, d.watch, d.liked, d.follow, d.history, d.jump)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUi())

    fun setTab(t: LibTab) { tab.value = t; edit.value = false; selected.value = emptySet() }
    fun setFilter(f: LibFilter) { filter.value = f }
    fun setSort(s: LibSort) { sort.value = s }
    fun toggleEdit() { edit.value = !edit.value; selected.value = emptySet() }
    fun toggleSelect(key: String) { selected.update { if (key in it) it - key else it + key } }
    fun remove(items: Collection<TitleSummary>) { viewModelScope.launch { library.removeFromWatchlist(items) } }
    fun undo(items: Collection<TitleSummary>) { viewModelScope.launch { items.forEach { library.toggleWatchlist(it) } } }
    fun removeSelected(all: List<TitleSummary>) {
        val items = all.filter { it.key in selected.value }; remove(items); selected.value = emptySet(); edit.value = false
    }
    fun removeHistory(t: TitleSummary) { viewModelScope.launch { library.removeFromHistory(t) } }
    fun unfollow(t: TitleSummary) { viewModelScope.launch { library.toggleFollow(t) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    unread: Boolean, onOpen: (TitleSummary) -> Unit, onPlay: (TitleSummary, Int, Int) -> Unit,
    onSearch: () -> Unit, onBell: () -> Unit, onAvatar: () -> Unit, onBrowse: () -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val brand = MaterialTheme.extras.brandText
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var sortMenu by remember { mutableStateOf(false) }

    fun play(t: TitleSummary, s: Int = 0, e: Int = 0) { if (t.type == MediaType.MOVIE) onPlay(t, 0, 0) else if (s > 0) onPlay(t, s, e) else onOpen(t) }

    Column(Modifier.fillMaxSize()) {
        TopBrandBar(unread, onSearch, onBell, onAvatar)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.Bottom) {
                    LibTab.entries.forEach { t ->
                        val sel = ui.tab == t
                        Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.setTab(t) }.padding(end = 20.dp)) {
                            Text(t.label, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = if (sel) cs.onSurface else cs.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.width(if (sel) 44.dp else 0.dp).height(3.dp).clip(CircleShape).background(cs.primary))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (ui.tab == LibTab.WATCHLIST) TextButton(vm::toggleEdit) {
                        Icon(if (ui.edit) Icons.Filled.Check else Icons.Outlined.Edit, null, tint = brand, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (ui.edit) "Done" else "Edit", style = MaterialTheme.typography.labelLarge, color = brand)
                    }
                }
            }
            if (ui.tab != LibTab.FOLLOWING) item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { HfChip("All (${ui.watchlist.size})", ui.filter == LibFilter.ALL, { vm.setFilter(LibFilter.ALL) }) }
                    item { HfChip("Movies (${ui.movieCount})", ui.filter == LibFilter.MOVIES, { vm.setFilter(LibFilter.MOVIES) }) }
                    item { HfChip("TV Shows (${ui.tvCount})", ui.filter == LibFilter.TV, { vm.setFilter(LibFilter.TV) }) }
                    if (ui.tab == LibTab.WATCHLIST) {
                        item { HfChip("Liked (${ui.liked.size})", ui.filter == LibFilter.LIKED, { vm.setFilter(LibFilter.LIKED) }, icon = Icons.Filled.Favorite) }
                        item {
                            Box {
                                HfChip(ui.sort.label, false, { sortMenu = true }, icon = Icons.Filled.SwapVert)
                                DropdownMenu(sortMenu, { sortMenu = false }) { LibSort.entries.forEach { s -> DropdownMenuItem({ Text(s.label) }, { vm.setSort(s); sortMenu = false }) } }
                            }
                        }
                    }
                }
            }
            ui.jump?.let { j ->
                if (ui.tab != LibTab.FOLLOWING) item { JumpBackIn(j) { play(j.title, j.season, j.episode) } }
            }

            when (ui.tab) {
                LibTab.WATCHLIST -> {
                    item {
                        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Saved Titles", style = MaterialTheme.typography.headlineMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
                            if (ui.saved.isNotEmpty() && !ui.edit) Text("Swipe left to remove", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                    }
                    if (ui.saved.isEmpty()) item { EmptyState(Icons.Outlined.BookmarkBorder, "Nothing saved yet", "Tap the bookmark on any title to add it to your watchlist.", cta = "Browse titles", onCta = onBrowse) }
                    items(ui.saved, key = { it.key }) { t ->
                        SwipeRow(t.key, enabled = !ui.edit, onDismiss = {
                            vm.remove(listOf(t))
                            scope.launch {
                                if (snack.showSnackbar("Removed “${t.title}”", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undo(listOf(t))
                            }
                        }) {
                            LibraryCard(
                                t, onClick = { if (ui.edit) vm.toggleSelect(t.key) else onOpen(t) }, onPlay = { play(t) },
                                selectable = ui.edit, selected = t.key in ui.selected,
                                menu = listOf("Open details" to { onOpen(t) }, "Remove from watchlist" to { vm.remove(listOf(t)) }),
                            )
                        }
                    }
                }
                LibTab.HISTORY -> {
                    item { Text("Recently Watched", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.headlineMedium, color = cs.onSurface) }
                    if (ui.historyShown.isEmpty()) item { EmptyState(Icons.Outlined.History, "Nothing watched yet", "Titles you play will show up here so you can pick up where you left off.", cta = "Browse titles", onCta = onBrowse) }
                    items(ui.historyShown, key = { it.title.key }) { h ->
                        LibraryCard(
                            h.title, onClick = { onOpen(h.title) }, onPlay = { play(h.title, h.season, h.episode) }, progress = h.fraction,
                            caption = if (h.title.type == MediaType.TV) "S${h.season} • E${h.episode} · ${h.remainingMin}m left" else "${h.remainingMin}m left",
                            menu = listOf("Open details" to { onOpen(h.title) }, "Remove from history" to { vm.removeHistory(h.title) }),
                        )
                    }
                }
                LibTab.FOLLOWING -> {
                    item { Text("Shows You Follow", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.headlineMedium, color = cs.onSurface) }
                    if (ui.following.isEmpty()) item { EmptyState(Icons.Outlined.NotificationsNone, "Not following any shows", "Tap “Track” on a TV show to get an alert when a new episode airs.", cta = "Find shows", onCta = onBrowse) }
                    items(ui.following, key = { it.key }) { t ->
                        LibraryCard(
                            t, onClick = { onOpen(t) }, onPlay = { onOpen(t) }, caption = "Tracking new episodes",
                            menu = listOf("Open details" to { onOpen(t) }, "Stop tracking" to { vm.unfollow(t) }),
                        )
                    }
                }
            }
            item { PremierePromo(onBrowse) }
        }
        if (ui.edit && ui.selected.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().background(cs.surface).padding(16.dp)) {
                PrimaryButton("Remove ${ui.selected.size} selected", { vm.removeSelected(ui.saved) }, Modifier.fillMaxWidth(), Icons.Outlined.Delete)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(key: String, enabled: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v -> if (v == SwipeToDismissBoxValue.EndToStart) { onDismiss(); true } else false })
    SwipeToDismissBox(
        state, modifier = Modifier.padding(horizontal = 16.dp), enableDismissFromStartToEnd = false, enableDismissFromEndToStart = enabled,
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(end = 24.dp), Alignment.CenterEnd) {
                Icon(Icons.Outlined.Delete, "Remove", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}

@Composable
private fun LibraryCard(
    t: TitleSummary, onClick: () -> Unit, onPlay: () -> Unit, progress: Float? = null, caption: String? = null,
    selectable: Boolean = false, selected: Boolean = false, menu: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    SurfaceCard(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), onClick) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectable) Checkbox(selected, { onClick() }, colors = CheckboxDefaults.colors(checkedColor = cs.primary))
            Box(Modifier.width(88.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))) {
                Poster(t, Modifier.fillMaxSize()); RatingBadge(t, Modifier.align(Alignment.TopStart).padding(5.dp))
                if (progress != null) LinearProgressIndicator({ progress }, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp), color = cs.primary, trackColor = Color(0x55FFFFFF), gapSize = 0.dp, drawStopIndicator = {})
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(t.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (menu.isNotEmpty()) Box {
                        IconButton({ open = true }, Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, "More options", tint = cs.onSurfaceVariant) }
                        DropdownMenu(open, { open = false }) { menu.forEach { (label, act) -> DropdownMenuItem({ Text(label) }, { open = false; act() }) } }
                    }
                }
                Text(caption ?: t.overview.ifBlank { "No overview available yet." }, style = MaterialTheme.typography.bodyMedium, color = if (caption != null) MaterialTheme.extras.brandText else cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.CalendarToday, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                    Text(t.year.ifBlank { "TBA" }, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = cs.onSurface)
                    Text("•", color = cs.onSurfaceVariant)
                    Text(if (t.type == MediaType.TV) "Series" else "Movie", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Text(t.genres.take(2).joinToString(", ").ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!selectable) CircleIconButton(Icons.Filled.PlayArrow, "Play ${t.title}", onPlay, container = cs.surfaceContainerHigh, size = 48.dp)
        }
    }
}

@Composable
private fun JumpBackIn(j: ContinueItem, onResume: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val saver = LocalDataSaver.current
    SurfaceCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(20.dp), onResume) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PlayCircle, null, tint = MaterialTheme.extras.brandText, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text("JUMP BACK IN", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp), color = MaterialTheme.extras.brandText, modifier = Modifier.weight(1f))
                Text("${j.remainingMin}m remaining", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(112.dp).aspectRatio(16f / 10f).clip(RoundedCornerShape(12.dp))) {
                    RemoteImage(TmdbImage.backdrop(j.title.backdropPath ?: j.title.posterPath, 300, saver), Modifier.fillMaxSize())
                    if (j.title.type == MediaType.TV) Text("S${j.season} : E${j.episode}", Modifier.align(Alignment.BottomStart).padding(8.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(j.title.title, style = MaterialTheme.typography.titleLarge, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (j.title.type == MediaType.TV) "Season ${j.season} · Episode ${j.episode}" else j.title.metaLineShort(), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1)
                    LinearProgressIndicator({ j.fraction }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = cs.primary, trackColor = cs.surfaceContainerHighest, gapSize = 0.dp, drawStopIndicator = {})
                }
                Box(Modifier.size(56.dp).clip(CircleShape).background(cs.primary), Alignment.Center) { Icon(Icons.Filled.PlayArrow, "Resume", tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
        }
    }
}

private fun TitleSummary.metaLineShort() = listOf(year, genres.firstOrNull().orEmpty()).filter { it.isNotBlank() }.joinToString(" • ")

@Composable
private fun PremierePromo(onBrowse: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    SurfaceCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(cs.surfaceContainerHigh).border(1.dp, cs.outlineVariant, CircleShape), Alignment.Center) {
                Icon(Icons.Filled.NotificationsActive, null, tint = MaterialTheme.extras.brandText, modifier = Modifier.size(34.dp))
            }
            Text("Never Miss a Premiere", style = MaterialTheme.typography.headlineMedium, color = cs.onSurface, textAlign = TextAlign.Center)
            Text("Track ongoing shows and get notified when new episodes drop.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            PrimaryButton("Browse What's Next", onBrowse, icon = Icons.Outlined.Explore)
        }
    }
}
