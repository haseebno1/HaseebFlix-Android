package com.haseebflix.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.haseebflix.app.BuildConfig
import com.haseebflix.app.data.repo.MediaRepository
import com.haseebflix.app.data.settings.*
import com.haseebflix.app.ui.components.*
import com.haseebflix.app.ui.theme.extras
import com.haseebflix.app.ui.theme.swatch
import com.haseebflix.app.work.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    private val settings: SettingsRepository,
    private val media: MediaRepository,
    private val http: OkHttpClient,
) : ViewModel() {
    val state: StateFlow<AppSettings> = settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    private val _cache = MutableStateFlow(0L)
    val cacheBytes = _cache.asStateFlow()

    init { refreshCache() }

    fun <T> set(key: Preferences.Key<T>, value: T) { viewModelScope.launch { settings.set(key, value) } }

    fun setAlert(key: Preferences.Key<Boolean>, on: Boolean) {
        viewModelScope.launch {
            settings.set(key, on)
            val s = settings.current()
            SyncScheduler.update(app, s.newEpisodeAlerts || s.watchlistAlerts)
        }
    }

    /** Language/region change the content TMDB returns, so cached lists and details are dropped. */
    fun setLocale(key: Preferences.Key<String>, value: String) {
        viewModelScope.launch { settings.set(key, value); media.clearContentCache() }
    }

    fun refreshCache() {
        viewModelScope.launch {
            _cache.value = withContext(Dispatchers.IO) { (SingletonImageLoader.get(app).diskCache?.size ?: 0L) + (http.cache?.size() ?: 0L) }
        }
    }

    fun clearCache(done: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val loader = SingletonImageLoader.get(app)
                loader.memoryCache?.clear(); loader.diskCache?.clear(); http.cache?.evictAll()
            }
            media.clearContentCache()
            refreshCache(); done()
        }
    }
}

private enum class Sheet { APPEARANCE, RESOLUTION, AUDIO, LANGUAGE, REGION }

