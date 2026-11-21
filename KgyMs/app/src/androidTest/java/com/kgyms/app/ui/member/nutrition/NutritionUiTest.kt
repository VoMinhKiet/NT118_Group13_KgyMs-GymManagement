package com.kgyms.app.ui.member.nutrition

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.kgyms.app.data.local.NutritionJournalPreferences
import com.kgyms.app.data.local.NutritionLocalStore
import com.kgyms.app.data.model.*
import com.kgyms.app.data.repository.MemberAccountSession
import com.kgyms.app.data.repository.MemberProfileDataSource
import com.kgyms.app.data.repository.NutritionJournalStore
import com.kgyms.app.data.repository.NutritionRepository
import com.kgyms.app.ui.member.LocalMemberAccount
import com.kgyms.app.ui.member.MemberAccountState
import com.kgyms.app.ui.member.MemberApp
import com.kgyms.app.ui.member.profile.ProfileAppearance
import com.kgyms.app.ui.theme.KgyMsTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.Locale

class NutritionUiTest {
    @get:Rule val compose = createComposeRule()

    private val setup = NutritionSetup(NutritionGoal.MAINTAIN, NutritionActivity.LOW, NutritionDiet.REGULAR)
    private val legacy = NutritionPlanData(setup, (0..6).associate { "$it:BREAKFAST" to "bf_oats" },
        NutritionMealCatalog.fingerprint(setup))

    @Test fun searchFilterReadRecipeChangeAndRemoveWithoutReconfiguring() {
        val store = MemoryJournal(NutritionJournal(legacy, pending = true))
        val repo = NutritionRepository("A", store, null, { true })
        host(repo, dark = false, language = "en")
        compose.onNodeWithTag("nutrition_change_breakfast").performScrollTo().performClick()
        chooserItem("nutrition_selected_meal").assertTextContains("Selected:", substring = true)
        val search = compose.onNodeWithTag("nutrition_meal_search")
        search.performTextInput("zzzz")
        chooserItem("nutrition_no_matching_meals").assertIsDisplayed()
        search.performTextReplacement("pho ga")
        chooserItem("nutrition_filter_Vegetarian").performClick()
        chooserItem("nutrition_no_matching_meals").assertIsDisplayed()
        chooserItem("nutrition_filter_All").performClick()
        chooserItem("nutrition_choice_bf_pho_ga").performClick()
        compose.waitUntil(5_000) { repo.view.value.plan?.meals?.containsValue("bf_pho_ga") == true }
        compose.onNodeWithTag("nutrition_meal_feedback").performScrollTo()
            .assertTextEquals("Meal updated on this device").assertIsDisplayed()

        compose.onNodeWithTag("nutrition_recipe_breakfast").performScrollTo().performClick()
        compose.onNodeWithTag("nutrition_meal_details").assertExists()
        compose.onNodeWithText("Preparation").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("nutrition_details_change").performScrollTo().performClick()
        chooserItem("nutrition_selected_meal").assertTextContains("Chicken pho", substring = true)
        chooserItem("nutrition_remove_meal").performClick()
        compose.onNodeWithTag("nutrition_confirm_remove").performClick()
        compose.waitUntil(5_000) { repo.view.value.plan?.meals?.size == 6 }
        compose.onNodeWithTag("nutrition_meal_feedback").performScrollTo()
            .assertTextEquals("Meal removed on this device").assertIsDisplayed()
        compose.onNodeWithTag("nutrition_setup").assertDoesNotExist()
        assertEquals(setup, repo.view.value.plan?.setup)
        assertFalse(repo.view.value.plan?.meals?.containsValue("bf_pho_ga") == true)
    }

