@file:OptIn(ExperimentalMaterial3Api::class)

package com.kgyms.app.ui.member.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import com.kgyms.app.data.model.*
import com.kgyms.app.ui.member.profile.LocalProfileLanguage
import com.kgyms.app.ui.member.profile.LocalProfilePalette
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class MealWrite(val target: NutritionMealTarget, val mealId: String?)
private enum class MealFeedback { Added, Changed, Removed }
private const val MAX_PREVIEW_SERVINGS = 12

private data class NutritionColors(
    val accent: Color,
    val accentSoft: Color,
    val mint: Color,
    val onMint: Color,
)

@Composable
private fun nutritionColors(): NutritionColors {
    val p = LocalProfilePalette.current
    val dark = p.background.luminance() < .5f
    return if (dark) NutritionColors(
        accent = p.accent,
        accentSoft = p.tint,
        mint = Color(0xFF173936),
        onMint = Color(0xFFB9E8DF),
    ) else NutritionColors(
        accent = Color(0xFF6B4BC3),
        accentSoft = Color(0xFFF0EAFF),
        mint = Color(0xFFE7F5F1),
        onMint = Color(0xFF28665E),
    )
}

/** Drop-in Nutrition entry screen. Parent MemberShell supplies the existing bottom navigation. */
@Composable
internal fun NutritionScreen(uid: String?, onBack: () -> Unit, onHome: () -> Unit) {
    val en = LocalProfileLanguage.current == "en"
    val palette = LocalProfilePalette.current
    val nutrition = nutritionColors()
    val repository = LocalNutritionRepository.current?.takeIf { it.uid == uid }
    var editingSetup by remember(uid) { mutableStateOf(false) }
    var saveError by remember(uid) { mutableStateOf(false) }
    var savingPlan by remember(uid, repository) { mutableStateOf(false) }
    var showConflict by remember(uid) { mutableStateOf(false) }
    var selectedDay by rememberSaveable(uid) {
        mutableIntStateOf(mondayIndex(System.currentTimeMillis()))
    }
    var choosing by remember(uid, repository) { mutableStateOf<NutritionMealTarget?>(null) }
    var openedRecipe by remember(uid, repository) { mutableStateOf<Pair<NutritionMealTarget, NutritionMeal>?>(null) }
    var savingMeal by remember(uid, repository) { mutableStateOf(false) }
    var failedMeal by remember(uid, repository) { mutableStateOf<MealWrite?>(null) }
    var feedback by remember(uid, repository) { mutableStateOf<Pair<MealWrite, MealFeedback>?>(null) }
    var confirmRegenerate by remember(uid) { mutableStateOf(false) }

    if (repository == null) {
        Column(
            Modifier.fillMaxSize().background(palette.background).statusBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (en) "Nutrition" else "Dinh dưỡng", color = palette.text,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            val message = if (uid == null) {
                if (en) "Please sign in to save a personal meal plan." else "Bạn cần đăng nhập để lưu thực đơn cá nhân."
            } else {
                if (en) "Nutrition storage is currently unavailable." else "Chưa thể mở nơi lưu thực đơn."
            }
            Text(message, color = palette.muted)
            TextButton(onClick = onHome) {
                Text(if (en) "Back to Home" else "Về Trang chủ", color = nutrition.accent)
            }
        }
        return
    }

    val view by repository.view.collectAsState()
    val saved = view.plan ?: NutritionPlanData()
    val scope = rememberCoroutineScope()

    val update: ((NutritionPlanData) -> NutritionPlanData, () -> Unit) -> Unit = update@{ change, done ->
        if (savingPlan) return@update
        savingPlan = true
        saveError = false
        scope.launch {
            try {
                saveError = !repository.save(change)
                if (!saveError) done()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saveError = true
            } finally {
                savingPlan = false
            }
        }
    }

    val submitMeal: (MealWrite) -> Unit = submit@{ request ->
        if (savingMeal) return@submit
        val previousId = view.plan?.meals?.get(request.target.key)
        savingMeal = true
        failedMeal = null
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (repository.saveMeal(request.target, request.mealId)) {
                    if (previousId != request.mealId) {
                        feedback = request to when {
                            request.mealId == null -> MealFeedback.Removed
                            previousId == null -> MealFeedback.Added
                            else -> MealFeedback.Changed
                        }
                    }
                    choosing = null
                    openedRecipe = null
                } else {
                    failedMeal = request
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failedMeal = request
            } finally {
                savingMeal = false
            }
        }
    }
    val openPicker: (NutritionMealTarget) -> Unit = { target ->
        failedMeal = null
        feedback = null
        openedRecipe = null
        choosing = target
    }
    val status: @Composable () -> Unit = {
        NutritionSyncBanner(en, view, onRetry = { scope.launch { repository.retry() } },
            onConflict = { showConflict = true })
    }
    val now = System.currentTimeMillis()
    val weekDates = remember(now) { currentWeekDates(now) }
    val checkingFirstPlan = view.plan == null && view.status in listOf(NutritionSyncStatus.Loading, NutritionSyncStatus.Syncing)
    if (!view.editable || checkingFirstPlan) {
        Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NutritionHeader(en, onBack)
            status()
        }
        return
    }

    if (saved.setup == null || editingSetup) {
        NutritionSetupContent(
            existing = saved.setup,
            en = en,
            onHome = onHome,
            onCancel = if (saved.setup != null) ({
                editingSetup = false
                saveError = false
            }) else null,

            onSave = { profile ->
                update({ current ->
                    if (current.setup == null) NutritionPlanData(
                        setup = profile,
                        meals = NutritionMealCatalog.suggest(profile),
                        generatedFor = NutritionMealCatalog.fingerprint(profile),
                    ) else current.copy(setup = profile)
                }, { editingSetup = false })
            },
            status = status,
            error = saveError,
            isSaving = savingPlan,
        )
    } else {
        val profile = checkNotNull(saved.setup)
        Column(
            Modifier.fillMaxSize().background(palette.background).statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("nutrition_hub"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NutritionHeader(en, onBack)
            status()
            DayPicker(en, selectedDay, weekDates) {
                if (selectedDay != it) feedback = null
                selectedDay = it
            }
            SelectedDayHeading(dayLabel(en, weekDates[selectedDay], now))
            DayNutritionCard(en, saved.dayNutrition(selectedDay))
            MealSlot.entries.forEach { slot ->
                val target = NutritionMealTarget(selectedDay, slot)
                val mealId = saved.meals[target.key]
                val meal = mealId?.let(NutritionMealCatalog::byId)
                MealCard(
                    en = en, slot = slot, meal = meal,
                    onRecipe = { if (meal != null) openedRecipe = target to meal },
                    onChange = { openPicker(target) },
                    feedback = feedback?.takeIf { it.first.target == target && it.first.mealId == mealId }?.second,
                )
            }
            ProfileSummary(en, profile, onEdit = { editingSetup = true })
            val noMatching = saved.meals.values.any { id ->
                NutritionMealCatalog.byId(id)?.let {
                    profile.diet == NutritionDiet.VEGETARIAN && !it.vegetarian
                } == true
            }
            if (saved.generatedFor != NutritionMealCatalog.fingerprint(profile) || noMatching) {
                PreferenceChangedCard(en) {
                    saveError = false
                    confirmRegenerate = true
                }
            }
            if (saveError) Text(
                if (en) "Could not save changes. Please try again."
                else "Không lưu được thay đổi. Vui lòng thử lại.",
                color = palette.danger,
            )
            Spacer(Modifier.height(6.dp))
        }
    }


    choosing?.let { target ->
        val setup = saved.setup ?: return@let
        key(target) {
            MealChooser(
                en = en,
                slot = target.slot,
                day = dayLabel(en, weekDates[target.day], now),
                setup = setup,
                selectedId = saved.meals[target.key],
                isSaving = savingMeal,
                onRetry = failedMeal?.let { request -> { submitMeal(request) } },
                onDismiss = {
                    if (!savingMeal) {
                        choosing = null
                        failedMeal = null
                    }
                },
                onChoose = { submitMeal(MealWrite(target, it)) },
                onRemove = { submitMeal(MealWrite(target, null)) },
            )
        }
    }
    openedRecipe?.let { (target, meal) ->
        ModalBottomSheet(
            onDismissRequest = { openedRecipe = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.card,
            contentColor = palette.text,
        ) {
            MealRecipeContent(
                en = en, meal = meal,
                onBack = { openedRecipe = null },
                primaryLabel = if (en) "Change meal" else "Đổi món",
                onPrimary = { openPicker(target) },
                backLabel = if (en) "Close" else "Đóng",
            )
        }
    }

    val cloud = view.cloudConflict
    if (showConflict && cloud != null) NutritionConflictDialog(en, saved, cloud.plan,
        onDismiss = { showConflict = false },
        onResolve = { keepDevice ->
            scope.launch {
                try {
                    saveError = !repository.resolveConflict(cloud, keepDevice)
                    if (!saveError) showConflict = false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    saveError = true
                }
            }
        },
    )
    if (confirmRegenerate) AlertDialog(
        onDismissRequest = { if (!savingPlan) confirmRegenerate = false },
        title = { Text(if (en) "Replace your meal plan?" else "Thay thế thực đơn hiện tại?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (en) "Your current selections will be replaced with new suggestions."
                    else "Các món bạn đã chọn sẽ được thay bằng gợi ý mới. Thao tác không ảnh hưởng hồ sơ cá nhân.")
                if (saveError) Text(if (en) "Could not save. Please try again." else "Không thể lưu. Vui lòng thử lại.",
                    color = palette.danger)
            }
        },
        confirmButton = { TextButton(enabled = !savingPlan, onClick = {
            update({ current ->
                val setup = checkNotNull(current.setup)
                current.copy(meals = NutritionMealCatalog.suggest(setup),
                    generatedFor = NutritionMealCatalog.fingerprint(setup))
            }, { confirmRegenerate = false })
        }) {
            Text(if (savingPlan) if (en) "Saving…" else "Đang lưu…" else if (en) "Replace" else "Tạo lại")
        } },
        dismissButton = { TextButton(enabled = !savingPlan, onClick = { confirmRegenerate = false }) {
            Text(if (en) "Cancel" else "Hủy", color = nutrition.accent)
        } },
    )
}

