package com.example.offlinemusic.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Holds every semantic colour slot the MUSED UI needs.
 * A single instance of this class fully describes one visual theme.
 */
data class MusedPalette(
    val key: String,
    val label: String,
    val accent: Color,
    val accentDark: Color,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val card: Color,
    val cardStrong: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val success: Color,
    val error: Color,
    /** A swatch preview circle colour used in the theme picker. */
    val swatch: Color = accent
)

/* ───────────────────── Built-in palettes ───────────────────── */

private val Cyan = MusedPalette(
    key = "cyan",
    label = "Cyan",
    accent = Color(0xFF67E8F9),
    accentDark = Color(0xFF22D3EE),
    background = Color(0xFF0B0F1A),
    surface = Color(0xFF0F1525),
    surfaceElevated = Color(0xFF151D2E),
    card = Color(0xFF1A2438),
    cardStrong = Color(0xFF223049),
    border = Color(0xFF2A3A54),
    textPrimary = Color(0xFFF0F4FA),
    textSecondary = Color(0xFF8B99AE),
    textMuted = Color(0xFF5A6A82),
    success = Color(0xFF34D399),
    error = Color(0xFFF87171)
)

private val Rose = MusedPalette(
    key = "rose",
    label = "Rose",
    accent = Color(0xFFFB7185),
    accentDark = Color(0xFFF43F5E),
    background = Color(0xFF0E0A10),
    surface = Color(0xFF140F18),
    surfaceElevated = Color(0xFF1B1422),
    card = Color(0xFF231B2E),
    cardStrong = Color(0xFF2E2339),
    border = Color(0xFF3D2F4A),
    textPrimary = Color(0xFFF8F0F5),
    textSecondary = Color(0xFFA8909E),
    textMuted = Color(0xFF78647A),
    success = Color(0xFF34D399),
    error = Color(0xFFF87171)
)

private val Violet = MusedPalette(
    key = "violet",
    label = "Violet",
    accent = Color(0xFFA78BFA),
    accentDark = Color(0xFF8B5CF6),
    background = Color(0xFF0C0A14),
    surface = Color(0xFF110F1C),
    surfaceElevated = Color(0xFF181526),
    card = Color(0xFF1F1B32),
    cardStrong = Color(0xFF28233F),
    border = Color(0xFF362F52),
    textPrimary = Color(0xFFF2F0FA),
    textSecondary = Color(0xFF9E95B5),
    textMuted = Color(0xFF6E6488),
    success = Color(0xFF34D399),
    error = Color(0xFFF87171)
)

private val Emerald = MusedPalette(
    key = "emerald",
    label = "Emerald",
    accent = Color(0xFF6EE7B7),
    accentDark = Color(0xFF34D399),
    background = Color(0xFF080E0E),
    surface = Color(0xFF0D1515),
    surfaceElevated = Color(0xFF121D1D),
    card = Color(0xFF182828),
    cardStrong = Color(0xFF1F3232),
    border = Color(0xFF2A4242),
    textPrimary = Color(0xFFEFF8F4),
    textSecondary = Color(0xFF8DADA0),
    textMuted = Color(0xFF5E7E72),
    success = Color(0xFF34D399),
    error = Color(0xFFF87171)
)

private val Amber = MusedPalette(
    key = "amber",
    label = "Amber",
    accent = Color(0xFFFCD34D),
    accentDark = Color(0xFFF59E0B),
    background = Color(0xFF100D08),
    surface = Color(0xFF16120C),
    surfaceElevated = Color(0xFF1D1812),
    card = Color(0xFF271F16),
    cardStrong = Color(0xFF30271C),
    border = Color(0xFF42362A),
    textPrimary = Color(0xFFFAF5EB),
    textSecondary = Color(0xFFB0A48E),
    textMuted = Color(0xFF807560),
    success = Color(0xFF34D399),
    error = Color(0xFFF87171)
)

private val Slate = MusedPalette(
    key = "slate",
    label = "Slate",
    accent = Color(0xFF94A3B8),
    accentDark = Color(0xFF64748B),
    background = Color(0xFF0C0E12),
    surface = Color(0xFF10131A),
    surfaceElevated = Color(0xFF161A22),
    card = Color(0xFF1C212C),
    cardStrong = Color(0xFF242A36),
    border = Color(0xFF303844),
    textPrimary = Color(0xFFEDF0F5),
    textSecondary = Color(0xFF8892A2),
    textMuted = Color(0xFF5C6578),
    success = Color(0xFF34D399),
    error = Color(0xFFF87171)
)

/** All shipped themes keyed by their identifier. */
val BuiltInThemes: Map<String, MusedPalette> = linkedMapOf(
    Cyan.key to Cyan,
    Rose.key to Rose,
    Violet.key to Violet,
    Emerald.key to Emerald,
    Amber.key to Amber,
    Slate.key to Slate
)

val DefaultThemeKey = Cyan.key

/* ───────────────────── CompositionLocal ───────────────────── */

val LocalMusedColors = staticCompositionLocalOf { Cyan }

/**
 * Convenience accessor: `MusedColors.accent`, `MusedColors.background`, etc.
 * Works only inside a `MusicTheme { … }` block.
 */
object MusedColors {
    val current: MusedPalette
        @Composable @ReadOnlyComposable
        get() = LocalMusedColors.current
}

/* ───────────────────── Persistence ───────────────────── */

class ThemeManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mused_theme", Context.MODE_PRIVATE)

    fun selectedKey(): String =
        prefs.getString("theme_key", DefaultThemeKey) ?: DefaultThemeKey

    fun select(key: String) {
        prefs.edit().putString("theme_key", key).apply()
    }

    fun resolve(): MusedPalette =
        BuiltInThemes[selectedKey()] ?: BuiltInThemes.getValue(DefaultThemeKey)
}
