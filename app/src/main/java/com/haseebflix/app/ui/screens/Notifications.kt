package com.haseebflix.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.haseebflix.app.data.local.NotificationEntity
import com.haseebflix.app.data.repo.LibraryRepository
import com.haseebflix.app.ui.components.*
import com.haseebflix.app.ui.theme.LocalDataSaver
import com.haseebflix.app.ui.theme.extras
import com.haseebflix.app.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NotifFilter(val label: String) { ALL("All"), EPISODES("New Episodes"), PREMIERES("Premieres") }

data class NotifUi(val all: List<NotificationEntity> = emptyList(), val filter: NotifFilter = NotifFilter.ALL, val followed: Int = 0) {
    val shown get() = when (filter) {
        NotifFilter.ALL -> all
        NotifFilter.EPISODES -> all.filter { it.kind == "EPISODE" }
        NotifFilter.PREMIERES -> all.filter { it.kind == "PREMIERE" }
    }
    val unread get() = all.count { !it.isRead }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(private val library: LibraryRepository) : ViewModel() {
    private val filter = MutableStateFlow(NotifFilter.ALL)
    val state: StateFlow<NotifUi> = combine(library.notifications, filter, library.followIds) { n, f, ids -> NotifUi(n, f, ids.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotifUi())
    fun setFilter(f: NotifFilter) { filter.value = f }
    fun markAllRead() { viewModelScope.launch { library.markAllRead() } }
    fun markRead(id: Long) { viewModelScope.launch { library.markRead(id) } }
    fun delete(id: Long) { viewModelScope.launch { library.deleteNotification(id) } }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpen: (String, Int) -> Unit, onManage: () -> Unit, vm: NotificationsViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val grouped = ui.shown.groupBy { dayBucket(it.createdAt) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, container = cs.surfaceContainerLow)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Activity & Alerts", style = MaterialTheme.typography.headlineMedium, color = cs.onSurface)
                    if (ui.unread > 0) Text("${ui.unread} new", Modifier.clip(CircleShape).background(cs.primary).padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White)
                }
                Text("New episodes and premieres for your followed list", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            CircleIconButton(Icons.Filled.DoneAll, "Mark all as read", vm::markAllRead, container = cs.surfaceContainerLow, size = 44.dp)
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NotifFilter.entries.forEach { f ->
                val n = when (f) { NotifFilter.ALL -> ui.all.size; NotifFilter.EPISODES -> ui.all.count { it.kind == "EPISODE" }; NotifFilter.PREMIERES -> ui.all.count { it.kind == "PREMIERE" } }
                HfChip("${f.label} $n", ui.filter == f, { vm.setFilter(f) })
            }
        }
        if (ui.shown.isEmpty()) {
            EmptyState(Icons.Outlined.NotificationsNone, "You're all caught up", "Track a TV show or save an upcoming movie and we'll alert you here when something new arrives.", Modifier.padding(top = 32.dp))
        } else LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0 to "TODAY", 1 to "THIS WEEK", 2 to "EARLIER").forEach { (bucket, label) ->
                val list = grouped[bucket].orEmpty()
                if (list.isNotEmpty()) {
                    item(key = "h$bucket") {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp), color = cs.onSurface)
                            val un = list.count { !it.isRead }
                            if (un > 0) Text("  •  $un unread", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.extras.brandText)
                            Spacer(Modifier.weight(1f))
                            Text("${list.size} items", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        }
                    }
                    items(list, key = { it.id }) { n ->
                        NotificationRow(n, { vm.markRead(n.id); onOpen(n.type.api, n.titleId) }, { vm.markRead(n.id) }, { vm.delete(n.id) })
                    }
                }
            }
            item(key = "manage") {
                SurfaceCard(Modifier.fillMaxWidth().padding(top = 8.dp), RoundedCornerShape(16.dp), onManage) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Tune, null, tint = cs.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text("Managing ${ui.followed} followed titles", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                            Text("Tap to customize which shows alert you", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationEntity, onClick: () -> Unit, onRead: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val saver = LocalDataSaver.current
    var menu by remember { mutableStateOf(false) }
    val label = if (n.kind == "EPISODE") "NEW EPISODE • S${n.season} E${n.episode}" else "NOW AVAILABLE"
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (n.isRead) cs.surfaceContainerLow else cs.surfaceContainer)
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(96.dp).aspectRatio(16f / 10f).clip(RoundedCornerShape(10.dp))) {
            RemoteImage(TmdbImage.backdrop(n.imagePath, 300, saver), Modifier.fillMaxSize())
            if (!n.isRead) Box(Modifier.padding(6.dp).size(9.dp).background(cs.primary, CircleShape))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp), color = if (n.kind == "EPISODE") MaterialTheme.extras.brandText else MaterialTheme.extras.rating)
            Text(n.title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(n.subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(12.dp), tint = cs.onSurfaceVariant)
                Text(relativeTime(n.createdAt), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Watch", Modifier.clip(CircleShape).background(cs.primary).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color.White)
            Box {
                IconButton({ menu = true }, Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, "More options", tint = cs.onSurfaceVariant) }
                DropdownMenu(menu, { menu = false }) {
                    if (!n.isRead) DropdownMenuItem({ Text("Mark as read") }, { menu = false; onRead() })
                    DropdownMenuItem({ Text("Delete") }, { menu = false; onDelete() })
                }
            }
        }
    }
}