@Composable
private fun NutritionHeader(en: Boolean, onBack: () -> Unit) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).testTag("nutrition_back"),
            contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(if (en) "‹ Back" else "‹ Trở lại", color = colors.accent)
        }
        Text(if (en) "My meal plan" else "Thực đơn của tôi",
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = p.text)
    }
}

@Composable
private fun PreferenceChangedCard(en: Boolean, onRegenerate: () -> Unit) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    Surface(color = colors.accentSoft, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.accent.copy(alpha = .28f)),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(if (en) "Preferences changed" else "Thiết lập đã thay đổi",
                fontWeight = FontWeight.SemiBold, color = p.text)
            TextButton(onClick = onRegenerate, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(if (en) "Generate new suggestions" else "Tạo lại gợi ý",
                    color = colors.accent)
            }
        }
    }
}

private fun mondayIndex(timeMillis: Long): Int {
    val calendar = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
    return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
}

private fun currentWeekDates(timeMillis: Long): List<Long> {
    val monday = Calendar.getInstance().apply {
        this.timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -mondayIndex(timeMillis))
    }
    return (0..6).map { offset ->
        (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }.timeInMillis
    }
}

@Composable
private fun SelectedDayHeading(title: String) {
    val p = LocalProfilePalette.current
    Text(title, color = p.text, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold, modifier = Modifier.testTag("nutrition_selected_day"))
}

