package com.haseebflix.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haseebflix.app.BuildConfig
import com.haseebflix.app.ui.components.AppLogo
import com.haseebflix.app.ui.theme.Montserrat
import com.haseebflix.app.ui.theme.extras
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val brand = MaterialTheme.extras.brandText
    val progress = remember { Animatable(0f) }
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        delay(250)
        finished()
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.96f, 1.06f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale",
    )

    Box(
        Modifier.fillMaxSize().background(cs.background).drawBehind {
            drawRect(Brush.radialGradient(listOf(Color(0xFF55111A), Color(0xFF0E0E11)), center, size.minDimension * 0.95f))
            listOf(0.08f to 0.24f, 0.9f to 0.33f, 0.12f to 0.66f, 0.8f to 0.75f).forEachIndexed { i, (x, y) ->
                drawCircle(if (i == 2) Color(0xFF7A2A33) else Color(0x80FFFFFF), if (i == 1) 5.dp.toPx() else 3.dp.toPx(), Offset(size.width * x, size.height * y))
            }
        },
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(150.dp).scale(pulse).background(Brush.radialGradient(listOf(cs.primary.copy(alpha = 0.45f), Color.Transparent)), CircleShape))
                AppLogo(92.dp, Modifier.scale(pulse))
            }
            Spacer(Modifier.height(36.dp))
            val s = androidx.compose.ui.text.TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = 6.sp)
            Row { Text("HASEEB-", style = s, color = Color.White); Text("FLIX", style = s, color = cs.primary) }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(7.dp).background(cs.primary, CircleShape))
                Text("STREAM MOVIES & TV", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp), color = Color(0xFFD0CFD8))
                Box(Modifier.size(5.dp).background(cs.outline, CircleShape))
                Text("FREE & AD-FREE", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp), color = Color(0xFFD0CFD8))
            }
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(cs.surfaceContainerLow).padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.LocalFireDepartment, null, tint = MaterialTheme.extras.rating, modifier = Modifier.size(18.dp))
                Text("No account • No ads • No tracking", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(cs.surfaceContainerHigh)) {
                Box(Modifier.fillMaxWidth(progress.value).fillMaxHeight().background(Brush.horizontalGradient(listOf(cs.primaryContainer, cs.primary)), CircleShape))
            }
            Row(Modifier.fillMaxWidth()) {
                Text(if (progress.value >= 1f) "READY TO STREAM" else "LOADING", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("${(progress.value * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = brand)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Movie, null, tint = MaterialTheme.extras.rating, modifier = Modifier.size(18.dp))
                Text("Powered by TMDB Database", style = MaterialTheme.typography.titleSmall, color = Color(0xFFE4E1E7))
            }
            Text("Version ${BuildConfig.VERSION_NAME} • Material 3 Expressive", style = MaterialTheme.typography.bodyMedium, color = cs.outline, textAlign = TextAlign.Center)
        }
    }
}