    @Test fun recipePortionPreviewScalesValuesWithoutChangingTheSavedMeal() {
        val store = MemoryJournal(NutritionJournal(legacy, pending = true))
        val repo = NutritionRepository("A", store, null, { true })
        host(repo, dark = false, language = "en")
        selectDay(0)
        val recipe = checkNotNull(NutritionMealCatalog.byId("bf_oats")?.recipe)
        val one = recipe.perServing
        fun energy(tag: String) = compose.onNode(hasTestTag("nutrition_energy") and hasAnyAncestor(hasTestTag(tag)))
        fun kcal(value: Double) = String.format(Locale.US, "≈ %.0f kcal", value)

        energy("nutrition_daily_totals").performScrollTo().assertTextEquals(kcal(one.kcal))
        compose.onNodeWithTag("nutrition_recipe_breakfast").performScrollTo().performClick()
        compose.onNodeWithTag("nutrition_portion_less").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("nutrition_portion_more").performScrollTo().performTouchInput { click() }
        compose.onNodeWithTag("nutrition_portion_count").assertTextEquals("2")
        energy("nutrition_portion_totals").performScrollTo().assertTextEquals(kcal(one.kcal * 2))
        val firstIngredient = recipe.ingredients.first()
        val scaledGrams = String.format(Locale.US, "%.0f", firstIngredient.grams * 2 / recipe.servings)
        compose.onNodeWithText("• ${firstIngredient.en} — $scaledGrams g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("nutrition_portion_less").performScrollTo().performClick()
        compose.onNodeWithTag("nutrition_portion_count").assertTextEquals("1")
        compose.onNodeWithText("Close").performScrollTo().performClick()

        energy("nutrition_daily_totals").performScrollTo().assertTextEquals(kcal(one.kcal))
        assertEquals(legacy, store.value.plan)
    }

    @Test fun searchStaysReachableAndCanResetFromNoResults() {
        val repo = NutritionRepository("A", MemoryJournal(NutritionJournal(legacy)), null, { true })
        host(repo, dark = false, language = "en")
        selectDay(0)
        compose.onNodeWithTag("nutrition_change_breakfast").performScrollTo().performClick()
        val lastMeal = NutritionMealCatalog.available(MealSlot.BREAKFAST, setup).last()
        chooserItem("nutrition_choice_${lastMeal.id}").assertIsDisplayed()
        val search = compose.onNodeWithTag("nutrition_meal_search")
        search.assertIsDisplayed().performTextReplacement("zzzz")
        chooserItem("nutrition_no_matching_meals").assertIsDisplayed()
        compose.onNodeWithTag("nutrition_clear_search").performClick()
        chooserItem("nutrition_choice_bf_oats").assertIsDisplayed()
        search.performTextReplacement("zzzz")
        chooserItem("nutrition_reset_search").performClick()
        search.assert(!hasText("zzzz"))
        chooserItem("nutrition_choice_bf_oats").assertIsDisplayed()
    }

    @Test fun narrowVietnameseLayoutKeepsMacrosAndDatesReachableWithLargeText() {
        val dayPlan = legacy.copy(meals = mapOf("0:BREAKFAST" to "bf_oats"))
        val repo = NutritionRepository("A", MemoryJournal(NutritionJournal(dayPlan)), null, { true })
        compose.setContent {
            LaunchedEffect(repo) { repo.load() }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f), LocalNutritionRepository provides repo) {
                ProfileAppearance(true, "vi") {
                    Box(Modifier.width(320.dp).fillMaxHeight()) {
                        NutritionScreen(repo.uid, onBack = {}, onHome = {})
                    }
                }
            }
        }
        selectDay(0)
        listOf("Chất đạm", "Bột đường", "Chất béo").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        selectDay(6)
        compose.onNodeWithTag("nutrition_day_6").assertIsSelected()
        compose.onNodeWithText("Chọn món ở một bữa bên dưới để xem dinh dưỡng tại đây.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("nutrition_energy").assertDoesNotExist()
        compose.onNodeWithTag("nutrition_change_breakfast").performScrollTo().assertIsDisplayed()
    }

