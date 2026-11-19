package com.kgyms.app.ui.member.progress

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.kgyms.app.data.model.AttemptExercise
import com.kgyms.app.data.model.WorkoutAttempt
import com.kgyms.app.data.model.WorkoutHistoryArchive
import com.kgyms.app.data.model.MemberProfileEdit
import com.kgyms.app.data.model.User
import com.kgyms.app.data.model.WorkoutRecord
import com.kgyms.app.data.model.WorkoutRecordStatus
import com.kgyms.app.data.model.memberAccountKey
import com.kgyms.app.data.repository.MemberAccountSession
import com.kgyms.app.data.repository.MemberProfileDataSource
import com.kgyms.app.data.repository.MemberWorkoutHistorySource
import com.kgyms.app.data.repository.WorkoutHistoryRepository
import com.kgyms.app.data.repository.WorkoutHistoryStore
import com.kgyms.app.ui.member.LocalMemberAccount
import com.kgyms.app.ui.member.MemberAccountState
import com.kgyms.app.ui.member.MemberApp
import com.kgyms.app.ui.member.profile.ProfileAppearance
import com.kgyms.app.ui.theme.KgyMsTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class MemberProgressUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun memberTabsAndRestorationKeepProgressReachable() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { KgyMsTheme { MemberApp() } }
        tab("progress").performClick().assertIsSelected()
        show("progress_history_unavailable")
        compose.onNodeWithTag("progress_completed").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        tab("progress").assertIsSelected()
        show("progress_open_plan").performClick()
        tab("plan").assertIsSelected()
        compose.onNodeWithTag("plan_content_workout").assertExists()
        tab("progress").performClick()
        show("progress_history_unavailable")
        Espresso.pressBack()
        tab("home").assertIsSelected()
        compose.onNodeWithTag("member_screen_home").assertExists()
        tab("progress").performClick()
        show("progress_open_home").performClick()
        tab("home").assertIsSelected()
        tab("progress").performClick()
        show("progress_create_plan").performClick()
        tab("plan").assertIsSelected()
        compose.onNodeWithTag("personal_setup").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithTag("plan_content_workout").assertExists()
    }

    @Test fun authenticatedTabsReadOnlyTheCurrentUidTarget() {
        val a = "progress-a-${UUID.randomUUID()}"
        val b = "progress-b-${UUID.randomUUID()}"
        val keyA = "weekly_target_${memberAccountKey(a)}"
        val keyB = "weekly_target_${memberAccountKey(b)}"
        val storage = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("kgyms_member_ui", Context.MODE_PRIVATE)
        storage.edit().putInt(keyA, 2).putInt(keyB, 5).commit()
        var owner by mutableStateOf(a)
        try {
            compose.setContent {
                KgyMsTheme {
                    key(owner) {
                        val uid = owner
                        val scope = rememberCoroutineScope()
                        val account = remember { MemberAccountState(MemberAccountSession(uid, testProfiles) { owner }, scope) }
                        CompositionLocalProvider(LocalMemberAccount provides account) { MemberApp() }
                    }
                }
            }
            tab("progress").performClick()
            show("progress_weekly_target").assertTextContains("2", substring = true)
            show("progress_history_unavailable")
            compose.onNodeWithTag("progress_completed").assertDoesNotExist()
            compose.runOnIdle { owner = b }
            tab("progress").performClick()
            show("progress_weekly_target").assertTextContains("5", substring = true)
            show("progress_history_unavailable")
            compose.onNodeWithTag("progress_completed").assertDoesNotExist()
        } finally {
            storage.edit().remove(keyA).remove(keyB).commit()
        }
    }

    @Test fun connectedHistoryClearsOnUidChangeAndShowsZeroOnlyAfterRead() {
        var uid by mutableStateOf("A")
        val bRead = CompletableDeferred<List<WorkoutRecord>>()
        val source = MemberWorkoutHistorySource { owner ->
            if (owner == "A") listOf(WorkoutRecord("a", System.currentTimeMillis() - 1,
                WorkoutRecordStatus.Completed, true)) else bRead.await()
        }
        compose.setContent {
            ProfileAppearance(false, "en") {
                MemberProgressScreen(uid, 3, {}, {}, source)
            }
        }
        show("progress_completed").assertExists()
        compose.runOnIdle { uid = "B" }
        compose.onNodeWithTag("member_screen_progress").performScrollToNode(hasText("Loading history…"))
        compose.onNodeWithTag("progress_completed").assertDoesNotExist()
        compose.runOnIdle { bRead.complete(emptyList()) }
        show("history_reported_unique").assertTextEquals("0 / 3")
        compose.onNodeWithTag("member_screen_progress").performScrollToNode(hasText("No workout history yet"))
    }

    @Test fun failedReadCanBeRetriedInVietnameseDarkTheme() {
        var attempts = 0
        val source = MemberWorkoutHistorySource {
            if (attempts++ == 0) error("Read failed")
            emptyList()
        }
        compose.setContent {
            ProfileAppearance(true, "vi") {
                MemberProgressScreen("A", 4, {}, {}, source)
            }
        }
        compose.onNodeWithText("Tiến độ tập luyện").assertIsDisplayed()
        compose.onNodeWithTag("member_screen_progress").performScrollToNode(hasText("Thử lại"))
        compose.onNodeWithText("Thử lại").performClick()
        show("history_reported_unique").assertTextEquals("0 / 4")
    }


    @Test fun archiveShowsChartAndExerciseResultsAndClearsDetailOnUidChange() {
        var uid by mutableStateOf("A")
        val now = System.currentTimeMillis() - 1_000
        val attempt = WorkoutAttempt("attempt-ui", "A", "session", "", "workout", "Lượt tập", "Recorded workout",
            now - 1_000, now, listOf(AttemptExercise("ui-performed", true), AttemptExercise("ui-skipped", false)))
        val archive = WorkoutHistoryArchive(listOf(attempt), setOf(attempt.id), remoteKnown = true)
        val history = WorkoutHistoryState(WorkoutHistoryRepository("A", object : WorkoutHistoryStore {
            override suspend fun read() = archive
            override suspend fun update(change: (WorkoutHistoryArchive) -> WorkoutHistoryArchive): WorkoutHistoryArchive =
                error("Progression should not mutate history")
        }))
        compose.setContent {
            LaunchedEffect(Unit) { history.load() }
            ProfileAppearance(false, "en") {
                CompositionLocalProvider(LocalWorkoutHistory provides history) {
                    MemberProgressScreen(uid, 3, {}, {})
                }
            }
        }
        show("progress_activity_chart")
        compose.onNodeWithContentDescription("1 completed sessions", substring = true).assertExists()
        show("history_attempt_attempt-ui").performClick()
        compose.onNodeWithTag("progress_attempt_detail").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("Not done").assertIsDisplayed()
        compose.runOnIdle { uid = "B" }
        compose.onNodeWithTag("progress_attempt_detail").assertDoesNotExist()
        show("progress_history_unavailable")
        compose.onNodeWithTag("history_attempt_attempt-ui").assertDoesNotExist()
    }

    private fun tab(name: String) = compose.onNodeWithTag("member_tab_$name")

    private fun show(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("member_screen_progress").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private val testProfiles = object : MemberProfileDataSource {
        override suspend fun load(uid: String) = User(uid = uid)
        override suspend fun save(uid: String, edit: MemberProfileEdit) = error("Unexpected save")
    }
}
