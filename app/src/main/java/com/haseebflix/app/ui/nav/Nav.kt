package com.haseebflix.app.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.haseebflix.app.data.repo.LibraryRepository
import com.haseebflix.app.data.settings.AppSettings
import com.haseebflix.app.data.settings.SettingsRepository
import com.haseebflix.app.domain.TitleSummary
import com.haseebflix.app.playback.PlayerState
import com.haseebflix.app.ui.components.*
import com.haseebflix.app.ui.screens.*
import com.haseebflix.app.ui.theme.*
import com.haseebflix.app.util.Connectivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import javax.inject.Inject

// ───────── type-safe routes ─────────
@Serializable object SplashRoute
@Serializable object OnboardingRoute
@Serializable object HomeRoute
@Serializable object DiscoverRoute
@Serializable object LibraryRoute
@Serializable object SettingsRoute
@Serializable object NotificationsRoute
@Serializable data class DetailsRoute(val type: String, val id: Int)
@Serializable data class GridRoute(val title: String, val feed: String = "", val type: String = "movie", val genreId: Int = 0)
@Serializable data class PlayerRoute(val type: String, val id: Int, val season: Int = 0, val episode: Int = 0)

/** Lets the Home search icon open Discover with the keyboard up. */
object SearchBus { val focus = MutableStateFlow(false) }

@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository, connectivity: Connectivity, library: LibraryRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settings.flow.map<AppSettings, AppSettings?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val online = connectivity.online
    val unread: StateFlow<Boolean> = library.unreadCount.map { it > 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}

private class Tab(val route: Any, val label: String, val icon: ImageVector, val selected: ImageVector, val matches: (NavDestination?) -> Boolean)

private val TABS = listOf(
    Tab(HomeRoute, "Home", Icons.Outlined.Home, Icons.Filled.Home) { it?.hasRoute<HomeRoute>() == true },
    Tab(DiscoverRoute, "Discover", Icons.Outlined.Explore, Icons.Filled.Explore) { it?.hasRoute<DiscoverRoute>() == true },
    Tab(LibraryRoute, "Library", Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark) { it?.hasRoute<LibraryRoute>() == true },
    Tab(SettingsRoute, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings) { it?.hasRoute<SettingsRoute>() == true },
)