    @Test fun applyingTheMealFromItsRecipeSavesTheChosenSlot() {
        val store = MemoryJournal(NutritionJournal(legacy, pending = true))
        val repo = NutritionRepository("A", store, null, { true })
        host(repo, dark = false, language = "en")
        compose.onNodeWithTag("nutrition_day_0").performScrollTo().performClick()
        compose.onNodeWithTag("nutrition_change_breakfast").performScrollTo().performClick()
        val choiceTag = "nutrition_choice_bf_pho_ga"
        chooserItem(choiceTag)
        compose.onNodeWithTag("nutrition_preview_bf_pho_ga").performClick()
        compose.onNodeWithTag("nutrition_meal_details").assertExists()
        compose.onNodeWithTag("nutrition_details_change").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("The recipe's primary action must save the chosen dish", "bf_pho_ga",
            store.value.plan?.meals?.get("0:BREAKFAST"))
        compose.onNodeWithTag("nutrition_meal_chooser").assertDoesNotExist()
    }

    @Test fun conflictKeepsDevicePlanVisibleUntilExplicitChoiceInVietnameseDarkTheme() {
        val remotePlan = legacy.copy(meals = (0..6).associate { "$it:BREAKFAST" to "bf_pho_ga" })
        val remote = NutritionRemoteRecord(2, "a".repeat(32), 100, remotePlan)
        val store = MemoryJournal(NutritionJournal(legacy, pending = true, conflict = remote))
        val repo = NutritionRepository("A", store, null, { true })
        host(repo, dark = true, language = "vi")
        compose.onNodeWithTag("nutrition_sync_conflict").performScrollTo().performClick()
        compose.onNodeWithText("Số bữa có món khác nhau: 7").assertExists()
        assertEquals(legacy, repo.view.value.plan)
        compose.onNodeWithTag("nutrition_sync_keep_cloud").performClick()
        compose.waitUntil(5_000) { repo.view.value.plan == remotePlan }
        assertFalse(store.value.pending)
        assertNull(repo.view.value.cloudConflict)
    }

    @Test fun replacingThePlanWithTheCloudVersionHidesThePreviousMealFeedback() {
        val cloudPlan = legacy.copy(meals = legacy.meals + ("0:BREAKFAST" to "bf_pho_ga"))
        val cloud = NutritionRemoteRecord(2, "a".repeat(32), 100, cloudPlan)
        val store = MemoryJournal(NutritionJournal(legacy, pending = true, conflict = cloud))
        val repo = NutritionRepository("A", store, null, { true })
        host(repo, dark = false, language = "en")

        choose(0, MealSlot.BREAKFAST, "bf_pancake_yen_mach")
        compose.onNodeWithTag("nutrition_meal_feedback").performScrollTo()
            .assertTextEquals("Meal updated on this device")
        compose.onNodeWithTag("nutrition_sync_conflict").performScrollTo().performClick()
        compose.onNodeWithTag("nutrition_sync_keep_cloud").performClick()
        compose.waitUntil(5_000) { repo.view.value.plan == cloudPlan }
        assertMeal(MealSlot.BREAKFAST, "bf_pho_ga")
        compose.onNodeWithTag("nutrition_meal_feedback").assertDoesNotExist()
    }

    @Test fun realPreferencesImportLegacyOnceKeepItAndIsolateTheJournalByUid() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uid = "nutrition-ui-" + UUID.randomUUID()
        val source = NutritionLocalStore(context, uid)
        assertTrue(source.save(legacy))
        val store = NutritionJournalPreferences(context, uid)
        assertEquals(NutritionJournal(legacy, pending = true), store.read())
        val edited = legacy.copy(meals = legacy.meals + ("0:BREAKFAST" to "bf_pho_ga"))
        val sent = NutritionPush(1, "b".repeat(32), edited)
        val queued = NutritionJournal(edited, pending = true, sent = sent)
        store.save(queued)

