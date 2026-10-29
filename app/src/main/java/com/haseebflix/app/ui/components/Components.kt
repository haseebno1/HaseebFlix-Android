package com.haseebflix.app.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.haseebflix.app.R
import com.haseebflix.app.domain.AppError
import com.haseebflix.app.domain.TitleSummary
import com.haseebflix.app.ui.theme.*
import com.haseebflix.app.util.TmdbImage
import com.haseebflix.app.util.metaLine

val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("SnackbarHostState not provided") }

// ───────────────────────── brand ─────────────────────────
@Composable
fun AppLogo(size: Dp = 32.dp, modifier: Modifier = Modifier) =
    Image(painterResource(R.drawable.ic_logo), null, modifier.size(size))

@Composable
fun Wordmark(size: Int = 20, spacing: Float = 0.06f) {
    val s = androidx.compose.ui.text.TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, letterSpacing = (spacing * size).sp)
    Row { Text("HASEEB-", style = s, color = MaterialTheme.colorScheme.onSurface); Text("FLIX", style = s, color = MaterialTheme.extras.brandText) }
}

@Composable
fun Avatar(filled: Boolean = false, size: Dp = 40.dp, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (filled) cs.primary else cs.surfaceContainer)
            .then(if (filled) Modifier else Modifier.border(1.dp, cs.outlineVariant, CircleShape))
            .clickable(onClick = onClick).semantics { contentDescription = "Profile and settings" },
        contentAlignment = Alignment.Center,
    ) { Text("AH", style = MaterialTheme.typography.labelLarge, color = if (filled) Color.White else cs.onSurface) }
}

@Composable
fun TopBrandBar(
    unread: Boolean, onSearch: (() -> Unit)?, onBell: () -> Unit, onAvatar: () -> Unit,
    title: String? = null, filledAvatar: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(64.dp).drawBehind { drawLine(cs.outlineVariant, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (title == null) { AppLogo(30.dp); Spacer(Modifier.width(10.dp)); Wordmark() }
        else Text(title, style = MaterialTheme.typography.headlineMedium, color = cs.onSurface)
        Spacer(Modifier.weight(1f))
        if (onSearch != null) IconButton(onSearch) { Icon(Icons.Outlined.Search, "Search", tint = cs.onSurface) }
        IconButton(onBell) {
            BadgedBox(badge = { if (unread) Badge(containerColor = cs.primary, modifier = Modifier.size(8.dp)) }) {
                Icon(Icons.Outlined.Notifications, "Notifications", tint = cs.onSurface)
            }
        }
        Spacer(Modifier.width(4.dp))
        Avatar(filledAvatar, onClick = onAvatar)
    }
}

// ───────────────────────── badges ─────────────────────────
@Composable
fun RatingBadge(t: TitleSummary, modifier: Modifier = Modifier) = RatingBadge(if (t.hasRating) "%.1f".format(t.rating) else "—", modifier)

@Composable
fun RatingBadge(text: String, modifier: Modifier = Modifier) {
    val ex = MaterialTheme.extras
    Row(
        modifier.clip(CircleShape).background(ex.badge).padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, null, tint = ex.rating, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = ex.rating)
    }
}

@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceContainerHigh, content: Color = MaterialTheme.colorScheme.onSurface, bordered: Boolean = false) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(container)
            .then(if (bordered) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) { Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = content, maxLines = 1) }
}

// ───────────────────────── imagery ─────────────────────────
@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop, description: String? = null) {
    val ph = remember { androidx.compose.ui.graphics.painter.ColorPainter(Color.Transparent) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        if (url == null) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.align(Alignment.Center).fillMaxSize(0.28f), alpha = 0.35f)
        } else AsyncImage(
            model = url, contentDescription = description, contentScale = scale, modifier = Modifier.fillMaxSize(),
            placeholder = ph, error = painterResource(R.drawable.ic_logo),
        )
    }
}

@Composable
fun Poster(t: TitleSummary, modifier: Modifier = Modifier, wanted: Int = 342) {
    val saver = LocalDataSaver.current
    RemoteImage(TmdbImage.poster(t.posterPath, wanted, saver), modifier)
}

fun Modifier.posterFrame(shape: Shape = RoundedCornerShape(16.dp)) = this.clip(shape)

