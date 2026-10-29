package com.haseebflix.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.haseebflix.app.data.settings.AccentColor
import com.haseebflix.app.data.settings.AppSettings
import com.haseebflix.app.data.settings.ThemeMode

/** Extra roles Material 3 has no slot for (see UI.md §2.4). */
@Immutable
class Extras(
    val brandText: Color,
    val rating: Color = Color(0xFFFFC107),
    val badge: Color = Color(0x99000000),
    val canvas: Color = Color(0xFF0E0E11),
    val onMedia: Color = Color.White,
    val success: Color = Color(0xFF34C759),
    val warning: Color = Color(0xFFFFC107),
)

val LocalExtras = staticCompositionLocalOf { Extras(brandText = Color(0xFFFF4D5A)) }
val LocalDataSaver = staticCompositionLocalOf { false }
val MaterialTheme.extras: Extras @Composable @ReadOnlyComposable get() = LocalExtras.current

private class AccentSpec(
    val primary: Color, val container: Color, val onContainer: Color, val pill: Color, val text: Color, val lightText: Color,
)

private fun AccentColor.spec() = when (this) {
    AccentColor.CRIMSON -> AccentSpec(Color(0xFFE11D2E), Color(0xFF4A0A0F), Color(0xFFFFDAD6), Color(0xFF3A2326), Color(0xFFFF4D5A), Color(0xFFC4000F))
    AccentColor.EMBER -> AccentSpec(Color(0xFFD84315), Color(0xFF4A1A0A), Color(0xFFFFDBCF), Color(0xFF3A2118), Color(0xFFFF8A65), Color(0xFFB23200))
    AccentColor.VIOLET -> AccentSpec(Color(0xFF6D4AE0), Color(0xFF241553), Color(0xFFE6DEFF), Color(0xFF2A2240), Color(0xFFB59CFF), Color(0xFF5030C0))
    AccentColor.OCEAN -> AccentSpec(Color(0xFF0A74B5), Color(0xFF062A40), Color(0xFFCDE9FF), Color(0xFF15293A), Color(0xFF6CC3FF), Color(0xFF075C91))
    AccentColor.FOREST -> AccentSpec(Color(0xFF17794E), Color(0xFF07301F), Color(0xFFC8F3DD), Color(0xFF15332A), Color(0xFF5FD9A0), Color(0xFF0F5E3B))
}

private val OnDark = Color(0xFFF5F5F7)
private val Variant = Color(0xFFA8A8B3)

private fun darkScheme(a: AccentSpec, amoled: Boolean) = darkColorScheme(
    primary = a.primary, onPrimary = Color.White, primaryContainer = a.container, onPrimaryContainer = a.onContainer,
    inversePrimary = a.primary,
    secondary = Variant, onSecondary = Color(0xFF0E0E11), secondaryContainer = a.pill, onSecondaryContainer = a.text,
    tertiary = Color(0xFFFABD00), onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF926D00), onTertiaryContainer = Color(0xFFFFF9F3),
    background = if (amoled) Color.Black else Color(0xFF0E0E11), onBackground = OnDark,
    surface = if (amoled) Color(0xFF0A0A0D) else Color(0xFF141418), onSurface = OnDark, onSurfaceVariant = Variant,
    surfaceVariant = Color(0xFF353439), surfaceTint = a.primary,
    surfaceDim = Color(0xFF0E0E11), surfaceBright = Color(0xFF39393D),
    surfaceContainerLowest = if (amoled) Color.Black else Color(0xFF0E0E12),
    surfaceContainerLow = if (amoled) Color(0xFF121216) else Color(0xFF1B1B20),
    surfaceContainer = if (amoled) Color(0xFF1A1A1E) else Color(0xFF222228),
    surfaceContainerHigh = if (amoled) Color(0xFF222228) else Color(0xFF2B2B32),
    surfaceContainerHighest = if (amoled) Color(0xFF2B2B30) else Color(0xFF353439),
    outline = Color(0xFF6E6E78), outlineVariant = if (amoled) Color(0xFF2A2A30) else Color(0xFF33333A),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE4E1E7), inverseOnSurface = Color(0xFF303034), scrim = Color.Black,
)

private fun lightScheme(a: AccentSpec) = lightColorScheme(
    primary = a.primary, onPrimary = Color.White,
    primaryContainer = a.primary.copy(alpha = 0.14f).compositeOver(Color.White), onPrimaryContainer = a.lightText,
    inversePrimary = a.text,
    secondary = Color(0xFF55555F), onSecondary = Color.White,
    secondaryContainer = a.primary.copy(alpha = 0.12f).compositeOver(Color.White), onSecondaryContainer = a.lightText,
    tertiary = Color(0xFF7A5900), onTertiary = Color.White,
    background = Color(0xFFFAFAFB), onBackground = Color(0xFF121216),
    surface = Color.White, onSurface = Color(0xFF121216), onSurfaceVariant = Color(0xFF55555F),
    surfaceVariant = Color(0xFFE2E2E8), surfaceTint = a.primary,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F6F9), surfaceContainer = Color(0xFFF0F0F4),
    surfaceContainerHigh = Color(0xFFE9E9EE), surfaceContainerHighest = Color(0xFFE2E2E8),
    outline = Color(0xFF8A8A94), outlineVariant = Color(0xFFE2E2E8),
    error = Color(0xFFBA1A1A), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
)

@Composable
fun HaseebFlixTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val amoled = settings.theme == ThemeMode.AMOLED
    val spec = settings.accent.spec()
    val ctx = LocalContext.current
    val useDynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= 31

    val scheme = if (Build.VERSION.SDK_INT >= 31 && settings.dynamicColor) {
        if (dark) dynamicDarkColorScheme(ctx).let {
            if (amoled) it.copy(background = Color.Black, surface = Color(0xFF0A0A0D), surfaceContainerLow = Color(0xFF121216), surfaceContainer = Color(0xFF1A1A1E)) else it
        } else dynamicLightColorScheme(ctx)
    } else if (dark) darkScheme(spec, amoled) else lightScheme(spec)
    val brandText = when {
        useDynamic -> scheme.primary
        dark -> spec.text
        else -> spec.lightText
    }
    val extras = remember(brandText, scheme.background) { Extras(brandText = brandText, canvas = scheme.background) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalExtras provides extras) {
        MaterialTheme(colorScheme = scheme, typography = HfTypography, shapes = HfShapes, content = content)
    }
}

fun AccentColor.swatch(): Color = spec().primary