@Composable
fun SettingsScreen(unread: Boolean, onSearch: () -> Unit, onBell: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val cacheBytes by vm.cacheBytes.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val brand = MaterialTheme.extras.brandText
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var privacy by remember { mutableStateOf(false) }
    var pendingAlert by remember { mutableStateOf<Preferences.Key<Boolean>?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingAlert?.let { vm.setAlert(it, granted) }
        pendingAlert = null
    }
    fun alert(key: Preferences.Key<Boolean>, on: Boolean) {
        val needs = on && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs) { pendingAlert = key; permission.launch(Manifest.permission.POST_NOTIFICATIONS) } else vm.setAlert(key, on)
    }

    Column(Modifier.fillMaxSize()) {
        TopBrandBar(unread, onSearch, onBell, onAvatar = {})
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DeveloperCard { ctx.openUrl("https://github.com/haseebno1") } }

            item { SectionLabel("PLAYBACK & STORAGE") }
            item {
                SettingsCard {
                    SettingRow(Icons.Outlined.DarkMode, "Theme", "${s.theme.label} · ${s.accent.label}${if (s.dynamicColor) " · Dynamic" else ""}", { sheet = Sheet.APPEARANCE }) {
                        Text("Active", style = MaterialTheme.typography.labelLarge, color = brand); Icon(Icons.Filled.ChevronRight, null, tint = cs.onSurfaceVariant)
                    }
                    SettingRow(Icons.Outlined.HighQuality, "Default Resolution", s.resolution.label, { sheet = Sheet.RESOLUTION }) {
                        InfoPill(s.resolution.label.substringBefore(" "))
                    }
                    SettingRow(Icons.Outlined.Audiotrack, "Audio Track", "Preferred: ${s.audioLanguage}", { sheet = Sheet.AUDIO }) { Icon(Icons.Filled.ChevronRight, null, tint = cs.onSurfaceVariant) }
                    SettingRow(Icons.Outlined.Wifi, "Stream over Wi-Fi only", "Conserve cellular bandwidth") { HfSwitch(s.wifiOnly) { vm.set(Keys.wifiOnly, it) } }
                    SettingRow(Icons.Outlined.Autorenew, "Temporary Cache", "%.1f MB occupied".format(cacheBytes / 1_048_576.0)) {
                        Text(
                            "Clear", Modifier.clip(CircleShape).background(cs.surfaceContainerHigh).clickable { vm.clearCache { scope.launch { snack.showSnackbar("Cache cleared") } } }.padding(horizontal = 20.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.labelLarge, color = brand,
                        )
                    }
                }
            }

            item { SectionLabel("CONTENT & DATA") }
            item {
                SettingsCard {
                    SettingRow(Icons.Outlined.Translate, "Content language", Options.languages.firstOrNull { it.first == s.language }?.second ?: s.language, { sheet = Sheet.LANGUAGE }) { Icon(Icons.Filled.ChevronRight, null, tint = cs.onSurfaceVariant) }
                    SettingRow(Icons.Outlined.Public, "Region", Options.regions.firstOrNull { it.first == s.region }?.second ?: s.region, { sheet = Sheet.REGION }) { Icon(Icons.Filled.ChevronRight, null, tint = cs.onSurfaceVariant) }
                    SettingRow(Icons.Outlined.Block, "Hide adult content", "Filter 18+ titles everywhere") { HfSwitch(s.hideAdult) { vm.set(Keys.hideAdult, it) } }
                    SettingRow(Icons.Outlined.DataSaverOn, "Data saver", "Load smaller images") { HfSwitch(s.dataSaver) { vm.set(Keys.dataSaver, it) } }
                }
            }

            item { SectionLabel("ALERTS & SUBSCRIPTIONS") }
            item {
                SettingsCard {
                    SettingRow(Icons.Outlined.Campaign, "New Episode Premieres", "Alerts for tracked TV shows") { HfSwitch(s.newEpisodeAlerts) { alert(Keys.epAlerts, it) } }
                    SettingRow(Icons.Outlined.BookmarkAdded, "Watchlist Updates", "Notify when saved titles release") { HfSwitch(s.watchlistAlerts) { alert(Keys.watchAlerts, it) } }
                }
            }

            item { SectionLabel("ABOUT & ARCHITECTURE") }
            item {
                SettingsCard {
                    SettingRow(Icons.Outlined.Terminal, "Source code on GitHub", "github.com/haseebno1/Haseeb-Flix", { ctx.openUrl("https://github.com/haseebno1/Haseeb-Flix") }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = cs.onSurfaceVariant) }
                    SettingRow(Icons.Outlined.Shield, "Privacy Policy", "Zero telemetry, local watch history only", { privacy = true }) { Icon(Icons.Filled.ChevronRight, null, tint = cs.onSurfaceVariant) }
                    SettingRow(Icons.Outlined.Verified, "Production Version", "Release channel: stable") { InfoPill("v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})") }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Haseeb-Flix", style = MaterialTheme.typography.titleLarge, color = brand)
                        Box(Modifier.size(5.dp).background(cs.primary, CircleShape))
                        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    }
                    Text("Data and imagery provided by TMDB. Built for cinephiles. Free & ad-free forever.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                    Text("This product uses the TMDB API but is not endorsed or certified by TMDB.", style = MaterialTheme.typography.bodySmall, color = cs.outline, textAlign = TextAlign.Center)
                    Text("Designed & developed with passion by Abdul Haseeb", style = MaterialTheme.typography.labelMedium, color = cs.outline, textAlign = TextAlign.Center)
                }
            }
        }
    }

    when (sheet) {
        Sheet.APPEARANCE -> AppearanceSheet(s, vm) { sheet = null }
        Sheet.RESOLUTION -> OptionSheet("Default Resolution", Resolution.entries.map { it.name to it.label }, s.resolution.name, { vm.set(Keys.resolution, it) }) { sheet = null }
        Sheet.AUDIO -> OptionSheet("Audio Track", Options.audio.map { it to it }, s.audioLanguage, { vm.set(Keys.audio, it) }) { sheet = null }
        Sheet.LANGUAGE -> OptionSheet("Content language", Options.languages, s.language, { vm.setLocale(Keys.language, it) }) { sheet = null }
        Sheet.REGION -> OptionSheet("Region", Options.regions, s.region, { vm.setLocale(Keys.region, it) }) { sheet = null }
        null -> {}
    }
    if (privacy) AlertDialog(
        onDismissRequest = { privacy = false },
        confirmButton = { TextButton({ privacy = false }) { Text("Got it") } },
        title = { Text("Privacy") },
        text = {
            Text(
                "Haseeb-Flix has no accounts, no ads and no analytics.\n\nYour watchlist, history, settings and progress are stored only on this device. " +
                    "The app talks to TMDB to fetch titles and images, and TMDB receives standard request data such as your IP address, language and region."
            )
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, Modifier.padding(start = 6.dp, top = 8.dp), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp), color = MaterialTheme.extras.brandText)
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerLow).border(1.dp, cs.outlineVariant, RoundedCornerShape(24.dp)), content = content)
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).background(cs.surfaceContainerHigh, CircleShape), Alignment.Center) { Icon(icon, null, tint = cs.onSurface, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = trailing)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeveloperCard(onGithub: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    SurfaceCard(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Box(Modifier.size(76.dp).background(cs.primary, CircleShape), Alignment.Center) { Text("AH", style = MaterialTheme.typography.headlineLarge, color = Color.White) }
                    Box(Modifier.align(Alignment.BottomEnd).size(26.dp).background(MaterialTheme.extras.success, CircleShape).border(2.dp, cs.surfaceContainerLow, CircleShape), Alignment.Center) {
                        Icon(Icons.Filled.Check, "Verified", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Abdul Haseeb", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 21.sp), color = cs.onSurface)
                        Text("CREATOR", Modifier.clip(RoundedCornerShape(6.dp)).background(cs.primary).padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp), color = Color.White)
                    }
                    Text("@haseebno1", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.extras.brandText)
                    Text("Lead Mobile & App Architect", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Icons.Filled.Android to "Android Native", Icons.Filled.Movie to "TMDB API", Icons.Filled.Code to "Kotlin / Jetpack Compose").forEach { (i, l) ->
                    Row(Modifier.clip(CircleShape).background(cs.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(i, null, tint = MaterialTheme.extras.brandText, modifier = Modifier.size(16.dp)); Text(l, style = MaterialTheme.typography.labelMedium, color = cs.onSurface)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).clickable(onClick = onGithub),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Code, null, tint = cs.onSurface); Spacer(Modifier.width(10.dp))
                Text("View GitHub Profile", style = MaterialTheme.typography.titleMedium, color = cs.onSurface); Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionSheet(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismiss, containerColor = cs.surfaceContainerHigh) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Text(title, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.headlineMedium, color = cs.onSurface) }
            items(options, key = { it.first }) { (value, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(value); onDismiss() }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(value == selected, null, colors = RadioButtonDefaults.colors(selectedColor = cs.primary))
                    Spacer(Modifier.width(16.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSheet(s: AppSettings, vm: SettingsViewModel, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismiss, containerColor = cs.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Appearance", style = MaterialTheme.typography.headlineMedium, color = cs.onSurface)
            Text("Theme", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m -> HfChip(m.label, s.theme == m, { vm.set(Keys.theme, m.name) }) }
            }
            Text("Accent colour", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AccentColor.entries.forEach { a ->
                    val sel = s.accent == a && !s.dynamicColor
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(a.swatch()).border(if (sel) 3.dp else 0.dp, cs.onSurface, CircleShape).clickable { vm.set(Keys.accent, a.name) },
                        Alignment.Center,
                    ) { if (sel) Icon(Icons.Filled.Check, a.label, tint = Color.White) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dynamic colour", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text(if (Build.VERSION.SDK_INT >= 31) "Use your wallpaper's colours instead of the brand accent" else "Requires Android 12 or newer", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Switch(s.dynamicColor, { vm.set(Keys.dynamic, it) }, enabled = Build.VERSION.SDK_INT >= 31)
            }
        }
    }
}