private fun dayLabel(en: Boolean, selected: Long, now: Long): String =
    if (sameLocalDay(selected, now)) {
        if (en) "Today" else "Hôm nay"
    } else {
        val locale = if (en) Locale.ENGLISH else Locale.forLanguageTag("vi-VN")
        SimpleDateFormat("EEEE, dd/MM", locale).format(Date(selected))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

private fun sameLocalDay(first: Long, second: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = first }
    val b = Calendar.getInstance().apply { timeInMillis = second }
    return a.get(Calendar.ERA) == b.get(Calendar.ERA) &&
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun NutritionSetupContent(
    existing: NutritionSetup?, en: Boolean, onHome: () -> Unit,
    onCancel: (() -> Unit)?, onSave: (NutritionSetup) -> Unit, error: Boolean,
    isSaving: Boolean,
    status: @Composable () -> Unit,
) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    var goal by remember(existing) { mutableStateOf(existing?.goal) }
    var activity by remember(existing) { mutableStateOf(existing?.activity) }
    var diet by remember(existing) { mutableStateOf(existing?.diet) }
    Column(
        Modifier.fillMaxSize().background(p.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("nutrition_setup"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onCancel?.invoke() ?: onHome() }, enabled = !isSaving,
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (en) "‹ Back" else "‹ Trở lại", color = colors.accent)
            }
            Text(if (en) "NUTRITION" else "DINH DƯỠNG",
                style = MaterialTheme.typography.labelMedium, color = colors.accent)
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(if (existing == null) {
                if (en) "Set up your nutrition" else "Thiết lập dinh dưỡng"
            } else {
                if (en) "Update your preferences" else "Chỉnh sửa thiết lập"
            }, color = p.text, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text(if (en) "Choose a goal, activity level and eating style. No body measurements are needed."
                else "Chọn mục tiêu, mức vận động và chế độ ăn. Bạn không cần nhập lại thông tin cơ thể.",
                color = p.muted, style = MaterialTheme.typography.bodyMedium)
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.mint,
            border = BorderStroke(1.dp, colors.onMint.copy(alpha = .18f)),
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = colors.onMint.copy(alpha = .12f)) {
                    Icon(painterResource(R.drawable.home_icon_nutrition), null,
                        tint = colors.onMint, modifier = Modifier.padding(10.dp).size(24.dp))
                }
                Text(if (en) "Three quick choices create a simple seven-day collection of meal ideas."
                    else "Ba lựa chọn nhanh giúp tạo bộ gợi ý món ăn trong 7 ngày.",
                    color = p.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        status()
        ChoiceGroup(
            index = "01",
            title = if (en) "What's your goal?" else "Mục tiêu của bạn",
            selected = goal,
            values = NutritionGoal.entries,
            label = { goalLabel(it, en) },
            enabled = !isSaving,
            onChange = { goal = it },
        )
        ChoiceGroup(
            index = "02",
            title = if (en) "How active are you?" else "Mức độ vận động",
            selected = activity,
            values = NutritionActivity.entries,
            label = { activityLabel(it, en) },
            enabled = !isSaving,
            onChange = { activity = it },
        )
        ChoiceGroup(
            index = "03",
            title = if (en) "Your eating style" else "Chế độ ăn",
            selected = diet,
            values = NutritionDiet.entries,
            label = { dietLabel(it, en) },
            enabled = !isSaving,
            onChange = { diet = it },
        )
        if (error) Text(
            if (en) "Could not save. Please try again." else "Không thể lưu. Vui lòng thử lại.",
            color = p.danger,
        )
        Button(
            onClick = {
                val g = goal
                val a = activity
                val d = diet
                if (g != null && a != null && d != null) onSave(NutritionSetup(g, a, d))
            },
            enabled = !isSaving && goal != null && activity != null && diet != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("nutrition_save_setup"),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
        ) {
            Text(if (isSaving) {
                if (en) "Saving on this device…" else "Đang lưu trên thiết bị…"
            } else if (existing == null) {
                if (en) "Save and view meals" else "Lưu và xem thực đơn"
            } else {
                if (en) "Save changes" else "Lưu thay đổi"
            }, fontWeight = FontWeight.SemiBold)
        }
        Text(if (en) "You can edit these choices later. Existing meals stay unchanged until you explicitly regenerate them."
            else "Bạn có thể chỉnh sửa sau. Thực đơn hiện có chỉ thay đổi khi bạn chủ động tạo lại.",
            style = MaterialTheme.typography.bodySmall, color = p.muted)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun <T> ChoiceGroup(
    index: String,
    title: String,
    selected: T?,
    values: List<T>,
    label: (T) -> String,
    enabled: Boolean,
    onChange: (T) -> Unit,
) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    Surface(
        color = p.card,
        border = BorderStroke(1.dp, p.border),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = colors.accentSoft) {
                    Text(index, color = colors.accent, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Text(title, color = p.text, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium)
            }
            values.forEach { item ->
                val active = item == selected
                Surface(
                    onClick = { onChange(item) },
                    enabled = enabled,
                    shape = RoundedCornerShape(16.dp),
                    color = if (active) colors.accentSoft else p.background,
                    border = BorderStroke(
                        if (active) 1.5.dp else 1.dp,
                        if (active) colors.accent else p.border,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = active,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = colors.accent),
                        )
                        Text(label(item), color = p.text,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun goalLabel(value: NutritionGoal, en: Boolean): String = when (value) {
    NutritionGoal.FAT_LOSS -> if (en) "Fat loss" else "Giảm mỡ"
    NutritionGoal.MUSCLE_GAIN -> if (en) "Muscle gain" else "Tăng cơ"
    NutritionGoal.MAINTAIN -> if (en) "Maintain" else "Duy trì"
}

private fun activityLabel(value: NutritionActivity, en: Boolean): String = when (value) {
    NutritionActivity.LOW -> if (en) "Low" else "Ít"
    NutritionActivity.MODERATE -> if (en) "Moderate" else "Vừa"
    NutritionActivity.HIGH -> if (en) "High" else "Nhiều"
}

private fun dietLabel(value: NutritionDiet, en: Boolean): String = when (value) {
    NutritionDiet.REGULAR -> if (en) "Regular" else "Bình thường"
    NutritionDiet.VEGETARIAN -> if (en) "Vegetarian" else "Ăn chay"
}

@Composable
private fun ProfileSummary(en: Boolean, profile: NutritionSetup, onEdit: () -> Unit) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    Surface(shape = RoundedCornerShape(18.dp), color = p.card, contentColor = p.text,
        border = BorderStroke(1.dp, p.border), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (en) "Eating goal" else "Mục tiêu ăn uống", color = p.muted,
                    style = MaterialTheme.typography.labelMedium)
                Text("${goalLabel(profile.goal, en)} · ${dietLabel(profile.diet, en)}",
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp).testTag("nutrition_edit_setup")) {
                Text(if (en) "Edit" else "Chỉnh sửa", color = colors.accent)
            }
        }
    }
}

@Composable
private fun DayPicker(
    en: Boolean,
    selectedDay: Int,
    dates: List<Long>,
    onSelect: (Int) -> Unit,
) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    val labels = if (en) listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        else listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    val today = System.currentTimeMillis()
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("nutrition_day_picker")) {
        val gap = 4.dp
        val density = LocalDensity.current
        val minWidth = 48.dp * density.fontScale.coerceAtLeast(1f)
        val itemWidth = maxOf(minWidth, (maxWidth - gap * 6) / 7)
        val viewportWidth = with(density) { maxWidth.roundToPx() }
        val itemStep = with(density) { (itemWidth + gap).roundToPx() }
        val scroll = rememberScrollState()
        LaunchedEffect(selectedDay, viewportWidth, itemStep) {
            val start = selectedDay * itemStep
            val end = start + with(density) { itemWidth.roundToPx() }
            when {
                start < scroll.value -> scroll.scrollTo(start)
                end > scroll.value + viewportWidth -> scroll.scrollTo((end - viewportWidth).coerceAtLeast(0))
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(scroll).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
            dates.take(7).forEachIndexed { index, millis ->
                val selected = index == selectedDay
                val isToday = sameLocalDay(millis, today)
                val calendar = remember(millis) {
                    Calendar.getInstance().apply { timeInMillis = millis }
                }
                val dateText = "${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}"
                val description = if (en) {
                    "${labels[index]}, $dateText" + if (isToday) ", today" else ""
                } else {
                    "${labels[index]}, ngày $dateText" + if (isToday) ", hôm nay" else ""
                }
                Column(
                    Modifier.width(itemWidth).heightIn(min = 68.dp)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                        .semantics { contentDescription = description }
                        .testTag("nutrition_day_$index"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(labels[index], color = if (selected) colors.accent else p.muted,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) colors.accent else p.card,
                        border = if (selected) null else BorderStroke(
                            if (isToday) 1.5.dp else 1.dp,
                            if (isToday) colors.accent else p.border,
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(dateText, Modifier.padding(horizontal = 3.dp, vertical = 7.dp),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else p.text,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center)
                        }
                    }
                    Box(Modifier.size(4.dp).background(
                        if (selected) colors.accent else Color.Transparent,
                        CircleShape,
                    ))
                }
            }
        }
    }
}

