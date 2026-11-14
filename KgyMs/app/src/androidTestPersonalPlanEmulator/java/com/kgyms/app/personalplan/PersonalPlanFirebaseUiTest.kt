package com.kgyms.app.personalplan

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kgyms.app.data.firebase.*
import com.kgyms.app.data.local.LocalPersonalPlanRepository
import com.kgyms.app.data.model.*
import com.kgyms.app.data.reference.WorkoutReferenceCatalog
import com.kgyms.app.data.repository.SyncedPersonalPlanRepository
import com.kgyms.app.data.repository.MemberAccountSession
import com.kgyms.app.ui.member.LocalMemberAccount
import com.kgyms.app.ui.member.MemberAccountState
import com.kgyms.app.ui.member.MemberApp
import com.kgyms.app.ui.member.plan.*
import com.kgyms.app.utils.PersonalPlanGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.TimeZone
import java.util.UUID

class PersonalPlanFirebaseUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun sharedHomePlanStatusOfflineEditAndConfirmedWorkoutUseExactRemoteSession() = runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val c=PersonalPlanEmulatorConnection.create(context)
        try {
            val uid=c.signIn("A","ui-${UUID.randomUUID()}"); withTimeout(15_000){c.available.first{it}}
            println("2B UI: initial demo connection ready")
            val now=System.currentTimeMillis(); val zone=TimeZone.getDefault().id; val date=PlanDates.date(now,zone)
            val catalog=WorkoutReferenceCatalog.snapshot()
            val original=PersonalPlanGenerator.seal(PersonalPlanGenerator.generate(uid,PersonalPlanConfig(WorkoutLevel.Beginner,1,date,zone,listOf(PlanDates.weekday(date,zone))),catalog,now))
            val repo=SyncedPersonalPlanRepository(LocalPersonalPlanRepository(context,uid),PersonalPlanFirebaseService(c,uid))
            repo.load(); repo.save(original,null)
            val state=PersonalPlanState(repo); state.load(); state.synchronize()
            assertEquals(PersonalPlanSyncStatus.Synced,state.syncStatus)
            compose.setContent {
                val scope=rememberCoroutineScope()
                val member=remember { MemberAccountState(MemberAccountSession(uid,DemoPersonalPlanMemberSource) { c.auth.currentUser?.uid },scope) }
                LaunchedEffect(member) { member.load() }
                CompositionLocalProvider(LocalPersonalPlans provides state, LocalMemberAccount provides member) { MemberApp() }
            }
            compose.onNode(hasText("Chưa cập nhật • Chưa kết nối lịch sử tập luyện.") or hasText("Not updated • Workout history is not connected.")).assertExists()
            compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("personal_sync_status"))
            compose.onNodeWithTag("personal_sync_status").assert(hasText("Đã đồng bộ với Firebase Emulator") or hasText("Synced with Firebase Emulator"))
            compose.onNodeWithTag("member_tab_plan").performClick()
            compose.onNodeWithTag("workout_list").performScrollToNode(hasTestTag("personal_edit_exercises"))
            compose.onNodeWithTag("personal_edit_exercises").performClick()
            c.setOnline(false)
            val removed=original.sessions.first().exerciseIds.first()
            compose.onNodeWithTag("personal_editor_list").performScrollToNode(hasTestTag("personal_remove_$removed"))
            compose.onNodeWithTag("personal_remove_$removed").performScrollTo().performClick()
            compose.onNodeWithTag("personal_editor_save").assertIsEnabled().performClick()
            compose.waitUntil(5_000){state.archive.plans.size==2}
            println("2B UI: offline edit persisted")
            state.synchronize(); assertEquals(PersonalPlanSyncStatus.Pending,state.syncStatus)
            compose.onNodeWithTag("workout_list").performScrollToNode(hasTestTag("personal_sync_status"))
            compose.onNodeWithTag("personal_sync_status").assert(hasText("Lưu trên thiết bị • Chờ đồng bộ") or hasText("On this device • Pending sync"))
            c.setOnline(true)
            compose.waitUntil(15_000) { c.available.value }
            state.synchronize()
            println("2B UI: reconnected and synchronized")
            assertEquals(PersonalPlanSyncStatus.Synced,state.syncStatus)
            val edited=state.active!!
            compose.onNodeWithTag("member_tab_home").performClick()
            compose.onNodeWithTag("member_screen_home").performScrollToNode(hasTestTag("personal_today_start"))
            compose.onNodeWithTag("personal_today_start").performClick()
            val runtime=edited.runtimeSessionId(edited.sessions.first())
            compose.onNodeWithTag("workout_list").performScrollToNode(hasTestTag("hero_personal-workout-$runtime"))
            compose.onNodeWithTag("hero_personal-workout-$runtime").assertExists()
            compose.onNodeWithTag("workout_list").performScrollToNode(hasTestTag("workout_start"))
            compose.onNodeWithTag("workout_start").performClick()
            edited.sessions.first().exerciseIds.forEach { id -> compose.onNodeWithTag("workout_list").performScrollToNode(hasTestTag("mark_$id")); compose.onNodeWithTag("mark_$id").assertExists() }
            compose.onNodeWithTag("mark_$removed").assertDoesNotExist()
            compose.onNodeWithTag("member_tab_progress").performClick()
            compose.onNodeWithTag("progress_completed").assertDoesNotExist()
            assertEquals(2,PersonalPlanFirebaseService(c,uid).read().records.size)
            repo.close()
        } finally { c.close() }
    }
}
