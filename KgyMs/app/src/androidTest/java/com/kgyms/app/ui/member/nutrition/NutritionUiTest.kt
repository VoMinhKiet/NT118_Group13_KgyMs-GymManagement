package com.kgyms.app.ui.member.nutrition

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

        compose.onNodeWithTag("nutrition_meal_breakfast").performScrollTo().performClick()
        compose.onNodeWithTag("nutrition_meal_details").assertExists()
        compose.onNodeWithText("Preparation").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("nutrition_details_change").performScrollTo().performClick()
        chooserItem("nutrition_selected_meal").assertTextContains("Chicken pho", substring = true)
        chooserItem("nutrition_remove_meal").performClick()
        compose.onNodeWithTag("nutrition_confirm_remove").performClick()
        compose.waitUntil(5_000) { repo.view.value.plan?.meals?.size == 6 }
        compose.onNodeWithTag("nutrition_setup").assertDoesNotExist()
        assertEquals(setup, repo.view.value.plan?.setup)
        assertFalse(repo.view.value.plan?.meals?.containsValue("bf_pho_ga") == true)
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
        Espresso.pressBack()
        compose.onNodeWithTag("workout_list").performScrollToNode(hasTestTag("plan_nutrition"))
        compose.onNodeWithTag("plan_nutrition").performClick()
        compose.onNodeWithTag("nutrition_hub").assertIsDisplayed()
        assertEquals(legacy, repo.view.value.plan)
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
        override suspend fun save(value: NutritionJournal) { this.value = value }
    }
}
