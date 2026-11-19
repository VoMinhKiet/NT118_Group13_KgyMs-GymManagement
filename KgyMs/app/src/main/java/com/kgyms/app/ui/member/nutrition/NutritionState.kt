package com.kgyms.app.ui.member.nutrition

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kgyms.app.data.firebase.NutritionFirebaseService
import com.kgyms.app.data.local.NutritionJournalPreferences
import com.kgyms.app.data.repository.NutritionRepository

internal val LocalNutritionRepository = staticCompositionLocalOf<NutritionRepository?> { null }

@Composable
internal fun rememberNutritionRepository(uid: String?): NutritionRepository? {
    val context = LocalContext.current.applicationContext
    val repository = remember(context, uid) {
        uid?.let { owner ->
            runCatching { NutritionFirebaseService.production(context, owner) }.getOrNull()?.let { service ->
                NutritionRepository(owner, NutritionJournalPreferences(context, owner), service, service::ownsSession)
            }
        }
    }
    LaunchedEffect(repository) { repository?.run() }
    DisposableEffect(repository) { onDispose { repository?.close() } }
    return repository
}