fun NavController.goTab(route: Any) = navigate(route) {
    popUpTo(HomeRoute) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppRoot(windowSize: WindowSizeClass, vm: AppViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val unread by vm.unread.collectAsStateWithLifecycle()
    val inPip by PlayerState.inPip.collectAsStateWithLifecycle()

    HaseebFlixTheme(s ?: AppSettings()) {
        CompositionLocalProvider(LocalDataSaver provides (s?.dataSaver ?: false)) {
            val nav = rememberNavController()
            val snack = remember { SnackbarHostState() }
            val entry by nav.currentBackStackEntryAsState()
            val tab = TABS.firstOrNull { it.matches(entry?.destination) }
            val wide = windowSize.widthSizeClass != WindowWidthSizeClass.Compact
            val onboardingDone by rememberUpdatedState(s?.onboardingDone ?: false)

            val openTitle: (TitleSummary) -> Unit = { nav.navigate(DetailsRoute(it.type.api, it.id)) }
            val openPlayer: (TitleSummary, Int, Int) -> Unit = { t, se, ep -> nav.navigate(PlayerRoute(t.type.api, t.id, se, ep)) }
            val toNotifications = { nav.navigate(NotificationsRoute) }
            val toSettings = { nav.goTab(SettingsRoute) }

            CompositionLocalProvider(LocalSnackbar provides snack) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Row(Modifier.fillMaxSize()) {
                        if (tab != null && wide && !inPip) HfRail(entry?.destination) { nav.goTab(it) }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Column(Modifier.weight(1f).then(if (tab != null) Modifier.statusBarsPadding() else Modifier)) {
                                AnimatedVisibility(visible = !online && tab != null) { OfflineBanner() }
                                NavHost(
                                    nav, startDestination = SplashRoute, modifier = Modifier.weight(1f).fillMaxWidth(),
                                    enterTransition = { fadeIn(tween(240)) + slideInVertically(tween(300)) { it / 28 } },
                                    exitTransition = { fadeOut(tween(160)) },
                                    popEnterTransition = { fadeIn(tween(220)) },
                                    popExitTransition = { fadeOut(tween(180)) + slideOutVertically(tween(240)) { it / 28 } },
                                ) {
                                    composable<SplashRoute> {
                                        SplashScreen {
                                            nav.navigate(if (onboardingDone) HomeRoute else OnboardingRoute) { popUpTo(SplashRoute) { inclusive = true } }
                                        }
                                    }
                                    composable<OnboardingRoute> {
                                        OnboardingScreen(onFinish = { nav.navigate(HomeRoute) { popUpTo(OnboardingRoute) { inclusive = true } } })
                                    }
                                    composable<HomeRoute> {
                                        HomeScreen(
                                            unread = unread, onOpen = openTitle, onPlay = openPlayer,
                                            onSeeAll = { title, feed -> nav.navigate(GridRoute(title, feed)) },
                                            onSearch = { SearchBus.focus.value = true; nav.goTab(DiscoverRoute) },
                                            onBell = { toNotifications() }, onAvatar = { toSettings() },
                                        )
                                    }
                                    composable<DiscoverRoute> {
                                        DiscoverScreen(
                                            unread = unread, onOpen = openTitle, onBell = { toNotifications() }, onAvatar = { toSettings() },
                                            onGenre = { id, name -> nav.navigate(GridRoute(name, genreId = id)) },
                                            onSeeAll = { title, feed -> nav.navigate(GridRoute(title, feed)) },
                                        )
                                    }
                                    composable<LibraryRoute> {
                                        LibraryScreen(
                                            unread = unread, onOpen = openTitle, onPlay = openPlayer,
                                            onSearch = { SearchBus.focus.value = true; nav.goTab(DiscoverRoute) },
                                            onBell = { toNotifications() }, onAvatar = { toSettings() }, onBrowse = { nav.goTab(DiscoverRoute) },
                                        )
                                    }
                                    composable<SettingsRoute> {
                                        SettingsScreen(
                                            unread = unread, onSearch = { SearchBus.focus.value = true; nav.goTab(DiscoverRoute) },
                                            onBell = { toNotifications() },
                                        )
                                    }
                                    composable<NotificationsRoute> {
                                        NotificationsScreen(
                                            onBack = { nav.popBackStack() }, onOpen = { type, id -> nav.navigate(DetailsRoute(type, id)) },
                                            onManage = { nav.goTab(LibraryRoute) },
                                        )
                                    }
                                    composable<DetailsRoute> {
                                        DetailsScreen(
                                            wide = wide, onBack = { nav.popBackStack() }, onOpen = openTitle,
                                            onPlay = { type, id, se, ep -> nav.navigate(PlayerRoute(type, id, se, ep)) },
                                        )
                                    }
                                    composable<GridRoute> { GridScreen(onBack = { nav.popBackStack() }, onOpen = openTitle) }
                                    composable<PlayerRoute> { PlayerScreen(onBack = { nav.popBackStack() }) }
                                }
                            }
                            if (tab != null && !wide) HfBottomBar(entry?.destination) { nav.goTab(it) }
                        }
                    }
                    SnackbarHost(
                        snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (tab != null && !wide) 72.dp else 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HfBottomBar(dest: NavDestination?, onTab: (Any) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val brand = MaterialTheme.extras.brandText
    NavigationBar(
        modifier = Modifier.drawBehind { drawLine(cs.outlineVariant, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) },
        containerColor = cs.surface, tonalElevation = 0.dp,
    ) {
        TABS.forEach { t ->
            val sel = t.matches(dest)
            NavigationBarItem(
                selected = sel, onClick = { if (!sel) onTab(t.route) },
                icon = { Icon(if (sel) t.selected else t.icon, t.label) },
                label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = brand, selectedTextColor = brand, indicatorColor = cs.secondaryContainer,
                    unselectedIconColor = cs.onSurfaceVariant, unselectedTextColor = cs.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun HfRail(dest: NavDestination?, onTab: (Any) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val brand = MaterialTheme.extras.brandText
    NavigationRail(
        containerColor = cs.surface,
        header = { Spacer(Modifier.height(8.dp)); AppLogo(36.dp) },
        modifier = Modifier.statusBarsPadding(),
    ) {
        Spacer(Modifier.weight(1f))
        TABS.forEach { t ->
            val sel = t.matches(dest)
            NavigationRailItem(
                selected = sel, onClick = { if (!sel) onTab(t.route) },
                icon = { Icon(if (sel) t.selected else t.icon, t.label) },
                label = { Text(t.label, style = MaterialTheme.typography.labelSmall) }, alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = brand, selectedTextColor = brand, indicatorColor = cs.secondaryContainer,
                    unselectedIconColor = cs.onSurfaceVariant, unselectedTextColor = cs.onSurfaceVariant,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
