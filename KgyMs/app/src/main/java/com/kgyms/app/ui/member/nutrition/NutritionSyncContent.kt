package com.kgyms.app.ui.member.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kgyms.app.data.model.MealSlot
import com.kgyms.app.data.model.NutritionMealCatalog
import com.kgyms.app.data.model.NutritionPlanData
import com.kgyms.app.data.model.NutritionSyncStatus
import com.kgyms.app.data.repository.NutritionViewData
import com.kgyms.app.ui.member.profile.LocalProfilePalette

@Composable
internal fun NutritionSyncBanner(
    en: Boolean,
    view: NutritionViewData,
    onRetry: () -> Unit,
    onConflict: () -> Unit,
) {
    val palette = LocalProfilePalette.current
    val label = when (view.status) {
        NutritionSyncStatus.Loading -> if (en) "Loading your meal plan…" else "Đang tải thực đơn…"
        NutritionSyncStatus.Device -> if (view.plan == null) return else
            if (en) "Saved on this device" else "Đã lưu trên thiết bị"
        NutritionSyncStatus.Syncing -> if (en) "Syncing…" else "Đang đồng bộ…"
        NutritionSyncStatus.Synced -> if (en) "Synced with Firebase" else "Đã đồng bộ Firebase"
        NutritionSyncStatus.Empty -> return
        NutritionSyncStatus.Offline -> if (en) "Waiting for a network connection" else "Đang chờ kết nối mạng"
        NutritionSyncStatus.Error -> if (en) "Could not sync your meal plan" else "Đồng bộ thực đơn thất bại"
        NutritionSyncStatus.Incompatible -> if (en) "Saved data could not be read safely" else "Chưa thể đọc an toàn dữ liệu đã lưu"
        NutritionSyncStatus.Conflict -> if (en) "The device and Firebase versions differ" else "Thực đơn trên thiết bị và Firebase khác nhau"
    }
    Surface(color = palette.card, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth().testTag("nutrition_sync_status")) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = palette.muted, style = MaterialTheme.typography.bodySmall)
            if (view.status == NutritionSyncStatus.Loading || view.status == NutritionSyncStatus.Syncing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (view.status in listOf(NutritionSyncStatus.Error, NutritionSyncStatus.Incompatible, NutritionSyncStatus.Offline)) {
                TextButton(onClick = onRetry, modifier = Modifier.testTag("nutrition_sync_retry")) {
                    Text(if (en) "Retry" else "Thử lại")
                }
            }
            if (view.cloudConflict != null && view.status == NutritionSyncStatus.Conflict) {
                TextButton(onClick = onConflict, modifier = Modifier.testTag("nutrition_sync_conflict")) {
                    Text(if (en) "Compare versions" else "So sánh hai bản")
                }
            }
        }
    }
}

@Composable
internal fun NutritionConflictDialog(
    en: Boolean,
    device: NutritionPlanData,
    cloud: NutritionPlanData,
    onDismiss: () -> Unit,
    onResolve: (keepDevice: Boolean) -> Unit,
) {
    val palette = LocalProfilePalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Choose a meal plan" else "Chọn bản thực đơn") },
        text = {
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (en) "Both versions are still available. The version you choose will be used across your devices."
                    else "Cả hai bản vẫn được giữ. Bản bạn chọn sẽ được dùng trên các thiết bị.")
                ConflictSummary(en, if (en) "This device" else "Thiết bị này", device)
                ConflictSummary(en, "Firebase", cloud)

                val changedSlots = (device.meals.keys + cloud.meals.keys).sorted().filter { device.meals[it] != cloud.meals[it] }
                Text(if (en) "Different meal slots: ${changedSlots.size}" else "Số bữa có món khác nhau: ${changedSlots.size}",
                    color = palette.muted)
                changedSlots.forEach { key ->
                    val day = key.substringBefore(':').toInt()
                    val slot = MealSlot.valueOf(key.substringAfter(':'))
                    val weekdays = if (en) listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        else listOf("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật")
                    Text("${weekdays[day]} · ${if (en) slot.en else slot.vi}", style = MaterialTheme.typography.titleSmall)
                    val localLabel = if (en) "Device" else "Thiết bị"
                    Text("$localLabel · ${conflictMealName(en, device.meals[key])}", style = MaterialTheme.typography.bodySmall)
                    Text("Firebase · ${conflictMealName(en, cloud.meals[key])}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.End) {
                TextButton(onClick = { onResolve(true) }, modifier = Modifier.testTag("nutrition_sync_keep_device")) {
                    Text(if (en) "Keep device version" else "Giữ bản trên thiết bị")
                }
                TextButton(onClick = { onResolve(false) }, modifier = Modifier.testTag("nutrition_sync_keep_cloud")) {
                    Text(if (en) "Use Firebase version" else "Dùng bản trên Firebase")
                }
                TextButton(onClick = onDismiss) { Text(if (en) "Decide later" else "Chọn sau") }
            }
        },
    )
}

@Composable
private fun ConflictSummary(en: Boolean, label: String, plan: NutritionPlanData) {
    val setup = plan.setup ?: return
    val values = if (en) listOf(setup.goal.en, setup.activity.en, setup.diet.en)
        else listOf(setup.goal.vi, setup.activity.vi, setup.diet.vi)
    Text(label, style = MaterialTheme.typography.titleSmall)
    Text(values.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
    Text(if (en) "${plan.meals.size} meals selected" else "${plan.meals.size} món đã chọn",
        style = MaterialTheme.typography.bodySmall)
}

private fun conflictMealName(en: Boolean, id: String?): String {
    val meal = id?.let(NutritionMealCatalog::byId)
    return if (en) meal?.en ?: "Empty" else meal?.vi ?: "Chưa chọn món"
}
