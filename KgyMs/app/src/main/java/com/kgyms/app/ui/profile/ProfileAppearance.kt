package com.kgyms.app.ui.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal data class ProfilePalette(
    val background: Color, val card: Color, val border: Color,
    val accent: Color, val text: Color, val muted: Color,
    val tint: Color, val danger: Color, val dangerBackground: Color,
    val avatarStart: Color, val avatarEnd: Color,
)

internal val DarkProfilePalette = ProfilePalette(
    Color(0xFF0B0E15), Color(0xFF1B2130), Color(0xFF292E3B),
    Color(0xFFA64DFF), Color(0xFFF5F5FC), Color(0xFFA1ACBE),
    Color(0xFF29264C), Color(0xFFFF788E), Color(0xFF481B24),
    Color(0xFF2B204B), Color(0xFF192D45),
)
internal val LightProfilePalette = ProfilePalette(
    Color(0xFFFFF9FC), Color.White, Color(0xFFF0DCE6),
    Color(0xFFC72D72), Color(0xFF271E25), Color(0xFF786571),
    Color(0xFFFCE2EE), Color(0xFFB32645), Color(0xFFFFE4EA),
    Color(0xFFFFDCEB), Color(0xFFFFF0F6),
)
internal val LocalProfilePalette = staticCompositionLocalOf { DarkProfilePalette }
internal val LocalProfileLanguage = staticCompositionLocalOf { "vi" }

/** A scoped override; the team's KgyMsTheme and typography remain unchanged. */
@Composable
internal fun ProfileAppearance(dark: Boolean, language: String, content: @Composable () -> Unit) {
    val p = if (dark) DarkProfilePalette else LightProfilePalette
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, onPrimary = Color.White, background = p.background,
        onBackground = p.text, surface = p.card, onSurface = p.text,
        surfaceVariant = p.tint, onSurfaceVariant = p.muted, outline = p.border,
        error = p.danger, onError = Color.White, secondary = p.accent,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = Color.White, background = p.background,
        onBackground = p.text, surface = p.card, onSurface = p.text,
        surfaceVariant = p.tint, onSurfaceVariant = p.muted, outline = p.border,
        error = p.danger, onError = Color.White, secondary = p.accent,
    )
    CompositionLocalProvider(LocalProfilePalette provides p, LocalProfileLanguage provides language) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
    }
}

@Composable
internal fun profileText(vi: String, en: String): String =
    if (LocalProfileLanguage.current == "vi") vi else en
