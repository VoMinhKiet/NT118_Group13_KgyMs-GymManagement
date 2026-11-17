@file:OptIn(ExperimentalMaterial3Api::class)

package com.kgyms.app.ui.member.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgyms.app.R
import com.kgyms.app.data.local.NutritionLocalStore
import com.kgyms.app.data.model.*
import com.kgyms.app.ui.member.profile.LocalProfileLanguage
import com.kgyms.app.ui.member.profile.LocalProfilePalette
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    val context = LocalContext.current
    val en = LocalProfileLanguage.current == "en"
    val palette = LocalProfilePalette.current
    val nutrition = nutritionColors()
    val store = remember(context, uid) {
        uid?.takeIf { it.isNotBlank() }?.let { NutritionLocalStore(context, it) }
    }
    var saved by remember(store) { mutableStateOf(store?.read() ?: NutritionPlanData()) }
    var editingSetup by remember(store) { mutableStateOf(false) }
    var saveError by remember(store) { mutableStateOf(false) }
    var selectedDay by rememberSaveable(uid) {
        mutableIntStateOf(mondayIndex(System.currentTimeMillis()))
    }
    var choosingSlot by remember { mutableStateOf<MealSlot?>(null) }
    var detailMeal by remember { mutableStateOf<NutritionMeal?>(null) }
    var confirmRegenerate by remember { mutableStateOf(false) }

    if (store == null) {
        Column(
            Modifier.fillMaxSize().background(palette.background).statusBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (en) "Nutrition" else "Dinh dưỡng", color = palette.text,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(if (en) "Please sign in to save a personal meal plan."
                else "Bạn cần đăng nhập để lưu thực đơn cá nhân.", color = palette.muted)
            TextButton(onClick = onHome) {
                Text(if (en) "Back to Home" else "Về Trang chủ", color = nutrition.accent)
            }
        }
        return
    }

    val update: (NutritionPlanData) -> Boolean = { next ->
        store.save(next).also { success ->
            if (success) {
                saved = next
                saveError = false
            } else saveError = true
        }
    }

    if (saved.setup == null || editingSetup) {
        NutritionSetupContent(
            existing = saved.setup,
            en = en,
            onHome = onHome,
            onCancel = if (saved.setup != null) ({ editingSetup = false }) else null,
            onSave = { profile ->
                val next = if (saved.setup == null) NutritionPlanData(
                    setup = profile,
                    meals = NutritionMealCatalog.suggest(profile),
                    generatedFor = NutritionMealCatalog.fingerprint(profile),
                ) else saved.copy(setup = profile)
                if (update(next)) editingSetup = false
            },
            error = saveError,
        )
    } else {
        val profile = checkNotNull(saved.setup)
        val now = System.currentTimeMillis()
        val weekKey = remember(now) {
            val calendar = Calendar.getInstance().apply { timeInMillis = now }
            calendar.get(Calendar.YEAR) * 400 + calendar.get(Calendar.DAY_OF_YEAR)
        }
        val weekDates = remember(weekKey) { currentWeekDates(now) }
        Column(
            Modifier.fillMaxSize().background(palette.background).statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("nutrition_hub"),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            NutritionHeader(en, onBack, onHome)
            ProfileSummary(en, profile, onEdit = { editingSetup = true })
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (en) "7-day meal plan" else "Thực đơn 7 ngày",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = palette.text)
                Text(if (en) "Meal ideas based on your goal and habits."
                    else "Gợi ý món ăn theo mục tiêu và thói quen đã chọn.",
                    style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
            DayPicker(en, selectedDay, weekDates) { selectedDay = it }
            DayNutritionCard(en, saved.dayNutrition(selectedDay))
            SelectedDayHeading(en, selectedDay, weekDates, now)
            MealSlot.entries.forEach { slot ->
                val meal = saved.meals["$selectedDay:${slot.name}"]?.let(NutritionMealCatalog::byId)
                MealCard(
                    en = en,
                    slot = slot,
                    meal = meal,
                    onOpen = { if (meal != null) detailMeal = meal },
                    onChange = { choosingSlot = slot },
                )
            }
            val noMatching = saved.meals.values.any { id ->
                NutritionMealCatalog.byId(id)?.let {
                    profile.diet == NutritionDiet.VEGETARIAN && !it.vegetarian
                } == true
            }
            if (saved.generatedFor != NutritionMealCatalog.fingerprint(profile) || noMatching) {
                PreferenceChangedCard(en) { confirmRegenerate = true }
            }
            if (saveError) Text(
                if (en) "Could not save changes. Please try again."
                else "Không lưu được thay đổi. Vui lòng thử lại.",
                color = palette.danger,
            )
            NutritionReferenceNotice(en)
            Spacer(Modifier.height(6.dp))
        }
    }

    choosingSlot?.let { slot ->
        val key = "$selectedDay:${slot.name}"
        MealChooser(
            en = en,
            slot = slot,
            setup = saved.setup ?: return@let,
            canRemove = key in saved.meals,
            onDismiss = { choosingSlot = null },
            onChoose = { mealId ->
                if (update(saved.copy(meals = saved.meals + (key to mealId)))) {
                    choosingSlot = null
                    detailMeal = null
                }
            },
            onRemove = {
                if (update(saved.copy(meals = saved.meals - key))) {
                    choosingSlot = null
                    detailMeal = null
                }
            },
        )
    }
    detailMeal?.let { meal ->
        MealDetails(
            en = en,
            meal = meal,
            onDismiss = { detailMeal = null },
            onChange = {
                detailMeal = null
                choosingSlot = meal.slot
            },
        )
    }
    if (confirmRegenerate) AlertDialog(
        onDismissRequest = { confirmRegenerate = false },
        title = { Text(if (en) "Replace your meal plan?" else "Thay thế thực đơn hiện tại?") },
        text = { Text(if (en) "Your current selections will be replaced with new suggestions."
            else "Các món bạn đã chọn sẽ được thay bằng gợi ý mới. Thao tác không ảnh hưởng hồ sơ cá nhân.") },
        confirmButton = { TextButton(onClick = {
            val setup = saved.setup ?: return@TextButton
            if (update(saved.copy(
                    meals = NutritionMealCatalog.suggest(setup),
                    generatedFor = NutritionMealCatalog.fingerprint(setup),
                ))) confirmRegenerate = false
        }) { Text(if (en) "Replace" else "Tạo lại", color = nutrition.accent) } },
        dismissButton = { TextButton(onClick = { confirmRegenerate = false }) {
            Text(if (en) "Cancel" else "Hủy", color = nutrition.accent)
        } },
    )
}

