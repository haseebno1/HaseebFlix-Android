package com.haseebflix.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haseebflix.app.data.settings.Keys
import com.haseebflix.app.data.settings.SettingsRepository
import com.haseebflix.app.domain.Genres
import com.haseebflix.app.ui.components.*
import com.haseebflix.app.ui.theme.extras
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    fun finish(genres: Set<Int>, done: () -> Unit) {
        viewModelScope.launch {
            settings.setGenres(genres)
            settings.set(Keys.onboarding, true)
            done()
        }
    }
}

private val genreIcons: Map<Int, ImageVector> = mapOf(
    28 to Icons.Filled.FlashOn, 878 to Icons.Filled.RocketLaunch, 35 to Icons.Filled.TheaterComedy, 27 to Icons.Filled.Dangerous,
    53 to Icons.Filled.Psychology, 12 to Icons.Filled.Explore, 18 to Icons.Filled.MoodBad, 16 to Icons.Filled.AutoAwesome,
    80 to Icons.Filled.Shield, 14 to Icons.Filled.AutoFixHigh, 99 to Icons.Filled.OndemandVideo, 9648 to Icons.Filled.QuestionMark,
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val cs = MaterialTheme.colorScheme
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    var picked by rememberSaveable { mutableStateOf(listOf<Int>()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { pager.animateScrollToPage(2) }
    }
    fun finish() = vm.finish(picked.toSet(), onFinish)

    Box(
        Modifier.fillMaxSize().background(cs.background)
            .background(Brush.radialGradient(listOf(cs.primary.copy(alpha = 0.16f), Color.Transparent), radius = 1100f))
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Wordmark(16, 0.08f)
                Spacer(Modifier.weight(1f))
                TextButton(::finish) { Text("Skip", style = MaterialTheme.typography.labelLarge, color = cs.onSurface) }
            }
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> AlertsPage()
                    else -> GenresPage(picked) { id -> picked = if (id in picked) picked - id else picked + id }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 14.dp)) {
                    repeat(3) { i ->
                        val w by animateDpAsState(if (pager.currentPage == i) 28.dp else 8.dp, label = "dot")
                        val c by animateColorAsState(if (pager.currentPage == i) cs.primary else cs.surfaceContainerHigh, label = "dotc")
                        Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(c))
                    }
                }
                PrimaryButton(
                    if (pager.currentPage == 2) "Get Started" else "Next",
                    onClick = {
                        when (pager.currentPage) {
                            0 -> scope.launch { pager.animateScrollToPage(1) }
                            1 -> if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                 else scope.launch { pager.animateScrollToPage(2) }
                            else -> finish()
                        }
                    },
                    Modifier.fillMaxWidth(), icon = if (pager.currentPage == 2) Icons.Filled.PlayArrow else Icons.AutoMirrored.Filled.ArrowForward, height = 56.dp,
                )
                Text(
                    when (pager.currentPage) { 0 -> "Instant streaming • No credit card required"; 1 -> "You can change alerts any time in Settings"; else -> "Step 3 of 3 • You can adjust preferences in Settings anytime" },
                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 14.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeatureChips(items: List<Pair<ImageVector, String>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (icon, label) ->
            Row(
                Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLow).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, null, tint = MaterialTheme.extras.rating, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun Pill(text: String, dot: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.clip(CircleShape).background(cs.primaryContainer.copy(alpha = 0.7f)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dot) Box(Modifier.size(6.dp).background(cs.primary, CircleShape))
        Text(text, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp), color = cs.onPrimaryContainer)
    }
}

@Composable
private fun WelcomePage() {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(26.dp)).background(cs.surfaceContainerLow).border(1.dp, cs.outlineVariant, RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) { AppLogo(60.dp) }
        Spacer(Modifier.height(24.dp))
        Pill("UNLIMITED ENTERTAINMENT")
        Spacer(Modifier.height(16.dp))
        Text("Stream movies and TV, free and ad-free", style = MaterialTheme.typography.displayMedium, color = cs.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text("Powered by TMDB. Discover thousands of blockbusters, indie gems, and trending series with zero interruptions or subscriptions.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        FeatureChips(listOf(Icons.Filled.Star to "100% Free", Icons.Filled.Block to "No Ads", Icons.Filled.WifiOff to "Offline-friendly"))
    }
}

@Composable
private fun AlertsPage() {
    val cs = MaterialTheme.colorScheme
    val brand = MaterialTheme.extras.brandText
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
        SurfaceCard(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).background(cs.primaryContainer, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.NotificationsActive, null, tint = brand) }
                    Column(Modifier.weight(1f)) {
                        Text("NEW EPISODE ALERT", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), color = cs.onSurface)
                        Text("Just now • Haseeb-Flix", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    InfoPill("LIVE", container = cs.primaryContainer, content = brand)
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(width = 52.dp, height = 74.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFF8B0010), Color(0xFF1B1B20)))), contentAlignment = Alignment.Center) { AppLogo(24.dp) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Your show • S1:E2", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                        Text("A new episode is now streaming", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        InfoPill("52 min")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.BookmarkAdded, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text("Added to your Watchlist", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
                    Text("Tap to watch", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Pill("NEVER MISS A PREMIERE • STEP 2 OF 3")
        Spacer(Modifier.height(14.dp))
        Text(
            buildAnnotatedString {
                append("Follow shows, get ")
                withStyle(SpanStyle(color = cs.primary)) { append("new-episode") }
                append(" alerts")
            },
            style = MaterialTheme.typography.displayMedium, color = cs.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Text("Track your favorite series and get a notification the moment new episodes drop on TMDB.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        FeatureChips(listOf(Icons.Filled.Notifications to "Smart Notifications", Icons.Filled.Bookmark to "One-tap Watchlist", Icons.Filled.CalendarMonth to "Release Calendar"))
    }
}

@Composable
private fun GenresPage(picked: List<Int>, onToggle: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val brand = MaterialTheme.extras.brandText
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Text("CUSTOMIZE YOUR FEED", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp), color = brand)
        Spacer(Modifier.height(6.dp))
        Text("Pick your favorite genres", style = MaterialTheme.typography.displayMedium, color = cs.onSurface)
        Spacer(Modifier.height(8.dp))
        Text("Choose at least 3 to personalize your movie and TV recommendations.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clip(CircleShape).background(cs.surfaceContainer).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(cs.primary, CircleShape))
                Text("${picked.size} genres selected", style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
            }
            Spacer(Modifier.weight(1f))
            Text(if (picked.size >= 3) "Ready to personalize" else "Pick ${3 - picked.size} more", style = MaterialTheme.typography.labelMedium, color = brand)
        }
        Spacer(Modifier.height(14.dp))
        Genres.onboarding.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { g ->
                    val sel = g.id in picked
                    val bg by animateColorAsState(if (sel) cs.primaryContainer else cs.surfaceContainer, label = "tile")
                    Row(
                        Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(18.dp)).background(bg)
                            .border(1.dp, if (sel) cs.primary.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(18.dp))
                            .clickable { onToggle(g.id) }.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(38.dp).background(if (sel) cs.primary else cs.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(genreIcons[g.id] ?: Icons.Filled.Movie, null, tint = if (sel) Color.White else cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        Text(g.name, style = MaterialTheme.typography.titleMedium, color = if (sel) Color.White else cs.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                        if (sel) Icon(Icons.Filled.Check, null, tint = brand, modifier = Modifier.size(18.dp))
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerLow).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(46.dp).background(cs.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.ThumbUp, null, tint = MaterialTheme.extras.rating) }
            Column {
                Text("Picked for you", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text("Your picks decide what Home shows first. They stay on this device.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
