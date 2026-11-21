package com.kgyms.app.ui.member

import android.content.Context
import android.app.Activity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kgyms.app.data.repository.LanguageRepository
import com.kgyms.app.data.local.FitnessGoalPreferences
import com.kgyms.app.data.local.ProfileDevicePreferences

internal data class MemberPreferences(
    val dark: Boolean,
    val language: String,
    val weeklyTarget: Int,
    val setAppearance: (Boolean, String) -> Unit,
    val setWeeklyTarget: (Int) -> Unit,
    val weeklyTargetOverridden: Boolean = false,
    val fitnessGoals: FitnessGoalPreferences = FitnessGoalPreferences(),
    val saveFitnessGoals: (suspend (Int, FitnessGoalPreferences) -> Boolean)? = null,
)

// Null allows existing standalone Profile previews/tests to retain their local state.
internal val LocalMemberPreferences = staticCompositionLocalOf<MemberPreferences?> { null }

@Composable
internal fun rememberMemberPreferences(accountUid: String? = null, planTarget: Int? = null): MemberPreferences {
    val context = LocalContext.current.applicationContext
    val storage = remember(context) { context.getSharedPreferences(LanguageRepository.PREFS_NAME, Context.MODE_PRIVATE) }
    val appLanguage = com.kgyms.app.ui.localization.LocalAppLanguage.current
    var dark by rememberSaveable { mutableStateOf(storage.getBoolean("dark", true)) }
    var language by rememberSaveable {
        mutableStateOf(
            LanguageRepository.normalize(
                storage.getString(LanguageRepository.KEY_LANGUAGE, null)
            )
        )
    }

    LaunchedEffect(appLanguage.language) {
        if (appLanguage.onLanguageChange != null) language = appLanguage.language
    }

    // Mỗi tài khoản có một mục tiêu tập luyện riêng.
    val targetKey = accountUid?.let {
        "weekly_target_" + com.kgyms.app.data.model.memberAccountKey(it)
    } ?: "weekly_target"

    var explicitTarget by rememberSaveable(targetKey) {
        mutableIntStateOf(
            if (storage.contains(targetKey)) {
                storage.getInt(targetKey, 1).coerceIn(1, 7)
            } else {
                0
            }
        )
    }

    // Ưu tiên mục tiêu người dùng tự đặt, sau đó đến Personal Plan.
    val target = com.kgyms.app.data.model.resolveWorkoutTarget(
        explicitTarget.takeIf { it > 0 },
        planTarget
    )
    val profilePreferences = remember(storage, accountUid) { accountUid?.let { ProfileDevicePreferences(storage, it) } }
    var fitnessGoals by remember(profilePreferences) { mutableStateOf(profilePreferences?.goals() ?: FitnessGoalPreferences()) }
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
        language = LanguageRepository.normalize(nextLanguage)
        appLanguage.onLanguageChange?.invoke(language)
        storage.edit()
            .putBoolean("dark", dark)
            .putString(LanguageRepository.KEY_LANGUAGE, language)
            .apply()
    }, { next ->
        explicitTarget = next.coerceIn(1, 7)
        storage.edit().putInt(targetKey, explicitTarget).commit()
    }, weeklyTargetOverridden = explicitTarget > 0, fitnessGoals = fitnessGoals,
        saveFitnessGoals = profilePreferences?.let { preferences ->
            { next, goals ->
                val saved = preferences.saveGoals(next, goals)
                if (saved) {
                    fitnessGoals = goals
                    explicitTarget = next
                }
                saved
            }
        })
}
