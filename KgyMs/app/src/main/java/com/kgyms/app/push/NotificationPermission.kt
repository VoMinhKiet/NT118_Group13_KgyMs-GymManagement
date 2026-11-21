package com.kgyms.app.push

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kgyms.app.data.repository.LanguageRepository

private const val ASKED_KEY = "asked_post_notifications"

/** Trạng thái quyền hiện thông báo của app; [request] xin quyền hoặc mở cài đặt hệ thống nếu bị chặn hẳn. */
@Stable
class NotificationPermissionState internal constructor(
    private val context: Context,
    private val launch: () -> Unit,
) {
    /** true khi app được phép hiện thông báo (quyền Android 13+ và không bị tắt trong cài đặt hệ thống). */
    var enabled by mutableStateOf(areEnabled(context))
        internal set

    internal fun refresh() {
        enabled = areEnabled(context)
    }

    fun request() {
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val activity = context.findActivity()
        val asked = prefs(context).getBoolean(ASKED_KEY, false)
        val blockedForGood = needsRuntimePermission && asked && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        if (needsRuntimePermission && !blockedForGood) {
            prefs(context).edit().putBoolean(ASKED_KEY, true).apply()
            launch()
        } else {
            openSystemSettings(context)
        }
    }

    internal companion object {
        fun areEnabled(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

        fun openSystemSettings(context: Context) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}

@Composable
fun rememberNotificationPermissionState(): NotificationPermissionState {
    val context = LocalContext.current
    var state: NotificationPermissionState? = null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { state?.refresh() }
    val created = remember(context) {
        NotificationPermissionState(context) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    state = created
    // Quay lại từ màn hình cài đặt hệ thống: đọc lại trạng thái.
    LifecycleResumeEffect(created) {
        created.refresh()
        onPauseOrDispose { }
    }
    return created
}

/** Hỏi quyền thông báo đúng một lần (Android 13+) khi hội viên vào app lần đầu. */
@Composable
fun RequestNotificationPermissionOnce() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted || prefs(context).getBoolean(ASKED_KEY, false)) return@LaunchedEffect
        prefs(context).edit().putBoolean(ASKED_KEY, true).apply()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private fun prefs(context: Context) =
    context.getSharedPreferences(LanguageRepository.PREFS_NAME, Context.MODE_PRIVATE)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