@Composable
fun PosterCard(t: TitleSummary, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 156.dp) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.then(if (width == Dp.Unspecified) Modifier.fillMaxWidth() else Modifier.width(width)).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(t.title); if (t.year.isNotBlank()) append(", ${t.year}")
                    append(if (t.hasRating) ", rating %.1f out of 10".format(t.rating) else ", not yet rated")
                    if (t.genres.isNotEmpty()) append(", ${t.genres.first()}")
                }
            },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))) {
            Poster(t, Modifier.fillMaxSize())
            RatingBadge(t, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(t.title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
        Text(t.metaLine(), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ───────────────────────── sections & chips ─────────────────────────
@Composable
fun SectionHeader(title: String, subtitle: String? = null, action: String? = null, onAction: () -> Unit = {}, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) TextButton(onAction, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.extras.brandText)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.extras.brandText, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun TitleRow(
    title: String, items: List<TitleSummary>, onItem: (TitleSummary) -> Unit,
    subtitle: String? = null, onSeeAll: (() -> Unit)? = null, cardWidth: Dp = 156.dp,
) {
    if (items.isEmpty()) return
    Column {
        SectionHeader(title, subtitle, if (onSeeAll != null) "See all" else null, onSeeAll ?: {})
        Spacer(Modifier.height(14.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.key }) { PosterCard(it, { onItem(it) }, width = cardWidth) }
        }
    }
}

@Composable
fun HfChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.primaryContainer else cs.surfaceContainerLow, label = "chipBg")
    val border by animateColorAsState(if (selected) cs.primary else cs.outlineVariant, label = "chipBorder")
    Row(
        modifier.height(36.dp).clip(CircleShape).background(bg).border(1.dp, border, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant, maxLines = 1)
    }
}

// ───────────────────────── buttons ─────────────────────────
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, height: Dp = 52.dp, enabled: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    Button(
        onClick, modifier.height(height).shadow(14.dp, CircleShape, ambientColor = cs.primary, spotColor = cs.primary), enabled = enabled,
        shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, height: Dp = 48.dp) {
    val cs = MaterialTheme.colorScheme
    OutlinedButton(
        onClick, modifier.height(height), shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = cs.surfaceContainer, contentColor = cs.onSurface),
        border = BorderStroke(1.dp, cs.outline), contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceContainerHigh, tint: Color = MaterialTheme.colorScheme.onSurface, size: Dp = 48.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(container).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
}

@Composable
fun HfSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Switch(
        checked, onChange, colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = cs.primary, checkedBorderColor = cs.primary,
            uncheckedThumbColor = cs.outline, uncheckedTrackColor = cs.surfaceContainerHighest, uncheckedBorderColor = cs.outline,
        )
    )
}

// ───────────────────────── states ─────────────────────────
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    val cs = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "skeleton")
    val c by t.animateColor(cs.surfaceContainerLow, cs.surfaceContainer, infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Reverse), label = "c")
    Box(modifier.clip(shape).background(c))
}

@Composable
fun PosterRowSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SkeletonBox(Modifier.padding(horizontal = 16.dp).width(180.dp).height(22.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Column(Modifier.width(156.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth().aspectRatio(2f / 3f), RoundedCornerShape(16.dp))
                    SkeletonBox(Modifier.width(110.dp).height(14.dp)); SkeletonBox(Modifier.width(80.dp).height(12.dp))
                }
            }
        }
    }
}

@Composable
fun ListSkeleton(rows: Int = 4) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBox(Modifier.width(92.dp).fillMaxHeight(), RoundedCornerShape(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth(0.7f).height(18.dp)); SkeletonBox(Modifier.fillMaxWidth().height(12.dp)); SkeletonBox(Modifier.fillMaxWidth(0.8f).height(12.dp))
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, cta: String? = null, onCta: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(cs.surfaceContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.extras.brandText, modifier = Modifier.size(34.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = cs.onSurface, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        if (cta != null) { Spacer(Modifier.height(6.dp)); PrimaryButton(cta, onCta) }
    }
}

@Composable
fun ErrorState(error: AppError?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val (title, body) = when (error?.kind) {
        AppError.Kind.OFFLINE -> "You're offline" to "Check your connection. Saved content is still available."
        AppError.Kind.TIMEOUT -> "That took too long" to "The server didn't answer in time. Try again."
        AppError.Kind.NO_TOKEN -> "TMDB access isn't set up" to (error.message ?: "Add your TMDB token to local.properties and rebuild.")
        else -> "Can't load content" to (error?.message ?: "Something went wrong. Try again.")
    }
    EmptyState(Icons.Outlined.WifiOff, title, body, modifier, "Retry", onRetry)
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp))
            .background(cs.secondaryContainer.copy(alpha = 0.9f)).border(1.dp, cs.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.WifiOff, null, tint = MaterialTheme.extras.warning, modifier = Modifier.size(18.dp))
        Text("You're offline. Showing saved content.", style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer)
    }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(20.dp), onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val m = modifier.clip(shape).background(cs.surfaceContainerLow).border(1.dp, cs.outlineVariant, shape)
    Column(if (onClick != null) m.clickable(onClick = onClick) else m, content = content)
}

fun Modifier.noRippleClickable(onClick: () -> Unit) = clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick)

@Composable
fun ScrimGradient(modifier: Modifier = Modifier, top: Boolean = false) {
    val c = MaterialTheme.extras.canvas
    Box(modifier.background(Brush.verticalGradient(if (top) listOf(Color(0xCC000000), Color.Transparent) else listOf(Color.Transparent, c.copy(alpha = 0.6f), c))))
}
