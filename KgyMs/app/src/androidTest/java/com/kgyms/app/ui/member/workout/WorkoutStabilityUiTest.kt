package com.kgyms.app.ui.member.workout

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kgyms.app.data.mock.MockMemberWorkoutDataSource
import com.kgyms.app.data.mock.ReferenceMemberWorkoutDataSource
import com.kgyms.app.data.mock.createMockMemberHome
import com.kgyms.app.data.model.MemberProfileEdit
import com.kgyms.app.data.model.MemberWorkoutDataSource
import com.kgyms.app.data.model.User
import com.kgyms.app.data.repository.MemberAccountSession
import com.kgyms.app.data.repository.MemberProfileDataSource
import com.kgyms.app.ui.member.LocalMemberAccount
import com.kgyms.app.ui.member.MemberAccountState
import com.kgyms.app.ui.member.MemberApp
import com.kgyms.app.ui.member.home.MemberHomeAction
import com.kgyms.app.ui.member.home.MemberHomeScreen
import com.kgyms.app.ui.member.profile.ProfileAppearance
import org.junit.*
import org.junit.Assert.*

/** All account data is in memory; these tests never authenticate or write Firebase. */
class WorkoutStabilityUiTest {
    @get:Rule val compose = createComposeRule()
    private val storage get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("kgyms_member_ui", Context.MODE_PRIVATE)
    private var previous: Map<String, *> = emptyMap<String, Any>()

    @Before fun seed() {
        previous = storage.all.toMap()
        storage.edit().clear().putBoolean("dark", false).putString("language", "en").putInt("weekly_target", 6).commit()
    }

    @After fun restore() {
        storage.edit().clear().apply {
            previous.forEach { (key, value) -> when (value) {
                is Boolean -> putBoolean(key, value)
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
        }.commit()
    }

    private fun scroll(tag: String) = compose.onNodeWithTag("workout_list").performScrollToNode(hasTestTag(tag))
    private fun click(tag: String) { scroll(tag); compose.onNodeWithTag(tag).performClick() }

    @Test fun homeWorkoutFinishRestoreRestartAndBackToPlan() {
        val restore = StateRestorationTester(compose)
        restore.setContent { MemberApp() }
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("home_workout_upper-body"))
        compose.onNodeWithTag("home_workout_upper-body").performClick()
        click("workout_start")
        click("mark_press")
        click("workout_start")
        // Back/outside dismiss must not restore the active state.
        androidx.test.espresso.Espresso.pressBack()
        scroll("workout_finished")
        compose.onNodeWithTag("workout_finished").assertIsDisplayed()
        scroll("mark_press")
        compose.onNodeWithTag("mark_press").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithTag("member_tab_home").performClick()
        compose.onNodeWithTag("member_tab_plan").performClick()
        restore.emulateSavedInstanceStateRestore()
        scroll("workout_finished")
        compose.onNodeWithTag("workout_finished").assertExists()
        click("workout_start")
        compose.onNodeWithTag("workout_finished").assertDoesNotExist()
        scroll("mark_press")
        compose.onNodeWithTag("mark_press").assertIsOff().assertIsEnabled()
        compose.onNodeWithTag("workout_back").performClick()
        compose.onNodeWithTag("workout_plan").assertIsSelected()
        compose.onNodeWithTag("member_tab_home").performClick()
        compose.onNodeWithTag("member_screen_home").assertExists()
    }

    @Test fun multipleSessionsOfOneWorkoutHaveSeparateMarksAndPractice() {
        val base = MockMemberWorkoutDataSource.snapshot(System.currentTimeMillis())
        val session = base.sessions.first().copy(id = "A")
        val data = base.copy(sessions = listOf(session, session.copy(id = "B")))
        var entry by mutableStateOf("session:A")
        var request by mutableIntStateOf(0)
        compose.setContent { ProfileAppearance(false, "en") { MemberWorkoutScreen(entry, request, {}, {}, catalog = data) } }
        click("workout_start"); click("mark_press")
        compose.runOnIdle { entry = "session:B"; request++ }
        click("workout_start")
        scroll("mark_press"); compose.onNodeWithTag("mark_press").assertIsOff()
        compose.runOnIdle { entry = "session:A"; request++ }
        scroll("mark_press"); compose.onNodeWithTag("mark_press").assertIsOn()
        compose.runOnIdle { entry = "workout:${session.workoutId}"; request++ }
        click("workout_start")
        scroll("mark_press"); compose.onNodeWithTag("mark_press").assertIsOff()
    }

    @Test fun emptyCatalogInvalidIdsAndMissingExercisesRemainNavigable() {
        val base = ReferenceMemberWorkoutDataSource.snapshot(System.currentTimeMillis())
        var data by mutableStateOf(base.copy(workouts = emptyList(), exercises = emptyList()))
        var entry by mutableStateOf("workout:missing")
        var request by mutableIntStateOf(0)
        compose.setContent { ProfileAppearance(false, "en") { MemberWorkoutScreen(entry, request, {}, {}, catalog = data) } }
        scroll("workout_unavailable"); compose.onNodeWithTag("workout_unavailable").assertExists()
        compose.onNodeWithTag("workout_back").performClick()
        scroll("workout_history_unavailable")
        compose.onNodeWithTag("workout_weekly_goal_count").assertDoesNotExist()
        compose.runOnIdle { entry = "library"; request++ }
        compose.onNodeWithTag("workout_search").performTextInput("missing")
        compose.onNodeWithTag("workout_list").performScrollToNode(hasText("No matching exercises"))
        compose.runOnIdle { data = base.copy(exercises = emptyList()); entry = "workout:upper-body"; request++ }
        scroll("workout_start"); compose.onNodeWithTag("workout_start").assertIsNotEnabled()
        scroll("workout_missing_exercises"); compose.onNodeWithTag("workout_missing_exercises").assertExists()
    }

    @Test fun missingVideoDoesNotInvokePlayerAndWrittenGuideIsAvailable() {
        var calls = 0
        compose.setContent { ProfileAppearance(false, "en") {
            MemberWorkoutScreen("exercise:press", 0, {}, {}, onPlayVideo = { calls++; error("No source") })
        } }
        click("exercise_play")
        compose.onNodeWithText("Video guide is being updated.", substring = true).assertExists()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithTag("workout_list").performScrollToNode(hasText("How to move"))
        compose.onNodeWithText("How to move").assertIsDisplayed()
    }

    @Test fun homeCardsEmitTheirOwnIdsIncludingSecondRecommendation() {
        val base = createMockMemberHome("en")
        val data = base.copy(workouts = listOf(base.workouts.first().copy(id = "full-body", name = "Full body"), base.workouts.first()))
        var last: MemberHomeAction? = null
        compose.setContent { ProfileAppearance(false, "en") { MemberHomeScreen(onAction = { last = it }, data = data) } }
        listOf("full-body", "upper-body").forEach { id ->
            compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("home_workout_$id"))
            compose.onNodeWithTag("home_workout_$id").performClick()
            compose.runOnIdle { assertEquals(MemberHomeAction.RecommendedWorkout(id), last) }
        }
    }

