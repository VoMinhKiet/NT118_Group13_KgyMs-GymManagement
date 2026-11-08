package com.kgyms.app.ui.member

import android.content.Context
import android.app.Activity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

internal data class MemberPreferences(
    val dark: Boolean,
    val language: String,
    val weeklyTarget: Int,
    val setAppearance: (Boolean, String) -> Unit,
    val setWeeklyTarget: (Int) -> Unit,
)

// Null allows existing standalone Profile previews/tests to retain their local state.
internal val LocalMemberPreferences = staticCompositionLocalOf<MemberPreferences?> { null }

@Composable
internal fun rememberMemberPreferences(accountUid: String? = null): MemberPreferences {
    val context = LocalContext.current.applicationContext
    val storage = remember(context) { context.getSharedPreferences("kgyms_member_ui", Context.MODE_PRIVATE) }
    var dark by rememberSaveable { mutableStateOf(storage.getBoolean("dark", true)) }
    var language by rememberSaveable { mutableStateOf(storage.getString("language", "vi").let { if (it == "en") "en" else "vi" }) }
    val targetKey = accountUid?.let { "weekly_target_" + com.kgyms.app.data.model.memberAccountKey(it) } ?: "weekly_target"
    var target by rememberSaveable(targetKey) { mutableIntStateOf(storage.getInt(targetKey, 6).coerceIn(1, 7)) }
    val window = (LocalContext.current as? Activity)?.window
    val view = LocalView.current
    // Restore host bar styling when Member leaves composition (e.g. future Auth host).
    DisposableEffect(window, view, dark) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
        onDispose {
            if (previousStatus != null) controller?.isAppearanceLightStatusBars = previousStatus
            if (previousNavigation != null) controller?.isAppearanceLightNavigationBars = previousNavigation
        }
    }
    return MemberPreferences(dark, language, target, { nextDark, nextLanguage ->
        dark = nextDark
        language = if (nextLanguage == "en") "en" else "vi"
        storage.edit().putBoolean("dark", dark).putString("language", language).apply()
    }, { next ->
        target = next.coerceIn(1, 7)
        storage.edit().putInt(targetKey, target).apply()
    })
}