@Composable
private fun MealCard(
    en: Boolean,
    slot: MealSlot,
    meal: NutritionMeal?,
    onRecipe: () -> Unit,
    onChange: () -> Unit,
    feedback: MealFeedback?,
) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    val tag = slot.name.lowercase(Locale.ROOT)
    Surface(shape = RoundedCornerShape(18.dp), color = p.card, contentColor = p.text,
        border = BorderStroke(1.dp, p.border), modifier = Modifier.fillMaxWidth().testTag("nutrition_meal_$tag")) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MealThumbnail(meal, slot, Modifier.size(56.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    MealTypeChip(if (en) slot.en else slot.vi, slot)
                    val name = if (en) meal?.en ?: "No meal selected" else meal?.vi ?: "Chưa chọn món"
                    Text(name, Modifier.testTag("nutrition_meal_name_$tag"),
                        color = if (meal == null) p.muted else p.text,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    meal?.nutritionPerServing?.let { nutrients ->
                        Text("≈ ${number(nutrients.kcal, 0, en)} kcal / " + if (en) "serving" else "phần",
                            color = p.muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (feedback != null) {
                Text(
                    when (feedback) {
                        MealFeedback.Added -> if (en) "Meal added on this device" else "Đã thêm món trên thiết bị"
                        MealFeedback.Changed -> if (en) "Meal updated on this device" else "Đã đổi món trên thiết bị"
                        MealFeedback.Removed -> if (en) "Meal removed on this device" else "Đã bỏ món trên thiết bị"
                    },
                    Modifier.padding(top = 8.dp).testTag("nutrition_meal_feedback")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = colors.accent, style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (meal != null) {
                    TextButton(onClick = onRecipe, modifier = Modifier.weight(1f).testTag("nutrition_recipe_$tag"),
                        contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(if (en) "View recipe" else "Xem công thức", color = colors.accent)
                    }
                }
                TextButton(onClick = onChange, modifier = Modifier.weight(1f).testTag("nutrition_change_$tag"),
                    contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(if (meal == null) {
                        if (en) "Choose meal" else "Chọn món"
                    } else if (en) "Change meal" else "Đổi món",
                        fontWeight = FontWeight.SemiBold, color = colors.accent)
                }
            }
        }
    }
}

@Composable
private fun MealTypeChip(text: String, slot: MealSlot) {
    val colors = nutritionColors()
    val useMint = slot == MealSlot.BREAKFAST || slot == MealSlot.SNACK
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (useMint) colors.mint else colors.accentSoft,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            color = if (useMint) colors.onMint else colors.accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MealThumbnail(
    meal: NutritionMeal?,
    slot: MealSlot,
    modifier: Modifier = Modifier,
) {
    val colors = nutritionColors()
    val chickenImage = meal?.id in setOf("bf_chicken", "lu_chicken", "di_chicken")
    if (chickenImage) {
        Image(
            painter = painterResource(R.drawable.home_meal_chicken_bowl),
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        val useMint = slot == MealSlot.BREAKFAST || slot == MealSlot.SNACK
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
            color = if (useMint) colors.mint else colors.accentSoft,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.home_icon_nutrition),
                    contentDescription = null,
                    tint = if (useMint) colors.onMint else colors.accent,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@Composable
private fun MealChooser(
    en: Boolean,
    slot: MealSlot,
    day: String,
    setup: NutritionSetup,
    selectedId: String?,
    isSaving: Boolean,
    onRetry: (() -> Unit)?,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(NutritionMealFilter.All) }
    var preview by remember { mutableStateOf<NutritionMeal?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    fun updateSearch(value: String, selection: NutritionMealFilter = filter) {
        query = value
        filter = selection
        listState.requestScrollToItem(0)
    }
    val selectMeal: (String) -> Unit = { mealId ->
        focusManager.clearFocus()
        onChoose(mealId)
    }
    val options = remember(slot, setup, query, filter) { NutritionMealCatalog.search(slot, setup, query, filter) }
    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = !isSaving,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !isSaving),
        containerColor = p.card,
        contentColor = p.text,
        modifier = Modifier.testTag("nutrition_meal_chooser"),
    ) {
        val recipe = preview
        if (recipe != null) {
            MealRecipeContent(
                en = en, meal = recipe,
                onBack = { preview = null },
                primaryLabel = if (en) "Choose meal" else "Chọn món này",
                onPrimary = { selectMeal(recipe.id) },
                backLabel = if (en) "Back to meals" else "Quay lại",
                isSaving = isSaving, onRetry = onRetry,
            )
        } else {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.91f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (en) "Choose a meal · ${slot.en}" else "Chọn món · ${slot.vi}",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(day, color = p.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = onDismiss, enabled = !isSaving) { Text(if (en) "Close" else "Đóng") }
                }
                MealWriteNotice(en, isSaving, onRetry)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f).testTag("nutrition_meal_options"),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    stickyHeader {
                        Box(Modifier.fillMaxWidth().background(p.card).padding(bottom = 4.dp)) {
                            OutlinedTextField(
                                value = query, onValueChange = { updateSearch(it) }, singleLine = true,
                                enabled = !isSaving,
                                label = { Text(if (en) "Search meals" else "Tìm món ăn") },
                                trailingIcon = if (query.isNotEmpty()) ({
                                    IconButton(onClick = { updateSearch("") }, enabled = !isSaving,
                                        modifier = Modifier.testTag("nutrition_clear_search")
                                            .semantics { contentDescription = if (en) "Clear search" else "Xóa từ khóa" }) {
                                        Text("×", fontSize = 22.sp)
                                    }
                                }) else null,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth().testTag("nutrition_meal_search"),
                                shape = RoundedCornerShape(16.dp),
                            )
                        }
                    }
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NutritionMealFilter.entries.forEach { option ->
                                FilterChip(selected = filter == option, onClick = { updateSearch(query, option) },
                                    enabled = !isSaving,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("nutrition_filter_${option.name}"),
                                    label = { Text(mealFilterLabel(option, en)) })
                            }
                        }
                    }
                    if (selectedId != null) item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val selected = NutritionMealCatalog.byId(selectedId)
                            val name = if (en) selected?.en else selected?.vi
                            Text((if (en) "Selected: " else "Đang chọn: ") + name.orEmpty(),
                                Modifier.weight(1f).testTag("nutrition_selected_meal"),
                                color = colors.accent, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { confirmRemove = true }, enabled = !isSaving,
                                modifier = Modifier.testTag("nutrition_remove_meal")) {
                                Text(if (en) "Remove meal" else "Bỏ món", color = p.danger)
                            }
                        }
                    }
                    if (options.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (en) "No matching meals." else "Không có món phù hợp.",
                                Modifier.testTag("nutrition_no_matching_meals"), color = p.muted,
                                textAlign = TextAlign.Center)
                            if (query.isNotBlank() || filter != NutritionMealFilter.All) {
                                TextButton(onClick = { updateSearch("", NutritionMealFilter.All) }, enabled = !isSaving,
                                    modifier = Modifier.testTag("nutrition_reset_search")) {
                                    Text(if (en) "Clear search and filters" else "Xóa từ khóa và bộ lọc")
                                }
                            }
                        }
                    }
                    items(options, key = { it.id }) { meal ->
                        val chosen = meal.id == selectedId
                        Surface(shape = RoundedCornerShape(16.dp),
                            color = if (chosen) colors.accentSoft else p.background, contentColor = p.text,
                            border = BorderStroke(1.dp, if (chosen) colors.accent else p.border),
                            modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f).testTag("nutrition_choice_${meal.id}")
                                    .clickable(enabled = !isSaving, role = Role.Button) { selectMeal(meal.id) }
                                    .heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    MealThumbnail(meal, meal.slot, Modifier.size(38.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(if (en) meal.en else meal.vi, style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold)
                                        val choiceLabel = if (chosen) {
                                            if (en) "Selected" else "Đang chọn"
                                        } else if (en) "Choose" else "Chọn"
                                        val energy = meal.nutritionPerServing?.let { " · ≈ ${number(it.kcal, 0, en)} kcal" }.orEmpty()
                                        Text(choiceLabel + energy,
                                            color = colors.accent, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                TextButton(onClick = {
                                    focusManager.clearFocus()
                                    preview = meal
                                }, enabled = !isSaving,
                                    modifier = Modifier.testTag("nutrition_preview_${meal.id}"),
                                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Text(if (en) "View recipe" else "Xem công thức", textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text(if (en) "Remove this meal?" else "Bỏ món này?") },
        confirmButton = { TextButton(onClick = {
            confirmRemove = false
            onRemove()
        }, modifier = Modifier.testTag("nutrition_confirm_remove")) {
            Text(if (en) "Remove" else "Bỏ món", color = p.danger)
        } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) {
            Text(if (en) "Cancel" else "Hủy", color = colors.accent)
        } },
    )
}

@Composable
private fun MealWriteNotice(en: Boolean, isSaving: Boolean, onRetry: (() -> Unit)?) {
    val palette = LocalProfilePalette.current
    when {
        isSaving -> {
            LinearProgressIndicator(Modifier.fillMaxWidth().testTag("nutrition_meal_saving"))
            Text(if (en) "Saving on this device…" else "Đang lưu trên thiết bị…",
                style = MaterialTheme.typography.bodySmall, color = palette.muted)
        }
        onRetry != null -> {
            Row(Modifier.fillMaxWidth().testTag("nutrition_meal_save_error"), verticalAlignment = Alignment.CenterVertically) {
                Text(if (en) "Could not save the meal on this device." else "Không lưu được món trên thiết bị.",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = palette.danger)
                TextButton(onClick = onRetry, modifier = Modifier.testTag("nutrition_meal_save_retry")) {
                    Text(if (en) "Retry" else "Thử lại")
                }
            }
        }
    }
}

private fun mealFilterLabel(filter: NutritionMealFilter, en: Boolean): String = when (filter) {
    NutritionMealFilter.All -> if (en) "All" else "Tất cả"
    NutritionMealFilter.Vegetarian -> if (en) "Vegetarian" else "Ăn chay"
    NutritionMealFilter.Protein -> if (en) "High protein" else "Giàu đạm"
    NutritionMealFilter.Light -> if (en) "Light meals" else "Món nhẹ"
}

@Composable
private fun MealRecipeContent(
    en: Boolean,
    meal: NutritionMeal,
    onBack: () -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    backLabel: String,
    isSaving: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    val recipe = meal.recipe
    var servings by rememberSaveable(meal.id) { mutableIntStateOf(1) }
    val estimated = remember(recipe, servings) { recipe?.perServing?.times(servings.toDouble()) }
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("nutrition_meal_details")
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MealThumbnail(meal, meal.slot, Modifier.size(96.dp))
        }
        MealTypeChip(if (en) meal.slot.en else meal.slot.vi, meal.slot)
        Text(if (en) meal.en else meal.vi,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = p.text)
        if (recipe != null) {
            PortionPreview(en, servings, enabled = !isSaving, onServings = { servings = it })
        }
        NutritionValuesCard(
            en = en,
            title = when {
                recipe == null -> if (en) "Nutrition" else "Dinh dưỡng"
                !en -> "Xem trước · $servings khẩu phần"
                servings == 1 -> "Preview · 1 serving"
                else -> "Preview · $servings servings"
            },
            values = estimated,
            modifier = Modifier.testTag("nutrition_portion_totals"),
        )
        MealWriteNotice(en, isSaving, onRetry)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onBack, enabled = !isSaving,
                modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, colors.accent),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent),
            ) { Text(backLabel, textAlign = TextAlign.Center) }
            Button(
                onClick = onPrimary, enabled = !isSaving,
                modifier = Modifier.weight(1f).heightIn(min = 50.dp).testTag("nutrition_details_change"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            ) { Text(primaryLabel, textAlign = TextAlign.Center) }
        }
        Surface(
            color = p.background,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, p.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (en) "Ingredients" else "Nguyên liệu",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = p.text)
                if (recipe == null) {
                    Text(if (en) meal.ingredientsEn else meal.ingredientsVi, color = p.text)
                } else recipe.ingredients.forEach { ingredient ->
                    val grams = ingredient.grams * servings / recipe.servings
                    val amount = number(grams, if (grams % 1 == 0.0) 0 else 1, en)
                    Text("• ${if (en) ingredient.en else ingredient.vi} — $amount g",
                        color = p.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        val steps = if (en) recipe?.stepsEn ?: meal.preparation?.stepsEn else recipe?.stepsVi ?: meal.preparation?.stepsVi
        if (!steps.isNullOrEmpty()) {
            Text(if (en) "Preparation" else "Cách chế biến", color = p.text,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            steps.forEachIndexed { index, step ->
                Text("${index + 1}. $step", color = p.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (recipe != null) {
            var showSources by remember(meal.id) { mutableStateOf(false) }
            TextButton(onClick = { showSources = !showSources }) {
                Text(if (en) "USDA ingredient sources ${if (showSources) "−" else "+"}"
                    else "Nguồn nguyên liệu USDA ${if (showSources) "−" else "+"}", color = colors.accent)
            }
            if (showSources) {
                Text(if (en) "FoodData Central • SR Legacy, April 2018 • checked 16/11/2026. Values per 100 g edible portion."
                    else "FoodData Central • SR Legacy, tháng 4/2018 • đối chiếu 16/11/2026. Giá trị trên 100 g phần ăn được.",
                    color = p.muted, style = MaterialTheme.typography.bodySmall)
                recipe.ingredients.distinctBy { it.fdcId }.forEach { ingredient ->
                    Column {
                        Text(ingredient.usdaDescription, color = p.muted, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { uriHandler.openUri(ingredient.sourceUrl) }) {
                            Text("USDA FDC ${ingredient.fdcId} ↗", color = colors.accent)
                        }
                    }
                }
            }
        }
        Surface(color = colors.mint, shape = RoundedCornerShape(16.dp)) {
            Text(if (recipe == null) {
                if (en) "Verified nutrition values are not available for this recipe yet." else "Công thức này chưa có số liệu dinh dưỡng đã kiểm chứng."
            } else {
                if (en) "USDA estimates. Ingredient brands, preparation and extra sauces can change the values."
                else "Số liệu ước tính từ USDA. Nhãn hàng, cách nấu và sốt thêm có thể làm thay đổi số liệu."
            },
                Modifier.fillMaxWidth().padding(14.dp),
                color = p.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PortionPreview(en: Boolean, servings: Int, enabled: Boolean, onServings: (Int) -> Unit) {
    val p = LocalProfilePalette.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            itemVerticalAlignment = Alignment.CenterVertically) {
            Text(if (en) "Preview servings" else "Xem trước khẩu phần",
                color = p.text, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = { onServings(servings - 1) }, enabled = enabled && servings > 1,
                    modifier = Modifier.size(48.dp).testTag("nutrition_portion_less")
                        .semantics { contentDescription = if (en) "Decrease portions" else "Giảm khẩu phần" }) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text(servings.toString(), Modifier.widthIn(min = 36.dp).testTag("nutrition_portion_count")
                    .semantics { liveRegion = LiveRegionMode.Polite },
                    color = p.text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                FilledTonalIconButton(onClick = { onServings(servings + 1) },
                    enabled = enabled && servings < MAX_PREVIEW_SERVINGS,
                    modifier = Modifier.size(48.dp).testTag("nutrition_portion_more")
                        .semantics { contentDescription = if (en) "Increase portions" else "Tăng khẩu phần" }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Text(if (en) "Your plan counts 1 serving when you save this dish."
            else "Khi lưu món, thực đơn tính 1 khẩu phần.",
            color = p.muted, style = MaterialTheme.typography.bodySmall)
    }
}

private fun number(value: Double, decimals: Int, en: Boolean): String =
    String.format(if (en) Locale.US else Locale.forLanguageTag("vi-VN"), "%.${decimals}f", value)

@Composable
private fun DayNutritionCard(en: Boolean, summary: NutritionDaySummary) {
    NutritionValuesCard(
        en = en,
        title = if (en) "Day’s nutrition" else "Dinh dưỡng trong ngày",
        values = summary.knownTotal,
        partial = !summary.complete && summary.knownTotal != null,
        note = when {
            summary.selectedMeals == 0 -> if (en) "Choose a meal below to see its nutrition here."
                else "Chọn món ở một bữa bên dưới để xem dinh dưỡng tại đây."
            summary.missingMeals > 0 -> if (en) "${summary.missingMeals} selected meals have no verified nutrition data."
                else "${summary.missingMeals} món đã chọn chưa có số liệu kiểm chứng."
            else -> if (en) "Planned nutrition for one serving of each selected dish."
                else "Ước tính cho mỗi món đã chọn một khẩu phần."
        },
        modifier = Modifier.testTag("nutrition_daily_totals"),
    )
}

@Composable
private fun NutritionValuesCard(
    en: Boolean,
    title: String,
    values: NutritionValues?,
    modifier: Modifier = Modifier,
    partial: Boolean = false,
    note: String? = null,
) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    Surface(color = p.card, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, p.border),
        modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = p.text, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium)
            if (partial) Text(if (en) "Incomplete data / subtotal" else "Chưa đủ dữ liệu / Tạm tính",
                color = colors.accent, style = MaterialTheme.typography.labelLarge)
            if (values != null) {
                Text("≈ ${number(values.kcal, 0, en)} kcal",
                    Modifier.testTag("nutrition_energy"), color = colors.accent,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                NutritionMacros(en, values)
            } else if (note == null) {
                Text(if (en) "No verified nutrition values" else "Chưa có số liệu dinh dưỡng kiểm chứng",
                    color = p.muted, style = MaterialTheme.typography.bodyMedium)
            }
            if (note != null) Text(note, color = p.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NutritionMacros(en: Boolean, values: NutritionValues) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    val metrics = listOf(
        (if (en) "Protein" else "Chất đạm") to values.protein,
        (if (en) "Carbs" else "Bột đường") to values.carb,
        (if (en) "Fat" else "Chất béo") to values.fat,
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        if (maxWidth >= 264.dp * fontScale.coerceAtLeast(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.forEach { (label, amount) ->
                    Surface(color = colors.accentSoft, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, color = p.muted, style = MaterialTheme.typography.labelMedium)
                            Text("${number(amount, 1, en)} g", color = p.text,
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            Surface(color = colors.accentSoft, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    metrics.forEach { (label, amount) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f), color = p.muted, style = MaterialTheme.typography.bodyMedium)
                            Text("${number(amount, 1, en)} g", color = p.text,
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