    @Test fun failedSourceCanRetryWithoutCrashing() {
        var fail = true
        val source = MemberWorkoutDataSource { now ->
            check(!fail) { "Unavailable test source" }
            ReferenceMemberWorkoutDataSource.snapshot(now)
        }
        compose.setContent { ProfileAppearance(true, "vi") { MemberWorkoutScreen("hub", 0, {}, {}, dataSource = source) } }
        scroll("workout_source_failed")
        compose.runOnIdle { fail = false }
        click("workout_source_retry")
        scroll("hero_pt-lower")
        compose.onNodeWithTag("hero_pt-lower").assertIsDisplayed()
        compose.onNodeWithTag("workout_source_failed").assertDoesNotExist()
    }

    @Test fun homeEmptyRecommendationsShowUsefulMessages() {
        val base = createMockMemberHome("en")
        val data = base.copy(workouts = listOf(base.workouts.first().copy(id = "")), meals = emptyList())
        compose.setContent { ProfileAppearance(false, "en") { MemberHomeScreen(onAction = {}, data = data) } }
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasText("No reference workouts yet.", substring = true))
        compose.onNodeWithText("No reference workouts yet.", substring = true).assertExists()
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasText("No reference meals yet."))
        compose.onNodeWithText("No reference meals yet.").assertExists()
    }

    @Test fun accountSwitchAndRestorationNeverLeakRunMarks() {
        var uid by mutableStateOf("A")
        val restore = StateRestorationTester(compose)
        restore.setContent {
            val scope = rememberCoroutineScope()
            val account = remember(uid) { MemberAccountState(MemberAccountSession(uid, MemorySource) { uid }, scope) }
            CompositionLocalProvider(LocalMemberAccount provides account) {
                ProfileAppearance(false, "en") { MemberWorkoutScreen("workout:upper-body", 0, {}, {}) }
            }
        }
        click("workout_start"); click("mark_press")
        compose.runOnIdle { uid = "B" }
        click("workout_start")
        scroll("mark_press"); compose.onNodeWithTag("mark_press").assertIsOff()
        restore.emulateSavedInstanceStateRestore()
        scroll("mark_press"); compose.onNodeWithTag("mark_press").assertIsOff()
        compose.runOnIdle { uid = "A" }
        click("workout_start")
        scroll("mark_press"); compose.onNodeWithTag("mark_press").assertIsOff()
    }

    @Test fun productionHomePlanAndProgressKeepUnavailableHistoryAndCurrentIdentity() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val account = remember { MemberAccountState(MemberAccountSession("A", MemorySource) { "A" }, scope) }
            LaunchedEffect(account) { account.load() }
            if (account.user != null) CompositionLocalProvider(LocalMemberAccount provides account) { MemberApp() }
        }
        compose.onNodeWithText("Account A").assertExists()
        compose.onNodeWithText("Not updated • Workout history is not connected.").assertExists()
        compose.onNodeWithText("0 / 6").assertDoesNotExist()
        compose.onNodeWithTag("member_tab_plan").performClick()
        scroll("workout_history_unavailable")
        compose.onNodeWithTag("workout_weekly_goal_count").assertDoesNotExist()
        compose.onNodeWithTag("member_tab_progress").performClick()
        compose.onNodeWithTag("member_screen_progress").assertExists()
        compose.onNodeWithTag("progress_completed").assertDoesNotExist()
        compose.onNodeWithTag("member_tab_home").performClick()
        compose.onNodeWithText("Account A").assertExists()
    }

    private object MemorySource : MemberProfileDataSource {
        override suspend fun load(uid: String) = User(uid = uid, fullName = "Account $uid")
        override suspend fun save(uid: String, edit: MemberProfileEdit) = error("Unexpected Firebase write")
        override suspend fun saveAvatar(uid: String, bytes: ByteArray?) = error("Unexpected upload")
    }
}
