package com.kgyms.app.ui.member.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kgyms.app.ui.member.DashboardDarkPalette
import com.kgyms.app.ui.member.DashboardLightPalette
import com.kgyms.app.ui.member.MemberDashboardAppearance

internal data class ProfilePalette(
    val background: Color, val card: Color, val border: Color,
    val accent: Color, val text: Color, val muted: Color,
    val tint: Color, val danger: Color, val dangerBackground: Color,
    val avatarStart: Color, val avatarEnd: Color,
)

internal val DarkProfilePalette = DashboardDarkPalette
internal val LightProfilePalette = DashboardLightPalette
internal val LocalProfilePalette = staticCompositionLocalOf { DarkProfilePalette }
internal val LocalProfileLanguage = staticCompositionLocalOf { "vi" }

/** A scoped override; the team's KgyMsTheme and typography remain unchanged. */
@Composable
internal fun ProfileAppearance(dark: Boolean, language: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalProfileLanguage provides language) {
        MemberDashboardAppearance(dark, content)
    }
}


// The member shell still hosts QR and Nutrition, which use their existing appearance.
private val LegacyDarkPalette = ProfilePalette(
    Color(0xFF0B0E15), Color(0xFF1B2130), Color(0xFF292E3B),
    Color(0xFFA64DFF), Color(0xFFF5F5FC), Color(0xFFA1ACBE),
    Color(0xFF29264C), Color(0xFFFF788E), Color(0xFF481B24),
    Color(0xFF2B204B), Color(0xFF192D45),
)
private val LegacyLightPalette = ProfilePalette(
    Color(0xFFFFF9FC), Color.White, Color(0xFFF0DCE6),
    Color(0xFFC72D72), Color(0xFF271E25), Color(0xFF786571),
    Color(0xFFFCE2EE), Color(0xFFB32645), Color(0xFFFFE4EA),
    Color(0xFFFFDCEB), Color(0xFFFFF0F6),
)

@Composable
internal fun MemberShellAppearance(dark: Boolean, language: String, content: @Composable () -> Unit) {
    val palette = if (dark) LegacyDarkPalette else LegacyLightPalette
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val colors = base.copy(primary = palette.accent, onPrimary = Color.White, background = palette.background,
        onBackground = palette.text, surface = palette.card, onSurface = palette.text,
        surfaceVariant = palette.tint, onSurfaceVariant = palette.muted, outline = palette.border,
        error = palette.danger, onError = Color.White, secondary = palette.accent)
    CompositionLocalProvider(LocalProfilePalette provides palette, LocalProfileLanguage provides language) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

@Composable
internal fun profileText(vi: String, en: String): String =
    if (LocalProfileLanguage.current == "vi") vi else en
