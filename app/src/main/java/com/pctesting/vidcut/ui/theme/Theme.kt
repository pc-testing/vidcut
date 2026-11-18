package com.pctesting.vidcut.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * VidCut's own colours. Every text/background pair used in the app is at least 4.5:1 contrast.
 */
@Immutable
data class VcColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,      // panels, sheets, cards
    val field: Color,        // inputs and segmented controls inside a surface
    val toolbar: Color,
    val border: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val icon: Color,
    val accent: Color,       // fills (buttons, slider)
    val onAccent: Color,
    val accentText: Color,   // accent used as text on background/surface
    val textTrack: Color,
    val onTextTrack: Color,
    val audioTrack: Color,
    val onAudioTrack: Color,
    val playhead: Color,
    val letterbox: Color,
    val sliderTrack: Color,
    val thumbPlaceholder: Color,
    val scrim: Color,
    val danger: Color,
)

val DarkColors = VcColors(
    isDark = true,
    background = Color(0xFF0E0E10),
    surface = Color(0xFF1A1A1E),
    field = Color(0xFF0E0E10),
    toolbar = Color(0xFF141417),
    border = Color(0xFF2A2A31),
    text = Color(0xFFF2F2F5),
    textSecondary = Color(0xFFA8A8B3),
    textTertiary = Color(0xFF8E8E99),
    icon = Color(0xFFC9C9D1),
    accent = Color(0xFF2FD3E0),
    onAccent = Color(0xFF0B0B0D),
    accentText = Color(0xFF2FD3E0),
    textTrack = Color(0xFF3A2E5C),
    onTextTrack = Color(0xFFE6DDFF),
    audioTrack = Color(0xFF1F3D2E),
    onAudioTrack = Color(0xFFCFF5DD),
    playhead = Color(0xFFFFFFFF),
    letterbox = Color(0xFF000000),
    sliderTrack = Color(0xFF3A3A42),
    thumbPlaceholder = Color(0xFF2B4450),
    scrim = Color(0xB3000000),
    danger = Color(0xFFFF6B6B),
)

val LightColors = VcColors(
    isDark = false,
    background = Color(0xFFF6F6F8),
    surface = Color(0xFFFFFFFF),
    field = Color(0xFFF6F6F8),
    toolbar = Color(0xFFFFFFFF),
    border = Color(0xFFE3E3E8),
    text = Color(0xFF141417),
    textSecondary = Color(0xFF5C5C66),
    textTertiary = Color(0xFF6B6B75),
    icon = Color(0xFF3A3A42),
    accent = Color(0xFF2FD3E0),
    onAccent = Color(0xFF0B0B0D),
    accentText = Color(0xFF00747E),
    textTrack = Color(0xFFE6DDFF),
    onTextTrack = Color(0xFF3A2E5C),
    audioTrack = Color(0xFFCFF5DD),
    onAudioTrack = Color(0xFF1F3D2E),
    playhead = Color(0xFF141417),
    letterbox = Color(0xFFE4E4E9),
    sliderTrack = Color(0xFFD4D4DA),
    thumbPlaceholder = Color(0xFF9FBAC6),
    scrim = Color(0x99000000),
    danger = Color(0xFFC62828),
)

val LocalVc = staticCompositionLocalOf { DarkColors }

/** Shortcut: `Vc.colors.text` etc. */
object Vc {
    val colors: VcColors
        @Composable get() = LocalVc.current
}

@Composable
fun VidCutTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            secondary = c.accent, onSecondary = c.onAccent,
            background = c.background, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.field, onSurfaceVariant = c.textSecondary,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface,
            surfaceContainerHighest = c.surface, surfaceContainerLow = c.surface,
            surfaceContainerLowest = c.surface,
            outline = c.border, outlineVariant = c.border,
            error = c.danger, onError = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            secondary = c.accent, onSecondary = c.onAccent,
            background = c.background, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.field, onSurfaceVariant = c.textSecondary,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface,
            surfaceContainerHighest = c.surface, surfaceContainerLow = c.surface,
            surfaceContainerLowest = c.surface,
            outline = c.border, outlineVariant = c.border,
            error = c.danger, onError = Color.White,
        )
    }
    CompositionLocalProvider(LocalVc provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
