package com.haseebflix.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.haseebflix.app.data.repo.MediaRepository
import com.haseebflix.app.domain.*
import com.haseebflix.app.ui.components.*
import com.haseebflix.app.ui.nav.GridRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GridUi(
    val items: List<TitleSummary> = emptyList(), val page: Int = 0, val totalPages: Int = 1, val loading: Boolean = true,
    val error: AppError? = null, val type: MediaType = MediaType.MOVIE, val sort: SortOption = SortOption.POPULAR,
)

/** Serves both "See all" feed pages and genre results. */
@HiltViewModel
class GridViewModel @Inject constructor(saved: SavedStateHandle, private val media: MediaRepository) : ViewModel() {
    private val route = saved.toRoute<GridRoute>()
    val title = route.title
    val isGenre = route.feed.isEmpty()
    private val _ui = MutableStateFlow(GridUi(type = MediaType.of(route.type)))
    val ui = _ui.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val u = _ui.value
            val next = u.page + 1
            val r = if (isGenre) media.discoverPage(u.type, route.genreId, u.sort, next) else media.feedPage(Feed.of(route.feed), next)
            r.fold(
                { p -> _ui.update { it.copy(items = if (next == 1) p.results else it.items + p.results, page = next, totalPages = p.totalPages, loading = false, error = null) } },
                { e -> _ui.update { it.copy(loading = false, error = e as? AppError) } },
            )
        }
    }

    fun loadMore() { val u = _ui.value; if (u.loading || u.page >= u.totalPages) return; _ui.update { it.copy(loading = true) }; load() }
    fun retry() { _ui.update { it.copy(loading = true, error = null) }; load() }
    fun setType(t: MediaType) { _ui.update { GridUi(type = t, sort = it.sort) }; load() }
    fun setSort(s: SortOption) { _ui.update { GridUi(type = it.type, sort = s) }; load() }
}

@Composable
fun GridScreen(onBack: () -> Unit, onOpen: (TitleSummary) -> Unit, vm: GridViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val state = rememberLazyGridState()
    var sortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state, ui.page) {
        snapshotFlow { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) to state.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (total > 0 && last >= total - 6) vm.loadMore() }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, container = cs.surfaceContainerLow)
            Spacer(Modifier.width(12.dp))
            Text(vm.title, style = MaterialTheme.typography.headlineMedium, color = cs.onSurface, maxLines = 2)
        }
        if (vm.isGenre) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HfChip("Movies", ui.type == MediaType.MOVIE, { vm.setType(MediaType.MOVIE) })
                HfChip("TV Shows", ui.type == MediaType.TV, { vm.setType(MediaType.TV) })
                Spacer(Modifier.weight(1f))
                Box {
                    HfChip(ui.sort.label, false, { sortMenu = true })
                    DropdownMenu(sortMenu, { sortMenu = false }) { SortOption.entries.forEach { s -> DropdownMenuItem({ Text(s.label) }, { vm.setSort(s); sortMenu = false }) } }
                }
            }
        }
        when {
            ui.loading && ui.items.isEmpty() -> LazyVerticalGrid(GridCells.Adaptive(140.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(9) { SkeletonBox(Modifier.fillMaxWidth().aspectRatio(2f / 3f), androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) }
            }
            ui.error != null && ui.items.isEmpty() -> ErrorState(ui.error, vm::retry)
            ui.items.isEmpty() -> EmptyState(Icons.Outlined.Movie, "Nothing to show", "No titles matched this list.")
            else -> LazyVerticalGrid(
                GridCells.Adaptive(140.dp), state = state, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(ui.items, key = { it.key }) { t -> PosterCard(t, { onOpen(t) }, width = Dp.Unspecified) }
                if (ui.loading) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
            }
        }
    }
}