@Composable
private fun NutritionHeader(en: Boolean, onBack: () -> Unit, onHome: () -> Unit) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (en) "‹ Back" else "‹ Trở lại", color = colors.accent)
            }
            TextButton(onClick = onHome, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (en) "Home" else "Trang chủ", color = colors.accent)
            }
        }
        Text(if (en) "My nutrition" else "Dinh dưỡng của tôi",
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = p.text)
        Text(if (en) "Reference meals shaped by your goal and daily habits."
            else "Thực đơn tham khảo theo mục tiêu và thói quen của bạn.",
            style = MaterialTheme.typography.bodyMedium, color = p.muted)
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
            Text(if (en) "Your preferences changed" else "Bạn đã thay đổi thiết lập",
                fontWeight = FontWeight.SemiBold, color = p.text)
            Text(if (en) "Your saved meals were kept. Generate new suggestions only if you want to replace them."
                else "Thực đơn cũ vẫn được giữ. Chỉ tạo gợi ý mới khi bạn muốn thay thế.",
                color = p.muted, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRegenerate, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(if (en) "Generate new suggestions" else "Tạo lại gợi ý",
                    color = colors.accent)
            }
        }
    }
}

@Composable
private fun NutritionReferenceNotice(en: Boolean) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    Surface(color = colors.mint, shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top) {
            Text("i", color = colors.onMint, fontWeight = FontWeight.Bold,
                modifier = Modifier.size(22.dp).background(colors.onMint.copy(alpha = .12f), CircleShape),
                textAlign = TextAlign.Center)
            Text(if (en) "Meal ideas are for reference, not a verified dietary prescription. Check ingredients and allergies; unverified nutrition totals are not shown."
                else "Món ăn chỉ mang tính tham khảo, không phải thực đơn điều trị. Hãy kiểm tra nguyên liệu và dị ứng; các chỉ số chưa kiểm chứng không được hiển thị.",
                style = MaterialTheme.typography.bodySmall, color = p.muted)
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
private fun SelectedDayHeading(en: Boolean, selectedDay: Int, dates: List<Long>, now: Long) {
    val p = LocalProfilePalette.current
    val selected = dates.getOrNull(selectedDay) ?: return
    val title = if (sameLocalDay(selected, now)) {
        if (en) "Today" else "Hôm nay"
    } else {
        val locale = if (en) Locale.ENGLISH else Locale.forLanguageTag("vi-VN")
        SimpleDateFormat("EEEE, dd/MM", locale).format(Date(selected))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
    Text(title, color = p.text, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold, modifier = Modifier.testTag("nutrition_selected_day"))
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
            TextButton(onClick = { onCancel?.invoke() ?: onHome() },
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
        ChoiceGroup(
            index = "01",
            title = if (en) "What's your goal?" else "Mục tiêu của bạn",
            selected = goal,
            values = NutritionGoal.entries,
            label = { goalLabel(it, en) },
            onChange = { goal = it },
        )
        ChoiceGroup(
            index = "02",
            title = if (en) "How active are you?" else "Mức độ vận động",
            selected = activity,
            values = NutritionActivity.entries,
            label = { activityLabel(it, en) },
            onChange = { activity = it },
        )
        ChoiceGroup(
            index = "03",
            title = if (en) "Your eating style" else "Chế độ ăn",
            selected = diet,
            values = NutritionDiet.entries,
            label = { dietLabel(it, en) },
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
            enabled = goal != null && activity != null && diet != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("nutrition_save_setup"),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
        ) {
            Text(if (existing == null) {
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
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = p.card,
        contentColor = p.text,
        border = BorderStroke(1.dp, p.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (en) "Your preferences" else "Thiết lập của bạn",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("nutrition_edit_setup"),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("✎", color = colors.accent, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(5.dp))
                    Text(if (en) "Edit" else "Chỉnh sửa", color = colors.accent)
                }
            }
            SummaryRow(if (en) "Goal" else "Mục tiêu", goalLabel(profile.goal, en))
            HorizontalDivider(color = p.border)
            SummaryRow(if (en) "Activity" else "Vận động", activityLabel(profile.activity, en))
            HorizontalDivider(color = p.border)
            SummaryRow(if (en) "Diet" else "Chế độ ăn", dietLabel(profile.diet, en))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    val p = LocalProfilePalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = p.muted)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold, color = p.text, textAlign = TextAlign.End)
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
    val labels = if (en) listOf("M", "T", "W", "T", "F", "S", "S")
        else listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    val today = System.currentTimeMillis()
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("nutrition_day_picker")) {
        val itemWidth = maxWidth / 7
        val circleSize = minOf(44.dp, itemWidth - 4.dp).coerceAtLeast(34.dp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
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
                    Modifier.weight(1f).heightIn(min = 82.dp)
                        .clickable { onSelect(index) }
                        .semantics {
                            contentDescription = description
                            role = Role.Button
                        }
                        .testTag("nutrition_day_$index"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(labels[index], color = if (selected) colors.accent else p.muted,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 1)
                    Surface(
                        shape = CircleShape,
                        color = if (selected) colors.accent else p.card,
                        border = if (selected) null else BorderStroke(
                            if (isToday) 1.5.dp else 1.dp,
                            if (isToday) colors.accent else p.border,
                        ),
                        modifier = Modifier.size(circleSize),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(dateText, color = if (selected) Color.White else p.text,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1)
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
    onOpen: () -> Unit,
    onChange: () -> Unit,
) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    val action = if (meal == null) onChange else onOpen
    Surface(
        onClick = action,
        shape = RoundedCornerShape(22.dp),
        color = p.card,
        contentColor = p.text,
        border = BorderStroke(1.dp, p.border),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().testTag("nutrition_meal_${slot.name.lowercase()}"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MealThumbnail(meal, slot, Modifier.size(86.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                MealTypeChip(if (en) slot.en else slot.vi, slot)
                if (meal == null) {
                    Text(if (en) "No meal selected" else "Chưa chọn món",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = p.text)
                    Text(if (en) "Add a meal idea for this time of day."
                        else "Thêm một món gợi ý cho bữa ăn này.",
                        style = MaterialTheme.typography.bodySmall, color = p.muted,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (en) "+ Add meal" else "+ Thêm món",
                        color = colors.accent, style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold)
                } else {
                    Text(if (en) meal.en else meal.vi, color = p.text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    Text(if (en) meal.ingredientsEn else meal.ingredientsVi,
                        color = p.muted, style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    meal.nutritionPerServing?.let { values ->
                        Text("≈ ${number(values.kcal, 0)} kcal / " + if (en) "1 serving" else "1 phần",
                            color = colors.accent, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = onChange,
                        modifier = Modifier.heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Text(if (en) "Change meal" else "Đổi món", color = colors.accent,
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Surface(
                onClick = action,
                shape = CircleShape,
                color = colors.accentSoft,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (meal == null) {
                        Text("+", color = colors.accent,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium)
                    } else {
                        Icon(painterResource(R.drawable.home_icon_arrow),
                            if (en) "View meal" else "Xem món",
                            tint = colors.accent, modifier = Modifier.size(19.dp))
                    }
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
    setup: NutritionSetup,
    canRemove: Boolean,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    var query by remember(slot) { mutableStateOf("") }
    var confirmRemove by remember(slot) { mutableStateOf(false) }
    val options = NutritionMealCatalog.available(slot, setup).filter {
        (if (en) it.en else it.vi).contains(query.trim(), ignoreCase = true) ||
            (if (en) it.ingredientsEn else it.ingredientsVi).contains(query.trim(), ignoreCase = true)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        contentColor = p.text,
        modifier = Modifier.testTag("nutrition_meal_chooser"),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (en) "Choose ${slot.en.lowercase()}" else "Chọn món cho ${slot.vi.lowercase()}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = p.text)
                Text(if (en) "Only meals matching your eating style are shown."
                    else "Chỉ hiển thị món phù hợp với chế độ ăn đã chọn.",
                    style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(if (en) "Search meals or ingredients" else "Tìm món hoặc nguyên liệu") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    focusedLabelColor = colors.accent,
                ),
            )
            if (options.isEmpty()) {
                Surface(color = p.background, shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, p.border), modifier = Modifier.fillMaxWidth()) {
                    Text(if (en) "No matching meal ideas." else "Không có món phù hợp.",
                        Modifier.padding(20.dp), color = p.muted,
                        textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 410.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(options, key = { it.id }) { meal ->
                        Surface(
                            onClick = { onChoose(meal.id) },
                            shape = RoundedCornerShape(18.dp),
                            color = p.background,
                            contentColor = p.text,
                            border = BorderStroke(1.dp, p.border),
                            modifier = Modifier.fillMaxWidth().testTag("nutrition_choice_${meal.id}"),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                MealThumbnail(meal, meal.slot, Modifier.size(64.dp))
                                Column(Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (en) meal.en else meal.vi,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(if (en) meal.ingredientsEn else meal.ingredientsVi,
                                        color = p.muted,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(painterResource(R.drawable.home_icon_arrow), null,
                                    tint = colors.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Text(if (en) "Meal ideas are for reference. Check ingredients and allergies."
                else "Món ăn mang tính tham khảo. Hãy kiểm tra nguyên liệu và dị ứng.",
                style = MaterialTheme.typography.bodySmall, color = p.muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (canRemove) TextButton(onClick = { confirmRemove = true },
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (en) "Remove meal" else "Bỏ món", color = p.danger)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (en) "Close" else "Đóng", color = colors.accent)
                }
            }
        }
    }
    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text(if (en) "Remove this meal?" else "Bỏ món này?") },
        text = { Text(if (en) "This meal slot will become empty. You can add another meal later."
            else "Bữa ăn này sẽ để trống. Bạn có thể thêm món khác sau.") },
        confirmButton = { TextButton(onClick = {
            confirmRemove = false
            onRemove()
        }) { Text(if (en) "Remove" else "Bỏ món", color = p.danger) } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) {
            Text(if (en) "Cancel" else "Hủy", color = colors.accent)
        } },
    )
}

@Composable
private fun MealDetails(
    en: Boolean,
    meal: NutritionMeal,
    onDismiss: () -> Unit,
    onChange: () -> Unit,
) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    val recipe = meal.recipe
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        contentColor = p.text,
        modifier = Modifier.testTag("nutrition_meal_details"),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MealThumbnail(meal, meal.slot, Modifier.size(154.dp))
            }
            MealTypeChip(if (en) meal.slot.en else meal.slot.vi, meal.slot)
            Text(if (en) meal.en else meal.vi,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = p.text)
            Text(if (en) "Reference recipe • ${recipe?.servings ?: "—"} serving(s)"
                else "Công thức tham khảo • ${recipe?.servings ?: "—"} phần ăn",
                color = p.muted, style = MaterialTheme.typography.bodyMedium)
            recipe?.let {
                NutritionValuesCard(en, if (en) "Nutrition per serving" else "Dinh dưỡng mỗi khẩu phần", it.perServing)
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
                        Text("• ${if (en) ingredient.en else ingredient.vi} — ${number(ingredient.grams, 0)} g",
                            color = p.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (recipe != null) {
                Text(if (en) "Preparation" else "Cách chế biến", color = p.text,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                (if (en) recipe.stepsEn else recipe.stepsVi).forEachIndexed { index, step ->
                    Text("${index + 1}. $step", color = p.text, style = MaterialTheme.typography.bodyMedium)
                }
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
                    if (en) "Nutrition and recipe quantities are not available yet." else "Chưa đủ dữ liệu dinh dưỡng và định lượng công thức."
                } else {
                    if (en) "Estimated from USDA ingredients for this recipe only, not measured intake. Weigh ingredients in the stated raw/cooked condition. Brands, cooking losses and extra sauces change the result. No added sauces or sugar are counted."
                    else "Ước tính từ nguyên liệu USDA cho công thức này, không phải lượng đã ăn thực tế. Cân đúng trạng thái sống/chín đã ghi. Nhãn hàng, hao hụt khi nấu và sốt thêm sẽ làm thay đổi kết quả. Không tính thêm sốt hoặc đường ngoài công thức."
                },
                    Modifier.fillMaxWidth().padding(14.dp),
                    color = p.muted, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.accent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent),
                ) { Text(if (en) "Close" else "Đóng") }
                Button(
                    onClick = onChange,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                ) { Text(if (en) "Change meal" else "Đổi món") }
            }
        }
    }
}

private fun number(value: Double, decimals: Int = 1): String =
    String.format(Locale.getDefault(), "%.${decimals}f", value)

@Composable
private fun DayNutritionCard(en: Boolean, summary: NutritionDaySummary) {
    val p = LocalProfilePalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NutritionValuesCard(en, if (en) "Selected day's nutrition" else "Dinh dưỡng ngày đã chọn",
            summary.knownTotal, partial = !summary.complete)
        Text(when {
            summary.selectedMeals == 0 -> if (en) "No meals selected yet." else "Chưa chọn món cho ngày này."
            summary.missingMeals > 0 -> if (en) "Incomplete data: ${summary.missingMeals} selected meal(s) have no verified values. Only known meals are subtotalled."
                else "Chưa đủ dữ liệu: ${summary.missingMeals} món chưa có số liệu xác minh. Chỉ tạm tính các món có dữ liệu."
            else -> if (en) "Planned estimate for ${summary.selectedMeals} selected meal(s), one serving each; not consumed calories."
                else "Năng lượng dự kiến của ${summary.selectedMeals} món đã chọn, mỗi món 1 phần; không phải lượng calo đã tiêu thụ."
        }, color = p.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NutritionValuesCard(en: Boolean, title: String, values: NutritionValues?, partial: Boolean = false) {
    val p = LocalProfilePalette.current
    val colors = nutritionColors()
    Surface(color = p.card, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, p.border),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = p.text, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium)
            if (partial) Text(if (en) "Incomplete data / subtotal" else "Chưa đủ dữ liệu / Tạm tính",
                color = colors.accent, style = MaterialTheme.typography.labelLarge)
            Text(values?.let { "≈ ${number(it.kcal, 0)} kcal" } ?: if (en) "No nutrition data" else "Chưa có dữ liệu dinh dưỡng",
                color = colors.accent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Protein" to values?.protein, "Carb" to values?.carb, "Fat" to values?.fat).forEach { (label, amount) ->
                    Surface(color = colors.accentSoft, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (!en && label == "Fat") "Chất béo" else label,
                                color = p.muted, style = MaterialTheme.typography.labelSmall)
                            Text(amount?.let { "${number(it)} g" } ?: "—", color = p.text,
                                fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
