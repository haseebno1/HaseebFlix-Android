package com.haseebflix.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.toRoute
import com.haseebflix.app.data.repo.LibraryRepository
import com.haseebflix.app.data.repo.MediaRepository
import com.haseebflix.app.data.settings.Resolution
import com.haseebflix.app.data.settings.SettingsRepository
import com.haseebflix.app.domain.MediaType
import com.haseebflix.app.domain.TitleSummary
import com.haseebflix.app.playback.*
import com.haseebflix.app.ui.components.*
import com.haseebflix.app.ui.nav.PlayerRoute
import com.haseebflix.app.util.Connectivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface PlayerUi {
    data object Resolving : PlayerUi
    data class NoSource(val trailerKey: String?, val reason: String) : PlayerUi
    data class Blocked(val reason: String) : PlayerUi
    data class Ready(val url: String, val startMs: Long) : PlayerUi
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val media: MediaRepository,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val source: PlaybackSource,
    private val connectivity: Connectivity,
) : ViewModel() {
    private val route = saved.toRoute<PlayerRoute>()
    private val type = MediaType.of(route.type)
    private val _ui = MutableStateFlow<PlayerUi>(PlayerUi.Resolving)
    val ui = _ui.asStateFlow()
    private var summary: TitleSummary? = null

    init { resolve() }

    fun resolve() {
        viewModelScope.launch {
            _ui.value = PlayerUi.Resolving
            media.refreshDetail(type, route.id)
            val d = media.detail(type, route.id).first()
            if (d == null) { _ui.value = PlayerUi.NoSource(null, "Couldn't load this title. Check your connection."); return@launch }
            summary = d.toSummary()
            val s = settings.current()
            if (s.wifiOnly && !connectivity.isWifiOrUnmetered()) {
                _ui.value = PlayerUi.Blocked("Stream over Wi-Fi only is on. Connect to Wi-Fi or turn it off in Settings."); return@launch
            }
            val height = when (s.resolution) { Resolution.AUTO -> 0; Resolution.P480 -> 480; Resolution.P720 -> 720; Resolution.P1080 -> 1080; Resolution.P2160 -> 2160 }
            val stream = source.resolve(PlaybackRequest(type, route.id, route.season, route.episode, d.title, height, s.audioLanguage))
            if (stream == null) { _ui.value = PlayerUi.NoSource(d.trailerKey, "No playback source is configured for this build."); return@launch }
            val saved = library.progressFor(type, route.id).first().firstOrNull { it.season == route.season && it.episode == route.episode }
            val start = saved?.takeIf { it.durationMs > 0 && it.positionMs.toFloat() / it.durationMs < 0.95f }?.positionMs ?: 0L
            _ui.value = PlayerUi.Ready(stream.url, start)
        }
    }

    fun save(positionMs: Long, durationMs: Long) {
        val t = summary ?: return
        if (durationMs <= 0) return
        viewModelScope.launch { withContext(NonCancellable) { library.savePlayback(t, route.season, route.episode, positionMs, durationMs) } }
    }
}

@Composable
fun PlayerScreen(onBack: () -> Unit, vm: PlayerViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val inPip by PlayerState.inPip.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val u = ui) {
            PlayerUi.Resolving -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
            is PlayerUi.Ready -> PlayerSurface(u.url, u.startMs, vm::save)
            is PlayerUi.NoSource -> Unavailable("Playback isn't set up", u.reason, u.trailerKey, onBack, vm::resolve)
            is PlayerUi.Blocked -> Unavailable("Waiting for Wi-Fi", u.reason, null, onBack, vm::resolve)
        }
        if (!inPip && ui is PlayerUi.Ready) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp), Color(0x66000000), Color.White)
        }
    }
}

@Composable
private fun Unavailable(title: String, reason: String, trailerKey: String?, onBack: () -> Unit, onRetry: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        AppLogo(64.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(reason, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFA8A8B3), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        if (trailerKey != null) { PrimaryButton("Watch trailer", { ctx.openTrailer(trailerKey) }, Modifier.fillMaxWidth(), Icons.Filled.PlayArrow); Spacer(Modifier.height(12.dp)) }
        SecondaryButton("Try again", onRetry, Modifier.fillMaxWidth(), Icons.Outlined.WifiOff)
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Go back", onBack, Modifier.fillMaxWidth())
    }
}

private fun Context.findActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.findActivity(); else -> null }

@Composable
private fun PlayerSurface(url: String, startMs: Long, onProgress: (Long, Long) -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val save by rememberUpdatedState(onProgress)
    val player = remember(url) {
        ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(url)); prepare(); seekTo(startMs); playWhenReady = true }
    }

    DisposableEffect(player) {
        val act = ctx.findActivity()
        val window = act?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        PlayerState.active = true
        onDispose {
            save(player.currentPosition, player.duration.coerceAtLeast(0))
            player.release()
            PlayerState.active = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(10_000)
            if (player.isPlaying) save(player.currentPosition, player.duration.coerceAtLeast(0))
        }
    }
    AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
}
