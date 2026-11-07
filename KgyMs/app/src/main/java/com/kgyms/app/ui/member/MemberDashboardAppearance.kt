package com.kgyms.app.ui.member

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kgyms.app.ui.member.profile.LocalProfilePalette
import com.kgyms.app.ui.member.profile.ProfilePalette

// Scoped to Home and the member bar. Profile, Workout and the host theme keep their styling.
internal val DashboardDarkPalette = ProfilePalette(
    background = Color(0xFF0E131B), card = Color(0xFF181F2A), border = Color(0xFF2B3443),
    accent = Color(0xFFBEACF5), text = Color(0xFFF2F4F8), muted = Color(0xFFADB7C8),
    tint = Color(0xFF302942), danger = Color(0xFFFFB4AB), dangerBackground = Color(0xFF482925),
    avatarStart = Color(0xFF353047), avatarEnd = Color(0xFF24383F),
)
internal val DashboardLightPalette = ProfilePalette(
    background = Color(0xFFF3F5F8), card = Color.White, border = Color(0xFFDEE3EB),
    accent = Color(0xFF6345A6), text = Color(0xFF1B2535), muted = Color(0xFF5D697B),
    tint = Color(0xFFEFE9F9), danger = Color(0xFFB3261E), dangerBackground = Color(0xFFFFEDEA),
    avatarStart = Color(0xFFE8E0F5), avatarEnd = Color(0xFFDCEDEF),
)

@Composable
internal fun MemberDashboardAppearance(content: @Composable () -> Unit) {
    val dark = LocalMemberPreferences.current?.dark ?: (LocalProfilePalette.current.background.luminance() < 0.5f)
    val p = if (dark) DashboardDarkPalette else DashboardLightPalette
    val colors = remember(dark) {
        if (dark) darkColorScheme(
            primary = p.accent, onPrimary = Color(0xFF201536), primaryContainer = p.tint, onPrimaryContainer = p.accent,
            secondary = Color(0xFF93CDD1), onSecondary = Color(0xFF103539),
            secondaryContainer = Color(0xFF213A41), onSecondaryContainer = Color(0xFFB8E7E9),
            tertiary = Color(0xFFB8D7A8), onTertiary = Color(0xFF203719),
            tertiaryContainer = Color(0xFF2C3B2B), onTertiaryContainer = Color(0xFFCCE8C0),
            background = p.background, onBackground = p.text, surface = p.card, onSurface = p.text,
            surfaceVariant = p.tint, onSurfaceVariant = p.muted, outline = p.border,
            error = p.danger, errorContainer = p.dangerBackground,
        ) else lightColorScheme(
            primary = p.accent, onPrimary = Color.White, primaryContainer = p.tint, onPrimaryContainer = p.accent,
            secondary = Color(0xFF286B73), onSecondary = Color.White,
            secondaryContainer = Color(0xFFDDEFF0), onSecondaryContainer = Color(0xFF285D64),
            tertiary = Color(0xFF486A37), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFE8F0E0), onTertiaryContainer = Color(0xFF3C5E2C),
            background = p.background, onBackground = p.text, surface = p.card, onSurface = p.text,
            surfaceVariant = p.tint, onSurfaceVariant = p.muted, outline = p.border,
            error = p.danger, errorContainer = p.dangerBackground,
        )
    }
    val inherited = MaterialTheme.typography
    val typography = remember(inherited) {
        inherited.copy(
            headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
            titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
            labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
        )
    }
    CompositionLocalProvider(LocalProfilePalette provides p) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}