        assertEquals(queued, NutritionJournalPreferences(context, uid).read())
        assertEquals(legacy, source.read())
        assertEquals(NutritionJournal(), NutritionJournalPreferences(context, "other-$uid").read())
    }


    @Test fun homeAndPlanOpenTheSameSavedNutrition() {
        val uid = "nutrition-nav-" + UUID.randomUUID()
        val store = MemoryJournal(NutritionJournal(legacy, pending = true))
        val repo = NutritionRepository(uid, store, null, { true })
        val profiles = object : MemberProfileDataSource {
            override suspend fun load(uid: String) = User(uid = uid)
            override suspend fun save(uid: String, edit: MemberProfileEdit) = error("Unexpected profile write")
        }
        compose.setContent {
            val scope = rememberCoroutineScope()
            val account = remember { MemberAccountState(MemberAccountSession(uid, profiles) { uid }, scope) }
            LaunchedEffect(repo) { repo.load() }
            CompositionLocalProvider(LocalMemberAccount provides account, LocalNutritionRepository provides repo) {
                KgyMsTheme { MemberApp() }
            }
        }
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("home_action_nutrition"))
        compose.onNodeWithTag("home_action_nutrition").performClick()
        compose.onNodeWithTag("nutrition_hub").assertIsDisplayed()
        choose(2, MealSlot.BREAKFAST, "bf_pho_ga")
        Espresso.pressBack()
        compose.onNodeWithTag("workout_list").performScrollToNode(hasTestTag("plan_nutrition"))
        compose.onNodeWithTag("plan_nutrition").performClick()
        compose.onNodeWithTag("nutrition_hub").assertIsDisplayed()
        compose.onNodeWithTag("nutrition_meal_name_breakfast").performScrollTo()
            .assert(hasText("Phở gà") or hasText("Chicken pho"))
        assertEquals("bf_pho_ga", repo.view.value.plan?.meals?.get("2:BREAKFAST"))
    }


    @Test fun choosingWithTouchWritesAllSlotsOnTheCorrectDaysAndSurvivesReopening() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uid = "nutrition-meals-" + UUID.randomUUID()
        val store = NutritionJournalPreferences(context, uid)
        val repo = NutritionRepository(uid, store, null, { true })
        assertTrue(NutritionLocalStore(context, uid).save(legacy))
        var visible by mutableStateOf(true)
        compose.setContent {
            LaunchedEffect(repo) { repo.load() }
            ProfileAppearance(false, "en") {
                CompositionLocalProvider(LocalNutritionRepository provides repo) {
                    if (visible) NutritionScreen(uid, onBack = {}, onHome = {})
                }
            }
        }
        data class Edit(val day: Int, val slot: MealSlot, val id: String)
        val edits = listOf(
            Edit(0, MealSlot.BREAKFAST, "bf_pho_ga"),
            Edit(2, MealSlot.LUNCH, "lu_com_bo_xao"),
            Edit(2, MealSlot.DINNER, "di_ca_hap_gung"),
            Edit(6, MealSlot.SNACK, "sn_sua_chua_granola"),
        )
        edits.forEach { (day, slot, meal) ->
            choose(day, slot, meal)
            assertMeal(slot, meal)
            if (slot != MealSlot.BREAKFAST) {
                compose.onNodeWithTag("nutrition_meal_feedback").performScrollTo()
                    .assertTextEquals("Meal added on this device").assertIsDisplayed()
            }
        }
        val expected = legacy.meals + edits.associate { "${it.day}:${it.slot.name}" to it.id }
        assertEquals(expected, runBlocking { NutritionJournalPreferences(context, uid).read() }.plan?.meals)
        assertEquals(legacy, NutritionLocalStore(context, uid).read())

        compose.runOnIdle { visible = false }
        compose.onNodeWithTag("nutrition_hub").assertDoesNotExist()
        compose.runOnIdle { visible = true }
        edits.forEach { (day, slot, meal) ->
            selectDay(day)
            assertMeal(slot, meal)
        }
    }

    @Test fun localWriteFailureKeepsPickerOpenAndRetrySavesTheSameSlot() {
        val store = MemoryJournal(NutritionJournal(legacy, pending = true))
        val repo = NutritionRepository("A", store, null, { true })
        host(repo, dark = true, language = "en")
        selectDay(1)
        compose.onNodeWithTag("nutrition_change_breakfast").performScrollTo().performClick()
        store.failWrites = true
        chooserItem("nutrition_choice_bf_pho_ga").performTouchInput { click() }
        compose.onNodeWithTag("nutrition_meal_save_error").assertIsDisplayed()
        compose.onNodeWithTag("nutrition_meal_chooser").assertIsDisplayed()
        assertEquals("bf_oats", store.value.plan?.meals?.get("1:BREAKFAST"))
        store.failWrites = false
        compose.onNodeWithTag("nutrition_meal_save_retry").performClick()
        compose.onNodeWithTag("nutrition_meal_chooser").assertDoesNotExist()
        assertMeal(MealSlot.BREAKFAST, "bf_pho_ga")
        assertEquals("bf_pho_ga", store.value.plan?.meals?.get("1:BREAKFAST"))
        assertEquals("bf_oats", store.value.plan?.meals?.get("0:BREAKFAST"))
    }

    @Test fun switchingUidDismissesPickerAndShowsOnlyTheNewOwnersMeals() {
        val repoA = NutritionRepository("A", MemoryJournal(NutritionJournal(legacy, pending = true)), null, { true })
        val bMeals = legacy.copy(meals = mapOf("0:BREAKFAST" to "bf_pancake_yen_mach"))
        val repoB = NutritionRepository("B", MemoryJournal(NutritionJournal(bMeals, pending = true)), null, { true })
        var owner by mutableStateOf(repoA)
        compose.setContent {
            val repository = owner
            LaunchedEffect(repository) { repository.load() }
            ProfileAppearance(false, "en") {
                CompositionLocalProvider(LocalNutritionRepository provides repository) {
                    NutritionScreen(repository.uid, onBack = {}, onHome = {})
                }
            }
        }
        choose(0, MealSlot.BREAKFAST, "bf_pho_ga")
        assertMeal(MealSlot.BREAKFAST, "bf_pho_ga")
        compose.onNodeWithTag("nutrition_change_breakfast").performScrollTo().performClick()
        compose.onNodeWithTag("nutrition_meal_chooser").assertExists()
        compose.runOnIdle { owner = repoB }
        compose.onNodeWithTag("nutrition_meal_chooser").assertDoesNotExist()
        selectDay(0)
        assertMeal(MealSlot.BREAKFAST, "bf_pancake_yen_mach")
        compose.runOnIdle { owner = repoA }
        selectDay(0)
        assertMeal(MealSlot.BREAKFAST, "bf_pho_ga")
    }

    private fun selectDay(day: Int) {
        compose.onNodeWithTag("nutrition_day_$day").performScrollTo().performClick()
    }

    private fun choose(day: Int, slot: MealSlot, meal: String) {
        selectDay(day)
        compose.onNodeWithTag("nutrition_change_${slot.name.lowercase()}").performScrollTo().performClick()
        chooserItem("nutrition_choice_$meal").performTouchInput { click() }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("nutrition_meal_chooser").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun assertMeal(slot: MealSlot, mealId: String) {
        compose.onNodeWithTag("nutrition_meal_name_${slot.name.lowercase()}").performScrollTo()
            .assertTextEquals(requireNotNull(NutritionMealCatalog.byId(mealId)).en).assertIsDisplayed()
    }

    private fun host(repo: NutritionRepository, dark: Boolean, language: String) {
        compose.setContent {
            LaunchedEffect(repo) { repo.load() }
            ProfileAppearance(dark, language) {
                CompositionLocalProvider(LocalNutritionRepository provides repo) {
                    NutritionScreen(repo.uid, onBack = {}, onHome = {})
                }
            }
        }
    }

    private fun chooserItem(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("nutrition_meal_options").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    private class MemoryJournal(@Volatile var value: NutritionJournal) : NutritionJournalStore {
        override suspend fun read() = value
        @Volatile var failWrites = false
        override suspend fun save(value: NutritionJournal) {
            check(!failWrites) { "Simulated local write failure" }
            this.value = value
        }
    }
}
