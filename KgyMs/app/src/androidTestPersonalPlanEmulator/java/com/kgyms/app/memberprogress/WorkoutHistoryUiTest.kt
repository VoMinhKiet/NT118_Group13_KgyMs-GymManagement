package com.kgyms.app.memberprogress

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kgyms.app.data.local.LocalWorkoutHistoryStore
import com.kgyms.app.data.model.*
import com.kgyms.app.data.reference.WorkoutReferenceCatalog
import com.kgyms.app.data.repository.*
import com.kgyms.app.ui.member.*
import com.kgyms.app.ui.member.plan.*
import com.kgyms.app.ui.member.progress.*
import com.kgyms.app.utils.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.TimeZone
import java.util.UUID

class WorkoutHistoryUiTest {
    @get:Rule val compose=createComposeRule()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val uid="m2c-ui-${UUID.randomUUID()}"
    private val catalog=WorkoutReferenceCatalog.snapshot()
    private val now=System.currentTimeMillis();private val zone=TimeZone.getDefault().id
    private fun plan(count:Int)=PersonalPlanGenerator.generate(uid,PersonalPlanConfig(WorkoutLevel.Beginner,count,PlanDates.date(now,zone),zone,
        (0 until count).map{(PlanDates.weekday(PlanDates.date(now,zone),zone)+it)%7}),catalog,now)
    @Composable private fun Host(history:WorkoutHistoryState,plans:PersonalPlanState) {
        val scope=rememberCoroutineScope()
        val account=remember{MemberAccountState(MemberAccountSession(uid,profiles){uid},scope)}
        LaunchedEffect(account){account.load()}
        LaunchedEffect(history){history.observeArchive()};LaunchedEffect(history){history.observeStatus()}
        CompositionLocalProvider(LocalMemberAccount provides account,LocalWorkoutHistory provides history,LocalPersonalPlans provides plans){MemberApp()}
    }
    private fun click(tag:String){compose.onNodeWithTag("workout_list").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()}
    private fun closeFinish(){compose.onNode(hasText("Đã hiểu") or hasText("Got it")).performClick()}
    private fun noTechnicalLabels() {
        listOf("Firebase","đồng bộ","sync","remote","revision","Phòng gym đã xác nhận","Buổi tự ghi nhận").forEach { text ->
            compose.onAllNodes(hasText(text,substring=true,ignoreCase=true)).assertCountEquals(0)
        }
    }
    @Test fun workoutFinishRetakeAndPlanEditProduceRealHistorySharedByHomeAndProgress()=runBlocking<Unit> {
        val history=WorkoutHistoryState(WorkoutHistoryRepository(uid,LocalWorkoutHistoryStore(context,uid)));history.load()
        val plans=PersonalPlanState(MemoryPersonalPlanRepository(uid));plans.load();assertTrue(plans.save(plan(1),catalog,now))
        compose.setContent{Host(history,plans)}
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("personal_today_start"));compose.onNodeWithTag("personal_today_start").performClick()
        click("workout_start");compose.waitUntil(5_000){history.archive.drafts.isNotEmpty()}
        val firstExercise=plans.active!!.sessions.first().exerciseIds.first();click("mark_$firstExercise")
        compose.waitUntil(5_000){history.archive.drafts.values.first().performed.contains(firstExercise)}
        click("workout_start");compose.waitUntil(5_000){history.archive.attempts.size==1};closeFinish()
        assertEquals(AttemptState.LocalPending,history.archive.state(history.archive.attempts.single()))
        click("workout_start");compose.waitUntil(5_000){history.archive.drafts.isNotEmpty()};click("workout_start")
        compose.waitUntil(5_000){history.archive.attempts.size==2};closeFinish()
        compose.onNodeWithTag("member_tab_home").performClick()
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("history_reported_unique"))
        compose.onNodeWithTag("history_reported_unique").assertTextEquals("1 / 1")
        compose.onNodeWithTag("history_completed_label").assert(hasText("buổi hoàn thành") or hasText("sessions completed"))
        compose.onNodeWithTag("progress_completed").assertExists()
        compose.onNodeWithTag("history_retry").assertDoesNotExist();noTechnicalLabels()
        compose.onNodeWithTag("member_tab_progress").performClick()
        compose.onNodeWithTag("member_screen_progress").performScrollToNode(hasTestTag("history_reported_unique"))
        compose.onNodeWithTag("history_reported_unique").assertTextEquals("1 / 1");noTechnicalLabels()
        val original=plans.active!!;assertTrue(plans.saveExercises(original,original.sessions.first().id,listOf("bridge"),ExerciseEditScope.Session,catalog,original.id))
        compose.onNodeWithTag("member_tab_home").performClick();compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("personal_today_start"));compose.onNodeWithTag("personal_today_start").performClick()
        click("workout_start");compose.waitUntil(5_000){history.archive.drafts.isNotEmpty()};click("workout_start")
        compose.waitUntil(5_000){history.archive.attempts.size==3};closeFinish()
        assertEquals(1,history.archive.attempts.map{it.logicalSessionId}.distinct().size)
        assertEquals(2,history.archive.attempts.map{it.planVersionId}.distinct().size)
        assertEquals(3,LocalWorkoutHistoryStore(context,uid).read().attempts.size)
    }
    @Test fun failedHistoryReadOffersFriendlyRetryAndSuccessIsSilent()=runBlocking<Unit> {
        var denied=true
        val remote=object:WorkoutHistoryRemote {
            override val uid=this@WorkoutHistoryUiTest.uid
            override val available=MutableStateFlow(true)
            override val changes=MutableStateFlow(0L)
            override suspend fun fetch():RemoteWorkoutHistory {if(denied)throw WorkoutHistoryDenied();return RemoteWorkoutHistory(emptyList(),emptyList())}
            override suspend fun append(attempt:WorkoutAttempt) {error("No attempts in this test")}
        }
        val history=WorkoutHistoryState(WorkoutHistoryRepository(uid,LocalWorkoutHistoryStore(context,uid),remote));history.load();history.synchronize()
        val plans=PersonalPlanState(MemoryPersonalPlanRepository(uid));plans.load()
        compose.setContent{Host(history,plans)}
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("history_retry"))
        compose.onNodeWithTag("progress_completed").assertDoesNotExist()
        compose.onNodeWithTag("history_retry").assert(hasText("Thử lại") or hasText("Retry"));noTechnicalLabels()
        denied=false;compose.onNodeWithTag("history_retry").performClick()
        compose.waitUntil(5_000){history.sync==HistorySyncState.Synced}
        compose.onNodeWithTag("history_sync_status").assertDoesNotExist()
        compose.onNodeWithTag("history_retry").assertDoesNotExist()
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("history_reported_unique"))
        compose.onNodeWithTag("history_reported_unique").assertTextEquals("0");noTechnicalLabels()
    }
    @Test fun loadingAndUnavailableHistoryNeverShowZeroCompletion()=runBlocking<Unit> {
        val history=WorkoutHistoryState(WorkoutHistoryRepository(uid,LocalWorkoutHistoryStore(context,uid)))
        val plans=PersonalPlanState(MemoryPersonalPlanRepository(uid));plans.load()
        compose.setContent{Host(history,plans)}
        compose.onNodeWithTag("member_tab_progress").performClick()
        compose.onNodeWithTag("progress_completed").assertDoesNotExist()
        compose.onNodeWithTag("history_retry").assertDoesNotExist()
        history.load()
        compose.onNodeWithTag("member_screen_progress").performScrollToNode(hasTestTag("progress_history_unavailable"))
        compose.onNodeWithTag("progress_completed").assertDoesNotExist();noTechnicalLabels()
    }
    @Test fun compactSummaryFitsLargeTextInBothLanguagesAndThemes()=runBlocking<Unit> {
        val history=WorkoutHistoryState(WorkoutHistoryRepository(uid,LocalWorkoutHistoryStore(context,uid)));history.load()
        history.begin(WorkoutRunContext("run","logical","plan","full-body","Toàn thân","Full body",listOf("bridge")),now-1000)
        history.finish("run",now-1)
        var dark by mutableStateOf(true);var language by mutableStateOf("vi")
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {
                com.kgyms.app.ui.member.profile.ProfileAppearance(dark,language) {
                    MemberDashboardAppearance {
                        Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()).padding(20.dp).testTag("compact_summary"),
                            verticalArrangement=Arrangement.spacedBy(8.dp)) {WorkoutHistorySummaryContent(history,3,now)}
                    }
                }
            }
        }
        for((mode,locale) in listOf(true to "vi",false to "vi",false to "en",true to "en")) {
            compose.runOnIdle {dark=mode;language=locale}
            compose.onNodeWithTag("compact_summary").performScrollToNode(hasTestTag("history_reported_unique"))
            compose.onNodeWithTag("history_reported_unique").assertTextEquals("1 / 3")
            compose.onNodeWithTag("history_completed_label").assertTextEquals(if(locale=="vi")"buổi hoàn thành" else "sessions completed")
            for(tag in listOf("history_reported_unique","history_completed_label")) {
                val layouts=mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
                val layout=layouts.single()
                assertFalse("Completion text must wrap without clipping: tag=$tag locale=$locale size=${layout.size} paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}",layout.hasVisualOverflow)
            }
            noTechnicalLabels()
        }
    }
    @Test fun legacyCustomGoalIsNeverOverwrittenByActivePlan()=runBlocking<Unit> {
        val history=WorkoutHistoryState(WorkoutHistoryRepository(uid,LocalWorkoutHistoryStore(context,uid)));history.load()
        val plans=PersonalPlanState(MemoryPersonalPlanRepository(uid));plans.load();assertTrue(plans.save(plan(3),catalog,now))
        // A stored key means an explicit prior choice; no key means use the active plan.
        context.getSharedPreferences("kgyms_member_ui",Context.MODE_PRIVATE).edit().putInt("weekly_target_${memberAccountKey(uid)}",6).commit()
        compose.setContent{Host(history,plans)}
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("history_weekly_target"))
        compose.onNodeWithTag("history_weekly_target").assertTextContains("6",substring=true)
        assertTrue(plans.save(plan(5),catalog,now))
        compose.onNodeWithTag("history_weekly_target").assertTextContains("6",substring=true)
        compose.onNodeWithTag("member_tab_progress").performClick();compose.onNodeWithTag("member_screen_progress").performScrollToNode(hasTestTag("progress_weekly_target"))
        compose.onNodeWithTag("progress_weekly_target").assertTextContains("6",substring=true)
    }
    @Test fun goalWithoutOverrideFollowsActivePlanAcrossHomeAndProgress()=runBlocking<Unit> {
        val history=WorkoutHistoryState(WorkoutHistoryRepository(uid,LocalWorkoutHistoryStore(context,uid)));history.load()
        val plans=PersonalPlanState(MemoryPersonalPlanRepository(uid));plans.load();assertTrue(plans.save(plan(3),catalog,now))
        compose.setContent{Host(history,plans)}
        compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("history_weekly_target"))
        compose.onNodeWithTag("history_weekly_target").assertTextContains("3",substring=true)
        assertTrue(plans.save(plan(5),catalog,now))
        compose.onNodeWithTag("history_weekly_target").assertTextContains("5",substring=true)
        compose.onNodeWithTag("member_tab_progress").performClick();compose.onNodeWithTag("member_screen_progress").performScrollToNode(hasTestTag("progress_weekly_target"))
        compose.onNodeWithTag("progress_weekly_target").assertTextContains("5",substring=true)
    }
    private val profiles=object:MemberProfileDataSource {
        override suspend fun load(uid:String)=User(uid=uid,fullName="History Demo")
        override suspend fun save(uid:String,edit:MemberProfileEdit)=error("Profile writes are not part of M2C")
        override suspend fun saveAvatar(uid:String,bytes:ByteArray?):String=error("Storage is forbidden")
    }
}
